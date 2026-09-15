import { Test, TestingModule } from '@nestjs/testing'
import { BadRequestException, ForbiddenException, NotFoundException } from '@nestjs/common'
import { RolesService } from './roles.service'
import { PrismaService } from '../prisma/prisma.service'
import { PermissionsService } from '../common/permissions/permissions.service'

const superRole = {
  id: 1,
  name: '超级管理员',
  code: 'super_admin',
  description: '拥有全部权限',
  status: 1,
}

const operatorRole = {
  id: 2,
  name: '操作员',
  code: 'operator',
  description: null,
  status: 1,
}

const mockPrisma = {
  role: {
    findUnique: jest.fn(),
    findMany: jest.fn(),
    count: jest.fn(),
    create: jest.fn(),
    update: jest.fn(),
    delete: jest.fn(),
  },
  menu: {
    findMany: jest.fn(),
  },
  roleMenu: {
    deleteMany: jest.fn(),
    createMany: jest.fn(),
    findMany: jest.fn(),
  },
  userRole: {
    findMany: jest.fn(),
  },
  refreshToken: {
    deleteMany: jest.fn(),
  },
  $transaction: jest.fn().mockResolvedValue([undefined, undefined]),
}

const mockPermissions = {
  isSuperAdmin: jest.fn().mockResolvedValue(true),
  getPermissionCodes: jest.fn().mockResolvedValue(['role:list']),
}

describe('RolesService', () => {
  let service: RolesService

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        RolesService,
        { provide: PrismaService, useValue: mockPrisma },
        { provide: PermissionsService, useValue: mockPermissions },
      ],
    }).compile()

    service = module.get(RolesService)
    jest.clearAllMocks()
    mockPermissions.isSuperAdmin.mockResolvedValue(true)
    mockPermissions.getPermissionCodes.mockResolvedValue(['role:list'])
  })

  it('forbids non-super from updating the super role', async () => {
    mockPrisma.role.findUnique.mockResolvedValue(superRole)
    mockPermissions.isSuperAdmin.mockResolvedValue(false)

    await expect(service.update(1, { name: '被改名' }, 9)).rejects.toThrow(ForbiddenException)
    expect(mockPrisma.role.update).not.toHaveBeenCalled()
  })

  it('still rejects disabling the super role even for a super actor', async () => {
    mockPrisma.role.findUnique.mockResolvedValue(superRole)
    mockPermissions.isSuperAdmin.mockResolvedValue(true)

    await expect(service.update(1, { status: 0 }, 1)).rejects.toThrow(BadRequestException)
  })

  it('rejects deleting the super role', async () => {
    mockPrisma.role.findUnique.mockResolvedValue(superRole)
    await expect(service.remove(1, 1)).rejects.toThrow(BadRequestException)
  })

  it('forbids non-super from replacing super role menus', async () => {
    mockPrisma.role.findUnique.mockResolvedValue(superRole)
    mockPermissions.isSuperAdmin.mockResolvedValue(false)

    await expect(service.assignMenus(1, [10], 9)).rejects.toThrow(ForbiddenException)
    expect(mockPrisma.menu.findMany).not.toHaveBeenCalled()
  })

  it('throws NotFoundException for missing role', async () => {
    mockPrisma.role.findUnique.mockResolvedValue(null)
    await expect(service.update(99, { name: 'x' }, 1)).rejects.toThrow(NotFoundException)
  })

  it('allows non-super to update a normal role', async () => {
    mockPrisma.role.findUnique.mockResolvedValue(operatorRole)
    mockPermissions.isSuperAdmin.mockResolvedValue(false)
    mockPrisma.role.update.mockResolvedValue({ ...operatorRole, name: '操作' })

    await service.update(2, { name: '操作' }, 9)

    expect(mockPrisma.role.update).toHaveBeenCalled()
  })
})
