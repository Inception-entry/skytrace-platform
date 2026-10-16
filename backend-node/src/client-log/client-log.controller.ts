import { Body, Controller, HttpCode, Post, Req } from '@nestjs/common';
import { PublicRoute } from '../auth/http-auth.decorators';
import { ClientLogDto } from './client-log.dto';
import { ClientLogService } from './client-log.service';

@Controller('client-logs')
@PublicRoute()
export class ClientLogController {
  constructor(private readonly clientLogs: ClientLogService) {}

  @Post()
  @HttpCode(204)
  accept(@Body() body: ClientLogDto, @Req() request: { ip?: string }): void {
    this.clientLogs.accept(body, request.ip);
  }
}
