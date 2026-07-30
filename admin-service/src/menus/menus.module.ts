import { Module } from '@nestjs/common'
import { PrismaModule } from '../prisma/prisma.module'
import { MenusService } from './menus.service'
import { MenusController } from './menus.controller'

@Module({
  imports: [PrismaModule],
  providers: [MenusService],
  controllers: [MenusController],
  exports: [MenusService],
})
export class MenusModule {}
