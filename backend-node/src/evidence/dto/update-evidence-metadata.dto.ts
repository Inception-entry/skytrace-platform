import { Type } from 'class-transformer'
import {
  ArrayMaxSize,
  IsArray,
  IsIn,
  IsInt,
  IsOptional,
  IsString,
  MaxLength,
} from 'class-validator'

export class UpdateEvidenceMetadataDto {
  @IsOptional()
  @IsString()
  @MaxLength(512)
  remark?: string

  @IsOptional()
  @IsIn(['PENDING', 'APPROVED', 'REJECTED'])
  reviewStatus?: 'PENDING' | 'APPROVED' | 'REJECTED'

  @IsOptional()
  @IsString()
  @MaxLength(512)
  reviewComment?: string

  @IsOptional()
  @IsArray()
  @ArrayMaxSize(50)
  @Type(() => Number)
  @IsInt({ each: true })
  tagIds?: number[]
}
