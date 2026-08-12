import { Transform, Type } from 'class-transformer';
import {
  IsBoolean,
  IsInt,
  IsOptional,
  IsString,
  Max,
  MaxLength,
  Min,
} from 'class-validator';

export class EvidenceMaintenanceBatchDto {
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(500)
  batchSize?: number;
}

export class EvidenceCleanupDto extends EvidenceMaintenanceBatchDto {
  @IsOptional()
  @Transform(({ value }) => {
    // 只转换明确的布尔文本，拼写错误必须交给 IsBoolean 返回 400。
    if (value === true || value === 'true') return true;
    if (value === false || value === 'false') return false;
    return value;
  })
  @IsBoolean()
  dryRun = true;

  @IsOptional()
  @IsString()
  @MaxLength(64)
  confirmation?: string;
}
