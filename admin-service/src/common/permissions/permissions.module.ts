import { Module } from '@nestjs/common'
import { PrismaModule } from '../../prisma/prisma.module'
import { PermissionsService } from './permissions.service'
import { PermissionsGuard } from './permissions.guard'

@Module({
  imports: [PrismaModule],
  providers: [PermissionsService, PermissionsGuard],
  exports: [PermissionsService, PermissionsGuard],
})
export class PermissionsModule {}
