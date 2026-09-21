require('reflect-metadata');

const assert = require('node:assert/strict');
const { test } = require('node:test');
const { BadRequestException } = require('@nestjs/common');
const {
  inspectUpload,
  inspectedMultipart,
  blobFromUpload,
} = require('../dist/common/upload-magic.js');

function jpegBytes() {
  return Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0, 16, 0x4a, 0x46, 0x49, 0x46]);
}

function mp4Bytes() {
  return Buffer.from([
    0, 0, 0, 24,
    0x66, 0x74, 0x79, 0x70,
    0x69, 0x73, 0x6f, 0x6d,
  ]);
}

test('jpeg evidence uses detected type not originalname', () => {
  const detected = inspectUpload(jpegBytes(), 'evidence', 'x.php.jpg');
  assert.equal(detected.contentType, 'image/jpeg');
  assert.equal(detected.ext, '.jpg');
});

test('html claiming to be jpeg is rejected', () => {
  assert.throws(
    () => inspectUpload(Buffer.from('<html>hi</html>'), 'evidence', 'a.jpg'),
    (error) => error instanceof BadRequestException,
  );
});

test('pdf knowledge requires %PDF magic', () => {
  const detected = inspectUpload(
    Buffer.from('%PDF-1.4 x'),
    'knowledge',
    'notes.php.pdf',
  );
  assert.equal(detected.contentType, 'application/pdf');
  assert.equal(detected.ext, '.pdf');
});

test('html named as pdf is rejected', () => {
  assert.throws(
    () => inspectUpload(Buffer.from('<!DOCTYPE html>'), 'knowledge', 'doc.pdf'),
    (error) => error instanceof BadRequestException,
  );
});

test('markdown is allowed when it is not html', () => {
  const detected = inspectUpload(Buffer.from('# title\n'), 'knowledge', 'guide.md');
  assert.equal(detected.contentType, 'text/markdown');
  assert.equal(detected.ext, '.md');
});

test('jpeg is rejected as vision video', () => {
  assert.throws(
    () => inspectUpload(jpegBytes(), 'video'),
    (error) => error instanceof BadRequestException,
  );
});

test('mp4 video is detected from ftyp', () => {
  const detected = inspectUpload(mp4Bytes(), 'video', 'clip.bin');
  assert.equal(detected.contentType, 'video/mp4');
  assert.equal(detected.ext, '.mp4');
});

test('knowledge multipart rewrites filename to document.pdf', () => {
  const rewritten = inspectedMultipart(
    {
      buffer: Buffer.from('%PDF-1.7 x'),
      originalname: '../../etc/passwd.pdf',
      mimetype: 'application/octet-stream',
    },
    'knowledge',
    '请选择需要上传的文档',
  );
  assert.equal(rewritten.originalname, 'document.pdf');
  assert.equal(rewritten.mimetype, 'application/pdf');
});

test('empty buffer is rejected', () => {
  assert.throws(
    () => inspectUpload(Buffer.alloc(0), 'image'),
    (error) => error instanceof BadRequestException,
  );
});

test('upload blob wraps the original buffer without Uint8Array copy', async () => {
  const buffer = Buffer.alloc(2048, 7);
  const blob = blobFromUpload({ buffer, mimetype: 'video/mp4' });
  assert.equal(blob.size, buffer.length);
  assert.equal(blob.type, 'video/mp4');
  assert.deepEqual(Buffer.from(await blob.arrayBuffer()), buffer);
});
