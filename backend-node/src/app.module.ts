import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { AuthModule } from './auth/auth.module';
import { JavaClientModule } from './common/java-client/java-client.module';
import { HealthModule } from './health/health.module';
import { MetricsModule } from './metrics/metrics.module';
import { DeviceModule } from './device/device.module';
import { RouteModule } from './route/route.module';
import { EvidenceModule } from './evidence/evidence.module';
import { AlarmModule } from './alarm/alarm.module';
import { InspectionTaskModule } from './inspection-task/inspection-task.module';
import { KnowledgeModule } from './knowledge/knowledge.module';
import { AdminModule } from './admin/admin.module';
import { RealtimeModule } from './realtime/realtime.module';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    AuthModule,
    JavaClientModule,
    HealthModule,
    MetricsModule,
    DeviceModule,
    RouteModule,
    EvidenceModule,
    AlarmModule,
    InspectionTaskModule,
    KnowledgeModule,
    AdminModule,
    RealtimeModule,
  ],
})
export class AppModule {}
