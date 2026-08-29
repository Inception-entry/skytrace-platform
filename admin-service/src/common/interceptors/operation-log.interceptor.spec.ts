import { ExecutionContext, UnauthorizedException } from '@nestjs/common'
import { Reflector } from '@nestjs/core'
import { lastValueFrom, of, throwError } from 'rxjs'
import { OperationLogInterceptor } from './operation-log.interceptor'
import { LogsService } from '../../logs/logs.service'
import { LOG_METADATA } from '../decorators/log.decorator'

describe('OperationLogInterceptor', () => {
  const logsService = { record: jest.fn().mockResolvedValue(undefined) }
  const reflector = { get: jest.fn() }
  let interceptor: OperationLogInterceptor

  beforeEach(() => {
    jest.clearAllMocks()
    interceptor = new OperationLogInterceptor(
      reflector as unknown as Reflector,
      logsService as unknown as LogsService,
    )
  })

  function contextWith(body: unknown, statusCode = 200): ExecutionContext {
    return {
      getHandler: () => ({}),
      switchToHttp: () => ({
        getRequest: () => ({
          method: 'POST',
          path: '/auth/login',
          body,
          ip: '127.0.0.1',
          user: { id: 1, username: 'alice' },
        }),
        getResponse: () => ({ statusCode }),
      }),
    } as unknown as ExecutionContext
  }

  it('does not log when the handler has no @Log metadata', async () => {
    reflector.get.mockReturnValue(undefined)
    await lastValueFrom(interceptor.intercept(contextWith({ password: 'x' }), { handle: () => of(null) }))
    expect(logsService.record).not.toHaveBeenCalled()
  })

  it('redacts the password before recording a successful login', async () => {
    reflector.get.mockReturnValue({ module: '系统', action: '登录' })
    await lastValueFrom(
      interceptor.intercept(
        contextWith({ username: 'alice', password: 'Admin@123' }),
        { handle: () => of({ access_token: 't' }) },
      ),
    )
    expect(logsService.record).toHaveBeenCalledTimes(1)
    const params = logsService.record.mock.calls[0][0].params as string
    expect(params).toContain('[REDACTED]')
    expect(params).not.toContain('Admin@123')
    expect(logsService.record.mock.calls[0][0].status).toBe(200)
  })

  it('still records one redacted row when the handler fails', async () => {
    reflector.get.mockReturnValue({ module: '系统', action: '登录' })
    await expect(
      lastValueFrom(
        interceptor.intercept(
          contextWith({ username: 'alice', password: 'wrong' }),
          { handle: () => throwError(() => new UnauthorizedException()) },
        ),
      ),
    ).rejects.toBeInstanceOf(UnauthorizedException)
    expect(logsService.record).toHaveBeenCalledTimes(1)
    const recorded = logsService.record.mock.calls[0][0]
    expect(recorded.params).not.toContain('wrong')
    expect(recorded.status).toBe(401)
  })

  it('looks up metadata with the log decorator key', async () => {
    reflector.get.mockReturnValue(undefined)
    await lastValueFrom(interceptor.intercept(contextWith({}), { handle: () => of(null) }))
    expect(reflector.get).toHaveBeenCalledWith(LOG_METADATA, expect.anything())
  })
})