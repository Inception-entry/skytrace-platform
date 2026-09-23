export const REFRESH_COOKIE_NAME = 'skytrace_admin_refresh'
export const REFRESH_COOKIE_PATH = '/admin-api'
export const CSRF_HEADER = 'x-skytrace-csrf'
const REFRESH_MAX_AGE_SEC = 7 * 24 * 60 * 60

export type RefreshCredential =
  | { ok: true; token: string; via: 'body' | 'cookie' }
  | { ok: false; reason: 'missing' | 'csrf' }

export function readCookie(header: string | undefined, name: string): string | undefined {
  if (!header) return undefined
  for (const part of header.split(';')) {
    const eq = part.indexOf('=')
    if (eq < 0) continue
    if (part.slice(0, eq).trim() !== name) continue
    const raw = part.slice(eq + 1).trim()
    if (!raw) return undefined
    try {
      return decodeURIComponent(raw)
    } catch {
      return undefined
    }
  }
  return undefined
}

export function refreshCookieSecure(): boolean {
  const flag = process.env.ADMIN_REFRESH_COOKIE_SECURE
  if (flag === 'true') return true
  if (flag === 'false') return false
  return process.env.NODE_ENV === 'production'
}

function cookieAttributes(secure: boolean): string[] {
  const parts = [
    `Path=${REFRESH_COOKIE_PATH}`,
    'HttpOnly',
    'SameSite=Lax',
  ]
  if (secure) parts.push('Secure')
  return parts
}

export function refreshSetCookie(token: string, secure = refreshCookieSecure()): string {
  return [
    `${REFRESH_COOKIE_NAME}=${encodeURIComponent(token)}`,
    `Max-Age=${REFRESH_MAX_AGE_SEC}`,
    ...cookieAttributes(secure),
  ].join('; ')
}

export function refreshClearCookie(secure = refreshCookieSecure()): string {
  return [
    `${REFRESH_COOKIE_NAME}=`,
    'Max-Age=0',
    ...cookieAttributes(secure),
  ].join('; ')
}

export function resolveRefreshCredential(input: {
  bodyToken?: unknown
  cookieHeader?: string
  csrfHeader?: string | string[]
}): RefreshCredential {
  const body = typeof input.bodyToken === 'string' ? input.bodyToken.trim() : ''
  if (body) return { ok: true, token: body, via: 'body' }
  const cookie = readCookie(input.cookieHeader, REFRESH_COOKIE_NAME)
  if (!cookie) return { ok: false, reason: 'missing' }
  const csrf = Array.isArray(input.csrfHeader) ? input.csrfHeader[0] : input.csrfHeader
  if (csrf !== '1') return { ok: false, reason: 'csrf' }
  return { ok: true, token: cookie, via: 'cookie' }
}
