import {
  Controller,
  Get,
  Post,
  Put,
  Delete,
  Body,
  Param,
  Query,
  ParseIntPipe,
  UseGuards,
  HttpCode,
} from '@nestjs/common'
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard'
import { PermissionsGuard } from '../common/permissions/permissions.guard'
import { RequirePermissions } from '../common/decorators/require-permissions.decorator'
import { CurrentUser, RequestUser } from '../common/decorators/current-user.decorator'
import { UsersService } from './users.service'
import { CreateUserDto } from './dto/create-user.dto'
import { UpdateUserDto } from './dto/update-user.dto'
import { QueryUserDto } from './dto/query-user.dto'
import { AssignRolesDto } from './dto/assign-roles.dto'
import { Log } from '../common/decorators/log.decorator'

@UseGuards(JwtAuthGuard, PermissionsGuard)
@Controller('users')
export class UsersController {
  constructor(private usersService: UsersService) {}

  @Get()
  @RequirePermissions('user:list')
  findAll(@Query() query: QueryUserDto) {
    return this.usersService.findAll(query)
  }

  @Get(':id')
  @RequirePermissions('user:list')
  findOne(@Param('id', ParseIntPipe) id: number) {
    return this.usersService.findOne(id)
  }

  @Post()
  @RequirePermissions('user:create')
  @Log('用户管理', '创建')
  create(@Body() dto: CreateUserDto) {
    return this.usersService.create(dto)
  }

  @Put(':id')
  @RequirePermissions('user:update')
  @Log('用户管理', '更新')
  update(
    @Param('id', ParseIntPipe) id: number,
    @Body() dto: UpdateUserDto,
    @CurrentUser() actor: RequestUser,
  ) {
    return this.usersService.update(id, dto, actor.id)
  }

  @Delete(':id')
  @HttpCode(204)
  @RequirePermissions('user:delete')
  @Log('用户管理', '删除')
  remove(@Param('id', ParseIntPipe) id: number, @CurrentUser() actor: RequestUser) {
    return this.usersService.remove(id, actor.id)
  }

  @Put(':id/roles')
  @RequirePermissions('user:assign-roles')
  @Log('用户管理', '分配角色')
  assignRoles(
    @Param('id', ParseIntPipe) id: number,
    @Body() dto: AssignRolesDto,
    @CurrentUser() actor: RequestUser,
  ) {
    return this.usersService.assignRoles(id, dto.roleIds, actor.id)
  }
}
