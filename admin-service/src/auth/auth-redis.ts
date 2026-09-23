import { connect } from 'net'

import type { RateLimitDecision } from './auth-rate-limit'

const SLIDING_WINDOW_LUA = `
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local maxn = tonumber(ARGV[3])
local member = ARGV[4]
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - window)
local count = redis.call('ZCARD', KEYS[1])
if count >= maxn then
  local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
  local retry = 1
  if oldest[2] ~= nil then
    retry = math.ceil((tonumber(oldest[2]) + window - now) / 1000)
    if retry < 1 then retry = 1 end
  end
  return {0, retry}
end
redis.call('ZADD', KEYS[1], now, member)
redis.call('PEXPIRE', KEYS[1], window)
return {1, 0}
`

class IncompleteReply extends Error {}

export function decisionFromReply(reply: unknown): RateLimitDecision {
  if (!Array.isArray(reply) || reply.length < 2) {
    throw new Error('限流响应无法解析')
  }
  const allowed = Number(reply[0]) === 1
  const retryAfterSec = Number(reply[1])
  if (!Number.isFinite(retryAfterSec)) {
    throw new Error('限流响应无法解析')
  }
  return {
    allowed,
    retryAfterSec: allowed ? 0 : Math.max(1, retryAfterSec),
  }
}

export function encodeCommand(args: string[]): Buffer {
  const chunks: Buffer[] = [Buffer.from(`*${args.length}\r\n`)]
  for (const arg of args) {
    const body = Buffer.from(arg)
    chunks.push(Buffer.from(`$${body.length}\r\n`), body, Buffer.from('\r\n'))
  }
  return Buffer.concat(chunks)
}

export function decodeReply(buffer: Buffer, offset = 0): { value: unknown; offset: number } {
  if (offset >= buffer.length) throw new IncompleteReply()
  const type = String.fromCharCode(buffer[offset])
  const lineEnd = buffer.indexOf('\r\n', offset)
  if (lineEnd < 0) throw new IncompleteReply()
  const line = buffer.subarray(offset + 1, lineEnd).toString('utf8')
  const next = lineEnd + 2
  if (type === '+') return { value: line, offset: next }
  if (type === '-') throw new Error(line)
  if (type === ':') return { value: Number(line), offset: next }
  if (type === '$') {
    const length = Number(line)
    if (length < 0) return { value: null, offset: next }
    if (buffer.length < next + length + 2) throw new IncompleteReply()
    return {
      value: buffer.subarray(next, next + length).toString('utf8'),
      offset: next + length + 2,
    }
  }
  if (type === '*') {
    const count = Number(line)
    if (count < 0) return { value: null, offset: next }
    const items: unknown[] = []
    let cursor = next
    for (let index = 0; index < count; index += 1) {
      const item = decodeReply(buffer, cursor)
      items.push(item.value)
      cursor = item.offset
    }
    return { value: items, offset: cursor }
  }
  throw new Error(`无法解析 Redis 响应: ${type}`)
}

export function redisCommand(
  host: string,
  port: number,
  args: string[],
  timeoutMs = 500,
): Promise<unknown> {
  return new Promise((resolve, reject) => {
    const socket = connect({ host, port })
    const chunks: Buffer[] = []
    let settled = false
    const finish = (error?: Error, value?: unknown) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      socket.destroy()
      if (error) reject(error)
      else resolve(value)
    }
    const timer = setTimeout(() => finish(new Error('Redis 超时')), timeoutMs)
    socket.on('error', (error) => finish(error))
    socket.on('data', (chunk) => {
      chunks.push(chunk)
      try {
        const parsed = decodeReply(Buffer.concat(chunks))
        finish(undefined, parsed.value)
      } catch (error) {
        if (error instanceof IncompleteReply) return
        finish(error instanceof Error ? error : new Error('Redis 响应失败'))
      }
    })
    socket.on('connect', () => {
      socket.write(encodeCommand(args))
    })
  })
}

export async function consumeRedisWindow(
  host: string,
  port: number,
  key: string,
  max: number,
  windowMs: number,
  now: number,
): Promise<RateLimitDecision> {
  const reply = await redisCommand(host, port, [
    'EVAL',
    SLIDING_WINDOW_LUA,
    '1',
    `skytrace:admin:auth:${key}`,
    String(now),
    String(windowMs),
    String(max),
    `${now}:${Math.random().toString(16).slice(2)}`,
  ])
  return decisionFromReply(reply)
}
