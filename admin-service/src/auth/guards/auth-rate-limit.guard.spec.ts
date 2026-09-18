import { ExecutionContext, HttpException, HttpStatus } from '@nestjs/common'
import { Reflector } from '@nestjs/core'
import { LOGIN_RATE_MAX, REFRESH_RATE_MAX } from '../auth-rate-limit'
import { AuthRateLimiter } from '../auth-rate-limiter'
import { AUTH_RATE_LIMIT_KIND } from '../decorators/auth-rate-limit.decorator'
import { AuthRateLimitGuard } from './auth-rate-limit.guard'

function mockContext(req: Record<string, unknown>) {
  const res = { setHeader: jest.fn() }
  const ctx = {
    getHandler: () => ({}),
    getClass: () => ({}),
    switchToHttp: () => ({
      getRequest: () => req,
      getResponse: () => res,
    }),
  } as unknown as ExecutionContext
  return { ctx, res }
}

describe('AuthRateLimitGuard', () => {
  let limiter: AuthRateLimiter
  let reflector: Reflector
  let guard: AuthRateLimitGuard

  beforeEach(() => {
    limiter = new AuthRateLimiter()
    reflector = new Reflector()
    jest.spyOn(reflector, 'getAllAndOverride')
    guard = new AuthRateLimitGuard(limiter, reflector)
  })

  it('skips when the handler has no rate-limit metadata', () => {
    jest.spyOn(reflector, 'getAllAndOverride').mockReturnValue(undefined)
    const { ctx } = mockContext({ ip: '1.1.1.1' })
    expect(guard.canActivate(ctx)).toBe(true)
  })

  it('returns 429 with Retry-After after too many logins from one IP', () => {
    jest.spyOn(reflector, 'getAllAndOverride').mockReturnValue('login')
    const { ctx, res } = mockContext({ ip: '8.8.8.8', body: { username: 'admin' } })
    for (let i = 0; i < LOGIN_RATE_MAX; i++) {
      expect(guard.canActivate(ctx)).toBe(true)
    }
    try {
      guard.canActivate(ctx)
      fail('expected 429')
    } catch (err) {
      expect(err).toBeInstanceOf(HttpException)
      expect((err as HttpException).getStatus()).toBe(HttpStatus.TOO_MANY_REQUESTS)
      expect((err as HttpException).getResponse()).toEqual({
        statusCode: 429,
        message: '请求过于频繁，请稍后再试',
      })
      expect(res.setHeader).toHaveBeenCalledWith('Retry-After', expect.any(String))
    }
  })

  it('rate-limits refresh independently of login', () => {
    jest.spyOn(reflector, 'getAllAndOverride').mockImplementation((key) => {
      if (key === AUTH_RATE_LIMIT_KIND) return 'refresh'
      return undefined
    })
    const { ctx } = mockContext({ ip: '9.9.9.9', body: { refresh_token: 'x' } })
    for (let i = 0; i < REFRESH_RATE_MAX; i++) {
      expect(guard.canActivate(ctx)).toBe(true)
    }
    expect(() => guard.canActivate(ctx)).toThrow(HttpException)
  })
})
