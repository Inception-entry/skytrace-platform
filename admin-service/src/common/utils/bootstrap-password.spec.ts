import { readFileSync } from 'fs'
import { join } from 'path'
import {
  BOOTSTRAP_PASSWORD_MIN_LENGTH,
  assertBootstrapPassword,
} from './bootstrap-password'

describe('assertBootstrapPassword', () => {
  const strong = 'local-dev-only-ok!!'

  it('returns the password when it is strong and not a known default', () => {
    expect(assertBootstrapPassword(strong)).toBe(strong)
  })

  it('rejects missing values', () => {
    expect(() => assertBootstrapPassword(undefined)).toThrow(/required/)
    expect(() => assertBootstrapPassword('')).toThrow(/required/)
  })

  it('rejects published default Admin@123', () => {
    expect(() => assertBootstrapPassword('Admin@123')).toThrow(/known published default/)
  })

  it('rejects other denylisted defaults case-insensitively', () => {
    expect(() => assertBootstrapPassword('PASSWORD')).toThrow(/known published default/)
    expect(() => assertBootstrapPassword('change-me')).toThrow(/known published default/)
  })

  it('rejects short passwords', () => {
    expect(() => assertBootstrapPassword('short-password')).toThrow(
      new RegExp(String(BOOTSTRAP_PASSWORD_MIN_LENGTH)),
    )
  })

  it('rejects surrounding whitespace', () => {
    expect(() => assertBootstrapPassword(`  ${strong}  `)).toThrow(/whitespace/)
  })

  it('rejects password equal to username', () => {
    expect(() => assertBootstrapPassword('administrator-ok!', 'administrator-ok!')).toThrow(
      /username/,
    )
  })

  it('does not echo the secret in the error message', () => {
    expect(() => assertBootstrapPassword('Admin@123')).toThrow()
    try {
      assertBootstrapPassword('Admin@123')
    } catch (error) {
      expect((error as Error).message).not.toContain('Admin@123')
    }
  })
})

describe('prisma/seed.ts source', () => {
  it('does not hardcode the published default password', () => {
    const seed = readFileSync(join(__dirname, '../../../prisma/seed.ts'), 'utf8')
    expect(seed).not.toMatch(/Admin@123/)
  })
})
