import { BadRequestException } from '@nestjs/common'
import { randomUUID } from 'node:crypto'
import { mkdirSync } from 'node:fs'
import { unlink } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { diskStorage } from 'multer'
import {
  inspectedDiskUpload,
  type DiskUpload,
  type UploadKind,
} from './upload-magic'

export type DiskUploadFile = {
  path: string
  originalname: string
  mimetype?: string
  size: number
}

export function uploadTempDir(): string {
  const configured = process.env.UPLOAD_TEMP_DIR?.trim()
  return configured || join(tmpdir(), 'skytrace-uploads')
}

export function diskUploadOptions(fileSize: number) {
  return {
    storage: diskStorage({
      destination: (_req, _file, callback) => {
        const dir = uploadTempDir()
        mkdirSync(dir, { recursive: true })
        callback(null, dir)
      },
      filename: (_req, _file, callback) => {
        callback(null, randomUUID())
      },
    }),
    limits: { fileSize },
  }
}

export async function unlinkUpload(filePath: string | undefined): Promise<void> {
  if (!filePath) {
    return
  }
  try {
    await unlink(filePath)
  } catch {
    // 请求结束时文件可能已经不在
  }
}

export async function withDiskUpload<T>(
  file: DiskUploadFile | undefined,
  kind: UploadKind,
  emptyMessage: string,
  send: (part: DiskUpload) => Promise<T>,
): Promise<T> {
  if (!file?.path) {
    throw new BadRequestException(emptyMessage)
  }
  try {
    return await send(inspectedDiskUpload(file, kind, emptyMessage))
  } finally {
    await unlinkUpload(file.path)
  }
}
