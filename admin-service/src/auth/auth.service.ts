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

interface AccessJwtPayload {
  sub: number
  username: string
}

interface RefreshJwtPayload extends AccessJwtPayload {
  type: 'refresh'
  jti: string
}

function hashToken(token: string): string {
  return createHash('sha256').update(token).digest('hex')
}

function isRefreshPayload(payload: unknown): payload is RefreshJwtPayload {
  if (!payload || typeof payload !== 'object') {
    return false
  }
  const value = payload as Record<string, unknown>
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
  constructor(
    private prisma: PrismaService,
    private jwtService: JwtService,
    private config: ConfigService,
  ) {}

  private get refreshSecret() {
    const secret = this.config.get<string>('JWT_REFRESH_SECRET')
    if (
      !secret
      || secret === 'dev-jwt-refresh-secret-change-in-production'
    ) {
      throw new Error(
        'JWT_REFRESH_SECRET must be set to a non-default value',
      )
    }
    return secret
  }

  async validateUser(username: string, password: string) {
    const user = await this.prisma.user.findUnique({ where: { username } })
    if (!user) return null
    if (user.status !== 1) throw new UnauthorizedException('账号已被禁用')
    const valid = await bcrypt.compare(password, user.password)
    if (!valid) return null
    return user
  }

  async login(userId: number, username: string) {
    const accessToken = this.jwtService.sign({ sub: userId, username })
    const refreshToken = this.signRefreshToken(userId, username)

    await this.prisma.refreshToken.create({
      data: {
        token: hashToken(refreshToken),
        userId,
        expiresAt: new Date(Date.now() + REFRESH_TTL_MS),
      },
    })

    return { access_token: accessToken, refresh_token: refreshToken, expires_in: 900 }
  }

  async refresh(refreshToken: string) {
    const payload = this.verifyRefreshToken(refreshToken)
    const tokenHash = hashToken(refreshToken)

    try {
      return await this.prisma.$transaction(async tx => {
        const stored = await tx.refreshToken.findUnique({ where: { token: tokenHash } })
        if (!stored) {
          throw new UnauthorizedException('令牌已撤销')
        }
        if (stored.expiresAt.getTime() <= Date.now()) {
          await tx.refreshToken.deleteMany({ where: { token: tokenHash } })
          throw new UnauthorizedException('刷新令牌已过期')
        }

        const consumed = await tx.refreshToken.deleteMany({ where: { token: tokenHash } })
        if (consumed.count !== 1) {
          throw new UnauthorizedException('令牌已撤销')
        }

        const user = await tx.user.findUnique({ where: { id: payload.sub } })
        if (!user || user.status !== 1) {
          throw new UnauthorizedException('账号不存在或已被禁用')
        }

        const newAccessToken = this.jwtService.sign({
          sub: user.id,
          username: user.username,
        })
        const newRefreshToken = this.signRefreshToken(user.id, user.username)
        await tx.refreshToken.create({
          data: {
            token: hashToken(newRefreshToken),
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

  private signRefreshToken(userId: number, username: string): string {
    const payload: RefreshJwtPayload = {
      sub: userId,
      username,
      type: 'refresh',
      jti: randomUUID(),
    }
    return this.jwtService.sign(payload, {
      secret: this.refreshSecret,
      expiresIn: '7d',
    })
  }

  private verifyRefreshToken(refreshToken: string): RefreshJwtPayload {
    let payload: unknown
    try {
      payload = this.jwtService.verify(refreshToken, { secret: this.refreshSecret })
    } catch {
      throw new UnauthorizedException('无效或已过期的刷新令牌')
    }
    if (!isRefreshPayload(payload)) {
      throw new UnauthorizedException('无效或已过期的刷新令牌')
    }
    return payload
  }
}
