import axios from 'axios'

export const ADMIN_LOGOUT_PATH = '/admin-api/auth/logout'
export const REVOKE_TIMEOUT_MS = 5000

export type AdminSessionTokens = {
  accessToken: string | null
  refreshToken: string | null
}

export async function revokeAdminSession(
  tokens: AdminSessionTokens,
): Promise<void> {
  if (!tokens.refreshToken) {
    return
  }
  await axios.post(
    ADMIN_LOGOUT_PATH,
    { refresh_token: tokens.refreshToken },
    {
      timeout: REVOKE_TIMEOUT_MS,
      headers: tokens.accessToken
        ? { Authorization: `Bearer ${tokens.accessToken}` }
        : undefined,
    },
  )
}

export function reportServerRevocationFailure(error: unknown): void {
  console.warn('[admin] failed to revoke server session', error)
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
