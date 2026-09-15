import {
  Injectable,
  ConflictException,
  NotFoundException,
  BadRequestException,
  ForbiddenException,
} from '@nestjs/common'
import * as bcrypt from 'bcryptjs'
import { PrismaService } from '../prisma/prisma.service'
import { PermissionsService, SUPER_ADMIN_ROLE_CODE } from '../common/permissions/permissions.service'
import { withSuperAdminLock } from '../common/permissions/super-admin-lock'
import { CreateUserDto } from './dto/create-user.dto'
import { UpdateUserDto } from './dto/update-user.dto'
import { QueryUserDto } from './dto/query-user.dto'
import { Prisma, type User } from '@prisma/client'

type SafeUser = Omit<User, 'password'>

function omitPassword(user: User): SafeUser {
  const { password: _password, ...rest } = user
  void _password
  return rest
}

@Injectable()
export class UsersService {
  constructor(
    private prisma: PrismaService,
    private permissions: PermissionsService,
  ) {}

  async findAll(query: QueryUserDto) {
    const { page = 1, pageSize = 10, username } = query
    const skip = (page - 1) * pageSize
    const where = username ? { username: { contains: username } } : {}
    const [rows, total] = await Promise.all([
      this.prisma.user.findMany({
        where,
        skip,
        take: pageSize,
        orderBy: { createdAt: 'desc' },
        include: {
          userRoles: { include: { role: { select: { id: true, name: true, code: true } } } },
        },
      }),
      this.prisma.user.count({ where }),
    ])
    return {
      data: rows.map(({ password: _pw, userRoles, ...rest }) => {
        void _pw
        return { ...rest, roles: userRoles.map(ur => ur.role) }
      }),
      total,
      page,
      pageSize,
    }
  }

  async findOne(id: number): Promise<SafeUser> {
    const user = await this.prisma.user.findUnique({ where: { id } })
    if (!user) throw new NotFoundException(`用户 ${id} 不存在`)
    return omitPassword(user)
  }

  async create(dto: CreateUserDto): Promise<SafeUser> {
    const existing = await this.prisma.user.findUnique({ where: { username: dto.username } })
    if (existing) throw new ConflictException('用户名已存在')
    const hashed = await bcrypt.hash(dto.password, 10)
    const user = await this.prisma.user.create({
      data: { ...dto, password: hashed },
    })
    return omitPassword(user)
  }

  async update(id: number, dto: UpdateUserDto, actorId: number): Promise<SafeUser> {
    await this.findOne(id)

    if (dto.status === 0) {
      if (id === actorId) throw new BadRequestException('不能禁用当前登录账号')
    }

    const targetIsSuper = await this.permissions.userHasSuperAdmin(id)
    await this.assertCanMutateSuperUser(actorId, targetIsSuper)

    const data: Partial<User> = { ...dto }
    if (dto.password) {
      data.password = await bcrypt.hash(dto.password, 10)
    }

    if (dto.status === 0 && targetIsSuper) {
      const user = await withSuperAdminLock(this.prisma, async tx => {
        await this.assertKeepsLastActiveSuper(tx, id, '不能禁用或删除最后一个超级管理员')
        const updated = await tx.user.update({ where: { id }, data })
        await tx.refreshToken.deleteMany({ where: { userId: id } })
        return updated
      })
      return omitPassword(user)
    }

    const user = await this.prisma.user.update({ where: { id }, data })
    if (dto.password || dto.status === 0) {
      await this.prisma.refreshToken.deleteMany({ where: { userId: id } })
    }
    return omitPassword(user)
  }

  async remove(id: number, actorId: number): Promise<void> {
    await this.findOne(id)
    if (id === actorId) throw new BadRequestException('不能删除当前登录账号')

    const targetIsSuper = await this.permissions.userHasSuperAdmin(id)
    await this.assertCanMutateSuperUser(actorId, targetIsSuper)

    if (targetIsSuper) {
      await withSuperAdminLock(this.prisma, async tx => {
        await this.assertKeepsLastActiveSuper(tx, id, '不能禁用或删除最后一个超级管理员')
        await tx.user.delete({ where: { id } })
      })
      return
    }

    await this.prisma.user.delete({ where: { id } })
  }

  async assignRoles(userId: number, roleIds: number[], actorId: number) {
    await this.findOne(userId)

    const roles = await this.prisma.role.findMany({ where: { id: { in: roleIds } } })
    if (roles.length !== roleIds.length) {
      throw new BadRequestException('存在无效的角色 ID')
    }
    if (roles.some(role => role.status !== 1)) {
      throw new BadRequestException('不能分配已停用的角色')
    }

    const actorIsSuper = await this.permissions.isSuperAdmin(actorId)
    const targetWasSuper = await this.permissions.userHasSuperAdmin(userId)
    const assignsSuper = roles.some(role => role.code === SUPER_ADMIN_ROLE_CODE)
    const targetWillBeSuper = roles.some(
      role => role.code === SUPER_ADMIN_ROLE_CODE && role.status === 1,
    )

    if ((targetWasSuper || assignsSuper) && !actorIsSuper) {
      throw new ForbiddenException('仅超级管理员可变更超级管理员用户或角色')
    }

    if (!actorIsSuper) {
      const actorCodes = new Set(await this.permissions.getPermissionCodes(actorId))
      const assignedCodes = await this.permissions.getPermissionCodesForRoleIds(roleIds)
      if (assignedCodes.some(code => !actorCodes.has(code))) {
        throw new ForbiddenException('不能分配超出自身权限范围的角色')
      }
    }

    const persist = async (tx: Prisma.TransactionClient | PrismaService) => {
      await tx.userRole.deleteMany({ where: { userId } })
      await tx.userRole.createMany({
        data: roleIds.map(roleId => ({ userId, roleId })),
        skipDuplicates: true,
      })
      await tx.refreshToken.deleteMany({ where: { userId } })
    }

    if (targetWasSuper || assignsSuper) {
      await withSuperAdminLock(this.prisma, async tx => {
        const stillSuper = await this.permissions.userHasSuperAdmin(userId, tx)
        if (stillSuper && !targetWillBeSuper) {
          await this.assertKeepsLastActiveSuper(tx, userId, '不能移除最后一个超级管理员的角色')
        }
        await persist(tx)
      })
    } else {
      await this.prisma.$transaction(async tx => persist(tx))
    }

    const userRoles = await this.prisma.userRole.findMany({
      where: { userId },
      include: { role: { select: { id: true, name: true, code: true } } },
    })
    return userRoles.map(ur => ur.role)
  }

  private async assertCanMutateSuperUser(actorId: number, targetIsSuper: boolean): Promise<void> {
    if (!targetIsSuper) return
    if (await this.permissions.isSuperAdmin(actorId)) return
    throw new ForbiddenException('仅超级管理员可变更超级管理员用户')
  }

  private async assertKeepsLastActiveSuper(
    tx: Prisma.TransactionClient,
    userId: number,
    message: string,
  ): Promise<void> {
    const user = await tx.user.findUnique({ where: { id: userId } })
    if (!user || user.status !== 1) return
    if (!(await this.permissions.userHasSuperAdmin(userId, tx))) return
    const count = await this.permissions.countActiveSuperAdmins(tx)
    if (count <= 1) {
      throw new BadRequestException(message)
    }
  }
}
