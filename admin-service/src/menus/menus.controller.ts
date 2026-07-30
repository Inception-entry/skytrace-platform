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
import { MenusService } from './menus.service'
import { CreateMenuDto } from './dto/create-menu.dto'
import { UpdateMenuDto } from './dto/update-menu.dto'
import { Log } from '../common/decorators/log.decorator'

@UseGuards(JwtAuthGuard)
@Controller('menus')
export class MenusController {
  constructor(private menusService: MenusService) {}

  @Get()
  findAll() {
    return this.menusService.findAll()
  }

  @Get('flat')
  findFlat() {
    return this.menusService.findFlat()
  }

  @Get(':id')
  findOne(@Param('id', ParseIntPipe) id: number) {
    return this.menusService.findOne(id)
  }

  @Post()
  @Log('菜单管理', '创建')
  create(@Body() dto: CreateMenuDto) {
    return this.menusService.create(dto)
  }

  @Put(':id')
  @Log('菜单管理', '更新')
  update(@Param('id', ParseIntPipe) id: number, @Body() dto: UpdateMenuDto) {
    return this.menusService.update(id, dto)
  }

  @Delete(':id')
  @HttpCode(204)
  @Log('菜单管理', '删除')
  remove(@Param('id', ParseIntPipe) id: number) {
    return this.menusService.remove(id)
  }
}
