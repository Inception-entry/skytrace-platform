import { UnauthorizedException } from '@nestjs/common'
import { ConfigService } from '@nestjs/config'
import { JwtStrategy } from './jwt.strategy'
import { PrismaService } from '../../prisma/prisma.service'

const ACCESS_SECRET = 'a'.repeat(32)
const REFRESH_SECRET = 'b'.repeat(32)

describe('JwtStrategy', () => {
  const prisma = {
    user: { findUnique: jest.fn() },
  }
  const config = {
    get: (key: string) => {
      if (key === 'JWT_SECRET') return ACCESS_SECRET
      if (key === 'JWT_REFRESH_SECRET') return REFRESH_SECRET
      return undefined
    },
  }
  const strategy = new JwtStrategy(
    config as ConfigService,
    prisma as unknown as PrismaService,
  )

  it('rejects refresh tokens presented as access tokens', async () => {
    await expect(
      strategy.validate({
        sub: 1,
        username: 'admin',
        type: 'refresh',
      }),
    ).rejects.toThrow(UnauthorizedException)
    expect(prisma.user.findUnique).not.toHaveBeenCalled()
  })

  it('loads enabled users for access tokens', async () => {
    prisma.user.findUnique.mockResolvedValue({
      id: 1,
      username: 'admin',
      status: 1,
    })
    await expect(
      strategy.validate({ sub: 1, username: 'admin' }),
    ).resolves.toEqual({ id: 1, username: 'admin' })
  })
})
