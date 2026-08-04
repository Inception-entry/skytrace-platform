import { Module } from '@nestjs/common'
import { PrismaModule } from '../prisma/prisma.module'
import { PermissionsModule } from '../common/permissions/permissions.module'
import { UsersService } from './users.service'
import { UsersController } from './users.controller'

@Module({
  imports: [PrismaModule, PermissionsModule],
  providers: [UsersService],
  controllers: [UsersController],
  exports: [UsersService],
})
export class UsersModule {}
