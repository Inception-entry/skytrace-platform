import { Test, TestingModule } from '@nestjs/testing'
import { ConflictException, NotFoundException } from '@nestjs/common'
import * as bcrypt from 'bcryptjs'
import { UsersService } from './users.service'
import { PrismaService } from '../prisma/prisma.service'

const now = new Date()
const baseUser = { id: 1, username: 'admin', password: 'hashed', email: null, nickname: null, status: 1, createdAt: now, updatedAt: now }

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
  $transaction: jest.fn().mockResolvedValue([undefined, undefined]),
}

describe('UsersService', () => {
  let service: UsersService

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        UsersService,
        { provide: PrismaService, useValue: mockPrisma },
      ],
    }).compile()

    service = module.get(UsersService)
    jest.clearAllMocks()
  })

  describe('findAll', () => {
    it('returns paginated result without passwords', async () => {
      mockPrisma.user.findMany.mockResolvedValue([baseUser])
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
      await expect(service.update(99, { nickname: 'X' })).rejects.toThrow(NotFoundException)
    })

    it('re-hashes password when updating it', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(baseUser)
      mockPrisma.user.update.mockResolvedValue(baseUser)

      await service.update(1, { password: 'new-pass' })

      const updateCall = mockPrisma.user.update.mock.calls[0][0]
      expect(updateCall.data.password).not.toBe('new-pass')
    })
  })

  describe('remove', () => {
    it('throws NotFoundException when user does not exist', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(null)
      await expect(service.remove(99)).rejects.toThrow(NotFoundException)
    })

    it('deletes user when found', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(baseUser)
      mockPrisma.user.delete.mockResolvedValue(baseUser)

      await service.remove(1)

      expect(mockPrisma.user.delete).toHaveBeenCalledWith({ where: { id: 1 } })
    })
  })
})
