import {
  DEFAULT_ACCESS_SECRET,
  DEFAULT_REFRESH_SECRET,
  MIN_JWT_SECRET_LENGTH,
  resolveJwtSecrets,
} from './jwt-secrets'

const ACCESS = 'a'.repeat(MIN_JWT_SECRET_LENGTH)
const REFRESH = 'b'.repeat(MIN_JWT_SECRET_LENGTH)

describe('resolveJwtSecrets', () => {
  it('accepts distinct secrets of at least 32 characters', () => {
    expect(
      resolveJwtSecrets({
        JWT_SECRET: ACCESS,
        JWT_REFRESH_SECRET: REFRESH,
      }),
    ).toEqual({
      accessSecret: ACCESS,
      refreshSecret: REFRESH,
    })
  })

  it('rejects missing or default access secrets', () => {
    expect(() =>
      resolveJwtSecrets({
        JWT_SECRET: DEFAULT_ACCESS_SECRET,
        JWT_REFRESH_SECRET: REFRESH,
      }),
    ).toThrow(/JWT_SECRET must be set to a non-default value/)
    expect(() =>
      resolveJwtSecrets({
        JWT_SECRET: '   ',
        JWT_REFRESH_SECRET: REFRESH,
      }),
    ).toThrow(/JWT_SECRET must be set to a non-default value/)
  })

  it('rejects missing or default refresh secrets at startup', () => {
    expect(() =>
      resolveJwtSecrets({
        JWT_SECRET: ACCESS,
        JWT_REFRESH_SECRET: DEFAULT_REFRESH_SECRET,
      }),
    ).toThrow(/JWT_REFRESH_SECRET must be set to a non-default value/)
  })

  it('rejects short secrets', () => {
    expect(() =>
      resolveJwtSecrets({
        JWT_SECRET: 'x'.repeat(MIN_JWT_SECRET_LENGTH - 1),
        JWT_REFRESH_SECRET: REFRESH,
      }),
    ).toThrow(/JWT_SECRET must be at least 32 characters/)
    expect(() =>
      resolveJwtSecrets({
        JWT_SECRET: ACCESS,
        JWT_REFRESH_SECRET: 'y'.repeat(MIN_JWT_SECRET_LENGTH - 1),
      }),
    ).toThrow(/JWT_REFRESH_SECRET must be at least 32 characters/)
  })

  it('rejects identical access and refresh secrets', () => {
    expect(() =>
      resolveJwtSecrets({
        JWT_SECRET: ACCESS,
        JWT_REFRESH_SECRET: ACCESS,
      }),
    ).toThrow(/must be different/)
  })
})
