import { Type } from 'class-transformer';
import {
  IsNotEmpty,
  IsNumber,
  IsOptional,
  IsString,
} from 'class-validator';

export class CreateAlarmDto {
  @IsString()
  @IsNotEmpty()
  deviceCode!: string;

  @IsString()
  @IsOptional()
  taskCode?: string;

  @IsString()
  @IsNotEmpty()
  eventType!: string;

  @IsString()
  @IsOptional()
  weaponType?: string;

  @IsOptional()
  @Type(() => Number)
  @IsNumber()
  confidence?: number;

  @IsOptional()
  @Type(() => Number)
  @IsNumber()
  latitude?: number;

  @IsOptional()
  @Type(() => Number)
  @IsNumber()
  longitude?: number;

  @IsOptional()
  @IsString()
  imageUrl?: string;

  @IsOptional()
  @IsString()
  videoUrl?: string;

  @IsOptional()
  @IsString()
  primaryEvidenceCode?: string;

  @IsOptional()
  @IsString()
  primaryVideoEvidenceCode?: string;

  @IsOptional()
  @IsString()
  imageObjectKey?: string;

  @IsOptional()
  @IsString()
  videoObjectKey?: string;

  @IsOptional()
  @IsString()
  eventTime?: string;
}
