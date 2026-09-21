import {
  Body,
  Controller,
  Delete,
  Get,
  Param,
  Post,
  UploadedFile,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { JavaClientService } from '../common/java-client/java-client.service';
import {
  diskUploadOptions,
  withDiskUpload,
  type DiskUploadFile,
} from '../common/upload-disk';
import { SearchKnowledgeDto } from './dto/search-knowledge.dto';
import { Roles } from '../auth/http-auth.decorators';

@Controller('knowledge')
export class KnowledgeController {
  constructor(private readonly javaClient: JavaClientService) {}

  @Get('documents')
  listDocuments(): Promise<unknown> {
    return this.javaClient.get('/knowledge/documents');
  }

  @Post('documents')
  @Roles('ADMIN')
  @UseInterceptors(FileInterceptor('file', diskUploadOptions(10 * 1024 * 1024)))
  uploadDocument(
    @UploadedFile() file?: DiskUploadFile,
  ): Promise<unknown> {
    return withDiskUpload(
      file,
      'knowledge',
      '请选择需要上传的文档',
      (part) => this.javaClient.postMultipart('/knowledge/documents', part),
    );
  }

  @Post('search')
  search(@Body() dto: SearchKnowledgeDto): Promise<unknown> {
    return this.javaClient.post('/knowledge/search', dto, 120_000);
  }

  @Delete('documents/:documentId')
  @Roles('ADMIN')
  deleteDocument(
    @Param('documentId') documentId: string,
  ): Promise<unknown> {
    return this.javaClient.delete(
      `/knowledge/documents/${encodeURIComponent(documentId)}`,
      30_000,
    );
  }
}
