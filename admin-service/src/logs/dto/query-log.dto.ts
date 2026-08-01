import { IsOptional, IsString, IsNumberString } from 'class-validator'

export class QueryLogDto {
  @IsOptional() @IsNumberString() page?: number
  @IsOptional() @IsNumberString() pageSize?: number
  @IsOptional() @IsString() username?: string
  @IsOptional() @IsString() module?: string
  @IsOptional() @IsString() action?: string
  @IsOptional() @IsString() startTime?: string
  @IsOptional() @IsString() endTime?: string
}
