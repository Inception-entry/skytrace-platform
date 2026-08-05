import { Controller, Get, Query } from '@nestjs/common';
import { Roles } from '../auth/http-auth.decorators';
import { JavaClientService } from '../common/java-client/java-client.service';
import { ListAuditLogDto } from './dto/list-audit-log.dto';

@Controller('admin')
@Roles('ADMIN')
export class AdminController {
  constructor(private readonly javaClient: JavaClientService) {}

  @Get('overview')
  overview(): Promise<unknown> {
    return this.javaClient.get('/admin/overview');
  }

  @Get('audit-logs')
  auditLogs(@Query() query: ListAuditLogDto): Promise<unknown> {
    const parameters = new URLSearchParams({
      page: String(query.page),
      size: String(query.size),
    });
    if (query.action) parameters.set('action', query.action);
    if (query.outcome) parameters.set('outcome', query.outcome);
    if (query.username) parameters.set('username', query.username);

    return this.javaClient.get(
      `/admin/audit-logs?${parameters.toString()}`,
    );
  }
}
