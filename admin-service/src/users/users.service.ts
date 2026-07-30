import {
  Injectable,
  ConflictException,
  NotFoundException,
} from '@nestjs/common'
import * as bcrypt from 'bcryptjs'
import { PrismaService } from '../prisma/prisma.service'
import { CreateUserDto } from './dto/create-user.dto'
import { UpdateUserDto } from './dto/update-user.dto'
import { QueryUserDto } from './dto/query-user.dto'
import type { User } from '@prisma/client'

type SafeUser = Omit<User, 'password'>

function omitPassword(user: User): SafeUser {
  const { password: _p, ...rest } = user
  return rest
}

@Injectable()
export class UsersService {
  constructor(private prisma: PrismaService) {}

  async findAll(query: QueryUserDto) {
    const { page = 1, pageSize = 10, username } = query
    const skip = (page - 1) * pageSize
    const where = username ? { username: { contains: username } } : {}
    const [data, total] = await Promise.all([
      this.prisma.user.findMany({
        where,
        skip,
        take: pageSize,
        orderBy: { createdAt: 'desc' },
      }),
      this.prisma.user.count({ where }),
    ])
    return { data: data.map(omitPassword), total, page, pageSize }
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

  async update(id: number, dto: UpdateUserDto): Promise<SafeUser> {
    await this.findOne(id)
    const data: Partial<User> = { ...dto }
    if (dto.password) {
      data.password = await bcrypt.hash(dto.password, 10)
    }
    const user = await this.prisma.user.update({ where: { id }, data })
    return omitPassword(user)
  }

  async remove(id: number): Promise<void> {
    await this.findOne(id)
    await this.prisma.user.delete({ where: { id } })
  }

  async assignRoles(userId: number, roleIds: number[]) {
    await this.findOne(userId)
    await this.prisma.$transaction([
      this.prisma.userRole.deleteMany({ where: { userId } }),
      this.prisma.userRole.createMany({
        data: roleIds.map(roleId => ({ userId, roleId })),
        skipDuplicates: true,
      }),
    ])
    const userRoles = await this.prisma.userRole.findMany({
      where: { userId },
      include: { role: { select: { id: true, name: true, code: true } } },
    })
    return userRoles.map(ur => ur.role)
  }
}
