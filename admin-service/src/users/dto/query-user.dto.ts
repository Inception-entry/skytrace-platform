import { IsInt, IsOptional, IsString, Min } from 'class-validator'
import { Type } from 'class-transformer'

export class QueryUserDto {
  @IsOptional()
  @IsString()
  username?: string

  @IsOptional()
  @IsInt()
  @Min(1)
  @Type(() => Number)
  page?: number = 1

  @IsOptional()
  @IsInt()
  @Min(1)
  @Type(() => Number)
  pageSize?: number = 10
}
