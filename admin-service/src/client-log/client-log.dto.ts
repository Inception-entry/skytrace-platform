import { Type } from 'class-transformer'
import {
  IsIn,
  IsInt,
  IsOptional,
  IsString,
  Matches,
  Max,
  MaxLength,
  Min,
  ValidateNested,
} from 'class-validator'

export class ClientLogDetailDto {
  @IsOptional()
  @IsInt()
  @Min(100)
  @Max(599)
  status?: number

  @IsOptional()
  @IsString()
  @MaxLength(8)
  @Matches(/^[A-Za-z]+$/)
  method?: string

  @IsOptional()
  @IsString()
  @MaxLength(200)
  url?: string

  @IsOptional()
  @IsString()
  @MaxLength(64)
  code?: string

  @IsOptional()
  @IsString()
  @MaxLength(200)
  serverMessage?: string
}

export class ClientLogDto {
  @IsIn(['warn', 'error'])
  level!: 'warn' | 'error'

  @IsString()
  @Matches(/^[A-Za-z0-9._-]{1,64}$/)
  scope!: string

  @IsString()
  @MaxLength(300)
  message!: string

  @IsOptional()
  @IsString()
  @MaxLength(200)
  path?: string

  @IsOptional()
  @ValidateNested()
  @Type(() => ClientLogDetailDto)
  detail?: ClientLogDetailDto
}
