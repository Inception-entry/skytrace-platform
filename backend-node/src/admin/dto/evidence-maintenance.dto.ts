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
import { strictBoolean } from '../../common/strict-boolean';

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
  @Transform(({ value }) => strictBoolean(value))
  @IsBoolean()
  dryRun = true;

  @IsOptional()
  @IsString()
  @MaxLength(64)
  confirmation?: string;
}
