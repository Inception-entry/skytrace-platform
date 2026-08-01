import { Injectable } from '@nestjs/common'
import { PrismaService } from '../prisma/prisma.service'
import { QueryLogDto } from './dto/query-log.dto'

interface LogRecord {
  userId: number
  username: string
  module: string
  action: string
  method: string
  path: string
  params: string
  ip: string
  status: number
  duration: number
}

@Injectable()
export class LogsService {
  constructor(private readonly prisma: PrismaService) {}

  async record(data: LogRecord) {
    try {
      await this.prisma.operationLog.create({ data })
    } catch {
      // never block the main request
    }
  }

  async findAll(query: QueryLogDto) {
    const { page = 1, pageSize = 20, username, module, action, startTime, endTime } = query
    const skip = (+page - 1) * +pageSize
    const where = {
      ...(username && { username: { contains: username } }),
      ...(module && { module }),
      ...(action && { action }),
      ...((startTime || endTime) && {
        createdAt: {
          ...(startTime && { gte: new Date(startTime) }),
          ...(endTime && { lte: new Date(endTime) }),
        },
      }),
    }
    const [data, total] = await Promise.all([
      this.prisma.operationLog.findMany({
        where,
        skip,
        take: +pageSize,
        orderBy: { createdAt: 'desc' },
      }),
      this.prisma.operationLog.count({ where }),
    ])
    return { data, total, page: +page, pageSize: +pageSize }
  }

  async clear() {
    const { count } = await this.prisma.operationLog.deleteMany({})
    return { count }
  }
}
