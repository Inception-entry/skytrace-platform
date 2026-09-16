import axios, {
  AxiosError,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  attachAdminAuthInterceptors,
  type AuthRefreshDeps,
} from './client'

type MockResult = { status: number; data?: unknown }

function authorization(config: InternalAxiosRequestConfig): string {
  const headers = config.headers
  if (headers && typeof headers.get === 'function') {
    return String(headers.get('Authorization') ?? '')
  }
  return String(
    (headers as Record<string, string> | undefined)?.Authorization ?? '',
  )
}

function createMockClient(handler: (config: InternalAxiosRequestConfig) => Promise<MockResult>) {
  return axios.create({
    baseURL: '/admin-api',
    adapter: async config => {
      const result = await handler(config)
      const response = {
        data: result.data ?? {},
        status: result.status,
        statusText: result.status >= 400 ? 'Error' : 'OK',
        headers: {},
        config,
      } as AxiosResponse
      if (result.status >= 400) {
        throw new AxiosError(
          `Request failed with status code ${result.status}`,
          String(result.status),
          config,
          null,
          response,
        )
      }
      return response
    },
  })
}

function createHarness(
  options?: {
    accessToken?: string | null
    refreshToken?: string | null
    refreshTokens?: AuthRefreshDeps['refreshTokens']
    handler?: (config: InternalAxiosRequestConfig) => Promise<MockResult>
  },
) {
  let accessToken: string | null = options?.accessToken ?? 'expired-access'
  let refreshToken: string | null = options?.refreshToken === undefined
    ? 'refresh-1'
    : options.refreshToken
  const onSessionExpired = vi.fn(() => {
    accessToken = null
    refreshToken = null
  })
  const refreshTokens = options?.refreshTokens
    ?? vi.fn(async () => ({
      access_token: 'new-access',
      refresh_token: 'new-refresh',
    }))
  const handler = options?.handler ?? (async config => {
    if (authorization(config) === 'Bearer new-access') {
      return { status: 200, data: { ok: true } }
    }
    return { status: 401, data: { message: 'expired' } }
  })
  const client = attachAdminAuthInterceptors(createMockClient(handler), {
    getAccessToken: () => accessToken,
    getRefreshToken: () => refreshToken,
    setTokens: (access, refresh) => {
      accessToken = access
      refreshToken = refresh
    },
    onSessionExpired,
    refreshTokens,
  })
  return { client, onSessionExpired, refreshTokens }
}

afterEach(() => {
  vi.useRealTimers()
})

describe('admin auth refresh interceptor', () => {
  it('rejects a 401 with no refresh token and does not hang the next 401', async () => {
    const { client, onSessionExpired, refreshTokens } = createHarness({
      refreshToken: null,
    })

    await expect(client.get('/users')).rejects.toThrow('NO_REFRESH_TOKEN')
    await expect(client.get('/users')).rejects.toThrow('NO_REFRESH_TOKEN')
    expect(refreshTokens).not.toHaveBeenCalled()
    expect(onSessionExpired).toHaveBeenCalled()
  })

  it('rejects queued 401s when refresh fails instead of leaving them pending', async () => {
    const refreshError = new Error('refresh-failed')
    const { client, onSessionExpired, refreshTokens } = createHarness({
      refreshTokens: vi.fn(async () => {
        throw refreshError
      }),
    })

    const first = client.get('/users')
    const second = client.get('/roles')
    await expect(first).rejects.toBe(refreshError)
    await expect(second).rejects.toBe(refreshError)
    expect(refreshTokens).toHaveBeenCalledTimes(1)
    expect(onSessionExpired).toHaveBeenCalledTimes(1)
  })

  it('single-flights ten concurrent 401s and replays all of them', async () => {
    let inFlight = 0
    let maxInFlight = 0
    const refreshTokens = vi.fn(async () => {
      inFlight += 1
      maxInFlight = Math.max(maxInFlight, inFlight)
      await new Promise(resolve => setTimeout(resolve, 30))
      inFlight -= 1
      return { access_token: 'new-access', refresh_token: 'new-refresh' }
    })
    const { client } = createHarness({ refreshTokens })

    const results = await Promise.all(
      Array.from({ length: 10 }, () => client.get('/users')),
    )

    expect(results.map(result => result.data)).toEqual(
      Array.from({ length: 10 }, () => ({ ok: true })),
    )
    expect(refreshTokens).toHaveBeenCalledTimes(1)
    expect(maxInFlight).toBe(1)
  })

  it('does not loop when the replayed request is still 401', async () => {
    const refreshTokens = vi.fn(async () => ({
      access_token: 'new-access',
      refresh_token: 'new-refresh',
    }))
    const { client, onSessionExpired } = createHarness({
      refreshTokens,
      handler: async () => ({ status: 401, data: { message: 'still-expired' } }),
    })

    await expect(client.get('/users')).rejects.toMatchObject({
      response: { status: 401 },
    })
    expect(refreshTokens).toHaveBeenCalledTimes(1)
    expect(onSessionExpired).not.toHaveBeenCalled()
  })

  it('does not refresh login, refresh or logout 401s', async () => {
    const { client, refreshTokens, onSessionExpired } = createHarness()

    await expect(client.post('/auth/login', {})).rejects.toMatchObject({
      response: { status: 401 },
    })
    await expect(client.post('/auth/refresh', {})).rejects.toMatchObject({
      response: { status: 401 },
    })
    await expect(client.post('/auth/logout', {})).rejects.toMatchObject({
      response: { status: 401 },
    })
    expect(refreshTokens).not.toHaveBeenCalled()
    expect(onSessionExpired).not.toHaveBeenCalled()
  })
})
