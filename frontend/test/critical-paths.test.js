import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const source = readFileSync(
  join(root, 'src/api/alarm-evidence.ts'),
  'utf8',
)

test('frontend alarm/evidence clients target critical API paths', () => {
  assert.match(source, /\/api\/alarms\/latest/)
  assert.match(source, /\/api\/alarms\/detections/)
  assert.match(source, /\/api\/alarms\/analyze/)
  assert.match(source, /\/api\/evidence/)
  assert.match(
    source,
    /\/api\/inspection-tasks\/\$\{encodeURIComponent\(taskCode\)\}\/workflow-status/,
  )
})
