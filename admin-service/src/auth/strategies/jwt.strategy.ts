import { ExtractJwt, Strategy } from 'passport-jwt'
import { PassportStrategy } from '@nestjs/passport'
import { Injectable, UnauthorizedException } from '@nestjs/common'
import { ConfigService } from '@nestjs/config'
import { PrismaService } from '../../prisma/prisma.service'
import { resolveJwtSecrets } from '../jwt-secrets'

interface JwtPayload {
  sub: number
  username: string
  type?: string
}

@Injectable()
export class JwtStrategy extends PassportStrategy(Strategy) {
  constructor(
    config: ConfigService,
    private readonly prisma: PrismaService,
  ) {
    const { accessSecret } = resolveJwtSecrets({
      JWT_SECRET: config.get<string>('JWT_SECRET'),
      JWT_REFRESH_SECRET: config.get<string>('JWT_REFRESH_SECRET'),
    })
    super({
      jwtFromRequest: ExtractJwt.fromAuthHeaderAsBearerToken(),
      ignoreExpiration: false,
      secretOrKey: accessSecret,
      algorithms: ['HS256'],
    })
  }

  async validate(payload: JwtPayload) {
    if (payload.type === 'refresh') {
      throw new UnauthorizedException('无效的访问令牌')
    }
    const user = await this.prisma.user.findUnique({ where: { id: payload.sub } })
    if (!user || user.status !== 1) {
      throw new UnauthorizedException('账号不存在或已被禁用')
    }
    return { id: user.id, username: user.username }
  }
}
