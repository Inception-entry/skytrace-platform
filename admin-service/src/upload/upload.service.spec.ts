import { ConfigService } from '@nestjs/config'
import { Test } from '@nestjs/testing'
import { UploadService } from './upload.service'

const jpeg = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10])

const mockClient = {
  bucketExists: jest.fn(),
  makeBucket: jest.fn(),
  setBucketPolicy: jest.fn(),
  putObject: jest.fn(),
}

jest.mock('minio', () => ({
  Client: jest.fn(() => mockClient),
}))

describe('UploadService', () => {
  let service: UploadService

  beforeEach(async () => {
    jest.clearAllMocks()
    mockClient.bucketExists.mockResolvedValue(true)
    mockClient.makeBucket.mockResolvedValue(undefined)
    mockClient.setBucketPolicy.mockResolvedValue(undefined)
    mockClient.putObject.mockResolvedValue(undefined)

    const module = await Test.createTestingModule({
      providers: [
        UploadService,
        {
          provide: ConfigService,
          useValue: {
            get: (key: string, fallback?: string) => {
              const values: Record<string, string> = {
                MINIO_ADMIN_BUCKET: 'admin-avatars',
                MINIO_ENDPOINT: 'minio',
                MINIO_PORT: '9000',
                MINIO_USE_SSL: 'false',
                MINIO_ACCESS_KEY: 'key',
                MINIO_SECRET_KEY: 'secret',
              }
              return values[key] ?? fallback
            },
          },
        },
      ],
    }).compile()

    service = module.get(UploadService)
  })

  it('stores jpeg magic as image/jpeg with a canonical .jpg object name', async () => {
    const url = await service.uploadAvatar(
      {
        buffer: jpeg,
        originalname: 'payload.php.jpg',
        mimetype: 'text/html',
        size: jpeg.length,
      } as Express.Multer.File,
      9,
    )

    expect(url).toMatch(/^\/files\/admin-avatars\/9\/[0-9a-f-]+\.jpg$/)
    expect(mockClient.putObject).toHaveBeenCalledWith(
      'admin-avatars',
      expect.stringMatching(/^9\/[0-9a-f-]+\.jpg$/),
      jpeg,
      jpeg.length,
      expect.objectContaining({
        'Content-Type': 'image/jpeg',
        'X-Content-Type-Options': 'nosniff',
      }),
    )
  })

  it('rejects HTML even when the client claims image/jpeg', async () => {
    await expect(
      service.uploadAvatar(
        {
          buffer: Buffer.from('<!DOCTYPE html><script>alert(1)</script>'),
          originalname: 'avatar.jpg',
          mimetype: 'image/jpeg',
          size: 40,
        } as Express.Multer.File,
        1,
      ),
    ).rejects.toThrow(/仅支持/)
    expect(mockClient.putObject).not.toHaveBeenCalled()
  })

  it('shares bucket initialization across concurrent first uploads', async () => {
    mockClient.bucketExists.mockResolvedValue(false)
    await Promise.all([
      service.uploadAvatar({ buffer: jpeg } as Express.Multer.File, 1),
      service.uploadAvatar({ buffer: jpeg } as Express.Multer.File, 1),
    ])
    expect(mockClient.makeBucket).toHaveBeenCalledTimes(1)
  })
})
