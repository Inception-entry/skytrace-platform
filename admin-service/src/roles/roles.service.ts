import {
  Injectable,
  NotFoundException,
  ConflictException,
  BadRequestException,
  ForbiddenException,
} from '@nestjs/common'
import { PrismaService } from '../prisma/prisma.service'
import { PermissionsService, SUPER_ADMIN_ROLE_CODE } from '../common/permissions/permissions.service'
import { CreateRoleDto } from './dto/create-role.dto'
import { UpdateRoleDto } from './dto/update-role.dto'
import { QueryRoleDto } from './dto/query-role.dto'

@Injectable()
export class RolesService {
  constructor(
    private prisma: PrismaService,
    private permissions: PermissionsService,
  ) {}

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
    if (dto.code === SUPER_ADMIN_ROLE_CODE) {
      throw new BadRequestException('不能创建与超级管理员相同编码的角色')
    }
    const existing = await this.prisma.role.findUnique({ where: { code: dto.code } })
    if (existing) throw new ConflictException('角色编码已存在')
    return this.prisma.role.create({ data: dto })
  }

  async update(id: number, dto: UpdateRoleDto) {
    const role = await this.findOne(id)
    if (role.code === SUPER_ADMIN_ROLE_CODE && dto.status === 0) {
      throw new BadRequestException('不能禁用超级管理员角色')
    }
    return this.prisma.role.update({ where: { id }, data: dto })
  }

  async remove(id: number) {
    const role = await this.findOne(id)
    if (role.code === SUPER_ADMIN_ROLE_CODE) {
      throw new BadRequestException('不能删除超级管理员角色')
    }
    await this.prisma.role.delete({ where: { id } })
  }

  async assignMenus(roleId: number, menuIds: number[], actorId: number) {
    await this.findOne(roleId)

    const menus = await this.prisma.menu.findMany({ where: { id: { in: menuIds } } })
    if (menus.length !== menuIds.length) {
      throw new BadRequestException('存在无效的菜单 ID')
    }

    const actorIsSuper = await this.permissions.isSuperAdmin(actorId)
    if (!actorIsSuper) {
      const actorCodes = new Set(await this.permissions.getPermissionCodes(actorId))
      const forbidden = menus.filter(m => !actorCodes.has(m.code))
      if (forbidden.length > 0) {
        throw new ForbiddenException('不能分配超出自身权限范围的菜单')
      }
    }

    await this.prisma.$transaction([
      this.prisma.roleMenu.deleteMany({ where: { roleId } }),
      this.prisma.roleMenu.createMany({
        data: menuIds.map(menuId => ({ roleId, menuId })),
        skipDuplicates: true,
      }),
    ])

    // Privilege changes: force re-login for users holding this role
    const holders = await this.prisma.userRole.findMany({
      where: { roleId },
      select: { userId: true },
    })
    if (holders.length > 0) {
      await this.prisma.refreshToken.deleteMany({
        where: { userId: { in: holders.map(h => h.userId) } },
      })
    }

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
