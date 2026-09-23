import { PrismaClient } from '@prisma/client'
import { redact, serializeRedacted } from '../src/common/utils/redact'

async function main() {
  const apply = process.argv.includes('--apply')
  const prisma = new PrismaClient()
  let scanned = 0
  let changed = 0
  try {
    const rows = await prisma.operationLog.findMany({
      select: { id: true, params: true },
    })
    for (const row of rows) {
      scanned += 1
      if (!row.params) continue
      let parsed: unknown
      try {
        parsed = JSON.parse(row.params)
      } catch {
        continue
      }
      const redacted = JSON.stringify(redact(parsed))
      if (redacted === JSON.stringify(parsed)) continue
      const next = serializeRedacted(parsed)
      if (next === row.params) continue
      changed += 1
      if (apply) {
        await prisma.operationLog.update({
          where: { id: row.id },
          data: { params: next },
        })
      }
    }
  } finally {
    await prisma.$disconnect()
  }
  console.log(`${apply ? '已改写' : '将改写'} ${changed} / ${scanned} 条操作日志。未加 --apply 时只统计。`)
}

main().catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : error)
  process.exit(1)
})
