const SENSITIVE_KEY = /token|password|authorization|secret|cookie|credential/i

type Level = 'info' | 'warn' | 'error'

export function installClientLog(): void {
  if (typeof window === 'undefined') return
  window.addEventListener('error', (event) => {
    logClientError('window', event.error ?? event.message)
  })
  window.addEventListener('unhandledrejection', (event) => {
    logClientError('unhandled', event.reason)
  })
}

export function logClientWarn(scope: string, message: string, detail?: unknown): void {
  write('warn', scope, message, detail)
}

export function logClientError(scope: string, error: unknown, message?: string): void {
  if (isAbort(error)) return
  write('error', scope, message ?? describeError(error), httpDetail(error))
}

const recentShips = new Map<string, number>()

function write(level: Level, scope: string, message: string, detail?: unknown): void {
  const path = typeof location === 'undefined' ? '' : location.pathname
  const line = `[skytrace-admin] ${new Date().toISOString()} ${level.toUpperCase()} ${scope} ${message} path=${path}`
  const safe = sanitize(detail)
  if (level === 'error') console.error(line, safe ?? '')
  else if (level === 'warn') console.warn(line, safe ?? '')
  else console.info(line)
  if (level === 'info') return
  ship(level, scope, message, path, safe)
}

function describeError(error: unknown): string {
  if (error instanceof Error && error.message) return error.message
  if (typeof error === 'string' && error) return error
  return '未知错误'
}

function isAbort(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError'
}

function httpDetail(error: unknown): unknown {
  if (!error || typeof error !== 'object') return undefined
  const candidate = error as {
    response?: { status?: number; data?: { message?: string; code?: string } }
    config?: { method?: string; url?: string }
  }
  const status = candidate.response?.status
  const method = candidate.config?.method
  const url = candidate.config?.url
  const serverMessage = candidate.response?.data?.message
  const code = candidate.response?.data?.code
  if (status == null && !url && !serverMessage) return undefined
  return { status, method, url, code, serverMessage }
}

function ship(
  level: 'warn' | 'error',
  scope: string,
  message: string,
  path: string,
  detail: unknown,
): void {
  if (typeof fetch !== 'function') return
  if (!/^[A-Za-z0-9._-]{1,64}$/.test(scope)) return
  const key = `${level}:${scope}:${message}`
  const now = Date.now()
  const previous = recentShips.get(key)
  if (previous != null && now - previous < 10_000) return
  recentShips.set(key, now)
  if (recentShips.size > 100) {
    const oldest = recentShips.keys().next().value
    if (oldest) recentShips.delete(oldest)
  }
  const payload: Record<string, unknown> = {
    level,
    scope,
    message: message.slice(0, 300),
    path: path.slice(0, 200),
  }
  const slim = shipDetail(detail)
  if (slim) payload.detail = slim
  void fetch('/admin-api/client-logs', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
    keepalive: true,
  }).catch(() => undefined)
}

function shipDetail(detail: unknown): Record<string, unknown> | undefined {
  if (!detail || typeof detail !== 'object') return undefined
  const source = detail as Record<string, unknown>
  const output: Record<string, unknown> = {}
  if (typeof source.status === 'number' && source.status >= 100 && source.status <= 599) {
    output.status = source.status
  }
  if (typeof source.method === 'string' && /^[A-Za-z]{1,8}$/.test(source.method)) {
    output.method = source.method
  }
  if (typeof source.url === 'string' && source.url) {
    output.url = source.url.split('?')[0].slice(0, 200)
  }
  if (typeof source.code === 'string' && source.code) output.code = source.code.slice(0, 64)
  if (typeof source.serverMessage === 'string' && source.serverMessage) {
    output.serverMessage = source.serverMessage.slice(0, 200)
  }
  return Object.keys(output).length ? output : undefined
}

function sanitize(value: unknown, depth = 0): unknown {
  if (value == null || depth > 3) return value
  if (value instanceof Error) return { name: value.name, message: value.message }
  if (typeof value !== 'object') return value
  if (Array.isArray(value)) return value.slice(0, 8).map((item) => sanitize(item, depth + 1))
  const output: Record<string, unknown> = {}
  for (const [key, item] of Object.entries(value as Record<string, unknown>)) {
    if (SENSITIVE_KEY.test(key)) {
      output[key] = '[redacted]'
      continue
    }
    if (key === 'headers' || key === 'config' || key === 'request') continue
    output[key] = sanitize(item, depth + 1)
  }
  return output
}
