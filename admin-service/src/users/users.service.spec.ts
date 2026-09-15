import { Test, TestingModule } from '@nestjs/testing'
import {
  ConflictException,
  NotFoundException,
  BadRequestException,
  ForbiddenException,
} from '@nestjs/common'
import * as bcrypt from 'bcryptjs'
import { UsersService } from './users.service'
import { PrismaService } from '../prisma/prisma.service'
import { PermissionsService } from '../common/permissions/permissions.service'
import { SUPER_ADMIN_ADVISORY_LOCK } from '../common/permissions/super-admin-lock'

const now = new Date()
const baseUser = {
  id: 1,
  username: 'admin',
  password: 'hashed',
  email: null,
  nickname: null,
  avatar: null,
  status: 1,
  createdAt: now,
  updatedAt: now,
}

const mockExecuteRaw = jest.fn().mockResolvedValue(1)

const mockPrisma = {
  user: {
    findUnique: jest.fn(),
    findMany: jest.fn(),
    count: jest.fn(),
    create: jest.fn(),
    update: jest.fn(),
    delete: jest.fn(),
  },
  userRole: {
    deleteMany: jest.fn(),
    createMany: jest.fn(),
    findMany: jest.fn(),
  },
  refreshToken: {
    deleteMany: jest.fn(),
  },
  role: {
    findMany: jest.fn(),
  },
  $executeRaw: mockExecuteRaw,
  $transaction: jest.fn(),
}

const mockPermissions = {
  isSuperAdmin: jest.fn().mockResolvedValue(true),
  userHasSuperAdmin: jest.fn().mockResolvedValue(false),
  countActiveSuperAdmins: jest.fn().mockResolvedValue(2),
  getPermissionCodes: jest.fn().mockResolvedValue(['user:list']),
  getPermissionCodesForRoleIds: jest.fn().mockResolvedValue(['user:list']),
}

