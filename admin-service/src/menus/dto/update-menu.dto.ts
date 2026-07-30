import { IsIn, IsInt, IsOptional, IsString, Min } from 'class-validator'
import { Type } from 'class-transformer'

export class UpdateMenuDto {
  @IsOptional()
  @IsString()
  name?: string

  @IsOptional()
  @IsString()
  path?: string

  @IsOptional()
  @IsString()
  component?: string

  @IsOptional()
  @IsString()
  icon?: string

  @IsOptional()
  @IsInt()
  @Min(0)
  @Type(() => Number)
  sort?: number

  @IsOptional()
  @IsInt()
  @IsIn([0, 1])
  @Type(() => Number)
  visible?: number
}
