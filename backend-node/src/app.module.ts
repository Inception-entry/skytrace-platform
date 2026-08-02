import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { HttpModule } from '@nestjs/axios';
import { APP_GUARD } from '@nestjs/core';
import { HealthController } from './health/health.controller';
import { MetricsController } from './metrics/metrics.controller';
import { JavaClientService } from './shared/java-client.service';
import { AlarmController } from './alarm/alarm.controller';
import { InspectionTaskController } from './inspection-task/inspection-task.controller';
import { AlarmRealtimeGateway } from './realtime/alarm-realtime.gateway';
import { KeycloakJwtService } from './auth/keycloak-jwt.service';
import { KnowledgeController } from './knowledge/knowledge.controller';
import { HttpJwtAuthGuard } from './auth/http-jwt-auth.guard';
import { HttpRolesGuard } from './auth/http-roles.guard';
import { AdminController } from './admin/admin.controller';
import { AlarmRealtimeConsumer } from './messaging/alarm-realtime.consumer';
import { EvidenceController } from './evidence/evidence.controller';
import { DeviceController } from './device/device.controller';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    HttpModule.register({ timeout: 5_000, maxRedirects: 3 }),
  ],
  controllers: [
    HealthController,
    MetricsController,
    AlarmController,
    InspectionTaskController,
    KnowledgeController,
    AdminController,
    EvidenceController,
    DeviceController,
  ],
  providers: [
    JavaClientService,
    KeycloakJwtService,
    AlarmRealtimeGateway,
    AlarmRealtimeConsumer,
    {
      provide: APP_GUARD,
      useClass: HttpJwtAuthGuard,
    },
    {
      provide: APP_GUARD,
      useClass: HttpRolesGuard,
    },
  ],
})
export class AppModule {}
