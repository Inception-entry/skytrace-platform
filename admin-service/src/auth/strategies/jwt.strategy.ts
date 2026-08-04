import { ExtractJwt, Strategy } from 'passport-jwt'
import { PassportStrategy } from '@nestjs/passport'
import { Injectable, UnauthorizedException } from '@nestjs/common'
import { ConfigService } from '@nestjs/config'
import { PrismaService } from '../../prisma/prisma.service'

interface JwtPayload {
  sub: number
  username: string
}

@Injectable()
export class JwtStrategy extends PassportStrategy(Strategy) {
  constructor(
    config: ConfigService,
    private readonly prisma: PrismaService,
  ) {
    const secret = config.get<string>('JWT_SECRET')
    if (!secret || secret === 'dev-jwt-secret-change-in-production') {
      throw new Error(
        'JWT_SECRET must be set to a non-default value before starting admin-service',
      )
    }
    super({
      jwtFromRequest: ExtractJwt.fromAuthHeaderAsBearerToken(),
      ignoreExpiration: false,
      secretOrKey: secret,
    })
  }

  async validate(payload: JwtPayload) {
    const user = await this.prisma.user.findUnique({ where: { id: payload.sub } })
    if (!user || user.status !== 1) {
      throw new UnauthorizedException('账号不存在或已被禁用')
    }
    return { id: user.id, username: user.username }
  }
}
