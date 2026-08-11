import { Type } from 'class-transformer'
import {
  IsBoolean,
  IsIn,
  IsInt,
  IsISO8601,
  IsOptional,
  IsString,
  Max,
  MaxLength,
  Min,
} from 'class-validator'

export class SearchEvidenceDto {
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(0)
  page = 0

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(100)
  size = 20

  @IsOptional()
  @IsString()
  @MaxLength(64)
  taskCode?: string

  @IsOptional()
  @IsString()
  @MaxLength(64)
  alarmEventCode?: string

  @IsOptional()
  @IsString()
  @MaxLength(64)
  deviceCode?: string

  @IsOptional()
  @IsIn(['IMAGE', 'VIDEO'])
  assetType?: 'IMAGE' | 'VIDEO'

  @IsOptional()
  @IsIn([
    'MANUAL_UPLOAD',
    'AI_DETECTION',
    'VIDEO_FRAME',
    'SYSTEM_GENERATED',
  ])
  sourceType?:
    | 'MANUAL_UPLOAD'
    | 'AI_DETECTION'
    | 'VIDEO_FRAME'
    | 'SYSTEM_GENERATED'

  @IsOptional()
  @IsIn(['PENDING', 'APPROVED', 'REJECTED'])
  reviewStatus?: 'PENDING' | 'APPROVED' | 'REJECTED'

  @IsOptional()
  @IsISO8601()
  startTime?: string

  @IsOptional()
  @IsISO8601()
  endTime?: string

  @IsOptional()
  @IsString()
  @MaxLength(128)
  keyword?: string

  @IsOptional()
  @Type(() => Boolean)
  @IsBoolean()
  includeDeleted?: boolean
}