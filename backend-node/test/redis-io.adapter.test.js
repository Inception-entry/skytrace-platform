import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const adapterSource = readFileSync(
  join(root, 'src/realtime/redis-io.adapter.ts'),
  'utf8',
)
const mainSource = readFileSync(join(root, 'src/main.ts'), 'utf8')
const packageJson = JSON.parse(
  readFileSync(join(root, 'package.json'), 'utf8'),
)

test('node depends on socket.io redis adapter and redis client', () => {
  assert.ok(packageJson.dependencies['@socket.io/redis-adapter'])
  assert.ok(packageJson.dependencies.redis)
})

test('main boots RedisIoAdapter before listen', () => {
  assert.match(mainSource, /RedisIoAdapter/)
  assert.match(mainSource, /connectToRedis/)
  assert.match(mainSource, /useWebSocketAdapter/)
})

test('redis adapter respects SOCKETIO_REDIS_ADAPTER and falls back', () => {
  assert.match(adapterSource, /SOCKETIO_REDIS_ADAPTER/)
  assert.match(adapterSource, /createAdapter/)
  assert.match(adapterSource, /fallback to memory/)
})
