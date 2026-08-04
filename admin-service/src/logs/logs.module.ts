import { Module } from '@nestjs/common'
import { PrismaModule } from '../prisma/prisma.module'
import { PermissionsModule } from '../common/permissions/permissions.module'
import { LogsService } from './logs.service'
import { LogsController } from './logs.controller'

@Module({
  imports: [PrismaModule, PermissionsModule],
  controllers: [LogsController],
  providers: [LogsService],
  exports: [LogsService],
})
export class LogsModule {}
