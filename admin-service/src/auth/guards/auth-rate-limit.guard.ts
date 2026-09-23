import {
  CanActivate,
  ExecutionContext,
  HttpException,
  HttpStatus,
  Injectable,
} from '@nestjs/common'
import { Reflector } from '@nestjs/core'
import { AuthRateLimiter } from '../auth-rate-limiter'
import { AUTH_RATE_LIMIT_KIND, AuthRateLimitKind } from '../decorators/auth-rate-limit.decorator'
import { normalizeIp } from '../auth-rate-limit'

@Injectable()
export class AuthRateLimitGuard implements CanActivate {
  constructor(
    private readonly limiter: AuthRateLimiter,
    private readonly reflector: Reflector,
  ) {}

  canActivate(context: ExecutionContext): boolean | Promise<boolean> {
    const kind = this.reflector.getAllAndOverride<AuthRateLimitKind | undefined>(AUTH_RATE_LIMIT_KIND, [
      context.getHandler(),
      context.getClass(),
    ])
    if (!kind) return true

    const req = context.switchToHttp().getRequest<{
      ip?: string
      socket?: { remoteAddress?: string }
      body?: { username?: unknown }
    }>()
    const res = context.switchToHttp().getResponse<{ setHeader?(name: string, value: string): void }>()
    const ip = normalizeIp(req.ip || req.socket?.remoteAddress)
    const username = typeof req.body?.username === 'string' ? req.body.username : undefined

    if (this.limiter.usesRedis()) {
      const pending = kind === 'refresh'
        ? this.limiter.consumeRefreshShared(ip)
        : this.limiter.consumeLoginShared(ip, username)
      return pending.then((decision) => this.apply(decision, res))
    }
    const decision = kind === 'refresh' ? this.limiter.consumeRefresh(ip) : this.limiter.consumeLogin(ip, username)
    return this.apply(decision, res)
  }

  private apply(
    decision: { allowed: boolean; retryAfterSec: number },
    res: { setHeader?(name: string, value: string): void },
  ): boolean {
    if (decision.allowed) return true
    res.setHeader?.('Retry-After', String(decision.retryAfterSec))
    throw new HttpException(
      { statusCode: HttpStatus.TOO_MANY_REQUESTS, message: '请求过于频繁，请稍后再试' },
      HttpStatus.TOO_MANY_REQUESTS,
    )
  }
}
