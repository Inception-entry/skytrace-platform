import { Injectable, UnauthorizedException } from '@nestjs/common'
import { JwtService } from '@nestjs/jwt'
import { ConfigService } from '@nestjs/config'
import * as bcrypt from 'bcryptjs'
import { PrismaService } from '../prisma/prisma.service'

interface JwtPayload {
  sub: number
  username: string
}

@Injectable()
export class AuthService {
  constructor(
    private prisma: PrismaService,
    private jwtService: JwtService,
    private config: ConfigService,
  ) {}

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
    const refreshSecret = this.config.get<string>('JWT_REFRESH_SECRET', 'dev-jwt-refresh-secret-change-in-production')
    return {
      access_token: this.jwtService.sign(payload),
      refresh_token: this.jwtService.sign(payload, { secret: refreshSecret, expiresIn: '7d' }),
      expires_in: 900,
    }
  }

  async refresh(refreshToken: string) {
    const refreshSecret = this.config.get<string>('JWT_REFRESH_SECRET', 'dev-jwt-refresh-secret-change-in-production')
    let payload: JwtPayload
    try {
      payload = this.jwtService.verify<JwtPayload>(refreshToken, { secret: refreshSecret })
    } catch {
      throw new UnauthorizedException('无效或已过期的刷新令牌')
    }

    const user = await this.prisma.user.findUnique({ where: { id: payload.sub } })
    if (!user || user.status !== 1) throw new UnauthorizedException('账号不存在或已被禁用')

    const newPayload: JwtPayload = { sub: user.id, username: user.username }
    return {
      access_token: this.jwtService.sign(newPayload),
      expires_in: 900,
    }
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

    const permissions = [
      ...new Set(user.userRoles.flatMap(ur => ur.role.roleMenus.map(rm => rm.menu.code))),
    ]

    return {
      id: user.id,
      username: user.username,
      nickname: user.nickname,
      email: user.email,
      roles,
      permissions,
    }
  }
}
