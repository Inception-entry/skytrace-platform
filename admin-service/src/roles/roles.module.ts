import { Module } from '@nestjs/common'
import { PrismaModule } from '../prisma/prisma.module'
import { PermissionsModule } from '../common/permissions/permissions.module'
import { RolesService } from './roles.service'
import { RolesController } from './roles.controller'

@Module({
  imports: [PrismaModule, PermissionsModule],
  providers: [RolesService],
  controllers: [RolesController],
  exports: [RolesService],
})
export class RolesModule {}
