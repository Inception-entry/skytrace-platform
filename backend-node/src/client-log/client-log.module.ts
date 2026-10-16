import { Module } from '@nestjs/common';
import { ClientLogController } from './client-log.controller';
import { ClientLogService } from './client-log.service';

@Module({
  controllers: [ClientLogController],
  providers: [ClientLogService],
})
export class ClientLogModule {}
