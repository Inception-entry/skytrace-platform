import { Controller, Post, Put, Get, Body, UseGuards, HttpCode, Req, Res, UnauthorizedException, ForbiddenException } from '@nestjs/common'
import type { Request, Response } from 'express'
import { AuthService } from './auth.service'
import { AuthRateLimit } from './decorators/auth-rate-limit.decorator'
import { AuthRateLimitGuard } from './guards/auth-rate-limit.guard'
import { LocalAuthGuard } from './guards/local-auth.guard'
import { JwtAuthGuard } from './guards/jwt-auth.guard'
import { CurrentUser, RequestUser } from '../common/decorators/current-user.decorator'
import { RefreshDto } from './dto/refresh.dto'
import { UpdateProfileDto } from './dto/update-profile.dto'
import { ChangePasswordDto } from './dto/change-password.dto'
import { Log } from '../common/decorators/log.decorator'
import {
  CSRF_HEADER,
  readCookie,
  REFRESH_COOKIE_NAME,
  refreshClearCookie,
  refreshSetCookie,
  resolveRefreshCredential,
} from './refresh-cookie'

@Controller('auth')
export class AuthController {
  constructor(private authService: AuthService) {}

  @AuthRateLimit('login')
  @UseGuards(AuthRateLimitGuard, LocalAuthGuard)
  @Post('login')
  @HttpCode(200)
  @Log('系统', '登录')
  async login(@CurrentUser() user: RequestUser, @Res({ passthrough: true }) res: Response) {
    const tokens = await this.authService.login(user.id, user.username)
    res.setHeader('Set-Cookie', refreshSetCookie(tokens.refresh_token))
    return tokens
  }

  @AuthRateLimit('refresh')
  @UseGuards(AuthRateLimitGuard)
  @Post('refresh')
  @Log('系统', '刷新令牌')
  @HttpCode(200)
  async refresh(
    @Body() dto: RefreshDto,
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
  ) {
    const credential = this.credential(dto, req)
    const tokens = await this.authService.refresh(credential.token)
    res.setHeader('Set-Cookie', refreshSetCookie(tokens.refresh_token))
    return tokens
  }

  @UseGuards(JwtAuthGuard)
  @Post('logout')
  @Log('系统', '登出')
  @HttpCode(204)
  async logout(
    @Body() dto: RefreshDto,
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
  ) {
    const body = typeof dto.refresh_token === 'string' ? dto.refresh_token.trim() : ''
    const cookie = readCookie(req.headers.cookie, REFRESH_COOKIE_NAME)
    if (!body && cookie) {
      this.credential(dto, req)
    }
    if (!body && !cookie) {
      throw new UnauthorizedException('缺少刷新令牌')
    }
    if (body) await this.authService.logout(body)
    if (cookie && cookie !== body) await this.authService.logout(cookie)
    res.setHeader('Set-Cookie', refreshClearCookie())
  }

  private credential(dto: RefreshDto, req: Request) {
    const credential = resolveRefreshCredential({
      bodyToken: dto.refresh_token,
      cookieHeader: req.headers.cookie,
      csrfHeader: req.headers[CSRF_HEADER],
    })
    if (!credential.ok) {
      throw credential.reason === 'csrf'
        ? new ForbiddenException('缺少 CSRF 请求头')
        : new UnauthorizedException('缺少刷新令牌')
    }
    return credential
  }

  @UseGuards(JwtAuthGuard)
  @Get('me')
  getMe(@CurrentUser() user: RequestUser) {
    return this.authService.getMe(user.id)
  }

  @UseGuards(JwtAuthGuard)
  @Put('profile')
  updateProfile(@CurrentUser() user: RequestUser, @Body() dto: UpdateProfileDto) {
    return this.authService.updateProfile(user.id, dto)
  }

  @UseGuards(JwtAuthGuard)
  @Put('password')
  @Log('系统', '修改密码')
  @HttpCode(204)
  async changePassword(@CurrentUser() user: RequestUser, @Body() dto: ChangePasswordDto) {
    await this.authService.changePassword(user.id, dto)
  }
}
