import { Module } from '@nestjs/common';
import { JavaClientModule } from '../common/java-client/java-client.module';
import { RouteController } from './route.controller';

@Module({
  imports: [JavaClientModule],
  controllers: [RouteController],
})
export class RouteModule {}
