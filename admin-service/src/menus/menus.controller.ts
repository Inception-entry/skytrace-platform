import {
  Controller,
  Get,
  Post,
  Put,
  Delete,
  Body,
  Param,
  ParseIntPipe,
  UseGuards,
  HttpCode,
} from '@nestjs/common'
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard'
import { PermissionsGuard } from '../common/permissions/permissions.guard'
import { RequireAnyPermission, RequirePermissions } from '../common/decorators/require-permissions.decorator'
import { MenusService } from './menus.service'
import { CreateMenuDto } from './dto/create-menu.dto'
import { UpdateMenuDto } from './dto/update-menu.dto'
import { Log } from '../common/decorators/log.decorator'

@UseGuards(JwtAuthGuard, PermissionsGuard)
@Controller('menus')
export class MenusController {
  constructor(private menusService: MenusService) {}

  @Get()
  @RequireAnyPermission('menu:list', 'role:assign-menus')
  findAll() {
    return this.menusService.findAll()
  }

  @Get('flat')
  @RequireAnyPermission('menu:list', 'role:assign-menus')
  findFlat() {
    return this.menusService.findFlat()
  }

  @Get(':id')
  @RequirePermissions('menu:list')
  findOne(@Param('id', ParseIntPipe) id: number) {
    return this.menusService.findOne(id)
  }

  @Post()
  @RequirePermissions('menu:create')
  @Log('菜单管理', '创建')
  create(@Body() dto: CreateMenuDto) {
    return this.menusService.create(dto)
  }

  @Put(':id')
  @RequirePermissions('menu:update')
  @Log('菜单管理', '更新')
  update(@Param('id', ParseIntPipe) id: number, @Body() dto: UpdateMenuDto) {
    return this.menusService.update(id, dto)
  }

  @Delete(':id')
  @HttpCode(204)
  @RequirePermissions('menu:delete')
  @Log('菜单管理', '删除')
  remove(@Param('id', ParseIntPipe) id: number) {
    return this.menusService.remove(id)
  }
}
