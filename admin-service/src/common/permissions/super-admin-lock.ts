import { Prisma } from '@prisma/client'
import { PrismaService } from '../../prisma/prisma.service'

/** Admin Service 保护「至少一名启用中的 super」用的事务锁，勿与其他业务共用。 */
export const SUPER_ADMIN_ADVISORY_LOCK = 734921

const SERIALIZATION_RETRIES = 3

export async function withSuperAdminLock<T>(
  prisma: PrismaService,
  fn: (tx: Prisma.TransactionClient) => Promise<T>,
): Promise<T> {
  let lastError: unknown
  for (let attempt = 1; attempt <= SERIALIZATION_RETRIES; attempt++) {
    try {
      return await prisma.$transaction(
        async tx => {
          await tx.$executeRaw`SELECT pg_advisory_xact_lock(${SUPER_ADMIN_ADVISORY_LOCK})`
          return fn(tx)
        },
        { isolationLevel: Prisma.TransactionIsolationLevel.Serializable },
      )
    } catch (error) {
      lastError = error
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === 'P2034' &&
        attempt < SERIALIZATION_RETRIES
      ) {
        continue
      }
      throw error
    }
  }
  throw lastError
}
