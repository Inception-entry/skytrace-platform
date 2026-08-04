import { Test, TestingModule } from '@nestjs/testing'
import { UnauthorizedException } from '@nestjs/common'
import { JwtService } from '@nestjs/jwt'
import { ConfigService } from '@nestjs/config'
import * as bcrypt from 'bcryptjs'
import { AuthService } from './auth.service'
import { PrismaService } from '../prisma/prisma.service'

const mockPrisma = {
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
  $transaction: jest.fn().mockResolvedValue([undefined, undefined]),
}

const mockJwt = {
  sign: jest.fn().mockReturnValue('mock-token'),
  verify: jest.fn(),
}

const mockConfig = {
  get: jest.fn().mockReturnValue('test-refresh-secret'),
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
    mockConfig.get.mockReturnValue('test-refresh-secret')
  })

  describe('validateUser', () => {
    it('returns null when user does not exist', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(null)
      expect(await service.validateUser('nobody', 'pass')).toBeNull()
    })

    it('throws 401 when user is disabled', async () => {
      mockPrisma.user.findUnique.mockResolvedValue({ id: 1, username: 'admin', password: 'hash', status: 0 })
      await expect(service.validateUser('admin', 'pass')).rejects.toThrow(UnauthorizedException)
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
  })

  describe('refresh', () => {
    it('throws when JWT signature is invalid', async () => {
      mockJwt.verify.mockImplementation(() => { throw new Error('invalid') })
      await expect(service.refresh('bad-token')).rejects.toThrow(UnauthorizedException)
    })

    it('throws when token is not in DB (revoked)', async () => {
      mockJwt.verify.mockReturnValue({ sub: 1, username: 'admin' })
      mockPrisma.refreshToken.findUnique.mockResolvedValue(null)
      await expect(service.refresh('valid-jwt-but-revoked')).rejects.toThrow(UnauthorizedException)
    })

    it('throws when refresh token row is expired', async () => {
      mockJwt.verify.mockReturnValue({ sub: 1, username: 'admin' })
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
      mockJwt.verify.mockReturnValue({ sub: 1, username: 'admin' })
      mockPrisma.refreshToken.findUnique.mockResolvedValue({
        id: 1,
        token: 'hash',
        userId: 1,
        expiresAt: new Date(Date.now() + 60_000),
      })
      mockPrisma.user.findUnique.mockResolvedValue({ id: 1, status: 0 })
      await expect(service.refresh('rt')).rejects.toThrow(UnauthorizedException)
    })

    it('rotates token and returns new token pair', async () => {
      mockJwt.verify.mockReturnValue({ sub: 1, username: 'admin' })
      mockPrisma.refreshToken.findUnique.mockResolvedValue({
        id: 1,
        token: 'hash',
        userId: 1,
        expiresAt: new Date(Date.now() + 60_000),
      })
      mockPrisma.user.findUnique.mockResolvedValue({ id: 1, username: 'admin', status: 1 })
      mockJwt.sign
        .mockReturnValueOnce('new-access-token')
        .mockReturnValueOnce('new-refresh-token')
      mockPrisma.$transaction.mockResolvedValue([undefined, undefined])

      const result = await service.refresh('old-refresh-token')

      expect(result.access_token).toBe('new-access-token')
      expect(result.refresh_token).toBe('new-refresh-token')
      expect(result.expires_in).toBe(900)
      expect(mockPrisma.$transaction).toHaveBeenCalledTimes(1)
    })
  })

  describe('logout', () => {
    it('deletes the refresh token from DB', async () => {
      mockPrisma.refreshToken.deleteMany.mockResolvedValue({ count: 1 })
      await service.logout('some-refresh-token')
      expect(mockPrisma.refreshToken.deleteMany).toHaveBeenCalledTimes(1)
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
