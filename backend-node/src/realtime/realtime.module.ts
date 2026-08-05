import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { AlarmRealtimeGateway } from './alarm-realtime.gateway';
import { AlarmRealtimeConsumer } from '../messaging/alarm-realtime.consumer';

@Module({
  imports: [AuthModule],
  providers: [AlarmRealtimeGateway, AlarmRealtimeConsumer],
  exports: [AlarmRealtimeGateway],
})
export class RealtimeModule {}
