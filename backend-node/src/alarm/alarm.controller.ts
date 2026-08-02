import { Body, Controller, Get, Post } from '@nestjs/common';
import { JavaClientService } from '../shared/java-client.service';
import { AlarmRealtimeGateway } from '../realtime/alarm-realtime.gateway';
import { CreateAlarmDto } from './dto/create-alarm.dto';
import { Roles } from '../auth/http-auth.decorators';

@Controller('alarms')
export class AlarmController {
  constructor(
    private readonly javaClient: JavaClientService,
    private readonly alarmGateway: AlarmRealtimeGateway,
  ) {}

  @Get('latest')
  latest() {
    return this.javaClient.get('/alarms/latest');
  }

  @Post()
  @Roles('ADMIN', 'OPERATOR')
  async create(@Body() dto: CreateAlarmDto) {
    const payload = {
      ...dto,
      eventTime: dto.eventTime ?? new Date().toISOString(),
    };
    const result = await this.javaClient.post('/alarms', payload);
    this.alarmGateway.broadcastAlarm(result);
    return result;
  }

  @Post('detections')
  @Roles('ADMIN', 'OPERATOR')
  publishDetection(@Body() dto: CreateAlarmDto) {
    const payload = {
      deviceCode: dto.deviceCode,
      taskCode: dto.taskCode,
      eventType: dto.eventType,
      weaponType: dto.weaponType,
      confidence: dto.confidence,
      latitude: dto.latitude,
      longitude: dto.longitude,
      imageObjectKey: dto.imageUrl,
      videoObjectKey: dto.videoUrl,
      eventTime: dto.eventTime ?? new Date().toISOString(),
    };
    return this.javaClient.post('/detections/alarms', payload);
  }
}
