import { Module } from '@nestjs/common';
import { JavaClientModule } from '../common/java-client/java-client.module';
import { AdminController } from './admin.controller';

/**
 * BFF admin/audit proxy. Maps to Java `audit` package; public path stays `/api/admin`.
 */
@Module({
  imports: [JavaClientModule],
  controllers: [AdminController],
})
export class AdminModule {}
