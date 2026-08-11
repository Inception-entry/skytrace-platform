import {
  BadRequestException,
  Body,
  Controller,
  Delete,
  Get,
  Param,
  Patch,
  Post,
  Query,
  UploadedFile,
  UseInterceptors,
} from '@nestjs/common'
import { FileInterceptor } from '@nestjs/platform-express'
import { Roles } from '../auth/http-auth.decorators'
import { JavaClientService } from '../common/java-client/java-client.service'
import { BatchReviewEvidenceDto } from './dto/batch-review-evidence.dto'
import { BatchTagEvidenceDto } from './dto/batch-tag-evidence.dto'
import { EvidenceCodeParamDto } from './dto/evidence-code.dto'
import { SearchEvidenceDto } from './dto/search-evidence.dto'
import { UpdateEvidenceMetadataDto } from './dto/update-evidence-metadata.dto'

interface UploadedEvidenceFile {
  buffer: Buffer
  originalname: string
  mimetype: string
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
      )
    }
    const parameters = new URLSearchParams()
    if (taskCode?.trim()) {
      parameters.set('taskCode', taskCode.trim())
    }
    if (alarmEventCode?.trim()) {
      parameters.set('alarmEventCode', alarmEventCode.trim())
    }
    return this.javaClient.get(`/evidence?${parameters.toString()}`)
  }

  @Get('tags')
  tags() {
    return this.javaClient.get('/evidence/tags')
  }

  @Get('search')
  search(@Query() query: SearchEvidenceDto) {
    const parameters = new URLSearchParams()
    parameters.set('page', String(query.page ?? 0))
    parameters.set('size', String(query.size ?? 20))
    if (query.taskCode?.trim()) {
      parameters.set('taskCode', query.taskCode.trim())
    }
    if (query.alarmEventCode?.trim()) {
      parameters.set('alarmEventCode', query.alarmEventCode.trim())
    }
    if (query.deviceCode?.trim()) {
      parameters.set('deviceCode', query.deviceCode.trim())
    }
    if (query.assetType) {
      parameters.set('assetType', query.assetType)
    }
    if (query.sourceType) {
      parameters.set('sourceType', query.sourceType)
    }
    if (query.reviewStatus) {
      parameters.set('reviewStatus', query.reviewStatus)
    }
    if (query.startTime) {
      parameters.set('startTime', query.startTime)
    }
    if (query.endTime) {
      parameters.set('endTime', query.endTime)
    }
    if (query.keyword?.trim()) {
      parameters.set('keyword', query.keyword.trim())
    }
    if (query.includeDeleted != null) {
      parameters.set('includeDeleted', String(query.includeDeleted))
    }
    return this.javaClient.get(`/evidence/search?${parameters.toString()}`)
  }

  @Get(':evidenceCode')
  detail(@Param() params: EvidenceCodeParamDto) {
    return this.javaClient.get(
      `/evidence/${encodeURIComponent(params.evidenceCode)}`,
    )
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
    @Body('deviceCode') deviceCode?: string,
  ) {
    if (!file) {
      throw new BadRequestException('请选择需要上传的证据文件')
    }
    return this.javaClient.postMultipart('/evidence', file, {
      taskCode,
      alarmEventCode,
      deviceCode,
    })
  }

  @Patch(':evidenceCode/metadata')
  @Roles('ADMIN', 'OPERATOR')
  updateMetadata(
    @Param() params: EvidenceCodeParamDto,
    @Body() body: UpdateEvidenceMetadataDto,
  ) {
    return this.javaClient.patch(
      `/evidence/${encodeURIComponent(params.evidenceCode)}/metadata`,
      body,
    )
  }

  @Post('batch/review')
  @Roles('ADMIN', 'OPERATOR')
  batchReview(@Body() body: BatchReviewEvidenceDto) {
    return this.javaClient.post('/evidence/batch/review', body)
  }

  @Post('batch/tags')
  @Roles('ADMIN', 'OPERATOR')
  batchTags(@Body() body: BatchTagEvidenceDto) {
    return this.javaClient.post('/evidence/batch/tags', body)
  }

  @Post(':evidenceCode/preview-url')
  @Roles('ADMIN', 'OPERATOR')
  previewUrl(@Param() params: EvidenceCodeParamDto) {
    return this.javaClient.post(
      `/evidence/${encodeURIComponent(params.evidenceCode)}/preview-url`,
      {},
    )
  }

  @Post(':evidenceCode/download-url')
  @Roles('ADMIN', 'OPERATOR')
  downloadUrl(@Param() params: EvidenceCodeParamDto) {
    return this.javaClient.post(
      `/evidence/${encodeURIComponent(params.evidenceCode)}/download-url`,
      {},
    )
  }

  @Delete(':evidenceCode')
  @Roles('ADMIN', 'OPERATOR')
  remove(@Param() params: EvidenceCodeParamDto) {
    return this.javaClient.delete(
      `/evidence/${encodeURIComponent(params.evidenceCode)}`,
    )
  }

  @Post(':evidenceCode/restore')
  @Roles('ADMIN', 'OPERATOR')
  restore(@Param() params: EvidenceCodeParamDto) {
    return this.javaClient.post(
      `/evidence/${encodeURIComponent(params.evidenceCode)}/restore`,
      {},
    )
  }
}