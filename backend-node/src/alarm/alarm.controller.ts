import {
  BadRequestException,
  Body,
  Controller,
  Get,
  Post,
  UploadedFile,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { JavaClientService } from '../shared/java-client.service';
import { AlarmRealtimeGateway } from '../realtime/alarm-realtime.gateway';
import { CreateAlarmDto } from './dto/create-alarm.dto';
import { Roles } from '../auth/http-auth.decorators';

interface UploadedVisionFile {
  buffer: Buffer;
  originalname: string;
  mimetype: string;
}

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
      imageObjectKey: dto.imageObjectKey ?? dto.imageUrl,
      videoObjectKey: dto.videoObjectKey ?? dto.videoUrl,
      eventTime: dto.eventTime ?? new Date().toISOString(),
    };
    return this.javaClient.post('/detections/alarms', payload);
  }

  @Post('analyze')
  @Roles('ADMIN', 'OPERATOR')
  @UseInterceptors(
    FileInterceptor('file', {
      limits: { fileSize: 10 * 1024 * 1024 },
    }),
  )
  analyze(
    @UploadedFile() file?: UploadedVisionFile,
    @Body('deviceCode') deviceCode?: string,
    @Body('taskCode') taskCode?: string,
    @Body('latitude') latitude?: string,
    @Body('longitude') longitude?: string,
    @Body('publishAlarms') publishAlarms?: string,
    @Body('maxAlarms') maxAlarms?: string,
  ) {
    if (!file) {
      throw new BadRequestException('请选择需要识别的图片');
    }
    return this.javaClient.postMultipart(
      '/detections/analyze',
      file,
      {
        deviceCode: deviceCode || 'UAV-001',
        taskCode,
        latitude,
        longitude,
        publishAlarms: publishAlarms ?? 'true',
        maxAlarms,
      },
      180_000,
    );
  }
}
