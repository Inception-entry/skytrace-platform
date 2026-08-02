import {
  IsDateString,
  IsNotEmpty,
  IsOptional,
  IsString,
  Matches,
  MaxLength,
} from 'class-validator';

export class UpdateInspectionTaskDto {
  @IsString()
  @IsNotEmpty()
  @MaxLength(128)
  taskName!: string;

  @IsString()
  @IsNotEmpty()
  @MaxLength(64)
  deviceCode!: string;

  @IsOptional()
  @IsString()
  @MaxLength(64)
  @Matches(/^$|^[A-Za-z0-9_-]+$/)
  routeCode?: string;

  @IsDateString()
  planStartTime!: string;

  @IsDateString()
  planEndTime!: string;
}

export class CreateInspectionTaskDto
  extends UpdateInspectionTaskDto {
  @IsString()
  @IsNotEmpty()
  @MaxLength(64)
  @Matches(/^[A-Za-z0-9_-]+$/)
  taskCode!: string;
}
