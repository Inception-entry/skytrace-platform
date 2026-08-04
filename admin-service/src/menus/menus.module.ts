import { Module } from '@nestjs/common'
import { PrismaModule } from '../prisma/prisma.module'
import { PermissionsModule } from '../common/permissions/permissions.module'
import { MenusService } from './menus.service'
import { MenusController } from './menus.controller'

@Module({
  imports: [PrismaModule, PermissionsModule],
  providers: [MenusService],
  controllers: [MenusController],
  exports: [MenusService],
})
export class MenusModule {}
