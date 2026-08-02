import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const alarmSource = readFileSync(
  join(root, 'src/api/alarm-evidence.ts'),
  'utf8',
)
const deviceSource = readFileSync(
  join(root, 'src/api/device.ts'),
  'utf8',
)

test('frontend alarm/evidence clients target critical API paths', () => {
  assert.match(alarmSource, /\/api\/alarms\/latest/)
  assert.match(alarmSource, /\/api\/alarms\/detections/)
  assert.match(alarmSource, /\/api\/alarms\/analyze/)
  assert.match(alarmSource, /\/api\/evidence/)
  assert.match(
    alarmSource,
    /\/api\/inspection-tasks\/\$\{encodeURIComponent\(taskCode\)\}\/workflow-status/,
  )
})

test('frontend device client targets device API paths', () => {
  assert.match(deviceSource, /\/api\/devices/)
  assert.match(
    deviceSource,
    /\/api\/devices\/\$\{encodeURIComponent\(deviceCode\)\}/,
  )
  assert.match(
    deviceSource,
    /\/api\/devices\/\$\{encodeURIComponent\(deviceCode\)\}\/heartbeat/,
  )
})

test('frontend evidence client supports list query', () => {
  assert.match(alarmSource, /\/api\/evidence\?\$\{query\.toString\(\)\}/)
  assert.match(alarmSource, /export function getEvidence/)
})

test('frontend vision clients target analyze endpoints', () => {
  assert.match(alarmSource, /\/api\/alarms\/analyze/)
  assert.match(alarmSource, /\/api\/alarms\/analyze-video/)
})
