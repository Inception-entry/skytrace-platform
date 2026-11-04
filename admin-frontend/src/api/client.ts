import axios, {
  type AxiosError,
  type AxiosInstance,
  type InternalAxiosRequestConfig,
} from 'axios'
import { useAuthStore } from '../store/auth'
import { logClientError, logClientWarn } from '../utils/client-log'
import { isSessionRejected } from './cookieSession'

type RetryConfig = InternalAxiosRequestConfig & { _retry?: boolean; _sessionVersion?: number }

export type AuthRefreshDeps = {
  getAccessToken: () => string | null
  getSessionVersion: () => number
  onSessionExpired: () => void
  refreshAccessToken: () => Promise<string>
}

function isAuthHandshake(config: InternalAxiosRequestConfig): boolean {
  const path = `${config.baseURL ?? ''}${config.url ?? ''}`
  return /\/auth\/(login|refresh|logout|session\/logout)(?:\?|$)/.test(path)
}

export function attachAdminAuthInterceptors(
  client: AxiosInstance,
  deps: AuthRefreshDeps,
): AxiosInstance {
  let refreshPromise: Promise<string> | null = null

  function refreshOnce(): Promise<string> {
    refreshPromise ??= (async () => {
      try {
        return await deps.refreshAccessToken()
      } catch (error) {
        logClientError('auth.refresh', error)
        if (isSessionRejected(error)) deps.onSessionExpired()
        throw error
      }
    })().finally(() => {
      refreshPromise = null
    })
    return refreshPromise
  }

  client.interceptors.request.use(config => {
    const request = config as RetryConfig
    const version = deps.getSessionVersion()
    if (request._sessionVersion !== undefined && request._sessionVersion !== version) {
      return Promise.reject(new Error('SESSION_CHANGED'))
    }
    request._sessionVersion = version
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
        if (error.response?.status === 401 && original?._retry
          && original._sessionVersion === deps.getSessionVersion()) deps.onSessionExpired()
        logAdminHttpError(error)
        return Promise.reject(error)
      }

      original._retry = true
      if (original._sessionVersion !== deps.getSessionVersion()) {
        return Promise.reject(new Error('SESSION_CHANGED'))
      }
      try {
        // A delayed 401 may arrive after another request already refreshed.
        const current = deps.getAccessToken()
        const token = current && original.headers.Authorization !== `Bearer ${current}`
          ? current
          : await refreshOnce()
        original.headers.Authorization = `Bearer ${token}`
        return client(original)
      } catch (refreshError) {
        logClientError('auth.refreshRetry', refreshError)
        return Promise.reject(refreshError)
      }
    },
  )

  return client
}

const client = axios.create({ baseURL: '/admin-api', withCredentials: true })

attachAdminAuthInterceptors(client, {
  getAccessToken: () => useAuthStore.getState().accessToken,
  getSessionVersion: () => useAuthStore.getState().sessionVersion,
  onSessionExpired: () => useAuthStore.getState().expireSession(),
  refreshAccessToken: () => useAuthStore.getState().refreshAccessToken(),
})

function logAdminHttpError(error: AxiosError): void {
  const status = error.response?.status
  const method = error.config?.method?.toUpperCase() ?? 'HTTP'
  const url = error.config?.url ?? ''
  if (status != null && status < 500) {
    logClientWarn('http', `${method} ${url} ${status}`)
    return
  }
  logClientError('http', error, `${method} ${url} ${status ?? 'network'}`)
}

export default client
