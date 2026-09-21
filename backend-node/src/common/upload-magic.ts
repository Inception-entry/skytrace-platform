import { BadRequestException } from '@nestjs/common'

export type UploadKind = 'evidence' | 'knowledge' | 'image' | 'video'

export type DetectedUpload = {
  contentType: string
  ext: string
}

const JPEG = Buffer.from([0xff, 0xd8, 0xff])
const PNG = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])
const RIFF = Buffer.from('RIFF')
const WEBP = Buffer.from('WEBP')
const FTYP = Buffer.from('ftyp')
const WEBM = Buffer.from([0x1a, 0x45, 0xdf, 0xa3])
const PDF = Buffer.from('%PDF-')
const UTF8_BOM = Buffer.from([0xef, 0xbb, 0xbf])

export function inspectUpload(
  buffer: Buffer | undefined,
  kind: UploadKind,
  originalname?: string,
): DetectedUpload {
  if (!buffer || buffer.length === 0) {
    throw new BadRequestException('文件为空')
  }

  switch (kind) {
    case 'evidence':
      return firstMatch(
        detectImage(buffer),
        detectVideo(buffer),
        '仅支持 jpg/png/webp 截图或 mp4/webm 视频',
      )
    case 'image':
      return firstMatch(detectImage(buffer), undefined, '仅支持 jpg/png/webp 图片')
    case 'video':
      return firstMatch(detectVideo(buffer), undefined, '仅支持 mp4/webm 视频')
    case 'knowledge':
      return detectKnowledge(buffer, originalname)
  }
}

export function inspectedMultipart(
  file:
    | {
        buffer: Buffer
        originalname: string
        mimetype: string
      }
    | undefined,
  kind: UploadKind,
  emptyMessage: string,
): {
  buffer: Buffer
  originalname: string
  mimetype: string
} {
  if (!file) {
    throw new BadRequestException(emptyMessage)
  }
  const detected = inspectUpload(file.buffer, kind, file.originalname)
  return {
    buffer: file.buffer,
    originalname:
      kind === 'knowledge' ? `document${detected.ext}` : file.originalname,
    mimetype: detected.contentType,
  }
}

function firstMatch(
  primary: DetectedUpload | undefined,
  secondary: DetectedUpload | undefined,
  message: string,
): DetectedUpload {
  if (primary) return primary
  if (secondary) return secondary
  throw new BadRequestException(message)
}

function detectImage(buffer: Buffer): DetectedUpload | undefined {
  const offset = skipBom(buffer)
  if (startsWith(buffer, offset, JPEG)) {
    return { contentType: 'image/jpeg', ext: '.jpg' }
  }
  if (startsWith(buffer, offset, PNG)) {
    return { contentType: 'image/png', ext: '.png' }
  }
  if (
    startsWith(buffer, offset, RIFF)
    && startsWith(buffer, offset + 8, WEBP)
  ) {
    return { contentType: 'image/webp', ext: '.webp' }
  }
  return undefined
}

function detectVideo(buffer: Buffer): DetectedUpload | undefined {
  const offset = skipBom(buffer)
  if (startsWith(buffer, offset + 4, FTYP)) {
    return { contentType: 'video/mp4', ext: '.mp4' }
  }
  if (startsWith(buffer, offset, WEBM)) {
    return { contentType: 'video/webm', ext: '.webm' }
  }
  return undefined
}

function detectKnowledge(
  buffer: Buffer,
  originalname?: string,
): DetectedUpload {
  const offset = skipBom(buffer)
  if (startsWith(buffer, offset, PDF)) {
    return { contentType: 'application/pdf', ext: '.pdf' }
  }
  if (looksLikeHtml(buffer, offset) || containsNul(buffer)) {
    throw new BadRequestException('仅支持 PDF、Markdown 和 TXT 文档')
  }
  const extension = extensionOf(originalname)
  if (extension === '.md' || extension === '.markdown') {
    return { contentType: 'text/markdown', ext: extension }
  }
  if (extension === '.txt') {
    return { contentType: 'text/plain', ext: '.txt' }
  }
  throw new BadRequestException('仅支持 PDF、Markdown 和 TXT 文档')
}

function extensionOf(filename?: string): string {
  if (!filename) return ''
  const name = filename.replaceAll('\\', '/').split('/').pop() ?? ''
  const dot = name.lastIndexOf('.')
  if (dot < 0 || dot === name.length - 1) return ''
  return name.slice(dot).toLowerCase()
}

function looksLikeHtml(buffer: Buffer, offset: number): boolean {
  let index = offset
  while (index < buffer.length && buffer[index] <= 0x20) {
    index += 1
  }
  const prefix = buffer.subarray(index, index + 32).toString('ascii').toLowerCase()
  return (
    prefix.startsWith('<!doctype html')
    || prefix.startsWith('<html')
    || prefix.startsWith('<svg')
    || prefix.startsWith('<script')
  )
}

function containsNul(buffer: Buffer): boolean {
  const limit = Math.min(buffer.length, 512)
  return buffer.subarray(0, limit).includes(0)
}

function skipBom(buffer: Buffer): number {
  return startsWith(buffer, 0, UTF8_BOM) ? 3 : 0
}

function startsWith(buffer: Buffer, offset: number, prefix: Buffer): boolean {
  if (offset < 0 || buffer.length - offset < prefix.length) {
    return false
  }
  return buffer.subarray(offset, offset + prefix.length).equals(prefix)
}
