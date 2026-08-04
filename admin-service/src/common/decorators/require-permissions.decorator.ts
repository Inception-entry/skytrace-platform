import { SetMetadata } from '@nestjs/common'

export const PERMISSIONS_KEY = 'require_permissions'
export const PERMISSIONS_ANY_KEY = 'require_any_permissions'

/** Require all listed permission codes (AND). Super-admin role bypasses. */
export const RequirePermissions = (...permissions: string[]) =>
  SetMetadata(PERMISSIONS_KEY, permissions)

/** Require at least one of the listed permission codes (OR). */
export const RequireAnyPermission = (...permissions: string[]) =>
  SetMetadata(PERMISSIONS_ANY_KEY, permissions)
