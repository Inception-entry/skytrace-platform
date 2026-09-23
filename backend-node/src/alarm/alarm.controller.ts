import {
  Body,
  Controller,
  Get,
  Post,
  UploadedFile,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { JavaClientService } from '../common/java-client/java-client.service';
import { requireOffsetEventTime } from '../common/java-local-date-time';
import {
  diskUploadOptions,
  withDiskUpload,
  type DiskUploadFile,
} from '../common/upload-disk';
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
      schemaVersion: 2,
      eventTime: requireOffsetEventTime(dto.eventTime),
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
      schemaVersion: 2,
      eventTime: requireOffsetEventTime(dto.eventTime),
      detectionId: dto.detectionId,
    };
    return this.javaClient.post('/detections/alarms', payload);
  }

  @Post('analyze')
  @Roles('ADMIN', 'OPERATOR')
  @UseInterceptors(FileInterceptor('file', diskUploadOptions(10 * 1024 * 1024)))
  analyze(
    @UploadedFile() file?: DiskUploadFile,
    @Body('deviceCode') deviceCode?: string,
    @Body('taskCode') taskCode?: string,
    @Body('latitude') latitude?: string,
    @Body('longitude') longitude?: string,
    @Body('publishAlarms') publishAlarms?: string,
    @Body('maxAlarms') maxAlarms?: string,
  ) {
    return withDiskUpload(
      file,
      'image',
      '请选择需要识别的图片',
      (part) =>
        this.javaClient.postMultipart(
          '/detections/analyze',
          part,
          {
            deviceCode: deviceCode || 'UAV-001',
            taskCode,
            latitude,
            longitude,
            publishAlarms: publishAlarms ?? 'true',
            maxAlarms,
          },
          180_000,
        ),
    );
  }

  @Post('analyze-video')
  @Roles('ADMIN', 'OPERATOR')
  @UseInterceptors(FileInterceptor('file', diskUploadOptions(50 * 1024 * 1024)))
  analyzeVideo(
    @UploadedFile() file?: DiskUploadFile,
    @Body('deviceCode') deviceCode?: string,
    @Body('taskCode') taskCode?: string,
    @Body('latitude') latitude?: string,
    @Body('longitude') longitude?: string,
    @Body('publishAlarms') publishAlarms?: string,
    @Body('maxAlarms') maxAlarms?: string,
    @Body('frameIntervalSec') frameIntervalSec?: string,
    @Body('maxFrames') maxFrames?: string,
  ) {
    return withDiskUpload(
      file,
      'video',
      '请选择需要识别的视频',
      (part) =>
        this.javaClient.postMultipart(
          '/detections/analyze-video',
          part,
          {
            deviceCode: deviceCode || 'UAV-001',
            taskCode,
            latitude,
            longitude,
            publishAlarms: publishAlarms ?? 'true',
            maxAlarms,
            frameIntervalSec,
            maxFrames,
          },
          300_000,
        ),
    );
  }
}
