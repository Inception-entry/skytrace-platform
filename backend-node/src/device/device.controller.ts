import {
  BadRequestException,
  Body,
  Controller,
  Get,
  Param,
  Post,
  Put,
  Delete,
} from '@nestjs/common';
import { Roles } from '../auth/http-auth.decorators';
import { JavaClientService } from '../common/java-client/java-client.service';

@Controller('devices')
export class DeviceController {
  constructor(private readonly javaClient: JavaClientService) {}

  @Get()

  list() {
    return this.javaClient.get('/devices');
  }

  @Get(':deviceCode')
  detail(@Param('deviceCode') deviceCode: string) {
    this.requireDeviceCode(deviceCode);
    return this.javaClient.get(
      `/devices/${encodeURIComponent(deviceCode)}`,
    );
  }

  @Post()
  @Roles('ADMIN', 'OPERATOR')
  create(@Body() body: Record<string, unknown>) {
    return this.javaClient.post('/devices', body);
  }

  @Put(':deviceCode')
  @Roles('ADMIN', 'OPERATOR')
  update(
    @Param('deviceCode') deviceCode: string,
    @Body() body: Record<string, unknown>,
  ) {
    this.requireDeviceCode(deviceCode);
    return this.javaClient.put(
      `/devices/${encodeURIComponent(deviceCode)}`,
      body,
    );
  }

  @Post(':deviceCode/heartbeat')
  @Roles('ADMIN', 'OPERATOR')
  heartbeat(@Param('deviceCode') deviceCode: string) {
    this.requireDeviceCode(deviceCode);
    return this.javaClient.post(
      `/devices/${encodeURIComponent(deviceCode)}/heartbeat`,
      {},
    );
  }

  @Delete(':deviceCode')
  @Roles('ADMIN', 'OPERATOR')
  delete(@Param('deviceCode') deviceCode: string) {
    this.requireDeviceCode(deviceCode);
    return this.javaClient.delete(`/devices/${encodeURIComponent(deviceCode)}`);
  }

  private requireDeviceCode(deviceCode: string) {
    if (!deviceCode?.trim()) {
      throw new BadRequestException('deviceCode 不能为空');
    }
  }
}
