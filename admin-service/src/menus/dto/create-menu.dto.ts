import { IsIn, IsInt, IsNotEmpty, IsOptional, IsString, Min } from 'class-validator'
import { Type } from 'class-transformer'

export class CreateMenuDto {
  @IsString()
  @IsNotEmpty()
  name!: string

  @IsString()
  @IsNotEmpty()
  code!: string

  @IsInt()
  @IsIn([1, 2, 3])
  @Type(() => Number)
  type!: number

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
  @Type(() => Number)
  parentId?: number

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
