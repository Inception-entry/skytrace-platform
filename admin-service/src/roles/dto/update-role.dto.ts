import { IsIn, IsInt, IsOptional, IsString } from 'class-validator'
import { Type } from 'class-transformer'

export class UpdateRoleDto {
  @IsOptional()
  @IsString()
  name?: string

  @IsOptional()
  @IsString()
  description?: string

  @IsOptional()
  @IsInt()
  @IsIn([0, 1])
  @Type(() => Number)
  status?: number
}
