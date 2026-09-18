import { Injectable } from '@nestjs/common'
import {
  AUTH_RATE_WINDOW_MS,
  LOGIN_RATE_MAX,
  REFRESH_RATE_MAX,
  SlidingWindowLimiter,
  loginRateKeys,
  mergeDecisions,
  refreshRateKey,
  type RateLimitDecision,
} from './auth-rate-limit'

@Injectable()
export class AuthRateLimiter {
  private readonly window = new SlidingWindowLimiter()

  consumeLogin(ip: string, username?: string, now = Date.now()): RateLimitDecision {
    const { ipKey, userKey } = loginRateKeys(ip, username)
    const ipResult = this.window.consume(ipKey, LOGIN_RATE_MAX, AUTH_RATE_WINDOW_MS, now)
    const userResult = userKey
      ? this.window.consume(userKey, LOGIN_RATE_MAX, AUTH_RATE_WINDOW_MS, now)
      : { allowed: true, retryAfterSec: 0 }
    return mergeDecisions(ipResult, userResult)
  }

  consumeRefresh(ip: string, now = Date.now()): RateLimitDecision {
    return this.window.consume(refreshRateKey(ip), REFRESH_RATE_MAX, AUTH_RATE_WINDOW_MS, now)
  }
}
