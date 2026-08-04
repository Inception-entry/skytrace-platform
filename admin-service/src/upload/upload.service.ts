import { Injectable, OnModuleInit, BadRequestException } from '@nestjs/common'
import { ConfigService } from '@nestjs/config'
import { randomUUID } from 'crypto'
import * as path from 'path'
import * as Minio from 'minio'

@Injectable()
export class UploadService implements OnModuleInit {
  private readonly client: Minio.Client
  private readonly bucket: string

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
      const exists = await this.client.bucketExists(this.bucket)
      if (!exists) {
        await this.client.makeBucket(this.bucket)
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
    } catch {
      // MinIO may not be ready at startup — bucket is created on first upload
    }
  }

  async uploadAvatar(file: Express.Multer.File, userId: number): Promise<string> {
    if (!file) throw new BadRequestException('未上传文件')

    const allowed = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
    if (!allowed.includes(file.mimetype)) {
      throw new BadRequestException('仅支持 jpg/png/gif/webp 格式')
    }

    await this.ensureBucket()

    const ext = path.extname(file.originalname) || '.jpg'
    const objectName = `${userId}/${randomUUID()}${ext}`

    await this.client.putObject(this.bucket, objectName, file.buffer, file.size, {
      'Content-Type': file.mimetype,
    })

    // Served via nginx /files/ → minio:9000
    return `/files/${this.bucket}/${objectName}`
  }

  private async ensureBucket() {
    const exists = await this.client.bucketExists(this.bucket)
    if (!exists) {
      await this.client.makeBucket(this.bucket)
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
}
