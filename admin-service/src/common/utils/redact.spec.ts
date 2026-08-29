import { redact, serializeRedacted } from './redact'

describe('redact', () => {
  it('replaces password', () => {
    expect(redact({ password: 'x' })).toEqual({ password: '[REDACTED]' })
  })

  it('replaces newPassword', () => {
    expect(redact({ newPassword: 'x' })).toEqual({ newPassword: '[REDACTED]' })
  })

  it('replaces new_password', () => {
    expect(redact({ new_password: 'x' })).toEqual({ new_password: '[REDACTED]' })
  })

  it('replaces refresh_token', () => {
    expect(redact({ refresh_token: 'abc' })).toEqual({ refresh_token: '[REDACTED]' })
  })

  it('redacts nested secrets and keeps sibling fields', () => {
    expect(redact({ user: { password: 'x', name: 'a' } })).toEqual({
      user: { password: '[REDACTED]', name: 'a' },
    })
  })

  it('leaves ordinary fields unchanged', () => {
    expect(redact({ username: 'alice', nickname: '阿莉' })).toEqual({
      username: 'alice',
      nickname: '阿莉',
    })
  })

  it('does not throw on circular objects', () => {
    const body: Record<string, unknown> = { username: 'alice' }
    body.self = body
    expect(redact(body)).toEqual({
      username: 'alice',
      self: '[CIRCULAR]',
    })
  })
})

describe('serializeRedacted', () => {
  it('does not include the plaintext password', () => {
    const params = serializeRedacted({
      username: 'alice',
      password: 'Admin@123',
    })
    expect(params).toContain('[REDACTED]')
    expect(params).not.toContain('Admin@123')
  })
})