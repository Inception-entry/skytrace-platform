import { Controller, Get, Delete, Query, UseGuards, HttpCode } from '@nestjs/common'
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard'
import { PermissionsGuard } from '../common/permissions/permissions.guard'
import { RequirePermissions } from '../common/decorators/require-permissions.decorator'
import { LogsService } from './logs.service'
import { QueryLogDto } from './dto/query-log.dto'
import { Log } from '../common/decorators/log.decorator'

@UseGuards(JwtAuthGuard, PermissionsGuard)
@Controller('logs')
export class LogsController {
  constructor(private readonly logsService: LogsService) {}

  @Get()
  @RequirePermissions('log:list')
  findAll(@Query() query: QueryLogDto) {
    return this.logsService.findAll(query)
  }

  @Delete()
  @HttpCode(200)
  @RequirePermissions('log:clear')
  @Log('操作日志', '清空')
  clear() {
    return this.logsService.clear()
  }
}
