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
import { RequireAnyPermission, RequirePermissions } from '../common/decorators/require-permissions.decorator'
import { CurrentUser, RequestUser } from '../common/decorators/current-user.decorator'
import { RolesService } from './roles.service'
import { CreateRoleDto } from './dto/create-role.dto'
import { UpdateRoleDto } from './dto/update-role.dto'
import { QueryRoleDto } from './dto/query-role.dto'
import { AssignMenusDto } from './dto/assign-menus.dto'
import { Log } from '../common/decorators/log.decorator'

@UseGuards(JwtAuthGuard, PermissionsGuard)
@Controller('roles')
export class RolesController {
  constructor(private rolesService: RolesService) {}

  @Get()
  @RequireAnyPermission('role:list', 'user:assign-roles')
  findAll(@Query() query: QueryRoleDto) {
    return this.rolesService.findAll(query)
  }

  @Get(':id')
  @RequirePermissions('role:list')
  findOne(@Param('id', ParseIntPipe) id: number) {
    return this.rolesService.findOneWithMenus(id)
  }

  @Post()
  @RequirePermissions('role:create')
  @Log('角色管理', '创建')
  create(@Body() dto: CreateRoleDto) {
    return this.rolesService.create(dto)
  }

  @Put(':id')
  @RequirePermissions('role:update')
  @Log('角色管理', '更新')
  update(@Param('id', ParseIntPipe) id: number, @Body() dto: UpdateRoleDto) {
    return this.rolesService.update(id, dto)
  }

  @Delete(':id')
  @HttpCode(204)
  @RequirePermissions('role:delete')
  @Log('角色管理', '删除')
  remove(@Param('id', ParseIntPipe) id: number) {
    return this.rolesService.remove(id)
  }

  @Get(':id/menus')
  @RequirePermissions('role:list')
  getMenuIds(@Param('id', ParseIntPipe) id: number) {
    return this.rolesService.getMenuIds(id)
  }

  @Put(':id/menus')
  @RequirePermissions('role:assign-menus')
  @Log('角色管理', '分配菜单')
  assignMenus(
    @Param('id', ParseIntPipe) id: number,
    @Body() dto: AssignMenusDto,
    @CurrentUser() actor: RequestUser,
  ) {
    return this.rolesService.assignMenus(id, dto.menuIds, actor.id)
  }
}
