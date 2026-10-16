import { Injectable, OnModuleInit, BadRequestException } from '@nestjs/common'
import { ConfigService } from '@nestjs/config'
import { randomUUID } from 'crypto'
import * as Minio from 'minio'
import {
  avatarObjectName,
  isBucketAlreadyExists,
} from './avatar-bytes'
import { avatarReadStream, type InspectedAvatarUpload } from './avatar-disk'

@Injectable()
export class UploadService implements OnModuleInit {
  private readonly client: Minio.Client
  private readonly presignClient: Minio.Client
  private readonly bucket: string
  private bucketReady: Promise<void> | null = null

  constructor(private readonly config: ConfigService) {
    this.bucket = config.get('MINIO_ADMIN_BUCKET', 'admin-avatars')
    const accessKey = config.get('MINIO_ACCESS_KEY', '')
    const secretKey = config.get('MINIO_SECRET_KEY', '')
    this.client = new Minio.Client({
      endPoint: config.get('MINIO_ENDPOINT', 'minio'),
      port: parseInt(config.get('MINIO_PORT', '9000')),
      useSSL: config.get('MINIO_USE_SSL', 'false') === 'true',
      accessKey,
      secretKey,
    })
    this.presignClient = presignClient(
      config.get('MINIO_PUBLIC_ENDPOINT', ''),
      this.client,
      accessKey,
      secretKey,
    )
  }

  async onModuleInit() {
    try {
      await this.ensureBucket()
    } catch {
      // MinIO may not be ready at startup — first upload retries via ensureBucket
    }
  }

  async uploadAvatar(
    file: InspectedAvatarUpload,
    userId: number,
  ): Promise<{ objectKey: string; url: string }> {
    if (!file) throw new BadRequestException('未上传文件')

    await this.ensureBucket()

    const objectName = avatarObjectName(userId, file.ext, randomUUID())
    const stream = avatarReadStream(file.path)
    try {
      await this.client.putObject(this.bucket, objectName, stream, file.size, {
        'Content-Type': file.contentType,
        'Content-Disposition': `inline; filename="avatar${file.ext}"`,
        'X-Content-Type-Options': 'nosniff',
      })
    } finally {
      stream.destroy()
    }

    return {
      objectKey: objectName,
      url: await this.presign(objectName),
    }
  }

  async displayUrl(stored: string | null): Promise<string | null> {
    const objectKey = objectKeyOf(stored, this.bucket)
    if (!objectKey) {
      return stored
    }
    try {
      return await this.presign(objectKey)
    } catch {
      return null
    }
  }

  private presign(objectName: string): Promise<string> {
    return this.presignClient.presignedGetObject(this.bucket, objectName, 60 * 60)
  }

  private ensureBucket() {
    if (!this.bucketReady) {
      this.bucketReady = this.createBucketIfNeeded().catch((error) => {
        this.bucketReady = null
        throw error
      })
    }
    return this.bucketReady
  }

  private async createBucketIfNeeded() {
    try {
      const exists = await this.client.bucketExists(this.bucket)
      if (!exists) {
        await this.client.makeBucket(this.bucket)
      }
    } catch (error) {
      if (!isBucketAlreadyExists(error)) throw error
    }
    // 新建桶默认私有。不要写入 Statement 为空的策略，MinIO 会直接返回 400。
  }
}

function presignClient(
  publicEndpoint: string,
  internal: Minio.Client,
  accessKey: string,
  secretKey: string,
): Minio.Client {
  if (!publicEndpoint) {
    return internal
  }
  const url = new URL(publicEndpoint)
  const useSSL = url.protocol === 'https:'
  return new Minio.Client({
    endPoint: url.hostname,
    port: url.port ? parseInt(url.port, 10) : (useSSL ? 443 : 80),
    useSSL,
    accessKey,
    secretKey,
  })
}

function objectKeyOf(stored: string | null, bucket: string): string | null {
  if (!stored) return null
  const publicPrefix = `/files/${bucket}/`
  if (stored.startsWith(publicPrefix)) {
    return stored.slice(publicPrefix.length)
  }
  if (/^\d+\/[0-9a-f-]+\.(jpg|png|webp)$/.test(stored)) {
    return stored
  }
  return null
}
