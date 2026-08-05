import { Module } from '@nestjs/common';
import { JavaClientModule } from '../common/java-client/java-client.module';
import { KnowledgeController } from './knowledge.controller';

@Module({
  imports: [JavaClientModule],
  controllers: [KnowledgeController],
})
export class KnowledgeModule {}
