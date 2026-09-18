import {
  AUTH_RATE_WINDOW_MS,
  LOGIN_RATE_MAX,
  REFRESH_RATE_MAX,
  SlidingWindowLimiter,
  loginRateKeys,
  mergeDecisions,
  normalizeIp,
  refreshRateKey,
} from './auth-rate-limit'
import { AuthRateLimiter } from './auth-rate-limiter'

describe('SlidingWindowLimiter', () => {
  it('allows up to max hits then blocks until the window slides', () => {
    const limiter = new SlidingWindowLimiter()
    const now = 1_000_000

    for (let i = 0; i < LOGIN_RATE_MAX; i++) {
      expect(limiter.consume('k', LOGIN_RATE_MAX, AUTH_RATE_WINDOW_MS, now + i).allowed).toBe(true)
    }

    const blocked = limiter.consume('k', LOGIN_RATE_MAX, AUTH_RATE_WINDOW_MS, now + LOGIN_RATE_MAX)
    expect(blocked.allowed).toBe(false)
    expect(blocked.retryAfterSec).toBeGreaterThan(0)

    const afterWindow = limiter.consume('k', LOGIN_RATE_MAX, AUTH_RATE_WINDOW_MS, now + AUTH_RATE_WINDOW_MS + 1)
    expect(afterWindow.allowed).toBe(true)
  })

  it('keeps keys independent', () => {
    const limiter = new SlidingWindowLimiter()
    for (let i = 0; i < LOGIN_RATE_MAX; i++) {
      limiter.consume('a', LOGIN_RATE_MAX, AUTH_RATE_WINDOW_MS, 10 + i)
    }
    expect(limiter.consume('b', LOGIN_RATE_MAX, AUTH_RATE_WINDOW_MS, 20).allowed).toBe(true)
  })
})

describe('rate-limit keys', () => {
  it('normalizes IPv4-mapped IPv6 and empty IP', () => {
    expect(normalizeIp('::ffff:127.0.0.1')).toBe('127.0.0.1')
    expect(normalizeIp('  ')).toBe('unknown')
  })

  it('builds login keys from IP and lowercase username', () => {
    expect(loginRateKeys('1.1.1.1', '  Admin ')).toEqual({
      ipKey: 'login:ip:1.1.1.1',
      userKey: 'login:user:admin',
    })
    expect(loginRateKeys('1.1.1.1')).toEqual({ ipKey: 'login:ip:1.1.1.1' })
    expect(refreshRateKey('::ffff:10.0.0.2')).toBe('refresh:ip:10.0.0.2')
  })

  it('mergeDecisions takes the stricter retry-after', () => {
    expect(mergeDecisions({ allowed: true, retryAfterSec: 0 }, { allowed: true, retryAfterSec: 0 })).toEqual({
      allowed: true,
      retryAfterSec: 0,
    })
    expect(
      mergeDecisions({ allowed: false, retryAfterSec: 3 }, { allowed: false, retryAfterSec: 9 }),
    ).toEqual({ allowed: false, retryAfterSec: 9 })
  })
})

describe('AuthRateLimiter', () => {
  it('blocks a sixth login from the same IP within one minute', () => {
    const limiter = new AuthRateLimiter()
    const now = 5_000
    for (let i = 0; i < LOGIN_RATE_MAX; i++) {
      expect(limiter.consumeLogin('10.0.0.1', `user-${i}`, now + i).allowed).toBe(true)
    }
    expect(limiter.consumeLogin('10.0.0.1', 'other', now + 10).allowed).toBe(false)
    expect(limiter.consumeLogin('10.0.0.2', 'other', now + 11).allowed).toBe(true)
  })

  it('blocks password spray against one username from many IPs', () => {
    const limiter = new AuthRateLimiter()
    const now = 8_000
    for (let i = 0; i < LOGIN_RATE_MAX; i++) {
      expect(limiter.consumeLogin(`10.0.0.${i}`, 'admin', now + i).allowed).toBe(true)
    }
    expect(limiter.consumeLogin('10.0.0.99', 'admin', now + 20).allowed).toBe(false)
    expect(limiter.consumeLogin('10.0.0.99', 'viewer', now + 21).allowed).toBe(true)
  })

  it('blocks refresh after the per-IP max and does not affect login', () => {
    const limiter = new AuthRateLimiter()
    const now = 9_000
    for (let i = 0; i < REFRESH_RATE_MAX; i++) {
      expect(limiter.consumeRefresh('10.1.1.1', now + i).allowed).toBe(true)
    }
    expect(limiter.consumeRefresh('10.1.1.1', now + 50).allowed).toBe(false)
    expect(limiter.consumeLogin('10.1.1.1', 'admin', now + 51).allowed).toBe(true)
  })
})
