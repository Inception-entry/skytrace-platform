import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const repositoryRoot = join(root, '..')
const evidenceApiSource = readFileSync(
  join(root, 'src/api/evidence.ts'),
  'utf8',
)
const evidenceViewSource = readFileSync(
  join(root, 'src/views/EvidenceView.vue'),
  'utf8',
)
const nodeControllerSource = readFileSync(
  join(repositoryRoot, 'backend-node/src/evidence/evidence.controller.ts'),
  'utf8',
)
const nginxSource = readFileSync(join(root, 'nginx.conf'), 'utf8')
const zhSource = readFileSync(join(root, 'src/locales/zh.js'), 'utf8')
const enSource = readFileSync(join(root, 'src/locales/en.js'), 'utf8')

test('phase3 client and BFF expose all archive job paths', () => {
  // Vue API 必须覆盖创建、状态、ZIP 下载和 manifest 下载四个操作。
  assert.match(evidenceApiSource, /createEvidenceArchiveJob/)
  assert.match(evidenceApiSource, /getEvidenceArchiveJob/)
  assert.match(evidenceApiSource, /createEvidenceArchiveDownloadUrl/)
  assert.match(evidenceApiSource, /createEvidenceArchiveManifestUrl/)
  assert.match(evidenceApiSource, /\/api\/evidence\/archive-jobs/)

  // 浏览器经过 NestJS BFF，不能只在 Java Controller 中存在这些路由。
  assert.match(nodeControllerSource, /@Post\('archive-jobs'\)/)
  assert.match(nodeControllerSource, /@Get\('archive-jobs\/:jobCode'\)/)
  assert.match(nodeControllerSource, /archive-jobs\/:jobCode\/download-url/)
  assert.match(nodeControllerSource, /archive-jobs\/:jobCode\/manifest-url/)
  assert.match(nodeControllerSource, /@Roles\('ADMIN', 'OPERATOR'\)/)
})

test('phase3 view creates polls and downloads archive jobs', () => {
  // 页面必须有真实交互函数，而不只是静态归档文案。
  assert.match(evidenceViewSource, /startArchive/)
  assert.match(evidenceViewSource, /refreshArchiveJob/)
  assert.match(evidenceViewSource, /scheduleArchivePolling/)
  assert.match(evidenceViewSource, /window\.setTimeout/)
  assert.match(evidenceViewSource, /downloadArchive\('package'\)/)
  assert.match(evidenceViewSource, /downloadArchive\('manifest'\)/)
  assert.match(evidenceViewSource, /packageContentHash/)
  assert.match(evidenceViewSource, /archiveStatus/)
  // 清理任务已认领或完成后，页面都不能再提供恢复入口。
  assert.match(evidenceViewSource, /\['PURGING', 'PURGED'\]\.includes/)
})

test('phase3 locales cover archive lifecycle in Chinese and English', () => {
  for (const source of [zhSource, enSource]) {
    assert.match(source, /archiveTitle:/)
    assert.match(source, /archivePackageHash:/)
    assert.match(source, /archiveDownloadZip:/)
    assert.match(source, /archiveDownloadManifest:/)
    assert.match(source, /archiveStatuses:/)
    assert.match(source, /PURGING:/)
    assert.match(source, /PURGED:/)
  }
})

test('presigned object paths are proxied to MinIO with original host', () => {
  // SigV4 包含 Host；代理若改成 minio:9000，浏览器签名会失效。
  assert.match(nginxSource, /location \/\$\{MINIO_EVIDENCE_BUCKET\}\//)
  assert.match(nginxSource, /proxy_pass http:\/\/minio:9000;/)
  assert.match(nginxSource, /proxy_set_header Host \$http_host;/)
})
