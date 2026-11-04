import axios, { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { describe, expect, it, vi } from 'vitest'
import { attachAdminAuthInterceptors } from './client'

type MockResult = { status: number; data?: unknown }

function authorization(config: InternalAxiosRequestConfig): string {
  return String(config.headers.get('Authorization') ?? '')
}

function createHarness(options: {
  accessToken?: string | null
  refresh?: () => Promise<string>
  handler?: (config: InternalAxiosRequestConfig) => Promise<MockResult>
} = {}) {
  let accessToken = options.accessToken === undefined ? 'expired-access' : options.accessToken
  let version = 0
  const onSessionExpired = vi.fn(() => { accessToken = null; version += 1 })
  const refresh = vi.fn(options.refresh ?? (async () => 'new-access'))
  const handler = options.handler ?? (async config =>
    authorization(config) === 'Bearer new-access' ? { status: 200, data: { ok: true } } : { status: 401 })
  const client = axios.create({
    baseURL: '/admin-api',
    adapter: async config => {
      const result = await handler(config)
      const response = { data: result.data ?? {}, status: result.status, statusText: 'test', headers: {}, config } as AxiosResponse
      if (result.status >= 400) throw new AxiosError('request failed', String(result.status), config, null, response)
      return response
    },
  })
  attachAdminAuthInterceptors(client, {
    getAccessToken: () => accessToken,
    getSessionVersion: () => version,
    onSessionExpired,
    refreshAccessToken: async () => { accessToken = await refresh(); return accessToken },
  })
  return {
    client, refresh, onSessionExpired,
    changeSession: () => { version += 1; accessToken = 'another-user-access' },
  }
}

function rejectedSession() {
  return Object.assign(new Error('expired cookie'), { response: { status: 401 } })
}

describe('admin cookie refresh interceptor', () => {
  it('can refresh using only the browser cookie after in-memory state is lost', async () => {
    const { client, refresh } = createHarness({ accessToken: null })
    await expect(client.get('/users')).resolves.toMatchObject({ data: { ok: true } })
    expect(refresh).toHaveBeenCalledOnce()
  })

  it('rejects all queued requests when the cookie is expired', async () => {
    const error = rejectedSession()
    const { client, refresh, onSessionExpired } = createHarness({ refresh: async () => { throw error } })
    const results = await Promise.allSettled([client.get('/users'), client.get('/roles')])
    expect(results).toEqual([{ status: 'rejected', reason: error }, { status: 'rejected', reason: error }])
    expect(refresh).toHaveBeenCalledOnce()
    expect(onSessionExpired).toHaveBeenCalledOnce()
  })

  it('single-flights ten concurrent 401s and replays them', async () => {
    const { client, refresh } = createHarness({
      refresh: async () => { await new Promise(resolve => setTimeout(resolve, 20)); return 'new-access' },
    })
    const results = await Promise.all(Array.from({ length: 10 }, () => client.get('/users')))
    expect(results.every(result => result.status === 200)).toBe(true)
    expect(refresh).toHaveBeenCalledOnce()
  })

  it('does not revoke the session on a temporary refresh failure and can retry', async () => {
    let attempt = 0
    const error = Object.assign(new Error('unavailable'), { response: { status: 503 } })
    const { client, onSessionExpired } = createHarness({
      refresh: async () => { if (++attempt === 1) throw error; return 'new-access' },
    })
    await expect(client.get('/users')).rejects.toBe(error)
    expect(onSessionExpired).not.toHaveBeenCalled()
    await expect(client.get('/users')).resolves.toMatchObject({ status: 200 })
  })

  it('stops after one replay and expires a still-rejected session', async () => {
    const { client, refresh, onSessionExpired } = createHarness({ handler: async () => ({ status: 401 }) })
    await expect(client.get('/users')).rejects.toMatchObject({ response: { status: 401 } })
    expect(refresh).toHaveBeenCalledOnce()
    expect(onSessionExpired).toHaveBeenCalledOnce()
  })

  it('does not refresh authentication handshake errors', async () => {
    const { client, refresh, onSessionExpired } = createHarness()
    for (const url of ['/auth/login', '/auth/refresh', '/auth/logout', '/auth/session/logout']) {
      await expect(client.post(url, {})).rejects.toMatchObject({ response: { status: 401 } })
    }
    expect(refresh).not.toHaveBeenCalled()
    expect(onSessionExpired).not.toHaveBeenCalled()
  })

  it('reuses a refreshed token when an older request returns a late 401', async () => {
    let release!: () => void
    const delayed = new Promise<void>(resolve => { release = resolve })
    const { client, refresh } = createHarness({
      handler: async config => {
        if (authorization(config) === 'Bearer new-access') return { status: 200 }
        if (config.url === '/slow') await delayed
        return { status: 401 }
      },
    })
    const slow = client.get('/slow')
    await client.get('/fast')
    release()
    await expect(slow).resolves.toMatchObject({ status: 200 })
    expect(refresh).toHaveBeenCalledOnce()
  })

  it('never retries a previous user request with a different session', async () => {
    let release!: () => void
    let started!: () => void
    const delayed = new Promise<void>(resolve => { release = resolve })
    const entered = new Promise<void>(resolve => { started = resolve })
    const { client, refresh, changeSession } = createHarness({
      handler: async () => { started(); await delayed; return { status: 401 } },
    })
    const pending = client.get('/users')
    await entered
    changeSession()
    release()
    await expect(pending).rejects.toThrow('SESSION_CHANGED')
    expect(refresh).not.toHaveBeenCalled()
  })
})
