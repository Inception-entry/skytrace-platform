export const LEGACY_AUTH_STORAGE_KEY = 'skytrace-admin-auth'

export function clearLegacyAuthStorage(storage?: Pick<Storage, 'removeItem'>): void {
  try {
    const target = storage ?? (typeof window === 'undefined' ? undefined : window.localStorage)
    target?.removeItem(LEGACY_AUTH_STORAGE_KEY)
  } catch {
    // Storage may be disabled. Authentication no longer depends on it.
  }
}
