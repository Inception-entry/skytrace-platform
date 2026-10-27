import axios from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  ADMIN_ME_PATH,
  AdminLoginRejectedError,
  AdminProfileLoadError,
  AdminSessionChangedError,
  establishAdminSession,
  loadAdminProfile,
  loginFailureMessage,
} from './sessionEstablish'

const user = {
  id: 1,
  username: 'admin',
  nickname: null,
  email: null,
  avatar: null,
  roles: [],
  permissions: [],
  menus: [],
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('loadAdminProfile', () => {
  it('loads /me with the captured access token, not the store', async () => {
    const get = vi.spyOn(axios, 'get').mockResolvedValue({ data: user })

    await expect(loadAdminProfile('access-1')).resolves.toEqual(user)
    expect(get).toHaveBeenCalledWith(
      ADMIN_ME_PATH,
      expect.objectContaining({
        headers: { Authorization: 'Bearer access-1' },
      }),
    )
  })
})

describe('establishAdminSession', () => {
  it('does not commit or rollback when login fails', async () => {
    const login = vi.fn(async () => {
      throw new Error('401')
    })
    const loadMe = vi.fn()
    const commit = vi.fn()
    const rollback = vi.fn()

    await expect(
      establishAdminSession(
        { login, loadMe, commit, rollback },
        { username: 'admin', password: 'bad' },
      ),
    ).rejects.toBeInstanceOf(AdminLoginRejectedError)
    expect(loadMe).not.toHaveBeenCalled()
    expect(commit).not.toHaveBeenCalled()
    expect(rollback).not.toHaveBeenCalled()
  })

  it('rolls back the cookie session and never commits when /me fails', async () => {
    const login = vi.fn(async () => ({
      access_token: 'access-1',
      expires_in: 900,
    }))
    const loadMe = vi.fn(async () => {
      throw new Error('me-failed')
    })
    const commit = vi.fn()
    const rollback = vi.fn(async () => {})

    await expect(
      establishAdminSession(
        { login, loadMe, commit, rollback },
        { username: 'admin', password: 'ok' },
      ),
    ).rejects.toBeInstanceOf(AdminProfileLoadError)
    expect(commit).not.toHaveBeenCalled()
    expect(rollback).toHaveBeenCalledWith()
  })

  it('commits only after login and /me both succeed', async () => {
    const tokens = {
      access_token: 'access-1',
      expires_in: 900,
    }
    const login = vi.fn(async () => tokens)
    const loadMe = vi.fn(async () => user)
    const commit = vi.fn()
    const rollback = vi.fn()

    await establishAdminSession(
      { login, loadMe, commit, rollback },
      { username: 'admin', password: 'ok' },
    )

    expect(commit).toHaveBeenCalledWith(tokens, user)
    expect(rollback).not.toHaveBeenCalled()
  })

  it('still fails the login when rollback itself fails', async () => {
    const onRollbackFailure = vi.fn()
    const rollbackError = new Error('revoke-failed')

    await expect(
      establishAdminSession(
        {
          login: async () => ({
            access_token: 'access-1',
            expires_in: 900,
          }),
          loadMe: async () => {
            throw new Error('me-failed')
          },
          commit: vi.fn(),
          rollback: async () => {
            throw rollbackError
          },
          onRollbackFailure,
        },
        { username: 'admin', password: 'ok' },
      ),
    ).rejects.toBeInstanceOf(AdminProfileLoadError)
    expect(onRollbackFailure).toHaveBeenCalledWith(rollbackError)
  })

  it('ignores a login response after another tab changes the session', async () => {
    let current = true
    const loadMe = vi.fn()
    const commit = vi.fn()
    const rollback = vi.fn()
    await expect(establishAdminSession({
      login: async () => { current = false; return { access_token: 'old', expires_in: 900 } },
      loadMe, commit, rollback, isCurrent: () => current,
    }, { username: 'admin', password: 'ok' })).rejects.toBeInstanceOf(AdminSessionChangedError)
    expect(loadMe).not.toHaveBeenCalled()
    expect(commit).not.toHaveBeenCalled()
    expect(rollback).not.toHaveBeenCalled()
  })

  it.each([false, true])('never commits or revokes a changed session after /me settles (failure=%s)', async fail => {
    let current = true
    const commit = vi.fn()
    const rollback = vi.fn()
    await expect(establishAdminSession({
      login: async () => ({ access_token: 'old', expires_in: 900 }),
      loadMe: async () => {
        current = false
        if (fail) throw new Error('old profile failed')
        return user
      },
      commit, rollback, isCurrent: () => current,
    }, { username: 'admin', password: 'ok' })).rejects.toBeInstanceOf(AdminSessionChangedError)
    expect(commit).not.toHaveBeenCalled()
    expect(rollback).not.toHaveBeenCalled()
  })
})

describe('loginFailureMessage', () => {
  it('does not call a profile failure a password error', () => {
    expect(loginFailureMessage(new AdminLoginRejectedError())).toBe(
      '用户名或密码错误',
    )
    expect(loginFailureMessage(new AdminProfileLoadError())).toBe(
      '登录成功但无法获取用户信息，已撤销会话',
    )
    expect(loginFailureMessage(new AdminSessionChangedError())).toBe(
      '会话已在其他页面变更，请重新登录',
    )
  })
})
