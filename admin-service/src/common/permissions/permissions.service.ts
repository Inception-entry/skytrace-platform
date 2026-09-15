import { Injectable } from '@nestjs/common'
import { Prisma } from '@prisma/client'
import { PrismaService } from '../../prisma/prisma.service'

export const SUPER_ADMIN_ROLE_CODE = 'super_admin'

type DbClient = PrismaService | Prisma.TransactionClient

@Injectable()
export class PermissionsService {
  constructor(private readonly prisma: PrismaService) {}

  async getRoleCodes(userId: number, db: DbClient = this.prisma): Promise<string[]> {
    const rows = await db.userRole.findMany({
      where: { userId, role: { status: 1 } },
      select: { role: { select: { code: true } } },
    })
    return rows.map(r => r.role.code)
  }

  async isSuperAdmin(userId: number, db: DbClient = this.prisma): Promise<boolean> {
    const roles = await this.getRoleCodes(userId, db)
    return roles.includes(SUPER_ADMIN_ROLE_CODE)
  }

  async getPermissionCodes(userId: number, db: DbClient = this.prisma): Promise<string[]> {
    const rows = await db.userRole.findMany({
      where: { userId, role: { status: 1 } },
      include: {
        role: {
          include: {
            roleMenus: { include: { menu: true } },
          },
        },
      },
    })

    const codes = new Set<string>()
    for (const ur of rows) {
      for (const rm of ur.role.roleMenus) {
        codes.add(rm.menu.code)
      }
    }
    return [...codes]
  }

  async getPermissionCodesForRoleIds(roleIds: number[], db: DbClient = this.prisma): Promise<string[]> {
    if (roleIds.length === 0) {
      return []
    }
    const rows = await db.roleMenu.findMany({
      where: { roleId: { in: roleIds } },
      select: { menu: { select: { code: true } } },
    })
    return [...new Set(rows.map(row => row.menu.code))]
  }

  async countActiveSuperAdmins(db: DbClient = this.prisma): Promise<number> {
    return db.user.count({
      where: {
        status: 1,
        userRoles: {
          some: { role: { code: SUPER_ADMIN_ROLE_CODE, status: 1 } },
        },
      },
    })
  }

  async userHasSuperAdmin(userId: number, db: DbClient = this.prisma): Promise<boolean> {
    return this.isSuperAdmin(userId, db)
  }
}
