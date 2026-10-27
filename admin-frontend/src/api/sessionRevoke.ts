import axios from 'axios'
import { logClientWarn } from '../utils/client-log'
import { withAdminSessionLock } from './cookieSession'

export const ADMIN_LOGOUT_PATH = '/admin-api/auth/session/logout'
export const REVOKE_TIMEOUT_MS = 5000

export async function revokeAdminSession(): Promise<void> {
  await withAdminSessionLock(() => axios.post(
    ADMIN_LOGOUT_PATH,
    {},
    {
      timeout: REVOKE_TIMEOUT_MS,
      withCredentials: true,
      headers: {
        'X-Skytrace-CSRF': '1',
      },
    },
  ))
}

export function reportServerRevocationFailure(error: unknown): void {
  logClientWarn('auth.logout', '服务端会话撤销失败', error)
}

export async function endAdminSession(deps: {
  clearLocalAuth: () => void
  waitForRefresh?: () => Promise<unknown>
  revokeSession: () => Promise<void>
  redirectToLogin: () => void
  // eslint-disable-next-line no-unused-vars
  onRevokeFailure?: (error: unknown) => void
}): Promise<void> {
  deps.clearLocalAuth()
  // Let a pending rotation finish before clearing the browser's current cookie.
  await deps.waitForRefresh?.().catch(() => {})
  try {
    await deps.revokeSession()
  } catch (error) {
    deps.onRevokeFailure?.(error)
  } finally {
    deps.redirectToLogin()
  }
}
