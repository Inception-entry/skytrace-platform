import { BadRequestException } from '@nestjs/common'
import { randomUUID } from 'node:crypto'
import { closeSync, createReadStream, mkdirSync, openSync, readSync, statSync } from 'node:fs'
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
  AVATAR_MAX_BYTES,
  inspectAvatarMagic,
  type InspectedAvatar,
} from './avatar-bytes'

const INVALID_UPLOAD = '无效的上传文件'
const AVATAR_FILENAME_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const AVATAR_SNIFF_BYTES = 16

export type InspectedAvatarUpload = InspectedAvatar & {
  path: string
  size: number
}

export function avatarTempDir(): string {
  const configured = process.env.AVATAR_TEMP_DIR?.trim()
  return configured || join(tmpdir(), 'skytrace-admin-avatars')
}

export function resolvedAvatarPath(candidate: string): string {
  const name = basename(candidate)
  if (!AVATAR_FILENAME_RE.test(name)) {
    throw new BadRequestException(INVALID_UPLOAD)
  }
  const root = resolve(avatarTempDir())
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

export function diskAvatarOptions() {
  return {
    storage: diskStorage({
      destination: (_req, _file, callback) => {
        const dir = avatarTempDir()
        mkdirSync(dir, { recursive: true })
        callback(null, dir)
      },
      filename: (_req, _file, callback) => {
        callback(null, randomUUID())
      },
    }),
    limits: { fileSize: AVATAR_MAX_BYTES },
  }
}

export function sniffAvatarHeader(filePath: string): Buffer {
  const safePath = resolvedAvatarPath(filePath)
  const fd = openSync(safePath, 'r')
  try {
    const header = Buffer.alloc(AVATAR_SNIFF_BYTES)
    const bytesRead = readSync(fd, header, 0, AVATAR_SNIFF_BYTES, 0)
    return header.subarray(0, bytesRead)
  } finally {
    closeSync(fd)
  }
}

export function inspectAvatarDiskFile(
  filePath: string,
  sizeHint?: number,
): InspectedAvatarUpload {
  const safePath = resolvedAvatarPath(filePath)
  const size = sizeHint ?? statSync(safePath).size
  if (size === 0) {
    throw new BadRequestException('文件为空')
  }
  if (size > AVATAR_MAX_BYTES) {
    throw new BadRequestException('文件超过 2MB')
  }
  const inspected = inspectAvatarMagic(sniffAvatarHeader(safePath))
  return { ...inspected, path: safePath, size }
}

export function avatarReadStream(filePath: string) {
  return createReadStream(resolvedAvatarPath(filePath))
}

export async function unlinkAvatar(filePath: string | undefined): Promise<void> {
  if (!filePath) {
    return
  }
  try {
    await unlink(resolvedAvatarPath(filePath))
  } catch {
    // 请求结束时文件可能已经不在
  }
}

export async function withAvatarDiskUpload<T>(
  file: { path?: string; size?: number } | undefined,
  send: (part: InspectedAvatarUpload) => Promise<T>,
): Promise<T> {
  if (!file?.path) {
    throw new BadRequestException('未上传文件')
  }
  try {
    return await send(inspectAvatarDiskFile(file.path, file.size))
  } finally {
    await unlinkAvatar(file.path)
  }
}
