import { ConfigService } from '@nestjs/config'
import { Test } from '@nestjs/testing'
import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { randomUUID } from 'node:crypto'
import { Readable } from 'node:stream'
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
  const tempRoot = join(tmpdir(), `skytrace-admin-avatars-test-${randomUUID()}`)

  beforeAll(() => {
    process.env.AVATAR_TEMP_DIR = tempRoot
    mkdirSync(tempRoot, { recursive: true })
  })

  afterAll(() => {
    rmSync(tempRoot, { recursive: true, force: true })
    delete process.env.AVATAR_TEMP_DIR
  })

  beforeEach(async () => {
    jest.clearAllMocks()
    mockClient.bucketExists.mockResolvedValue(true)
    mockClient.makeBucket.mockResolvedValue(undefined)
    mockClient.setBucketPolicy.mockResolvedValue(undefined)
    mockClient.putObject.mockImplementation(async (_bucket, _name, stream) => {
      await new Promise((resolve, reject) => {
        stream.on('error', reject)
        stream.on('end', resolve)
        stream.on('close', resolve)
        if (typeof stream.resume === 'function') {
          stream.resume()
        }
      })
    })

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

  function jpegOnDisk() {
    const path = join(tempRoot, randomUUID())
    writeFileSync(path, jpeg)
    return {
      path,
      size: jpeg.length,
      contentType: 'image/jpeg' as const,
      ext: '.jpg' as const,
    }
  }

  it('stores jpeg magic as image/jpeg with a canonical .jpg object name', async () => {
    const url = await service.uploadAvatar(jpegOnDisk(), 9)

    expect(url).toMatch(/^\/files\/admin-avatars\/9\/[0-9a-f-]+\.jpg$/)
    expect(mockClient.putObject).toHaveBeenCalledWith(
      'admin-avatars',
      expect.stringMatching(/^9\/[0-9a-f-]+\.jpg$/),
      expect.any(Readable),
      jpeg.length,
      expect.objectContaining({
        'Content-Type': 'image/jpeg',
        'X-Content-Type-Options': 'nosniff',
      }),
    )
  })

  it('shares bucket initialization across concurrent first uploads', async () => {
    mockClient.bucketExists.mockResolvedValue(false)
    await Promise.all([
      service.uploadAvatar(jpegOnDisk(), 1),
      service.uploadAvatar(jpegOnDisk(), 1),
    ])
    expect(mockClient.makeBucket).toHaveBeenCalledTimes(1)
  })
})
