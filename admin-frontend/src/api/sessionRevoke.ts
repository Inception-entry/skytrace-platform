import axios from 'axios'
import { logClientWarn } from '../utils/client-log'

export const ADMIN_LOGOUT_PATH = '/admin-api/auth/logout'
export const REVOKE_TIMEOUT_MS = 5000

export type AdminSessionTokens = {
  accessToken: string | null
  refreshToken: string | null
}

export async function revokeAdminSession(
  tokens: AdminSessionTokens,
): Promise<void> {
  await axios.post(
    ADMIN_LOGOUT_PATH,
    tokens.refreshToken ? { refresh_token: tokens.refreshToken } : {},
    {
      timeout: REVOKE_TIMEOUT_MS,
      withCredentials: true,
      headers: {
        'X-Skytrace-CSRF': '1',
        ...(tokens.accessToken
          ? { Authorization: `Bearer ${tokens.accessToken}` }
          : {}),
      },
    },
  )
}

export function reportServerRevocationFailure(error: unknown): void {
  logClientWarn('auth.logout', '服务端会话撤销失败', error)
}

export async function endAdminSession(deps: {
  getTokens: () => AdminSessionTokens
  clearLocalAuth: () => void
  revokeSession: (tokens: AdminSessionTokens) => Promise<void>
  redirectToLogin: () => void
  onRevokeFailure?: (error: unknown) => void
}): Promise<void> {
  const tokens = deps.getTokens()
  deps.clearLocalAuth()
  try {
    await deps.revokeSession(tokens)
  } catch (error) {
    deps.onRevokeFailure?.(error)
  } finally {
    deps.redirectToLogin()
  }
}
