import { Injectable, Logger } from '@nestjs/common'
import { consumeRedisWindow } from './auth-redis'
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

type RemoteConsume = (
  key: string,
  max: number,
  windowMs: number,
  now: number,
) => Promise<RateLimitDecision>

@Injectable()
export class AuthRateLimiter {
  private readonly logger = new Logger(AuthRateLimiter.name)
  private readonly window = new SlidingWindowLimiter()
  private readonly remote?: RemoteConsume
  private redisFailedUntil = 0

  constructor(options?: { redisHost?: string; redisPort?: number; remote?: RemoteConsume }) {
    if (options?.remote) {
      this.remote = options.remote
      return
    }
    const host = options?.redisHost?.trim()
    if (!host) return
    const port = options?.redisPort && options.redisPort > 0 ? options.redisPort : 6379
    this.remote = (key, max, windowMs, now) => consumeRedisWindow(host, port, key, max, windowMs, now)
  }

  usesRedis(now = Date.now()): boolean {
    return Boolean(this.remote) && now >= this.redisFailedUntil
  }

  consumeLogin(ip: string, username?: string, now = Date.now()): RateLimitDecision {
    const { ipKey, userKey } = loginRateKeys(ip, username)
    return this.consumeLoginLocal(ipKey, userKey, now)
  }

  consumeRefresh(ip: string, now = Date.now()): RateLimitDecision {
    return this.window.consume(refreshRateKey(ip), REFRESH_RATE_MAX, AUTH_RATE_WINDOW_MS, now)
  }

  consumeLoginShared(ip: string, username?: string, now = Date.now()): Promise<RateLimitDecision> {
    if (!this.usesRedis(now)) return Promise.resolve(this.consumeLogin(ip, username, now))
    const { ipKey, userKey } = loginRateKeys(ip, username)
    return this.consumeLoginRemote(ipKey, userKey, now)
  }

  consumeRefreshShared(ip: string, now = Date.now()): Promise<RateLimitDecision> {
    if (!this.usesRedis(now)) return Promise.resolve(this.consumeRefresh(ip, now))
    return this.consumeRemote(refreshRateKey(ip), REFRESH_RATE_MAX, now)
  }

  private consumeLoginLocal(ipKey: string, userKey: string | undefined, now: number): RateLimitDecision {
    const ipResult = this.window.consume(ipKey, LOGIN_RATE_MAX, AUTH_RATE_WINDOW_MS, now)
    const userResult = userKey
      ? this.window.consume(userKey, LOGIN_RATE_MAX, AUTH_RATE_WINDOW_MS, now)
      : { allowed: true, retryAfterSec: 0 }
    return mergeDecisions(ipResult, userResult)
  }

  private async consumeLoginRemote(ipKey: string, userKey: string | undefined, now: number) {
    const ipResult = await this.consumeRemote(ipKey, LOGIN_RATE_MAX, now)
    const userResult = userKey
      ? await this.consumeRemote(userKey, LOGIN_RATE_MAX, now)
      : { allowed: true, retryAfterSec: 0 }
    return mergeDecisions(ipResult, userResult)
  }

  private async consumeRemote(key: string, max: number, now: number): Promise<RateLimitDecision> {
    try {
      return await this.remote!(key, max, AUTH_RATE_WINDOW_MS, now)
    } catch (error) {
      this.redisFailedUntil = now + 30_000
      this.logger.warn(`Redis 限流不可用，30 秒内退回本进程计数: ${error instanceof Error ? error.message : 'unknown'}`)
      return this.window.consume(key, max, AUTH_RATE_WINDOW_MS, now)
    }
  }
}
