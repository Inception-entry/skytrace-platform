const assert = require('node:assert/strict')
const { Readable } = require('node:stream')
const { pipeline } = require('node:stream/promises')
const test = require('node:test')

// Run outside Jest so these checks load the real packages rather than the
// MinIO mock used by authentication unit tests. No object store is required.
test('MinIO can initialize with the installed production dependencies', () => {
  const { Client } = require('minio')
  const client = new Client({
    endPoint: 'localhost',
    port: 9000,
    useSSL: false,
    accessKey: 'runtime-check',
    secretKey: 'runtime-check-secret',
  })

  assert.ok(client instanceof Client)
})

test('query-string decodes object keys through the ESM decoder bridge', () => {
  const queryString = require('query-string')
  const parsed = queryString.parse(
    'key=%E8%AF%81%E6%8D%AE%2Fframe%201.jpg&tag=fire&tag=smoke',
  )

  assert.deepEqual({ ...parsed }, {
    key: '证据/frame 1.jpg',
    tag: ['fire', 'smoke'],
  })
})

test('the legacy JSONL parser handles records split across UTF-8 bytes', async () => {
  const { make } = require('stream-json/jsonl/Parser.js')
  const expected = [
    { key: '证据/frame 1.jpg', size: 128 },
    { key: '证据/frame 2.jpg', size: 256 },
  ]
  const input = Buffer.from(expected.map(value => JSON.stringify(value)).join('\n') + '\n')
  const chunks = Array.from(input, byte => Buffer.from([byte]))
  const actual = []

  await pipeline(
    Readable.from(chunks, { objectMode: false }),
    make(),
    async source => {
      for await (const record of source) actual.push(record.value)
    },
  )

  assert.deepEqual(actual, expected)
})

test('the legacy JSONL parser reports invalid JSON instead of dropping it', async () => {
  const { make } = require('stream-json/jsonl/Parser.js')

  await assert.rejects(
    pipeline(
      Readable.from(['{invalid json}\n'], { objectMode: false }),
      make(),
      async source => {
        for await (const record of source) void record
      },
    ),
    SyntaxError,
  )
})
