import axios from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ADMIN_LOGOUT_PATH, REVOKE_TIMEOUT_MS, endAdminSession, revokeAdminSession } from './sessionRevoke'

afterEach(() => { vi.restoreAllMocks() })

describe('cookie session revocation', () => {
  it('uses the cookie and CSRF header without an access or refresh token', async () => {
    const post = vi.spyOn(axios, 'post').mockResolvedValue({ status: 204 })
    await revokeAdminSession()
    expect(post).toHaveBeenCalledWith(
      ADMIN_LOGOUT_PATH,
      {},
      expect.objectContaining({
        timeout: REVOKE_TIMEOUT_MS,
        withCredentials: true,
        headers: { 'X-Skytrace-CSRF': '1' },
      }),
    )
  })

  it('clears local auth and waits for rotation before revoking the current cookie', async () => {
    const calls: string[] = []
    await endAdminSession({
      clearLocalAuth: () => { calls.push('clear') },
      waitForRefresh: async () => { calls.push('refresh-finished') },
      revokeSession: async () => { calls.push('revoke') },
      redirectToLogin: () => { calls.push('redirect') },
    })
    expect(calls).toEqual(['clear', 'refresh-finished', 'revoke', 'redirect'])
  })

  it('still revokes the cookie if a pending refresh failed', async () => {
    const revokeSession = vi.fn(async () => {})
    await endAdminSession({
      clearLocalAuth: () => {},
      waitForRefresh: async () => { throw new Error('refresh-failed') },
      revokeSession,
      redirectToLogin: () => {},
    })
    expect(revokeSession).toHaveBeenCalledOnce()
  })

  it('reports a revocation failure and still returns to login', async () => {
    const failure = new Error('network')
    const onRevokeFailure = vi.fn()
    const redirectToLogin = vi.fn()
    await endAdminSession({
      clearLocalAuth: () => {},
      revokeSession: async () => { throw failure },
      redirectToLogin,
      onRevokeFailure,
    })
    expect(onRevokeFailure).toHaveBeenCalledWith(failure)
    expect(redirectToLogin).toHaveBeenCalledOnce()
  })
})
