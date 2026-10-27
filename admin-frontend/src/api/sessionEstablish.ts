import axios from 'axios'
import type { LoginResponse, MeResponse } from '../types'

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

export class AdminSessionChangedError extends Error {
  constructor() {
    super('SESSION_CHANGED')
    this.name = 'AdminSessionChangedError'
  }
}

export function loginFailureMessage(error: unknown): string {
  if (error instanceof AdminSessionChangedError) {
    return '会话已在其他页面变更，请重新登录'
  }
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
    // eslint-disable-next-line no-unused-vars
    login: (credentials: {
      username: string
      password: string
    }) => Promise<LoginResponse>
    // eslint-disable-next-line no-unused-vars
    loadMe: (accessToken: string) => Promise<MeResponse>
    // eslint-disable-next-line no-unused-vars
    commit: (tokens: LoginResponse, user: MeResponse) => void
    rollback: () => Promise<void>
    isCurrent?: () => boolean
    // eslint-disable-next-line no-unused-vars
    onRollbackFailure?: (error: unknown) => void
  },
  credentials: { username: string; password: string },
): Promise<void> {
  const ensureCurrent = () => {
    if (deps.isCurrent && !deps.isCurrent()) throw new AdminSessionChangedError()
  }
  let tokens: LoginResponse
  try {
    tokens = await deps.login(credentials)
  } catch (error) {
    ensureCurrent()
    throw new AdminLoginRejectedError(error)
  }

  ensureCurrent()
  try {
    const user = await deps.loadMe(tokens.access_token)
    ensureCurrent()
    deps.commit(tokens, user)
  } catch (error) {
    // Another tab may already have replaced or revoked the shared cookie.
    // Do not revive this login or revoke that newer session during rollback.
    ensureCurrent()
    try {
      await deps.rollback()
    } catch (revokeError) {
      deps.onRollbackFailure?.(revokeError)
    }
    throw new AdminProfileLoadError(error)
  }
}
