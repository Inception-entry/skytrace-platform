import { IsOptional, IsString, MinLength } from 'class-validator'

export class RefreshDto {
  @IsOptional()
  @IsString()
  @MinLength(1)
  refresh_token?: string
}
