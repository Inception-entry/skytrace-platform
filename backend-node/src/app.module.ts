import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { AuthModule } from './auth/auth.module';
import { JavaClientModule } from './common/java-client/java-client.module';
import { HealthController } from './health/health.controller';
import { MetricsController } from './metrics/metrics.controller';
import { AlarmController } from './alarm/alarm.controller';
import { InspectionTaskController } from './inspection-task/inspection-task.controller';
import { AlarmRealtimeGateway } from './realtime/alarm-realtime.gateway';
import { KnowledgeController } from './knowledge/knowledge.controller';
import { AdminController } from './admin/admin.controller';
import { AlarmRealtimeConsumer } from './messaging/alarm-realtime.consumer';
import { EvidenceController } from './evidence/evidence.controller';
import { DeviceController } from './device/device.controller';
import { RouteController } from './route/route.controller';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    AuthModule,
    JavaClientModule,
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
    RouteController,
  ],
  providers: [
    AlarmRealtimeGateway,
    AlarmRealtimeConsumer,
  ],
})
export class AppModule {}
