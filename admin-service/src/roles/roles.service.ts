import { Injectable, NotFoundException, ConflictException } from '@nestjs/common'
import { PrismaService } from '../prisma/prisma.service'
import { CreateRoleDto } from './dto/create-role.dto'
import { UpdateRoleDto } from './dto/update-role.dto'
import { QueryRoleDto } from './dto/query-role.dto'

@Injectable()
export class RolesService {
  constructor(private prisma: PrismaService) {}

  async findAll(query: QueryRoleDto) {
    const { page = 1, pageSize = 10, name } = query
    const skip = (page - 1) * pageSize
    const where = name ? { name: { contains: name } } : {}
    const [data, total] = await Promise.all([
      this.prisma.role.findMany({ where, skip, take: pageSize, orderBy: { id: 'asc' } }),
      this.prisma.role.count({ where }),
    ])
    return { data, total, page, pageSize }
  }

  async findOne(id: number) {
    const role = await this.prisma.role.findUnique({ where: { id } })
    if (!role) throw new NotFoundException(`角色 ${id} 不存在`)
    return role
  }

  async findOneWithMenus(id: number) {
    const role = await this.prisma.role.findUnique({
      where: { id },
      include: { roleMenus: { select: { menuId: true } } },
    })
    if (!role) throw new NotFoundException(`角色 ${id} 不存在`)
    const { roleMenus, ...rest } = role
    return { ...rest, menuIds: roleMenus.map(rm => rm.menuId) }
  }

  async create(dto: CreateRoleDto) {
    const existing = await this.prisma.role.findUnique({ where: { code: dto.code } })
    if (existing) throw new ConflictException('角色编码已存在')
    return this.prisma.role.create({ data: dto })
  }

  async update(id: number, dto: UpdateRoleDto) {
    await this.findOne(id)
    return this.prisma.role.update({ where: { id }, data: dto })
  }

  async remove(id: number) {
    await this.findOne(id)
    await this.prisma.role.delete({ where: { id } })
  }

  async assignMenus(roleId: number, menuIds: number[]) {
    await this.findOne(roleId)
    await this.prisma.$transaction([
      this.prisma.roleMenu.deleteMany({ where: { roleId } }),
      this.prisma.roleMenu.createMany({
        data: menuIds.map(menuId => ({ roleId, menuId })),
        skipDuplicates: true,
      }),
    ])
    return this.findOneWithMenus(roleId)
  }

  async getMenuIds(roleId: number) {
    await this.findOne(roleId)
    const roleMenus = await this.prisma.roleMenu.findMany({
      where: { roleId },
      select: { menuId: true },
    })
    return roleMenus.map(rm => rm.menuId)
  }
}
