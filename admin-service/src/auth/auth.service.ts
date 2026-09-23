import { Injectable, UnauthorizedException, BadRequestException } from '@nestjs/common'
import { JwtService } from '@nestjs/jwt'
import { ConfigService } from '@nestjs/config'
import { Prisma } from '@prisma/client'
import { createHash, randomUUID } from 'crypto'
import * as bcrypt from 'bcryptjs'
import { PrismaService } from '../prisma/prisma.service'
import { buildMenuTree } from '../common/utils/menu-tree'
import { UpdateProfileDto } from './dto/update-profile.dto'
import { ChangePasswordDto } from './dto/change-password.dto'
import { DUMMY_PASSWORD_HASH } from './dummy-password-hash'
import { resolveJwtSecrets, type JwtSecrets } from './jwt-secrets'

interface AccessJwtPayload {
  sub: number
  username: string
}

interface RefreshJwtPayload extends AccessJwtPayload {
  type: 'refresh'
  jti: string
  familyId?: string
}

function hashToken(token: string): string {
  return createHash('sha256').update(token).digest('hex')
}

function isRefreshPayload(payload: unknown): payload is RefreshJwtPayload {
  if (!payload || typeof payload !== 'object') {
    return false
  }
  const value = payload as Record<string, unknown>
  if (value.familyId !== undefined && (typeof value.familyId !== 'string' || value.familyId.length === 0)) {
    return false
  }
  return (
    value.type === 'refresh'
    && typeof value.jti === 'string'
    && value.jti.length > 0
    && typeof value.sub === 'number'
    && typeof value.username === 'string'
  )
}

function isPrismaConflict(error: unknown): boolean {
  return (
    error instanceof Prisma.PrismaClientKnownRequestError
    && (error.code === 'P2002' || error.code === 'P2025')
  )
}

const REFRESH_TTL_MS = 7 * 24 * 60 * 60 * 1000

@Injectable()
export class AuthService {
  private readonly secrets: JwtSecrets

  constructor(
    private prisma: PrismaService,
    private jwtService: JwtService,
    config: ConfigService,
  ) {
    this.secrets = resolveJwtSecrets({
      JWT_SECRET: config.get<string>('JWT_SECRET'),
      JWT_REFRESH_SECRET: config.get<string>('JWT_REFRESH_SECRET'),
    })
  }

  private get refreshSecret() {
    return this.secrets.refreshSecret
  }

  async validateUser(username: string, password: string) {
    const user = await this.prisma.user.findUnique({ where: { username } })
    const hash = user && user.status === 1 ? user.password : DUMMY_PASSWORD_HASH
    const valid = await bcrypt.compare(password, hash)
    if (!user || user.status !== 1 || !valid) return null
    return user
  }

  async login(userId: number, username: string) {
    const accessToken = this.jwtService.sign({ sub: userId, username })
    const familyId = randomUUID()
    const refreshToken = this.signRefreshToken(userId, username, familyId)

    await this.prisma.refreshToken.create({
      data: {
        token: hashToken(refreshToken),
        familyId,
        userId,
        expiresAt: new Date(Date.now() + REFRESH_TTL_MS),
      },
    })

    return { access_token: accessToken, refresh_token: refreshToken, expires_in: 900 }
  }

