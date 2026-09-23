import {
  CSRF_HEADER,
  REFRESH_COOKIE_NAME,
  readCookie,
  refreshClearCookie,
  refreshSetCookie,
  resolveRefreshCredential,
} from './refresh-cookie'

describe('refresh cookie', () => {
  it('keeps a body refresh token without a CSRF header', () => {
    expect(resolveRefreshCredential({
      bodyToken: 'body-token',
      cookieHeader: `${REFRESH_COOKIE_NAME}=cookie-token`,
    })).toEqual({ ok: true, token: 'body-token', via: 'body' })
  })

  it('accepts the cookie when the CSRF header is present', () => {
    expect(resolveRefreshCredential({
      cookieHeader: `${REFRESH_COOKIE_NAME}=${encodeURIComponent('a.b.c')}`,
      csrfHeader: '1',
    })).toEqual({ ok: true, token: 'a.b.c', via: 'cookie' })
  })

  it('rejects a cookie refresh without the CSRF header', () => {
    expect(resolveRefreshCredential({
      cookieHeader: `${REFRESH_COOKIE_NAME}=cookie-token`,
    })).toEqual({ ok: false, reason: 'csrf' })
  })

  it('rejects a request with neither body nor cookie', () => {
    expect(resolveRefreshCredential({})).toEqual({ ok: false, reason: 'missing' })
  })

  it('sets an HttpOnly cookie and can clear it', () => {
    const set = refreshSetCookie('a.b.c', true)
    expect(set).toContain(`${REFRESH_COOKIE_NAME}=a.b.c`)
    expect(set).toContain('HttpOnly')
    expect(set).toContain('Secure')
    expect(set).toContain('Path=/admin-api')
    expect(set).toContain('SameSite=Lax')
    expect(refreshClearCookie(false)).toContain('Max-Age=0')
    expect(refreshClearCookie(false)).not.toContain('Secure')
    expect(readCookie(set, REFRESH_COOKIE_NAME)).toBe('a.b.c')
  })

  it('names the CSRF header', () => {
    expect(CSRF_HEADER).toBe('x-skytrace-csrf')
  })
})
