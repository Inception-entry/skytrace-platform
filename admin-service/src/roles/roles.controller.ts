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
import { RolesService } from './roles.service'
import { CreateRoleDto } from './dto/create-role.dto'
import { UpdateRoleDto } from './dto/update-role.dto'
import { QueryRoleDto } from './dto/query-role.dto'
import { AssignMenusDto } from './dto/assign-menus.dto'

@UseGuards(JwtAuthGuard)
@Controller('roles')
export class RolesController {
  constructor(private rolesService: RolesService) {}

  @Get()
  findAll(@Query() query: QueryRoleDto) {
    return this.rolesService.findAll(query)
  }

  @Get(':id')
  findOne(@Param('id', ParseIntPipe) id: number) {
    return this.rolesService.findOneWithMenus(id)
  }

  @Post()
  create(@Body() dto: CreateRoleDto) {
    return this.rolesService.create(dto)
  }

  @Put(':id')
  update(@Param('id', ParseIntPipe) id: number, @Body() dto: UpdateRoleDto) {
    return this.rolesService.update(id, dto)
  }

  @Delete(':id')
  @HttpCode(204)
  remove(@Param('id', ParseIntPipe) id: number) {
    return this.rolesService.remove(id)
  }

  @Get(':id/menus')
  getMenuIds(@Param('id', ParseIntPipe) id: number) {
    return this.rolesService.getMenuIds(id)
  }

  @Put(':id/menus')
  assignMenus(@Param('id', ParseIntPipe) id: number, @Body() dto: AssignMenusDto) {
    return this.rolesService.assignMenus(id, dto.menuIds)
  }
}
