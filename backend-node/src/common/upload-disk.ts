import { BadRequestException } from '@nestjs/common'
import { randomUUID } from 'node:crypto'
import { closeSync, mkdirSync, openSync, readSync, statSync } from 'node:fs'
import { unlink } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import {
  basename,
  isAbsolute,
  join,
  relative,
  resolve,
  sep,
} from 'node:path'
import { diskStorage } from 'multer'
import {
  inspectUpload,
  UPLOAD_SNIFF_BYTES,
  type DiskUpload,
  type UploadKind,
} from './upload-magic'

const INVALID_UPLOAD = '无效的上传文件'

const UPLOAD_FILENAME_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

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

export function resolvedUploadPath(candidate: string): string {
  const name = basename(candidate)
  if (!UPLOAD_FILENAME_RE.test(name)) {
    throw new BadRequestException(INVALID_UPLOAD)
  }
  const root = resolve(uploadTempDir())
  const resolved = resolve(join(root, name))
  const rel = relative(root, resolved)
  if (
    !resolved.startsWith(root + sep)
    || !rel
    || rel.startsWith('..')
    || isAbsolute(rel)
    || rel.includes(sep)
    || rel !== name
  ) {
    throw new BadRequestException(INVALID_UPLOAD)
  }
  return resolved
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

export function sniffFileHeader(filePath: string): Buffer {
  const safePath = resolvedUploadPath(filePath)
  const fd = openSync(safePath, 'r')
  try {
    const header = Buffer.alloc(UPLOAD_SNIFF_BYTES)
    const bytesRead = readSync(fd, header, 0, UPLOAD_SNIFF_BYTES, 0)
    return header.subarray(0, bytesRead)
  } finally {
    closeSync(fd)
  }
}

export function inspectedDiskUpload(
  file:
    | {
        path?: string
        originalname: string
        size?: number
      }
    | undefined,
  kind: UploadKind,
  emptyMessage: string,
): DiskUpload {
  if (!file?.path) {
    throw new BadRequestException(emptyMessage)
  }
  const safePath = resolvedUploadPath(file.path)
  const size = file.size ?? statSync(safePath).size
  if (size === 0) {
    throw new BadRequestException('文件为空')
  }
  const detected = inspectUpload(
    sniffFileHeader(safePath),
    kind,
    file.originalname,
  )
  return {
    path: safePath,
    originalname:
      kind === 'knowledge' ? `document${detected.ext}` : file.originalname,
    mimetype: detected.contentType,
    size,
  }
}

export async function unlinkUpload(filePath: string | undefined): Promise<void> {
  if (!filePath) {
    return
  }
  try {
    await unlink(resolvedUploadPath(filePath))
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
