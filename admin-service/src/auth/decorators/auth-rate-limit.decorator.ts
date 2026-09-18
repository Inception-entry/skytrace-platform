import { SetMetadata } from '@nestjs/common'

export const AUTH_RATE_LIMIT_KIND = 'authRateLimitKind'
export type AuthRateLimitKind = 'login' | 'refresh'

export const AuthRateLimit = (kind: AuthRateLimitKind) => SetMetadata(AUTH_RATE_LIMIT_KIND, kind)