describe('UsersService', () => {
  let service: UsersService

  beforeEach(async () => {
    mockPrisma.$transaction.mockImplementation(async (arg: unknown) => {
      if (typeof arg === 'function') {
        return (arg as (tx: typeof mockPrisma) => unknown)(mockPrisma)
      }
      return Promise.all(arg as Promise<unknown>[])
    })

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        UsersService,
        { provide: PrismaService, useValue: mockPrisma },
        { provide: PermissionsService, useValue: mockPermissions },
      ],
    }).compile()

    service = module.get(UsersService)
    jest.clearAllMocks()
    mockExecuteRaw.mockResolvedValue(1)
    mockPrisma.$transaction.mockImplementation(async (arg: unknown) => {
      if (typeof arg === 'function') {
        return (arg as (tx: typeof mockPrisma) => unknown)(mockPrisma)
      }
      return Promise.all(arg as Promise<unknown>[])
    })
    mockPermissions.isSuperAdmin.mockResolvedValue(true)
    mockPermissions.userHasSuperAdmin.mockResolvedValue(false)
    mockPermissions.countActiveSuperAdmins.mockResolvedValue(2)
    mockPermissions.getPermissionCodes.mockResolvedValue(['user:list'])
    mockPermissions.getPermissionCodesForRoleIds.mockResolvedValue(['user:list'])
  })

  describe('findAll', () => {
    it('returns paginated result without passwords', async () => {
      mockPrisma.user.findMany.mockResolvedValue([{ ...baseUser, userRoles: [] }])
      mockPrisma.user.count.mockResolvedValue(1)

      const result = await service.findAll({ page: 1, pageSize: 10 })

      expect(result.total).toBe(1)
      expect(result.data[0]).not.toHaveProperty('password')
    })
  })

  describe('findOne', () => {
    it('throws NotFoundException for missing user', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(null)
      await expect(service.findOne(99)).rejects.toThrow(NotFoundException)
    })

    it('returns user without password', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(baseUser)
      const result = await service.findOne(1)
      expect(result.id).toBe(1)
      expect(result).not.toHaveProperty('password')
    })
  })

  describe('create', () => {
    it('throws ConflictException for duplicate username', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(baseUser)
      await expect(service.create({ username: 'admin', password: 'pass' })).rejects.toThrow(ConflictException)
    })

    it('hashes password before saving', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(null)
      mockPrisma.user.create.mockResolvedValue(baseUser)

      await service.create({ username: 'newuser', password: 'plain-text' })

      const createCall = mockPrisma.user.create.mock.calls[0][0]
      expect(createCall.data.password).not.toBe('plain-text')
      const isHashed = await bcrypt.compare('plain-text', createCall.data.password as string)
      expect(isHashed).toBe(true)
    })
  })

  describe('update', () => {
    it('throws NotFoundException when user does not exist', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(null)
      await expect(service.update(99, { nickname: 'X' }, 1)).rejects.toThrow(NotFoundException)
    })

    it('re-hashes password when updating it', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(baseUser)
      mockPrisma.user.update.mockResolvedValue(baseUser)
      mockPrisma.refreshToken.deleteMany.mockResolvedValue({ count: 1 })

      await service.update(1, { password: 'new-pass' }, 2)

      const updateCall = mockPrisma.user.update.mock.calls[0][0]
      expect(updateCall.data.password).not.toBe('new-pass')
      expect(mockPrisma.refreshToken.deleteMany).toHaveBeenCalledWith({ where: { userId: 1 } })
    })

    it('rejects disabling self', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(baseUser)
      await expect(service.update(1, { status: 0 }, 1)).rejects.toThrow(BadRequestException)
    })

    it('forbids non-super from updating a super user', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(baseUser)
      mockPermissions.userHasSuperAdmin.mockResolvedValue(true)
      mockPermissions.isSuperAdmin.mockResolvedValue(false)

      await expect(service.update(1, { nickname: 'X' }, 2)).rejects.toThrow(ForbiddenException)
      expect(mockPrisma.user.update).not.toHaveBeenCalled()
    })

    it('forbids non-super from disabling a super user', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(baseUser)
      mockPermissions.userHasSuperAdmin.mockResolvedValue(true)
      mockPermissions.isSuperAdmin.mockResolvedValue(false)

      await expect(service.update(1, { status: 0 }, 2)).rejects.toThrow(ForbiddenException)
    })

    it('rejects disabling the last active super inside the advisory lock', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(baseUser)
      mockPermissions.userHasSuperAdmin.mockResolvedValue(true)
      mockPermissions.isSuperAdmin.mockResolvedValue(true)
      mockPermissions.countActiveSuperAdmins.mockResolvedValue(1)

      await expect(service.update(1, { status: 0 }, 2)).rejects.toThrow(BadRequestException)
      expect(mockExecuteRaw).toHaveBeenCalled()
      expect(String(mockExecuteRaw.mock.calls[0][0])).toContain('pg_advisory_xact_lock')
      expect(mockExecuteRaw.mock.calls[0][1]).toBe(SUPER_ADMIN_ADVISORY_LOCK)
      expect(mockPrisma.user.update).not.toHaveBeenCalled()
    })
  })

  describe('remove', () => {
    it('throws NotFoundException when user does not exist', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(null)
      await expect(service.remove(99, 1)).rejects.toThrow(NotFoundException)
    })

    it('rejects deleting self', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(baseUser)
      await expect(service.remove(1, 1)).rejects.toThrow(BadRequestException)
    })

    it('deletes user when found', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(baseUser)
      mockPrisma.user.delete.mockResolvedValue(baseUser)

      await service.remove(1, 2)

      expect(mockPrisma.user.delete).toHaveBeenCalledWith({ where: { id: 1 } })
    })

    it('forbids non-super from deleting a super user', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(baseUser)
      mockPermissions.userHasSuperAdmin.mockResolvedValue(true)
      mockPermissions.isSuperAdmin.mockResolvedValue(false)

      await expect(service.remove(1, 2)).rejects.toThrow(ForbiddenException)
      expect(mockPrisma.user.delete).not.toHaveBeenCalled()
    })
  })

  describe('assignRoles', () => {
    it('rejects inactive roles', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(baseUser)
      mockPrisma.role.findMany.mockResolvedValue([
        { id: 2, code: 'operator', status: 0 },
      ])

      await expect(service.assignRoles(1, [2], 3)).rejects.toThrow(BadRequestException)
    })

    it('forbids non-super from assigning roles beyond their permissions', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(baseUser)
      mockPrisma.role.findMany.mockResolvedValue([
        { id: 2, code: 'operator', status: 1 },
      ])
      mockPermissions.isSuperAdmin.mockResolvedValue(false)
      mockPermissions.userHasSuperAdmin.mockResolvedValue(false)
      mockPermissions.getPermissionCodes.mockResolvedValue(['user:list'])
      mockPermissions.getPermissionCodesForRoleIds.mockResolvedValue(['user:list', 'user:create'])

      await expect(service.assignRoles(1, [2], 3)).rejects.toThrow(ForbiddenException)
    })

    it('forbids non-super from changing a super user roles', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(baseUser)
      mockPrisma.role.findMany.mockResolvedValue([
        { id: 2, code: 'operator', status: 1 },
      ])
      mockPermissions.isSuperAdmin.mockResolvedValue(false)
      mockPermissions.userHasSuperAdmin.mockResolvedValue(true)

      await expect(service.assignRoles(1, [2], 3)).rejects.toThrow(ForbiddenException)
    })

    it('rejects removing the last super role inside the advisory lock', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(baseUser)
      mockPrisma.role.findMany.mockResolvedValue([
        { id: 2, code: 'operator', status: 1 },
      ])
      mockPermissions.isSuperAdmin.mockResolvedValue(true)
      mockPermissions.userHasSuperAdmin.mockResolvedValue(true)
      mockPermissions.countActiveSuperAdmins.mockResolvedValue(1)
      mockPrisma.userRole.findMany.mockResolvedValue([])

      await expect(service.assignRoles(1, [2], 2)).rejects.toThrow(BadRequestException)
      expect(String(mockExecuteRaw.mock.calls[0][0])).toContain('pg_advisory_xact_lock')
    })
  })
})
