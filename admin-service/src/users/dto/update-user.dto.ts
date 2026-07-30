import { IsEmail, IsIn, IsInt, IsOptional, IsString, MinLength } from 'class-validator'
import { Type } from 'class-transformer'

export class UpdateUserDto {
  @IsOptional()
  @IsString()
  @MinLength(6)
  password?: string

  @IsOptional()
  @IsEmail()
  email?: string

  @IsOptional()
  @IsString()
  nickname?: string

  @IsOptional()
  @IsInt()
  @IsIn([0, 1])
  @Type(() => Number)
  status?: number
}
