import { Module } from '@nestjs/common';
import { JavaClientModule } from '../common/java-client/java-client.module';
import { DeviceController } from './device.controller';

@Module({
  imports: [JavaClientModule],
  controllers: [DeviceController],
})
export class DeviceModule {}
