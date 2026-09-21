require('reflect-metadata');

const assert = require('node:assert/strict');
const { randomUUID } = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { test } = require('node:test');
const { BadRequestException } = require('@nestjs/common');
const {
  inspectUpload,
  UPLOAD_SNIFF_BYTES,
} = require('../dist/common/upload-magic.js');
const {
  inspectedDiskUpload,
  resolvedUploadPath,
  sniffFileHeader,
  uploadTempDir,
  withDiskUpload,
} = require('../dist/common/upload-disk.js');

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

function writeTemp(bytes) {
  const dir = uploadTempDir();
  fs.mkdirSync(dir, { recursive: true });
  const filePath = path.join(dir, randomUUID());
  fs.writeFileSync(filePath, bytes);
  return filePath;
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

test('knowledge disk upload rewrites filename to document.pdf', () => {
  const filePath = writeTemp(Buffer.from('%PDF-1.7 x'));
  try {
    const rewritten = inspectedDiskUpload(
      {
        path: filePath,
        originalname: '../../etc/passwd.pdf',
        size: fs.statSync(filePath).size,
      },
      'knowledge',
      '请选择需要上传的文档',
    );
    assert.equal(rewritten.originalname, 'document.pdf');
    assert.equal(rewritten.mimetype, 'application/pdf');
    assert.equal(rewritten.path, path.resolve(filePath));
  } finally {
    fs.unlinkSync(filePath);
  }
});

test('empty buffer is rejected', () => {
  assert.throws(
    () => inspectUpload(Buffer.alloc(0), 'image'),
    (error) => error instanceof BadRequestException,
  );
});

test('disk sniff only reads the first 512 bytes', () => {
  const bytes = Buffer.concat([jpegBytes(), Buffer.alloc(8000, 1)]);
  const filePath = writeTemp(bytes);
  try {
    const header = sniffFileHeader(filePath);
    assert.equal(header.length, UPLOAD_SNIFF_BYTES);
    const part = inspectedDiskUpload(
      {
        path: filePath,
        originalname: 'shot.jpg',
        size: bytes.length,
      },
      'evidence',
      '请选择需要上传的证据文件',
    );
    assert.equal(part.mimetype, 'image/jpeg');
    assert.equal(part.size, bytes.length);
    assert.equal(fs.existsSync(filePath), true);
  } finally {
    fs.unlinkSync(filePath);
  }
});

test('empty disk file is rejected', () => {
  const filePath = writeTemp(Buffer.alloc(0));
  try {
    assert.throws(
      () => inspectedDiskUpload(
        { path: filePath, originalname: 'a.jpg', size: 0 },
        'image',
        '请选择需要识别的图片',
      ),
      (error) => error instanceof BadRequestException,
    );
  } finally {
    fs.unlinkSync(filePath);
  }
});

test('withDiskUpload deletes the temp file after success', async () => {
  const filePath = writeTemp(jpegBytes());
  const result = await withDiskUpload(
    {
      path: filePath,
      originalname: 'a.jpg',
      size: fs.statSync(filePath).size,
    },
    'image',
    '请选择需要识别的图片',
    async (part) => {
      assert.equal(part.mimetype, 'image/jpeg');
      assert.equal(fs.existsSync(filePath), true);
      return 'ok';
    },
  );
  assert.equal(result, 'ok');
  assert.equal(fs.existsSync(filePath), false);
});

test('withDiskUpload deletes the temp file after failure', async () => {
  const filePath = writeTemp(jpegBytes());
  await assert.rejects(
    () => withDiskUpload(
      {
        path: filePath,
        originalname: 'a.jpg',
        size: fs.statSync(filePath).size,
      },
      'image',
      '请选择需要识别的图片',
      async () => {
        throw new Error('upstream');
      },
    ),
  );
  assert.equal(fs.existsSync(filePath), false);
});

test('rejects non-uuid upload filenames', () => {
  assert.throws(
    () => resolvedUploadPath(path.join(uploadTempDir(), 'passwd')),
    (error) => error instanceof BadRequestException,
  );
});

test('rejects path traversal in upload paths', () => {
  assert.throws(
    () => resolvedUploadPath(path.join(uploadTempDir(), '..', 'passwd')),
    (error) => error instanceof BadRequestException,
  );
});

test('ignores caller directory and stays in the upload temp dir', () => {
  const name = randomUUID();
  const safePath = resolvedUploadPath(path.join('/etc', name));
  assert.equal(safePath, path.resolve(uploadTempDir(), name));
});
