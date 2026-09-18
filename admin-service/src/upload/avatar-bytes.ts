import { BadRequestException } from '@nestjs/common'

export const AVATAR_MAX_BYTES = 2 * 1024 * 1024

export type AvatarContentType = 'image/jpeg' | 'image/png' | 'image/gif' | 'image/webp'
export type AvatarExt = '.jpg' | '.png' | '.gif' | '.webp'

export type InspectedAvatar = {
  contentType: AvatarContentType
  ext: AvatarExt
}

const PNG = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])
const GIF87A = Buffer.from('GIF87a')
const GIF89A = Buffer.from('GIF89a')
const RIFF = Buffer.from('RIFF')
const WEBP = Buffer.from('WEBP')

export function inspectAvatarBuffer(buffer: Buffer | undefined): InspectedAvatar {
  if (!buffer || buffer.length === 0) {
    throw new BadRequestException('文件为空')
  }
  if (buffer.length > AVATAR_MAX_BYTES) {
    throw new BadRequestException('文件超过 2MB')
  }

  if (buffer.length >= 3 && buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff) {
    return { contentType: 'image/jpeg', ext: '.jpg' }
  }
  if (buffer.length >= PNG.length && buffer.subarray(0, PNG.length).equals(PNG)) {
    return { contentType: 'image/png', ext: '.png' }
  }
  if (
    buffer.length >= 6
    && (buffer.subarray(0, 6).equals(GIF87A) || buffer.subarray(0, 6).equals(GIF89A))
  ) {
    return { contentType: 'image/gif', ext: '.gif' }
  }
  if (
    buffer.length >= 12
    && buffer.subarray(0, 4).equals(RIFF)
    && buffer.subarray(8, 12).equals(WEBP)
  ) {
    return { contentType: 'image/webp', ext: '.webp' }
  }

  throw new BadRequestException('仅支持 jpg/png/gif/webp 格式')
}

export function avatarObjectName(userId: number, ext: AvatarExt, id: string): string {
  if (!Number.isInteger(userId) || userId <= 0) {
    throw new BadRequestException('无效的用户')
  }
  return `${userId}/${id}${ext}`
}

export function isBucketAlreadyExists(error: unknown): boolean {
  if (!error || typeof error !== 'object' || !('code' in error)) return false
  const code = String((error as { code: unknown }).code)
  return code === 'BucketAlreadyOwnedByYou' || code === 'BucketAlreadyExists'
}
