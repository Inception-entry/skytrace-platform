import { Injectable, OnModuleInit, BadRequestException } from '@nestjs/common'
import { ConfigService } from '@nestjs/config'
import { randomUUID } from 'crypto'
import * as Minio from 'minio'
import {
  avatarObjectName,
  inspectAvatarBuffer,
  isBucketAlreadyExists,
} from './avatar-bytes'

@Injectable()
export class UploadService implements OnModuleInit {
  private readonly client: Minio.Client
  private readonly bucket: string
  private bucketReady: Promise<void> | null = null

  constructor(private readonly config: ConfigService) {
    this.bucket = config.get('MINIO_ADMIN_BUCKET', 'admin-avatars')
    this.client = new Minio.Client({
      endPoint: config.get('MINIO_ENDPOINT', 'minio'),
      port: parseInt(config.get('MINIO_PORT', '9000')),
      useSSL: config.get('MINIO_USE_SSL', 'false') === 'true',
      accessKey: config.get('MINIO_ACCESS_KEY', ''),
      secretKey: config.get('MINIO_SECRET_KEY', ''),
    })
  }

  async onModuleInit() {
    try {
      await this.ensureBucket()
    } catch {
      // MinIO may not be ready at startup — first upload retries via ensureBucket
    }
  }

  async uploadAvatar(file: Express.Multer.File, userId: number): Promise<string> {
    if (!file) throw new BadRequestException('未上传文件')

    const inspected = inspectAvatarBuffer(file.buffer)
    await this.ensureBucket()

    const objectName = avatarObjectName(userId, inspected.ext, randomUUID())
    await this.client.putObject(this.bucket, objectName, file.buffer, file.buffer.length, {
      'Content-Type': inspected.contentType,
      'Content-Disposition': `inline; filename="avatar${inspected.ext}"`,
      'X-Content-Type-Options': 'nosniff',
    })

    // Served via nginx /files/ → minio:9000
    return `/files/${this.bucket}/${objectName}`
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
    await this.client.setBucketPolicy(
      this.bucket,
      JSON.stringify({
        Version: '2012-10-17',
        Statement: [
          {
            Effect: 'Allow',
            Principal: '*',
            Action: ['s3:GetObject'],
            Resource: [`arn:aws:s3:::${this.bucket}/*`],
          },
        ],
      }),
    )
  }
}
