import { useAuthStore } from '../store/auth'

export function usePermission() {
  const user = useAuthStore(s => s.user)
  const permissions = user?.permissions ?? []
  const roles = user?.roles?.map(r => r.code) ?? []
  const isSuperAdmin = roles.includes('super_admin')

  function hasPermission(code: string) {
    return isSuperAdmin || permissions.includes(code)
  }

  function hasAnyPermission(...codes: string[]) {
    return isSuperAdmin || codes.some(c => permissions.includes(c))
  }

  return { permissions, roles, isSuperAdmin, hasPermission, hasAnyPermission }
}
