import {
  BadRequestException,
  Body,
  Controller,
  Get,
  Param,
  Post,
} from '@nestjs/common';
import { Roles } from '../auth/http-auth.decorators';
import { JavaClientService } from '../shared/java-client.service';

@Controller('devices')
export class DeviceController {
  constructor(private readonly javaClient: JavaClientService) {}

  @Get()
  list() {
    return this.javaClient.get('/devices');
  }

  @Post(':deviceCode/heartbeat')
  @Roles('ADMIN', 'OPERATOR')
  heartbeat(@Param('deviceCode') deviceCode: string) {
    if (!deviceCode?.trim()) {
      throw new BadRequestException('deviceCode 不能为空');
    }
    return this.javaClient.post(
      `/devices/${encodeURIComponent(deviceCode)}/heartbeat`,
      {},
    );
  }
}
