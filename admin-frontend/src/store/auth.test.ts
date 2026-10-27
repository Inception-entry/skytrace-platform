import { describe, expect, it, vi } from 'vitest'
import { createAdminAuthStore } from './auth'
import type { LoginResponse } from '../types'

const user = { id: 1, username: 'admin', nickname: null, email: null, avatar: null, roles: [], permissions: [], menus: [] }
const tokens = { access_token: 'access', expires_in: 900 }

function harness(overrides: { refresh?: () => Promise<LoginResponse>; loadMe?: () => Promise<typeof user> } = {}) {
  const deps = {
    refresh: vi.fn(overrides.refresh ?? (async () => tokens)),
    loadMe: vi.fn(overrides.loadMe ?? (async () => user)),
    revoke: vi.fn(async () => {}), redirect: vi.fn(), notify: vi.fn(), onRevokeFailure: vi.fn(),
  }
  return { store: createAdminAuthStore(deps), deps }
}

describe('in-memory admin session', () => {
  it('restores once through the cookie before exposing the protected session', async () => {
    const { store, deps } = harness()
    expect(store.getState().accessToken).toBeNull()
    await Promise.all([store.getState().restoreSession(), store.getState().restoreSession()])
    expect(deps.refresh).toHaveBeenCalledOnce()
    expect(deps.loadMe).toHaveBeenCalledWith('access')
    expect(store.getState()).toMatchObject({ status: 'authenticated', accessToken: 'access', user })
    expect(store.getState()).not.toHaveProperty('refreshToken')
    expect(deps.notify).not.toHaveBeenCalled()
  })

  it('does not treat an expired cookie as a transient server error', async () => {
    const { store } = harness({ refresh: async () => { throw { response: { status: 401 } } } })
    await store.getState().restoreSession()
    expect(store.getState()).toMatchObject({ status: 'anonymous', accessToken: null, user: null })
  })

  it('keeps temporary outages retryable without revoking the cookie', async () => {
    let attempt = 0
    const { store, deps } = harness({ refresh: async () => {
      if (++attempt === 1) throw { response: { status: 503 } }
      return tokens
    } })
    await store.getState().restoreSession()
    expect(store.getState().status).toBe('unavailable')
    expect(deps.revoke).not.toHaveBeenCalled()
    await store.getState().restoreSession()
    expect(store.getState().status).toBe('authenticated')
  })

  it('does not expose a half-restored session when profile loading fails', async () => {
    const { store } = harness({ loadMe: async () => { throw new Error('network') } })
    await store.getState().restoreSession()
    expect(store.getState()).toMatchObject({ status: 'unavailable', accessToken: null, user: null })
  })

  it('shares a refresh between bootstrap and API requests', async () => {
    const { store, deps } = harness()
    await Promise.all([store.getState().restoreSession(), store.getState().refreshAccessToken()])
    expect(deps.refresh).toHaveBeenCalledOnce()
  })

  it('waits for a pending rotation before logout and never restores its access token', async () => {
    let finish!: (value: LoginResponse) => void
    const pending = new Promise<LoginResponse>(resolve => { finish = resolve })
    const { store, deps } = harness({ refresh: () => pending })
    store.getState().setSession(tokens, user)
    const refresh = store.getState().refreshAccessToken()
    const rejected = expect(refresh).rejects.toThrow('SESSION_CHANGED')
    const firstLogout = store.getState().logout()
    const secondLogout = store.getState().logout()
    expect(store.getState()).toMatchObject({ status: 'logging-out', accessToken: null, user: null })
    expect(deps.revoke).not.toHaveBeenCalled()
    finish({ access_token: 'stale', expires_in: 900 })
    await rejected
    await Promise.all([firstLogout, secondLogout])
    expect(deps.revoke).toHaveBeenCalledOnce()
    expect(deps.redirect).toHaveBeenCalledOnce()
    expect(store.getState()).toMatchObject({ status: 'anonymous', accessToken: null, user: null })
  })

  it('does not let an older rejected refresh expire a newer login', async () => {
    let reject!: (error: unknown) => void
    const pending = new Promise<LoginResponse>((_resolve, fail) => { reject = fail })
    const { store } = harness({ refresh: () => pending })
    const refreshing = store.getState().refreshAccessToken()
    const rejected = expect(refreshing).rejects.toThrow('SESSION_CHANGED')
    store.getState().setSession({ access_token: 'new-login', expires_in: 900 }, user)
    reject({ response: { status: 401 } })
    await rejected
    expect(store.getState()).toMatchObject({ status: 'authenticated', accessToken: 'new-login' })
  })

  it('ignores a profile response that arrives after another tab signed out', async () => {
    let finish!: (value: typeof user) => void
    let entered!: () => void
    const ready = new Promise<void>(resolve => { entered = resolve })
    const { store } = harness({ loadMe: () => { entered(); return new Promise(resolve => { finish = resolve }) } })
    const restoring = store.getState().restoreSession()
    await ready
    store.getState().expireSession()
    finish(user)
    await restoring
    expect(store.getState()).toMatchObject({ status: 'anonymous', accessToken: null, user: null })
  })
})
