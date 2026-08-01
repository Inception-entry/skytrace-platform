import { Controller, Get, Delete, Query, UseGuards, HttpCode } from '@nestjs/common'
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard'
import { LogsService } from './logs.service'
import { QueryLogDto } from './dto/query-log.dto'

@UseGuards(JwtAuthGuard)
@Controller('logs')
export class LogsController {
  constructor(private readonly logsService: LogsService) {}

  @Get()
  findAll(@Query() query: QueryLogDto) {
    return this.logsService.findAll(query)
  }

  @Delete()
  @HttpCode(200)
  clear() {
    return this.logsService.clear()
  }
}
