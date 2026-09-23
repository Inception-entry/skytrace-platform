-- AlterTable
ALTER TABLE "sys_refresh_token" ADD COLUMN "family_id" TEXT;

-- CreateIndex
CREATE INDEX "sys_refresh_token_family_id_idx" ON "sys_refresh_token"("family_id");
