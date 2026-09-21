import { BadRequestException } from '@nestjs/common'
import { mkdirSync, rmSync, writeFileSync, existsSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { randomUUID } from 'node:crypto'
import {
  inspectAvatarDiskFile,
  resolvedAvatarPath,
  withAvatarDiskUpload,
} from './avatar-disk'

const jpeg = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10])

describe('avatar-disk', () => {
  const tempRoot = join(tmpdir(), `skytrace-admin-avatars-disk-${randomUUID()}`)

  beforeAll(() => {
    process.env.AVATAR_TEMP_DIR = tempRoot
    mkdirSync(tempRoot, { recursive: true })
  })

  afterAll(() => {
    rmSync(tempRoot, { recursive: true, force: true })
    delete process.env.AVATAR_TEMP_DIR
  })

  function writeNamed(name: string, body: Buffer) {
    const path = join(tempRoot, name)
    writeFileSync(path, body)
    return path
  }

  it('sniffs jpeg from disk and rejects HTML', () => {
    const jpegPath = writeNamed(randomUUID(), jpeg)
    expect(inspectAvatarDiskFile(jpegPath, jpeg.length)).toMatchObject({
      contentType: 'image/jpeg',
      ext: '.jpg',
      size: jpeg.length,
    })

    const htmlPath = writeNamed(randomUUID(), Buffer.from('<!DOCTYPE html>'))
    expect(() => inspectAvatarDiskFile(htmlPath, 15)).toThrow(/仅支持/)
  })

  it('rejects paths outside the temp dir or non-uuid names', () => {
    expect(() => resolvedAvatarPath('/etc/passwd')).toThrow(BadRequestException)
    expect(() => resolvedAvatarPath(join(tempRoot, 'not-a-uuid.jpg'))).toThrow(BadRequestException)
  })

  it('unlinks the temp file after success and failure', async () => {
    const okPath = writeNamed(randomUUID(), jpeg)
    await withAvatarDiskUpload({ path: okPath, size: jpeg.length }, async (part) => {
      expect(part.contentType).toBe('image/jpeg')
      return 'ok'
    })
    expect(existsSync(okPath)).toBe(false)

    const badPath = writeNamed(randomUUID(), Buffer.from('<html>'))
    await expect(
      withAvatarDiskUpload({ path: badPath, size: 6 }, async () => 'nope'),
    ).rejects.toThrow(/仅支持/)
    expect(existsSync(badPath)).toBe(false)
  })
})
