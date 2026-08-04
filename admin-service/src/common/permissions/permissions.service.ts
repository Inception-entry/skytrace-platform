import { Injectable } from '@nestjs/common'
import { PrismaService } from '../../prisma/prisma.service'

export const SUPER_ADMIN_ROLE_CODE = 'super_admin'

@Injectable()
export class PermissionsService {
  constructor(private readonly prisma: PrismaService) {}

  async getRoleCodes(userId: number): Promise<string[]> {
    const rows = await this.prisma.userRole.findMany({
      where: { userId, role: { status: 1 } },
      select: { role: { select: { code: true } } },
    })
    return rows.map(r => r.role.code)
  }

  async isSuperAdmin(userId: number): Promise<boolean> {
    const roles = await this.getRoleCodes(userId)
    return roles.includes(SUPER_ADMIN_ROLE_CODE)
  }

  async getPermissionCodes(userId: number): Promise<string[]> {
    const rows = await this.prisma.userRole.findMany({
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

  async countActiveSuperAdmins(): Promise<number> {
    return this.prisma.user.count({
      where: {
        status: 1,
        userRoles: {
          some: { role: { code: SUPER_ADMIN_ROLE_CODE, status: 1 } },
        },
      },
    })
  }

  async userHasSuperAdmin(userId: number): Promise<boolean> {
    return this.isSuperAdmin(userId)
  }
}
