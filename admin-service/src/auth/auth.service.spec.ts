import { Test, TestingModule } from '@nestjs/testing'
import { UnauthorizedException } from '@nestjs/common'
import { JwtService } from '@nestjs/jwt'
import { ConfigService } from '@nestjs/config'
import { Prisma } from '@prisma/client'
import * as bcrypt from 'bcryptjs'
import { AuthService } from './auth.service'
import { DUMMY_PASSWORD_HASH } from './dummy-password-hash'
import { PrismaService } from '../prisma/prisma.service'

const mockPrisma: {
  user: { findUnique: jest.Mock; findUniqueOrThrow: jest.Mock; update: jest.Mock }
  refreshToken: {
    create: jest.Mock
    findUnique: jest.Mock
    delete: jest.Mock
    deleteMany: jest.Mock
  }
  $transaction: jest.Mock
} = {
  user: {
    findUnique: jest.fn(),
    findUniqueOrThrow: jest.fn(),
    update: jest.fn(),
  },
  refreshToken: {
    create: jest.fn(),
    findUnique: jest.fn(),
    delete: jest.fn(),
    deleteMany: jest.fn(),
  },
  $transaction: jest.fn(),
}

mockPrisma.$transaction.mockImplementation(async (arg: unknown) => {
  if (typeof arg === 'function') {
    return arg(mockPrisma)
  }
  return Promise.all(arg as Promise<unknown>[])
})

const ACCESS_SECRET = 'a'.repeat(32)
const REFRESH_SECRET = 'b'.repeat(32)

const mockJwt = {
  sign: jest.fn().mockReturnValue('mock-token'),
  verify: jest.fn(),
}

const mockConfig = {
  get: jest.fn((key: string) => {
    if (key === 'JWT_SECRET') return ACCESS_SECRET
    if (key === 'JWT_REFRESH_SECRET') return REFRESH_SECRET
    return undefined
  }),
}

function refreshClaims(overrides?: { sub?: number; username?: string; jti?: string; familyId?: string }) {
  return {
    sub: overrides?.sub ?? 1,
    username: overrides?.username ?? 'admin',
    type: 'refresh' as const,
    jti: overrides?.jti ?? 'jti-1',
    ...(overrides?.familyId ? { familyId: overrides.familyId } : {}),
  }
}

