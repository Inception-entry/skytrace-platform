import { IsString, IsOptional, IsEmail, MaxLength } from 'class-validator'

export class UpdateProfileDto {
  @IsString()
  @IsOptional()
  @MaxLength(50)
  nickname?: string

  @IsEmail()
  @IsOptional()
  email?: string

  @IsString()
  @IsOptional()
  avatar?: string
}
