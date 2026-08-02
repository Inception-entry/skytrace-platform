import {
  BadRequestException,
  Body,
  Controller,
  Post,
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
