import { Body, Controller, HttpCode, Post, Req } from '@nestjs/common'
import type { Request } from 'express'
import { ClientLogDto } from './client-log.dto'
import { ClientLogService } from './client-log.service'

@Controller('client-logs')
export class ClientLogController {
  constructor(private readonly clientLogs: ClientLogService) {}

  @Post()
  @HttpCode(204)
  accept(@Body() body: ClientLogDto, @Req() request: Request): void {
    this.clientLogs.accept(body, request.ip)
  }
}
