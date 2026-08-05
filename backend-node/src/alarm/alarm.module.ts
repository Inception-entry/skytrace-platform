import { Module } from '@nestjs/common';
import { JavaClientModule } from '../common/java-client/java-client.module';
import { RealtimeModule } from '../realtime/realtime.module';
import { AlarmController } from './alarm.controller';

@Module({
  imports: [JavaClientModule, RealtimeModule],
  controllers: [AlarmController],
})
export class AlarmModule {}
