import { Module } from '@nestjs/common'
import { APP_INTERCEPTOR } from '@nestjs/core'
import { ConfigModule } from '@nestjs/config'
import { PrismaModule } from './prisma/prisma.module'
import { AuthModule } from './auth/auth.module'
import { UsersModule } from './users/users.module'
import { RolesModule } from './roles/roles.module'
import { MenusModule } from './menus/menus.module'
import { LogsModule } from './logs/logs.module'
import { DashboardModule } from './dashboard/dashboard.module'
import { UploadModule } from './upload/upload.module'
import { HealthController } from './health/health.controller'
import { MetricsController } from './metrics/metrics.controller'
import { OperationLogInterceptor } from './common/interceptors/operation-log.interceptor'

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    PrismaModule,
    AuthModule,
    UsersModule,
    RolesModule,
    MenusModule,
    LogsModule,
    DashboardModule,
    UploadModule,
  ],
  controllers: [HealthController, MetricsController],
  providers: [
    {
      provide: APP_INTERCEPTOR,
      useClass: OperationLogInterceptor,
    },
  ],
})
export class AppModule {}
