import { Controller, Get, Post, Query } from '@nestjs/common';
import { Roles } from '../auth/http-auth.decorators';
import { JavaClientService } from '../common/java-client/java-client.service';
import { ListAuditLogDto } from './dto/list-audit-log.dto';
import {
  EvidenceCleanupDto,
  EvidenceMaintenanceBatchDto,
} from './dto/evidence-maintenance.dto';

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

  @Get('evidence-maintenance/policy')
  evidenceMaintenancePolicy(): Promise<unknown> {
    return this.javaClient.get('/admin/evidence-maintenance/policy');
  }

  @Post('evidence-maintenance/hash-backfill')
  hashBackfill(
    @Query() query: EvidenceMaintenanceBatchDto,
  ): Promise<unknown> {
    const parameters = new URLSearchParams();
    if (query.batchSize != null) {
      parameters.set('batchSize', String(query.batchSize));
    }
    const suffix = parameters.size > 0
      ? `?${parameters.toString()}`
      : '';
    return this.javaClient.post(
      `/admin/evidence-maintenance/hash-backfill${suffix}`,
      {},
    );
  }

  @Get('evidence-maintenance/cleanup-preview')
  cleanupPreview(
    @Query() query: EvidenceMaintenanceBatchDto,
  ): Promise<unknown> {
    const parameters = new URLSearchParams();
    if (query.batchSize != null) {
      parameters.set('batchSize', String(query.batchSize));
    }
    const suffix = parameters.size > 0
      ? `?${parameters.toString()}`
      : '';
    return this.javaClient.get(
      `/admin/evidence-maintenance/cleanup-preview${suffix}`,
    );
  }

  @Post('evidence-maintenance/cleanup')
  cleanup(@Query() query: EvidenceCleanupDto): Promise<unknown> {
    const parameters = new URLSearchParams({
      dryRun: String(query.dryRun),
    });
    if (query.batchSize != null) {
      parameters.set('batchSize', String(query.batchSize));
    }
    if (query.confirmation) {
      parameters.set('confirmation', query.confirmation);
    }
    return this.javaClient.post(
      `/admin/evidence-maintenance/cleanup?${parameters.toString()}`,
      {},
    );
  }
}
