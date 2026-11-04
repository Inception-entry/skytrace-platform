import { describe, expect, it, vi } from 'vitest'
import { clearLegacyAuthStorage, LEGACY_AUTH_STORAGE_KEY } from './sessionStorage'

describe('legacy authentication storage migration', () => {
  it('removes only the old auth record and preserves unrelated preferences', () => {
    const values = new Map([[LEGACY_AUTH_STORAGE_KEY, 'old-tokens'], ['theme', 'dark']])
    clearLegacyAuthStorage({ removeItem: key => { values.delete(key) } })
    expect([...values.entries()]).toEqual([['theme', 'dark']])
  })

  it('does not block the application when storage is disabled', () => {
    const removeItem = vi.fn(() => { throw new Error('storage disabled') })
    expect(() => clearLegacyAuthStorage({ removeItem })).not.toThrow()
  })
})
