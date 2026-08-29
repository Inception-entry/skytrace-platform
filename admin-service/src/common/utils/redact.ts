const SECRET_KEYS = new Set([
  'password',
  'currentpassword',
  'newpassword',
  'token',
  'accesstoken',
  'refreshtoken',
  'secret',
  'authorization',
  'cookie',
])

function normalizeKey(key: string): string {
  return key.toLowerCase().replace(/[_-]/g, '')
}

export function redact(value: unknown, depth = 0, seen = new WeakSet<object>()): unknown {
  if (depth > 5) {
    return '[TRUNCATED]'
  }
  if (value === null || typeof value !== 'object') {
    return value
  }
  if (seen.has(value)) {
    return '[CIRCULAR]'
  }
  seen.add(value)

  if (Array.isArray(value)) {
    return value.slice(0, 20).map((item) => redact(item, depth + 1, seen))
  }

  return Object.fromEntries(
    Object.entries(value as Record<string, unknown>).map(([key, nested]) => {
      const secret = SECRET_KEYS.has(normalizeKey(key))
      return [key, secret ? '[REDACTED]' : redact(nested, depth + 1, seen)]
    }),
  )
}

export function serializeRedacted(value: unknown, maxLength = 500): string {
  return JSON.stringify(redact(value ?? {})).slice(0, maxLength)
}