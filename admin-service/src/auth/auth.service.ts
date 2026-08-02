import { Injectable, UnauthorizedException, BadRequestException } from '@nestjs/common'
import { JwtService } from '@nestjs/jwt'
import { ConfigService } from '@nestjs/config'
import { createHash } from 'crypto'
import * as bcrypt from 'bcryptjs'
import { PrismaService } from '../prisma/prisma.service'
import { buildMenuTree } from '../common/utils/menu-tree'
import { UpdateProfileDto } from './dto/update-profile.dto'
import { ChangePasswordDto } from './dto/change-password.dto'

interface JwtPayload {
  sub: number
  username: string
}

function hashToken(token: string): string {
  return createHash('sha256').update(token).digest('hex')
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
    const payload: JwtPayload = { sub: userId, username }
    const accessToken = this.jwtService.sign(payload)
    const refreshToken = this.jwtService.sign(payload, { secret: this.refreshSecret, expiresIn: '7d' })

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
    let payload: JwtPayload
    try {
      payload = this.jwtService.verify<JwtPayload>(refreshToken, { secret: this.refreshSecret })
    } catch {
      throw new UnauthorizedException('无效或已过期的刷新令牌')
    }

    const tokenHash = hashToken(refreshToken)
    const stored = await this.prisma.refreshToken.findUnique({ where: { token: tokenHash } })
    if (!stored) throw new UnauthorizedException('令牌已撤销')

    const user = await this.prisma.user.findUnique({ where: { id: payload.sub } })
    if (!user || user.status !== 1) throw new UnauthorizedException('账号不存在或已被禁用')

    const newPayload: JwtPayload = { sub: user.id, username: user.username }
    const newAccessToken = this.jwtService.sign(newPayload)
    const newRefreshToken = this.jwtService.sign(newPayload, { secret: this.refreshSecret, expiresIn: '7d' })

    await this.prisma.$transaction([
      this.prisma.refreshToken.delete({ where: { token: tokenHash } }),
      this.prisma.refreshToken.create({
        data: {
          token: hashToken(newRefreshToken),
          userId: user.id,
          expiresAt: new Date(Date.now() + REFRESH_TTL_MS),
        },
      }),
    ])

    return { access_token: newAccessToken, refresh_token: newRefreshToken, expires_in: 900 }
  }

  async logout(refreshToken: string) {
    const tokenHash = hashToken(refreshToken)
    await this.prisma.refreshToken.deleteMany({ where: { token: tokenHash } })
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
    await this.prisma.user.update({ where: { id: userId }, data: { password: hashed } })
  }

  async getMe(userId: number) {
    const user = await this.prisma.user.findUniqueOrThrow({
      where: { id: userId },
      include: {
        userRoles: {
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
    const menus = buildMenuTree(uniqueMenus)

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
}
