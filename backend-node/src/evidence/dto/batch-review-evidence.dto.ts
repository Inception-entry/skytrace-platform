import {
  ArrayMaxSize,
  ArrayMinSize,
  IsArray,
  IsIn,
  IsOptional,
  IsString,
  MaxLength,
} from 'class-validator'

export class BatchReviewEvidenceDto {
  @IsArray()
  @ArrayMinSize(1)
  @ArrayMaxSize(100)
  @IsString({ each: true })
  evidenceCodes!: string[]

  @IsIn(['PENDING', 'APPROVED', 'REJECTED'])
  reviewStatus!: 'PENDING' | 'APPROVED' | 'REJECTED'

  @IsOptional()
  @IsString()
  @MaxLength(512)
  reviewComment?: string
}
