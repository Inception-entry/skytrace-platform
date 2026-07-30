import { Injectable } from '@nestjs/common'
import { PrismaService } from '../prisma/prisma.service'

@Injectable()
export class DashboardService {
  constructor(private readonly prisma: PrismaService) {}

  async getStats() {
    const since = new Date()
    since.setDate(since.getDate() - 6)
    since.setHours(0, 0, 0, 0)

    const [userCount, roleCount, menuCount, sessionCount, loginLogs] = await Promise.all([
      this.prisma.user.count({ where: { status: 1 } }),
      this.prisma.role.count({ where: { status: 1 } }),
      this.prisma.menu.count(),
      this.prisma.refreshToken.count({ where: { expiresAt: { gt: new Date() } } }),
      this.prisma.operationLog.findMany({
        where: { action: '登录', createdAt: { gte: since } },
        select: { createdAt: true },
      }),
    ])

    // Build 7-day trend
    const trend: Record<string, number> = {}
    for (let i = 0; i < 7; i++) {
      const d = new Date(since)
      d.setDate(d.getDate() + i)
      trend[d.toISOString().slice(0, 10)] = 0
    }
    for (const log of loginLogs) {
      const day = log.createdAt.toISOString().slice(0, 10)
      if (day in trend) trend[day]++
    }

    return {
      userCount,
      roleCount,
      menuCount,
      sessionCount,
      loginTrend: Object.entries(trend).map(([date, count]) => ({ date, count })),
    }
  }
}
