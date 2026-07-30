import { Injectable, NotFoundException, ConflictException } from '@nestjs/common'
import { PrismaService } from '../prisma/prisma.service'
import { buildMenuTree } from '../common/utils/menu-tree'
import { CreateMenuDto } from './dto/create-menu.dto'
import { UpdateMenuDto } from './dto/update-menu.dto'

@Injectable()
export class MenusService {
  constructor(private prisma: PrismaService) {}

  async findAll() {
    const menus = await this.prisma.menu.findMany({ orderBy: { sort: 'asc' } })
    return buildMenuTree(menus)
  }

  async findFlat() {
    return this.prisma.menu.findMany({ orderBy: [{ parentId: 'asc' }, { sort: 'asc' }] })
  }

  async findOne(id: number) {
    const menu = await this.prisma.menu.findUnique({ where: { id } })
    if (!menu) throw new NotFoundException(`菜单 ${id} 不存在`)
    return menu
  }

  async create(dto: CreateMenuDto) {
    const existing = await this.prisma.menu.findUnique({ where: { code: dto.code } })
    if (existing) throw new ConflictException('菜单编码已存在')
    if (dto.parentId) await this.findOne(dto.parentId)
    return this.prisma.menu.create({ data: dto })
  }

  async update(id: number, dto: UpdateMenuDto) {
    await this.findOne(id)
    return this.prisma.menu.update({ where: { id }, data: dto })
  }

  async remove(id: number) {
    await this.findOne(id)
    const children = await this.prisma.menu.count({ where: { parentId: id } })
    if (children > 0) throw new ConflictException('请先删除子菜单')
    await this.prisma.menu.delete({ where: { id } })
  }
}
