import { Injectable } from '@nestjs/common'
import { SlidingWindowLimiter } from '../auth/auth-rate-limit'
import { ClientLogDto } from './client-log.dto'

function redact(value: string): string {
  return value.replace(/token|password|authorization|secret|cookie|credential/gi, '[redacted]')
}

@Injectable()
export class ClientLogService {
  private readonly limiter = new SlidingWindowLimiter()

  accept(entry: ClientLogDto, ip: string | undefined, now = Date.now()): boolean {
    const decision = this.limiter.consume(`client-log:${ip?.trim() || 'unknown'}`, 30, 60_000, now)
    if (!decision.allowed) return false
    const line = formatAdminClientLog(entry)
    if (entry.level === 'error') console.error(line)
    else console.warn(line)
    return true
  }
}

export function formatAdminClientLog(entry: ClientLogDto): string {
  const message = redact(entry.message).slice(0, 300)
  const path = redact(entry.path ?? '').slice(0, 200)
  const detail = formatDetail(entry.detail)
  const head = `[skytrace-admin] ${new Date().toISOString()} ${entry.level.toUpperCase()} ${entry.scope} ${message} path=${path}`
  return detail ? `${head} ${detail}` : head
}

function formatDetail(detail: ClientLogDto['detail']): string {
  if (!detail) return ''
  const parts: string[] = []
  if (detail.status != null) parts.push(`status=${detail.status}`)
  if (detail.method) parts.push(`method=${detail.method.toUpperCase()}`)
  if (detail.url) parts.push(`url=${stripQuery(detail.url)}`)
  if (detail.code) parts.push(`code=${redact(detail.code)}`)
  if (detail.serverMessage) parts.push(`serverMessage=${redact(detail.serverMessage)}`)
  return parts.join(' ')
}

function stripQuery(url: string): string {
  const query = url.indexOf('?')
  return redact(query === -1 ? url : url.slice(0, query))
}

