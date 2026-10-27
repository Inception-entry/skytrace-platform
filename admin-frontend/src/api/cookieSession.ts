import axios from 'axios'
import type { LoginResponse } from '../types'

export const SESSION_TIMEOUT_MS = 10_000
export const SESSION_LOCK_NAME = 'skytrace-admin-session'

// Cookie rotation is shared by tabs. Serialize it when Web Locks is available
// so two tabs do not consume the same single-use refresh token simultaneously.
export async function withAdminSessionLock<T>(operation: () => Promise<T>): Promise<T> {
  if (typeof navigator !== 'undefined' && navigator.locks) {
    return await navigator.locks.request(SESSION_LOCK_NAME, operation)
  }
  return operation()
}

function accessSession(data: LoginResponse): LoginResponse {
  if (typeof data.access_token !== 'string' || !data.access_token) {
    throw new Error('INVALID_SESSION_RESPONSE')
  }
  // Even an older server response must not put its refresh token into JS state.
  return { access_token: data.access_token, expires_in: data.expires_in }
}

export function loginCookieSession(credentials: { username: string; password: string }) {
  return withAdminSessionLock(async () => {
    const { data } = await axios.post<LoginResponse>('/admin-api/auth/login', credentials, {
      withCredentials: true,
      timeout: SESSION_TIMEOUT_MS,
      headers: { 'X-Skytrace-Session': 'cookie', 'X-Skytrace-CSRF': '1' },
    })
    return accessSession(data)
  })
}

export function refreshCookieSession(): Promise<LoginResponse> {
  return withAdminSessionLock(async () => {
    const { data } = await axios.post<LoginResponse>('/admin-api/auth/refresh', {}, {
      withCredentials: true,
      timeout: SESSION_TIMEOUT_MS,
      headers: { 'X-Skytrace-CSRF': '1' },
    })
    return accessSession(data)
  })
}

export function isSessionRejected(error: unknown): boolean {
  const status = (error as { response?: { status?: number } } | null)?.response?.status
  return status === 401 || status === 403
}
