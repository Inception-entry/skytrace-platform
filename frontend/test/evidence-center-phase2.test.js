import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const evidenceSource = readFileSync(
  join(root, 'src/api/evidence.ts'),
  'utf8',
)
const evidenceViewSource = readFileSync(
  join(root, 'src/views/EvidenceView.vue'),
  'utf8',
)
const zhSource = readFileSync(join(root, 'src/locales/zh.js'), 'utf8')
const enSource = readFileSync(join(root, 'src/locales/en.js'), 'utf8')

test('evidence phase2 client targets tags metadata and batch paths', () => {
  assert.match(evidenceSource, /\/api\/evidence\/tags/)
  assert.match(
    evidenceSource,
    /\/api\/evidence\/\$\{encodeURIComponent\(evidenceCode\)\}\/metadata/,
  )
  assert.match(evidenceSource, /\/api\/evidence\/batch\/review/)
  assert.match(evidenceSource, /\/api\/evidence\/batch\/tags/)
  assert.match(evidenceSource, /export function listEvidenceTags/)
  assert.match(evidenceSource, /export function updateEvidenceMetadata/)
  assert.match(evidenceSource, /export function batchReviewEvidence/)
  assert.match(evidenceSource, /export function batchTagEvidence/)
  assert.match(evidenceSource, /reviewStatus\?:/)
})

test('evidence phase2 view wires review filter selection and metadata save', () => {
  assert.match(evidenceViewSource, /filters\.reviewStatus/)
  assert.match(evidenceViewSource, /listEvidenceTags/)
  assert.match(evidenceViewSource, /batchReviewEvidence/)
  assert.match(evidenceViewSource, /batchTagEvidence/)
  assert.match(evidenceViewSource, /updateEvidenceMetadata/)
  assert.match(evidenceViewSource, /selectedCodes/)
  assert.match(evidenceViewSource, /thumbnailUrl/)
  assert.match(evidenceViewSource, /posterUrl/)
  assert.match(evidenceViewSource, /saveMetadata/)
})

test('evidence phase2 locales include review and batch keys', () => {
  for (const source of [zhSource, enSource]) {
    assert.match(source, /reviewStatus:/)
    assert.match(source, /allReviewStatuses:/)
    assert.match(source, /batchApprove:/)
    assert.match(source, /batchReject:/)
    assert.match(source, /batchTag:/)
    assert.match(source, /saveMetadata:/)
    assert.match(source, /PENDING:/)
    assert.match(source, /APPROVED:/)
    assert.match(source, /REJECTED:/)
  }
})
