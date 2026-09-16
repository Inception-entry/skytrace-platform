import axios from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  ADMIN_LOGOUT_PATH,
  REVOKE_TIMEOUT_MS,
  endAdminSession,
  revokeAdminSession,
} from './sessionRevoke'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('revokeAdminSession', () => {
  it('posts captured access and refresh tokens without reading the store', async () => {
    const post = vi.spyOn(axios, 'post').mockResolvedValue({ status: 204 })

    await revokeAdminSession({
      accessToken: 'access-1',
      refreshToken: 'refresh-1',
    })

    expect(post).toHaveBeenCalledWith(
      ADMIN_LOGOUT_PATH,
      { refresh_token: 'refresh-1' },
      expect.objectContaining({
        timeout: REVOKE_TIMEOUT_MS,
        headers: { Authorization: 'Bearer access-1' },
      }),
    )
  })

  it('does not call logout when there is no refresh token', async () => {
    const post = vi.spyOn(axios, 'post').mockResolvedValue({ status: 204 })

    await revokeAdminSession({
      accessToken: 'access-1',
      refreshToken: null,
    })

    expect(post).not.toHaveBeenCalled()
  })
})

describe('endAdminSession', () => {
  it('clears local auth before revoke and still sends the captured tokens', async () => {
    const calls: string[] = []
    let stored = {
      accessToken: 'access-1' as string | null,
      refreshToken: 'refresh-1' as string | null,
    }
    let revoked: { accessToken: string | null; refreshToken: string | null } | null = null

    await endAdminSession({
      getTokens: () => {
        calls.push('get')
        return { ...stored }
      },
      clearLocalAuth: () => {
        calls.push('clear')
        stored = { accessToken: null, refreshToken: null }
      },
      revokeSession: async tokens => {
        calls.push('revoke')
        expect(stored).toEqual({ accessToken: null, refreshToken: null })
        revoked = tokens
      },
      redirectToLogin: () => {
        calls.push('redirect')
      },
    })

    expect(calls).toEqual(['get', 'clear', 'revoke', 'redirect'])
    expect(revoked).toEqual({
      accessToken: 'access-1',
      refreshToken: 'refresh-1',
    })
  })

  it('redirects even when server revocation fails', async () => {
    const failure = new Error('network')
    const onRevokeFailure = vi.fn()
    const redirectToLogin = vi.fn()

    await endAdminSession({
      getTokens: () => ({ accessToken: 'access-1', refreshToken: 'refresh-1' }),
      clearLocalAuth: () => {},
      revokeSession: async () => {
        throw failure
      },
      redirectToLogin,
      onRevokeFailure,
    })

    expect(onRevokeFailure).toHaveBeenCalledWith(failure)
    expect(redirectToLogin).toHaveBeenCalledTimes(1)
  })
})