describe('AuthService', () => {
  let service: AuthService

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AuthService,
        { provide: PrismaService, useValue: mockPrisma },
        { provide: JwtService, useValue: mockJwt },
        { provide: ConfigService, useValue: mockConfig },
      ],
    }).compile()

    service = module.get(AuthService)
    jest.clearAllMocks()
    mockJwt.sign.mockReturnValue('mock-token')
    mockConfig.get.mockImplementation((key: string) => {
      if (key === 'JWT_SECRET') return ACCESS_SECRET
      if (key === 'JWT_REFRESH_SECRET') return REFRESH_SECRET
      return undefined
    })
    mockPrisma.$transaction.mockImplementation(async (arg: unknown) => {
      if (typeof arg === 'function') {
        return arg(mockPrisma)
      }
      return Promise.all(arg as Promise<unknown>[])
    })
  })

  describe('validateUser', () => {
    afterEach(() => {
      jest.restoreAllMocks()
    })

    it('returns null when user does not exist and still runs bcrypt against the dummy hash', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(null)
      const compare = jest.spyOn(bcrypt, 'compare')
      expect(await service.validateUser('nobody', 'pass')).toBeNull()
      expect(compare).toHaveBeenCalledWith('pass', DUMMY_PASSWORD_HASH)
    })

    it('returns null for a disabled user even with the correct password', async () => {
      const hashed = await bcrypt.hash('correct', 10)
      mockPrisma.user.findUnique.mockResolvedValue({
        id: 1,
        username: 'admin',
        password: hashed,
        status: 0,
      })
      const compare = jest.spyOn(bcrypt, 'compare')
      expect(await service.validateUser('admin', 'correct')).toBeNull()
      expect(compare).toHaveBeenCalledWith('correct', DUMMY_PASSWORD_HASH)
    })

    it('returns null for wrong password', async () => {
      const hashed = await bcrypt.hash('correct', 10)
      mockPrisma.user.findUnique.mockResolvedValue({ id: 1, username: 'admin', password: hashed, status: 1 })
      expect(await service.validateUser('admin', 'wrong')).toBeNull()
    })

    it('returns user for valid credentials', async () => {
      const hashed = await bcrypt.hash('Admin@123', 10)
      const user = { id: 1, username: 'admin', password: hashed, status: 1 }
      mockPrisma.user.findUnique.mockResolvedValue(user)
      const result = await service.validateUser('admin', 'Admin@123')
      expect(result?.id).toBe(1)
    })
  })

  describe('login', () => {
    it('returns access_token, refresh_token and expires_in', async () => {
      mockJwt.sign
        .mockReturnValueOnce('access-token-123')
        .mockReturnValueOnce('refresh-token-abc')
      mockPrisma.refreshToken.create.mockResolvedValue({})

      const result = await service.login(1, 'admin')

      expect(result).toMatchObject({
        access_token: 'access-token-123',
        refresh_token: 'refresh-token-abc',
        expires_in: 900,
      })
      expect(mockPrisma.refreshToken.create).toHaveBeenCalledTimes(1)
    })

    it('persists hashed refresh token in DB', async () => {
      mockJwt.sign.mockReturnValueOnce('at').mockReturnValueOnce('rt-plain')
      mockPrisma.refreshToken.create.mockResolvedValue({})

      await service.login(2, 'operator')

      const createCall = mockPrisma.refreshToken.create.mock.calls[0][0]
      expect(createCall.data.userId).toBe(2)
      expect(createCall.data.token).not.toBe('rt-plain')
      expect(createCall.data.token).toHaveLength(64) // SHA-256 hex
    })

    it('signs refresh tokens with unique jti so same-second logins differ', async () => {
      const refreshJtis: string[] = []
      const families: string[] = []
      mockJwt.sign.mockImplementation((payload: { type?: string; jti?: string; familyId?: string }) => {
        if (payload.type === 'refresh') {
          refreshJtis.push(payload.jti ?? '')
          families.push(payload.familyId ?? '')
          return `rt-${payload.jti}`
        }
        return 'access-token'
      })
      mockPrisma.refreshToken.create.mockResolvedValue({})

      const first = await service.login(1, 'admin')
      const second = await service.login(1, 'admin')

      expect(refreshJtis).toHaveLength(2)
      expect(refreshJtis[0]).not.toBe(refreshJtis[1])
      expect(families[0]).toHaveLength(36)
      expect(families[0]).not.toBe(families[1])
      expect(first.refresh_token).not.toBe(second.refresh_token)
      expect(mockJwt.sign).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'refresh', sub: 1, username: 'admin' }),
        expect.objectContaining({ expiresIn: '7d' }),
      )
    })
  })

  describe('refresh', () => {
    it('throws when JWT signature is invalid', async () => {
      mockJwt.verify.mockImplementation(() => { throw new Error('invalid') })
      await expect(service.refresh('bad-token')).rejects.toThrow(UnauthorizedException)
    })

    it('throws when token is not a refresh JWT', async () => {
      mockJwt.verify.mockReturnValue({ sub: 1, username: 'admin' })
      await expect(service.refresh('access-looking-token')).rejects.toThrow('无效或已过期的刷新令牌')
    })

    it('throws when token is not in DB (revoked)', async () => {
      mockJwt.verify.mockReturnValue(refreshClaims())
      mockPrisma.refreshToken.findUnique.mockResolvedValue(null)
      await expect(service.refresh('valid-jwt-but-revoked')).rejects.toThrow(UnauthorizedException)
      expect(mockPrisma.refreshToken.deleteMany).not.toHaveBeenCalled()
    })

    it('revokes the whole family when a consumed refresh token is presented again', async () => {
      mockJwt.verify.mockReturnValue(refreshClaims({ familyId: 'fam-1' }))
      mockPrisma.refreshToken.findUnique.mockResolvedValue(null)
      mockPrisma.refreshToken.deleteMany.mockResolvedValue({ count: 1 })
      await expect(service.refresh('reused')).rejects.toThrow('令牌已撤销')
      expect(mockPrisma.refreshToken.deleteMany).toHaveBeenCalledWith({ where: { familyId: 'fam-1' } })
    })

    it('throws when refresh token row is expired', async () => {
      mockJwt.verify.mockReturnValue(refreshClaims())
      mockPrisma.refreshToken.findUnique.mockResolvedValue({
        id: 1,
        token: 'hash',
        userId: 1,
        expiresAt: new Date(Date.now() - 1000),
      })
      mockPrisma.refreshToken.deleteMany.mockResolvedValue({ count: 1 })
      await expect(service.refresh('rt')).rejects.toThrow(UnauthorizedException)
    })

    it('throws when user is disabled', async () => {
      mockJwt.verify.mockReturnValue(refreshClaims())
      mockPrisma.refreshToken.findUnique.mockResolvedValue({
        id: 1,
        token: 'hash',
        userId: 1,
        expiresAt: new Date(Date.now() + 60_000),
      })
      mockPrisma.refreshToken.deleteMany.mockResolvedValue({ count: 1 })
      mockPrisma.user.findUnique.mockResolvedValue({ id: 1, status: 0 })
      await expect(service.refresh('rt')).rejects.toThrow(UnauthorizedException)
    })

    it('rotates token and returns new token pair', async () => {
      mockJwt.verify.mockReturnValue(refreshClaims())
      mockPrisma.refreshToken.findUnique.mockResolvedValue({
        id: 1,
        token: 'hash',
        userId: 1,
        expiresAt: new Date(Date.now() + 60_000),
      })
      mockPrisma.refreshToken.deleteMany.mockResolvedValue({ count: 1 })
      mockPrisma.user.findUnique.mockResolvedValue({ id: 1, username: 'admin', status: 1 })
      mockJwt.sign
        .mockReturnValueOnce('new-access-token')
        .mockReturnValueOnce('new-refresh-token')
      mockPrisma.refreshToken.create.mockResolvedValue({})

      const result = await service.refresh('old-refresh-token')

      expect(result.access_token).toBe('new-access-token')
      expect(result.refresh_token).toBe('new-refresh-token')
      expect(result.expires_in).toBe(900)
      expect(mockPrisma.refreshToken.deleteMany).toHaveBeenCalledTimes(1)
      expect(mockPrisma.refreshToken.create).toHaveBeenCalledTimes(1)
      expect(mockPrisma.refreshToken.create.mock.calls[0][0].data.familyId).toEqual(expect.any(String))
    })

    it('keeps the stored family id when rotating', async () => {
      mockJwt.verify.mockReturnValue(refreshClaims({ familyId: 'from-jwt' }))
      mockPrisma.refreshToken.findUnique.mockResolvedValue({
        id: 1,
        token: 'hash',
        familyId: 'fam-keep',
        userId: 1,
        expiresAt: new Date(Date.now() + 60_000),
      })
      mockPrisma.refreshToken.deleteMany.mockResolvedValue({ count: 1 })
      mockPrisma.user.findUnique.mockResolvedValue({ id: 1, username: 'admin', status: 1 })
      mockJwt.sign.mockImplementation((payload: { type?: string; familyId?: string }) => {
        return payload.type === 'refresh' ? `rt-${payload.familyId}` : 'access'
      })
      mockPrisma.refreshToken.create.mockResolvedValue({})

      const result = await service.refresh('old-refresh-token')
      expect(result.refresh_token).toBe('rt-fam-keep')
      expect(mockPrisma.refreshToken.create.mock.calls.at(-1)[0].data.familyId).toBe('fam-keep')
    })

    it('lets only one concurrent refresh of the same token succeed', async () => {
      mockJwt.verify.mockReturnValue(refreshClaims())
      mockPrisma.refreshToken.findUnique.mockResolvedValue({
        id: 1,
        token: 'hash',
        userId: 1,
        expiresAt: new Date(Date.now() + 60_000),
      })
      mockPrisma.refreshToken.deleteMany
        .mockResolvedValueOnce({ count: 1 })
        .mockResolvedValueOnce({ count: 0 })
      mockPrisma.user.findUnique.mockResolvedValue({ id: 1, username: 'admin', status: 1 })
      mockJwt.sign.mockReturnValueOnce('new-access').mockReturnValueOnce('new-refresh')
      mockPrisma.refreshToken.create.mockResolvedValue({})

      const winner = service.refresh('shared-refresh')
      const loser = service.refresh('shared-refresh')

      await expect(winner).resolves.toMatchObject({
        access_token: 'new-access',
        refresh_token: 'new-refresh',
      })
      await expect(loser).rejects.toThrow('令牌已撤销')
    })

    it('maps unique-constraint races to 401', async () => {
      mockJwt.verify.mockReturnValue(refreshClaims())
      mockPrisma.refreshToken.findUnique.mockResolvedValue({
        id: 1,
        token: 'hash',
        userId: 1,
        expiresAt: new Date(Date.now() + 60_000),
      })
      mockPrisma.refreshToken.deleteMany.mockResolvedValue({ count: 1 })
      mockPrisma.user.findUnique.mockResolvedValue({ id: 1, username: 'admin', status: 1 })
      mockJwt.sign.mockReturnValueOnce('new-access').mockReturnValueOnce('new-refresh')
      mockPrisma.refreshToken.create.mockRejectedValue(
        new Prisma.PrismaClientKnownRequestError('Unique constraint failed', {
          code: 'P2002',
          clientVersion: '6.1.0',
        }),
      )

      await expect(service.refresh('rt')).rejects.toThrow('令牌已撤销')
    })
  })

  describe('logout', () => {
    it('deletes the hashed refresh token from DB', async () => {
      mockPrisma.refreshToken.deleteMany.mockResolvedValue({ count: 1 })
      await service.logout('some-refresh-token')
      expect(mockPrisma.refreshToken.deleteMany).toHaveBeenCalledTimes(1)
    })

    it('makes the old refresh token unusable after logout', async () => {
      mockPrisma.refreshToken.deleteMany.mockResolvedValue({ count: 1 })
      await service.logout('plain-refresh')
      const deletedHash = mockPrisma.refreshToken.deleteMany.mock.calls[0][0].where.token
      expect(deletedHash).toHaveLength(64)
      expect(deletedHash).not.toBe('plain-refresh')

      mockJwt.verify.mockReturnValue(refreshClaims())
      mockPrisma.refreshToken.findUnique.mockResolvedValue(null)
      await expect(service.refresh('plain-refresh')).rejects.toThrow('令牌已撤销')
      expect(mockPrisma.refreshToken.findUnique).toHaveBeenCalledWith({
        where: { token: deletedHash },
      })
    })
  })

  describe('changePassword', () => {
    it('revokes all refresh tokens after password change', async () => {
      const hashed = await bcrypt.hash('old', 10)
      mockPrisma.user.findUniqueOrThrow.mockResolvedValue({ id: 1, password: hashed })
      mockPrisma.$transaction.mockResolvedValue([undefined, undefined])

      await service.changePassword(1, { currentPassword: 'old', newPassword: 'new-pass' })

      expect(mockPrisma.$transaction).toHaveBeenCalled()
    })
  })
})
