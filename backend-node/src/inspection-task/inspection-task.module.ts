import { Module } from '@nestjs/common';
import { JavaClientModule } from '../common/java-client/java-client.module';
import { InspectionTaskController } from './inspection-task.controller';

@Module({
  imports: [JavaClientModule],
  controllers: [InspectionTaskController],
})
export class InspectionTaskModule {}
