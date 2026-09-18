export const MIN_JWT_SECRET_LENGTH = 32

export const DEFAULT_ACCESS_SECRET = 'dev-jwt-secret-change-in-production'
export const DEFAULT_REFRESH_SECRET = 'dev-jwt-refresh-secret-change-in-production'

export type JwtSecrets = {
  accessSecret: string
  refreshSecret: string
}

export function resolveJwtSecrets(env: {
  JWT_SECRET?: string | null
  JWT_REFRESH_SECRET?: string | null
}): JwtSecrets {
  const accessSecret = env.JWT_SECRET?.trim() ?? ''
  const refreshSecret = env.JWT_REFRESH_SECRET?.trim() ?? ''

  if (!accessSecret || accessSecret === DEFAULT_ACCESS_SECRET) {
    throw new Error(
      'JWT_SECRET must be set to a non-default value before starting admin-service',
    )
  }
  if (!refreshSecret || refreshSecret === DEFAULT_REFRESH_SECRET) {
    throw new Error(
      'JWT_REFRESH_SECRET must be set to a non-default value before starting admin-service',
    )
  }
  if (accessSecret.length < MIN_JWT_SECRET_LENGTH) {
    throw new Error(
      `JWT_SECRET must be at least ${MIN_JWT_SECRET_LENGTH} characters`,
    )
  }
  if (refreshSecret.length < MIN_JWT_SECRET_LENGTH) {
    throw new Error(
      `JWT_REFRESH_SECRET must be at least ${MIN_JWT_SECRET_LENGTH} characters`,
    )
  }
  if (accessSecret === refreshSecret) {
    throw new Error('JWT_SECRET and JWT_REFRESH_SECRET must be different')
  }

  return { accessSecret, refreshSecret }
}
