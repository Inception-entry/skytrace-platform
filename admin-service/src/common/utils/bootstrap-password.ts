export const BOOTSTRAP_PASSWORD_MIN_LENGTH = 16

const KNOWN_DEFAULT_PASSWORDS = new Set(
  [
    'Admin@123',
    'admin',
    'admin123',
    'admin123456',
    'password',
    'Password123',
    '12345678',
    'change-me',
    'changeme',
  ].map((value) => value.toLowerCase()),
)

/**
 * 首次创建引导管理员时使用。
 * 不要把传入的密码写进异常消息或日志。
 */
export function assertBootstrapPassword(
  raw: string | undefined,
  username = 'admin',
): string {
  if (raw === undefined || raw.length === 0) {
    throw new Error(
      'ADMIN_INITIAL_PASSWORD is required when creating the bootstrap admin. Use a unique password of at least 16 characters. Published defaults are not allowed.',
    )
  }
  if (raw !== raw.trim()) {
    throw new Error('ADMIN_INITIAL_PASSWORD must not start or end with whitespace')
  }
  if (KNOWN_DEFAULT_PASSWORDS.has(raw.toLowerCase())) {
    throw new Error('ADMIN_INITIAL_PASSWORD matches a known published default and is not allowed')
  }
  if (raw.toLowerCase() === username.toLowerCase()) {
    throw new Error('ADMIN_INITIAL_PASSWORD must not equal the bootstrap username')
  }
  if ([...raw].length < BOOTSTRAP_PASSWORD_MIN_LENGTH) {
    throw new Error(
      `ADMIN_INITIAL_PASSWORD must be at least ${BOOTSTRAP_PASSWORD_MIN_LENGTH} characters`,
    )
  }
  return raw
}
