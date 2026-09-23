import { Module } from '@nestjs/common'
import { JwtModule } from '@nestjs/jwt'
import { PassportModule } from '@nestjs/passport'
import { ConfigModule, ConfigService } from '@nestjs/config'
import { PrismaModule } from '../prisma/prisma.module'
import { AuthService } from './auth.service'
import { AuthController } from './auth.controller'
import { AuthRateLimiter } from './auth-rate-limiter'
import { AuthRateLimitGuard } from './guards/auth-rate-limit.guard'
import { LocalStrategy } from './strategies/local.strategy'
import { JwtStrategy } from './strategies/jwt.strategy'
import { resolveJwtSecrets } from './jwt-secrets'

@Module({
  imports: [
    PrismaModule,
    PassportModule,
    JwtModule.registerAsync({
      imports: [ConfigModule],
      inject: [ConfigService],
      useFactory: (config: ConfigService) => {
        const { accessSecret } = resolveJwtSecrets({
          JWT_SECRET: config.get<string>('JWT_SECRET'),
          JWT_REFRESH_SECRET: config.get<string>('JWT_REFRESH_SECRET'),
        })
        return {
          secret: accessSecret,
          signOptions: { expiresIn: '15m', algorithm: 'HS256' },
          verifyOptions: { algorithms: ['HS256'] },
        }
      },
    }),
  ],
  providers: [
    AuthService,
    {
      provide: AuthRateLimiter,
      inject: [ConfigService],
      useFactory: (config: ConfigService) => new AuthRateLimiter({
        redisHost: config.get<string>('REDIS_HOST'),
        redisPort: Number(config.get<string>('REDIS_PORT') ?? 6379),
      }),
    },
    AuthRateLimitGuard,
    LocalStrategy,
    JwtStrategy,
  ],
  controllers: [AuthController],
})
export class AuthModule {}
