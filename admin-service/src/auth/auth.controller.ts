import { Controller, Post, Get, Body, UseGuards, HttpCode } from '@nestjs/common'
import { AuthService } from './auth.service'
import { LocalAuthGuard } from './guards/local-auth.guard'
import { JwtAuthGuard } from './guards/jwt-auth.guard'
import { CurrentUser, RequestUser } from '../common/decorators/current-user.decorator'
import { RefreshDto } from './dto/refresh.dto'

@Controller('auth')
export class AuthController {
  constructor(private authService: AuthService) {}

  // Credentials are read by LocalStrategy from req.body; guard fires before pipes
  @UseGuards(LocalAuthGuard)
  @Post('login')
  @HttpCode(200)
  login(@CurrentUser() user: RequestUser) {
    return this.authService.login(user.id, user.username)
  }

  @Post('refresh')
  @HttpCode(200)
  refresh(@Body() dto: RefreshDto) {
    return this.authService.refresh(dto.refresh_token)
  }

  @UseGuards(JwtAuthGuard)
  @Get('me')
  getMe(@CurrentUser() user: RequestUser) {
    return this.authService.getMe(user.id)
  }
}
