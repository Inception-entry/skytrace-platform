export const LOGIN_RATE_MAX = 5
export const REFRESH_RATE_MAX = 20
export const AUTH_RATE_WINDOW_MS = 60_000

export type RateLimitDecision = {
  allowed: boolean
  retryAfterSec: number
}

export class SlidingWindowLimiter {
  private readonly hits = new Map<string, number[]>()

  consume(key: string, max: number, windowMs: number, now = Date.now()): RateLimitDecision {
    const cutoff = now - windowMs
    const prev = (this.hits.get(key) ?? []).filter((t) => t > cutoff)
    if (prev.length >= max) {
      this.hits.set(key, prev)
      const retryAfterMs = Math.max(0, prev[0] + windowMs - now)
      return { allowed: false, retryAfterSec: Math.max(1, Math.ceil(retryAfterMs / 1000)) }
    }
    prev.push(now)
    this.hits.set(key, prev)
    return { allowed: true, retryAfterSec: 0 }
  }
}

export function loginRateKeys(ip: string, username?: string): { ipKey: string; userKey?: string } {
  const ipKey = `login:ip:${normalizeIp(ip)}`
  const normalized = username?.trim().toLowerCase()
  if (!normalized) return { ipKey }
  return { ipKey, userKey: `login:user:${normalized}` }
}

export function refreshRateKey(ip: string): string {
  return `refresh:ip:${normalizeIp(ip)}`
}

export function normalizeIp(ip: string | undefined): string {
  const value = (ip ?? '').trim()
  if (!value) return 'unknown'
  return value.replace(/^::ffff:/, '')
}

export function mergeDecisions(...decisions: RateLimitDecision[]): RateLimitDecision {
  const blocked = decisions.filter((d) => !d.allowed)
  if (blocked.length === 0) return { allowed: true, retryAfterSec: 0 }
  return {
    allowed: false,
    retryAfterSec: Math.max(...blocked.map((d) => d.retryAfterSec)),
  }
}