  async refresh(refreshToken: string) {
    const payload = this.verifyRefreshToken(refreshToken)
    const tokenHash = hashToken(refreshToken)
    let familyToRevoke: string | undefined

    try {
      return await this.prisma.$transaction(async tx => {
        const stored = await tx.refreshToken.findUnique({ where: { token: tokenHash } })
        const familyId = stored?.familyId ?? payload.familyId
        if (!stored) {
          familyToRevoke = payload.familyId
          throw new UnauthorizedException('令牌已撤销')
        }
        if (stored.expiresAt.getTime() <= Date.now()) {
          await tx.refreshToken.deleteMany({ where: { token: tokenHash } })
          throw new UnauthorizedException('刷新令牌已过期')
        }

        const consumed = await tx.refreshToken.deleteMany({ where: { token: tokenHash } })
        if (consumed.count !== 1) {
          familyToRevoke = familyId ?? undefined
          throw new UnauthorizedException('令牌已撤销')
        }

        const user = await tx.user.findUnique({ where: { id: payload.sub } })
        if (!user || user.status !== 1) {
          throw new UnauthorizedException('账号不存在或已被禁用')
        }

        const nextFamilyId = familyId ?? randomUUID()
        const newAccessToken = this.jwtService.sign({
          sub: user.id,
          username: user.username,
        })
        const newRefreshToken = this.signRefreshToken(user.id, user.username, nextFamilyId)
        await tx.refreshToken.create({
          data: {
            token: hashToken(newRefreshToken),
            familyId: nextFamilyId,
            userId: user.id,
            expiresAt: new Date(Date.now() + REFRESH_TTL_MS),
          },
        })

        return {
          access_token: newAccessToken,
          refresh_token: newRefreshToken,
          expires_in: 900,
        }
      })
    } catch (error) {
      if (familyToRevoke) {
        await this.prisma.refreshToken.deleteMany({ where: { familyId: familyToRevoke } })
      }
      if (error instanceof UnauthorizedException) {
        throw error
      }
      if (isPrismaConflict(error)) {
        throw new UnauthorizedException('令牌已撤销')
      }
      throw error
    }
  }

  async logout(refreshToken: string) {
    const tokenHash = hashToken(refreshToken)
    await this.prisma.refreshToken.deleteMany({ where: { token: tokenHash } })
  }

  async revokeAllSessions(userId: number) {
    await this.prisma.refreshToken.deleteMany({ where: { userId } })
  }

  async updateProfile(userId: number, dto: UpdateProfileDto) {
    const user = await this.prisma.user.update({
      where: { id: userId },
      data: dto,
    })
    return {
      id: user.id,
      username: user.username,
      nickname: user.nickname,
      email: user.email,
      avatar: user.avatar,
    }
  }

  async changePassword(userId: number, dto: ChangePasswordDto) {
    const user = await this.prisma.user.findUniqueOrThrow({ where: { id: userId } })
    const valid = await bcrypt.compare(dto.currentPassword, user.password)
    if (!valid) throw new BadRequestException('当前密码不正确')
    const hashed = await bcrypt.hash(dto.newPassword, 10)
    await this.prisma.$transaction([
      this.prisma.user.update({ where: { id: userId }, data: { password: hashed } }),
      this.prisma.refreshToken.deleteMany({ where: { userId } }),
    ])
  }

  async getMe(userId: number) {
    const user = await this.prisma.user.findUniqueOrThrow({
      where: { id: userId },
      include: {
        userRoles: {
          where: { role: { status: 1 } },
          include: {
            role: {
              include: {
                roleMenus: { include: { menu: true } },
              },
            },
          },
        },
      },
    })

    const roles = user.userRoles.map(ur => ({
      id: ur.role.id,
      name: ur.role.name,
      code: ur.role.code,
    }))

    const allMenus = user.userRoles.flatMap(ur => ur.role.roleMenus.map(rm => rm.menu))
    const uniqueMenus = [...new Map(allMenus.map(m => [m.id, m])).values()]

    const permissions = uniqueMenus.map(m => m.code)
    const menus = buildMenuTree(uniqueMenus.filter(m => m.type !== 3 && m.visible === 1))

    return {
      id: user.id,
      username: user.username,
      nickname: user.nickname,
      email: user.email,
      avatar: user.avatar,
      roles,
      permissions,
      menus,
    }
  }

  private signRefreshToken(userId: number, username: string, familyId: string): string {
    const payload: RefreshJwtPayload = {
      sub: userId,
      username,
      type: 'refresh',
      jti: randomUUID(),
      familyId,
    }
    return this.jwtService.sign(payload, {
      secret: this.refreshSecret,
      expiresIn: '7d',
      algorithm: 'HS256',
    })
  }

  private verifyRefreshToken(refreshToken: string): RefreshJwtPayload {
    let payload: unknown
    try {
      payload = this.jwtService.verify(refreshToken, {
        secret: this.refreshSecret,
        algorithms: ['HS256'],
      })
    } catch {
      throw new UnauthorizedException('无效或已过期的刷新令牌')
    }
    if (!isRefreshPayload(payload)) {
      throw new UnauthorizedException('无效或已过期的刷新令牌')
    }
    return payload
  }
}
