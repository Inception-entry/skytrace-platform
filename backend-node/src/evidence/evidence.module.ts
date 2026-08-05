import { Module } from '@nestjs/common';
import { JavaClientModule } from '../common/java-client/java-client.module';
import { EvidenceController } from './evidence.controller';

@Module({
  imports: [JavaClientModule],
  controllers: [EvidenceController],
})
export class EvidenceModule {}
