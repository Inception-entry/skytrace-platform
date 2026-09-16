import axios, {
  type AxiosError,
  type AxiosInstance,
  type InternalAxiosRequestConfig,
} from 'axios'
import { useAuthStore } from '../store/auth'

type RetryConfig = InternalAxiosRequestConfig & { _retry?: boolean }

export type AuthRefreshDeps = {
  getAccessToken: () => string | null
  getRefreshToken: () => string | null
  setTokens: (accessToken: string, refreshToken: string) => void
  onSessionExpired: () => void
  refreshTokens: (refreshToken: string) => Promise<{
    access_token: string
    refresh_token: string
  }>
}

function isAuthHandshake(config: InternalAxiosRequestConfig): boolean {
  const path = `${config.baseURL ?? ''}${config.url ?? ''}`
  return /\/auth\/(login|refresh|logout)(?:\?|$)/.test(path)
}

async function defaultRefreshTokens(refreshToken: string) {
  const { data } = await axios.post<{
    access_token: string
    refresh_token: string
  }>('/admin-api/auth/refresh', { refresh_token: refreshToken })
  return data
}

export function attachAdminAuthInterceptors(
  client: AxiosInstance,
  deps: AuthRefreshDeps,
): AxiosInstance {
  let refreshPromise: Promise<string> | null = null

  function refreshOnce(): Promise<string> {
    refreshPromise ??= (async () => {
      const refreshToken = deps.getRefreshToken()
      if (!refreshToken) {
        deps.onSessionExpired()
        throw new Error('NO_REFRESH_TOKEN')
      }
      try {
        const tokens = await deps.refreshTokens(refreshToken)
        deps.setTokens(tokens.access_token, tokens.refresh_token)
        return tokens.access_token
      } catch (error) {
        deps.onSessionExpired()
        throw error
      }
    })().finally(() => {
      refreshPromise = null
    })
    return refreshPromise
  }

  client.interceptors.request.use(config => {
    const token = deps.getAccessToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  })

  client.interceptors.response.use(
    response => response,
    async (error: AxiosError) => {
      const original = error.config as RetryConfig | undefined
      if (
        error.response?.status !== 401 ||
        !original ||
        original._retry ||
        isAuthHandshake(original)
      ) {
        return Promise.reject(error)
      }

      original._retry = true
      try {
        const token = await refreshOnce()
        original.headers.Authorization = `Bearer ${token}`
        return client(original)
      } catch (refreshError) {
        return Promise.reject(refreshError)
      }
    },
  )

  return client
}

const client = axios.create({ baseURL: '/admin-api' })

attachAdminAuthInterceptors(client, {
  getAccessToken: () => useAuthStore.getState().accessToken,
  getRefreshToken: () => useAuthStore.getState().refreshToken,
  setTokens: (accessToken, refreshToken) =>
    useAuthStore.getState().setTokens(accessToken, refreshToken),
  onSessionExpired: () => useAuthStore.getState().logout(),
  refreshTokens: defaultRefreshTokens,
})

export default client
