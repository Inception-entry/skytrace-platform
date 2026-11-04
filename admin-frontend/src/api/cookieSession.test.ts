import axios from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { loginCookieSession, refreshCookieSession, SESSION_LOCK_NAME, SESSION_TIMEOUT_MS } from './cookieSession'

afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals() })

describe('cookie session transport', () => {
  it('opts into cookie login and never returns a legacy refresh token to callers', async () => {
    const post = vi.spyOn(axios, 'post').mockResolvedValue({ data: {
      access_token: 'access', expires_in: 900, refresh_token: 'must-not-reach-state',
    } })
    await expect(loginCookieSession({ username: 'admin', password: 'password' })).resolves.toEqual({
      access_token: 'access', expires_in: 900,
    })
    expect(post).toHaveBeenCalledWith('/admin-api/auth/login', { username: 'admin', password: 'password' }, {
      withCredentials: true, timeout: SESSION_TIMEOUT_MS,
      headers: { 'X-Skytrace-Session': 'cookie', 'X-Skytrace-CSRF': '1' },
    })
  })

  it('refreshes with an empty body, credentials and the CSRF header', async () => {
    const post = vi.spyOn(axios, 'post').mockResolvedValue({ data: { access_token: 'next', expires_in: 900 } })
    await expect(refreshCookieSession()).resolves.toEqual({ access_token: 'next', expires_in: 900 })
    expect(post).toHaveBeenCalledWith('/admin-api/auth/refresh', {}, {
      withCredentials: true, timeout: SESSION_TIMEOUT_MS, headers: { 'X-Skytrace-CSRF': '1' },
    })
  })

  it('uses the shared browser session lock when supported', async () => {
    const request = vi.fn(async (_name: string, operation: () => Promise<unknown>) => operation())
    vi.stubGlobal('navigator', { locks: { request } })
    vi.spyOn(axios, 'post').mockResolvedValue({ data: { access_token: 'next', expires_in: 900 } })
    await refreshCookieSession()
    expect(request).toHaveBeenCalledWith(SESSION_LOCK_NAME, expect.any(Function))
  })

  it('rejects a malformed successful response', async () => {
    vi.spyOn(axios, 'post').mockResolvedValue({ data: { refresh_token: 'not-an-access-token' } })
    await expect(refreshCookieSession()).rejects.toThrow('INVALID_SESSION_RESPONSE')
  })
})
