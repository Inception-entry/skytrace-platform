import {
  BadRequestException,
  Body,
  Controller,
  Get,
  Post,
  Query,
  UploadedFile,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { Roles } from '../auth/http-auth.decorators';
import { JavaClientService } from '../shared/java-client.service';

interface UploadedEvidenceFile {
  buffer: Buffer;
  originalname: string;
  mimetype: string;
}

@Controller('evidence')
export class EvidenceController {
  constructor(private readonly javaClient: JavaClientService) {}

  @Get()
  list(
    @Query('taskCode') taskCode?: string,
    @Query('alarmEventCode') alarmEventCode?: string,
  ) {
    if (!taskCode?.trim() && !alarmEventCode?.trim()) {
      throw new BadRequestException(
        '请至少提供 taskCode 或 alarmEventCode',
      );
    }
    const parameters = new URLSearchParams();
    if (taskCode?.trim()) {
      parameters.set('taskCode', taskCode.trim());
    }
    if (alarmEventCode?.trim()) {
      parameters.set('alarmEventCode', alarmEventCode.trim());
    }
    return this.javaClient.get(`/evidence?${parameters.toString()}`);
  }

  @Post()
  @Roles('ADMIN', 'OPERATOR')
  @UseInterceptors(
    FileInterceptor('file', {
      limits: { fileSize: 20 * 1024 * 1024 },
    }),
  )
  upload(
    @UploadedFile() file?: UploadedEvidenceFile,
    @Body('taskCode') taskCode?: string,
    @Body('alarmEventCode') alarmEventCode?: string,
  ) {
    if (!file) {
      throw new BadRequestException('请选择需要上传的证据文件');
    }
    return this.javaClient.postMultipart(
      '/evidence',
      file,
      {
        taskCode,
        alarmEventCode,
      },
    );
  }
}
