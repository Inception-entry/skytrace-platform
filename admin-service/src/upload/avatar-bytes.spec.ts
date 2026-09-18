import { BadRequestException } from '@nestjs/common'
import {
  AVATAR_MAX_BYTES,
  avatarObjectName,
  inspectAvatarBuffer,
  isBucketAlreadyExists,
} from './avatar-bytes'

const jpeg = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10])
const png = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00])
const gif = Buffer.from('GIF89a....')
const webp = Buffer.concat([Buffer.from('RIFF'), Buffer.alloc(4), Buffer.from('WEBP')])

describe('inspectAvatarBuffer', () => {
  it('detects jpeg/png/gif/webp from magic bytes and ignores claimed names', () => {
    expect(inspectAvatarBuffer(jpeg)).toEqual({ contentType: 'image/jpeg', ext: '.jpg' })
    expect(inspectAvatarBuffer(png)).toEqual({ contentType: 'image/png', ext: '.png' })
    expect(inspectAvatarBuffer(gif)).toEqual({ contentType: 'image/gif', ext: '.gif' })
    expect(inspectAvatarBuffer(webp)).toEqual({ contentType: 'image/webp', ext: '.webp' })
  })

  it('rejects empty, oversized, and non-image payloads including HTML', () => {
    expect(() => inspectAvatarBuffer(undefined)).toThrow(BadRequestException)
    expect(() => inspectAvatarBuffer(Buffer.alloc(0))).toThrow(/文件为空/)
    expect(() => inspectAvatarBuffer(Buffer.alloc(AVATAR_MAX_BYTES + 1, 0xff))).toThrow(/文件超过 2MB/)
    expect(() => inspectAvatarBuffer(Buffer.from('<!DOCTYPE html>'))).toThrow(/仅支持/)
    expect(() => inspectAvatarBuffer(Buffer.from('<?xml version="1.0"?><svg></svg>'))).toThrow(/仅支持/)
  })
})

describe('avatarObjectName', () => {
  it('uses the user id and canonical extension, not the original filename', () => {
    expect(avatarObjectName(7, '.png', 'abc-uuid')).toBe('7/abc-uuid.png')
    expect(() => avatarObjectName(0, '.jpg', 'x')).toThrow(/无效的用户/)
    expect(() => avatarObjectName(1.5, '.jpg', 'x')).toThrow(/无效的用户/)
  })
})

describe('isBucketAlreadyExists', () => {
  it('recognizes MinIO already-exists codes', () => {
    expect(isBucketAlreadyExists({ code: 'BucketAlreadyOwnedByYou' })).toBe(true)
    expect(isBucketAlreadyExists({ code: 'BucketAlreadyExists' })).toBe(true)
    expect(isBucketAlreadyExists({ code: 'AccessDenied' })).toBe(false)
    expect(isBucketAlreadyExists('nope')).toBe(false)
  })
})
