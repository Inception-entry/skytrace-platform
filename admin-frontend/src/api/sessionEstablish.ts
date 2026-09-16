import axios from 'axios'
import type { LoginResponse, MeResponse } from '../types'
import type { AdminSessionTokens } from './sessionRevoke'

export const ADMIN_ME_PATH = '/admin-api/auth/me'
export const PROFILE_LOAD_TIMEOUT_MS = 5000

export class AdminLoginRejectedError extends Error {
  readonly cause?: unknown

  constructor(cause?: unknown) {
    super('LOGIN_REJECTED')
    this.name = 'AdminLoginRejectedError'
    this.cause = cause
  }
}

export class AdminProfileLoadError extends Error {
  readonly cause?: unknown

  constructor(cause?: unknown) {
    super('PROFILE_LOAD_FAILED')
    this.name = 'AdminProfileLoadError'
    this.cause = cause
  }
}

export function loginFailureMessage(error: unknown): string {
  if (error instanceof AdminProfileLoadError) {
    return '登录成功但无法获取用户信息，已撤销会话'
  }
  return '用户名或密码错误'
}

export async function loadAdminProfile(accessToken: string): Promise<MeResponse> {
  const { data } = await axios.get<MeResponse>(ADMIN_ME_PATH, {
    timeout: PROFILE_LOAD_TIMEOUT_MS,
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  return data
}

export async function establishAdminSession(
  deps: {
    login: (credentials: {
      username: string
      password: string
    }) => Promise<LoginResponse>
    loadMe: (accessToken: string) => Promise<MeResponse>
    commit: (tokens: LoginResponse, user: MeResponse) => void
    rollback: (tokens: AdminSessionTokens) => Promise<void>
    onRollbackFailure?: (error: unknown) => void
  },
  credentials: { username: string; password: string },
): Promise<void> {
  let tokens: LoginResponse
  try {
    tokens = await deps.login(credentials)
  } catch (error) {
    throw new AdminLoginRejectedError(error)
  }

  try {
    const user = await deps.loadMe(tokens.access_token)
    deps.commit(tokens, user)
  } catch (error) {
    try {
      await deps.rollback({
        accessToken: tokens.access_token,
        refreshToken: tokens.refresh_token,
      })
    } catch (revokeError) {
      deps.onRollbackFailure?.(revokeError)
    }
    throw new AdminProfileLoadError(error)
  }
}
