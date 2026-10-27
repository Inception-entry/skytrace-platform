import { INestApplication, ServiceUnavailableException, UnauthorizedException, ValidationPipe } from '@nestjs/common'
import { Test } from '@nestjs/testing'
import type { AddressInfo } from 'node:net'
import { AuthController } from './auth.controller'
import { AuthService } from './auth.service'
import { AuthRateLimitGuard } from './guards/auth-rate-limit.guard'
import { LocalAuthGuard } from './guards/local-auth.guard'
import { REFRESH_COOKIE_NAME } from './refresh-cookie'

jest.mock('minio', () => ({ Client: jest.fn() }))

describe('admin cookie session HTTP contract', () => {
  let app: INestApplication
  let baseUrl: string
  const tokens = { access_token: 'access-token', refresh_token: 'refresh-token', expires_in: 900 }
  const service = { login: jest.fn(), refresh: jest.fn(), logout: jest.fn() }

  beforeAll(async () => {
    const module = await Test.createTestingModule({
      controllers: [AuthController],
      providers: [{ provide: AuthService, useValue: service }],
    })
      .overrideGuard(AuthRateLimitGuard).useValue({ canActivate: () => true })
      .overrideGuard(LocalAuthGuard).useValue({
        canActivate: (context: { switchToHttp: () => { getRequest: () => { user: unknown } } }) => {
          context.switchToHttp().getRequest().user = { id: 1, username: 'admin' }
          return true
        },
      })
      .compile()
    app = module.createNestApplication()
    app.setGlobalPrefix('admin-api')
    app.useGlobalPipes(new ValidationPipe({ whitelist: true, transform: true }))
    await app.listen(0, '127.0.0.1')
    const address = app.getHttpServer().address() as AddressInfo
    baseUrl = `http://127.0.0.1:${address.port}/admin-api/auth`
  })

  afterAll(async () => { await app?.close() })
  beforeEach(() => {
    jest.resetAllMocks()
    service.login.mockResolvedValue(tokens)
    service.refresh.mockResolvedValue(tokens)
    service.logout.mockResolvedValue(undefined)
  })

  const post = (path: string, body = {}, headers: Record<string, string> = {}) => fetch(baseUrl + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body),
  })
  const cookie = { Cookie: `${REFRESH_COOKIE_NAME}=browser-refresh` }
  const csrf = { 'X-Skytrace-CSRF': '1' }

  it('opts the browser login into access-only JSON and an HttpOnly refresh cookie', async () => {
    const response = await post('/login', { username: 'admin', password: 'password' }, { 'X-Skytrace-Session': 'cookie' })
    expect(response.status).toBe(200)
    expect(await response.json()).toEqual({ access_token: 'access-token', expires_in: 900 })
    expect(response.headers.get('Set-Cookie')).toContain('HttpOnly')
    expect(response.headers.get('Set-Cookie')).toContain(`${REFRESH_COOKIE_NAME}=refresh-token`)
    expect(response.headers.get('Cache-Control')).toBe('no-store')
  })

  it('preserves the legacy login JSON response', async () => {
    const response = await post('/login', { username: 'admin', password: 'password' })
    expect(response.status).toBe(200)
    expect(await response.json()).toEqual(tokens)
  })

  it('never exchanges a cookie for a JS-readable refresh token, even without a session-mode header', async () => {
    const response = await post('/refresh', {}, { ...cookie, ...csrf })
    expect(response.status).toBe(200)
    expect(await response.json()).toEqual({ access_token: 'access-token', expires_in: 900 })
    expect(service.refresh).toHaveBeenCalledWith('browser-refresh')
    expect(response.headers.get('Set-Cookie')).toContain('refresh-token')
    expect(response.headers.get('Cache-Control')).toBe('no-store')
  })

  it('rejects cookie refresh without the CSRF header', async () => {
    const response = await post('/refresh', {}, cookie)
    expect(response.status).toBe(403)
    expect(service.refresh).not.toHaveBeenCalled()
  })

  it('keeps body-token clients compatible without requiring a CSRF header', async () => {
    const response = await post('/refresh', { refresh_token: 'legacy-refresh' })
    expect(response.status).toBe(200)
    expect(await response.json()).toEqual(tokens)
    expect(service.refresh).toHaveBeenCalledWith('legacy-refresh')
  })

  it('clears an expired or revoked browser refresh cookie', async () => {
    service.refresh.mockRejectedValue(new UnauthorizedException('expired'))
    const response = await post('/refresh', {}, { ...cookie, ...csrf })
    expect(response.status).toBe(401)
    expect(response.headers.get('Set-Cookie')).toContain('Max-Age=0')
  })

  it('preserves the cookie on a temporary refresh outage', async () => {
    service.refresh.mockRejectedValue(new ServiceUnavailableException('temporary outage'))
    const response = await post('/refresh', {}, { ...cookie, ...csrf })
    expect(response.status).toBe(503)
    expect(response.headers.get('Set-Cookie')).toBeNull()
  })

  it('revokes the browser session without needing a valid access token', async () => {
    const response = await post('/session/logout', {}, { ...cookie, ...csrf })
    expect(response.status).toBe(204)
    expect(service.logout).toHaveBeenCalledWith('browser-refresh')
    expect(response.headers.get('Set-Cookie')).toContain('Max-Age=0')
  })

  it('requires CSRF protection for cookie logout', async () => {
    const response = await post('/session/logout', {}, cookie)
    expect(response.status).toBe(403)
    expect(service.logout).not.toHaveBeenCalled()
  })

  it('does not accept a body token as authorization for the cookie-only logout route', async () => {
    const response = await post('/session/logout', { refresh_token: 'legacy-refresh' }, csrf)
    expect(response.status).toBe(401)
    expect(service.logout).not.toHaveBeenCalled()
  })
})
