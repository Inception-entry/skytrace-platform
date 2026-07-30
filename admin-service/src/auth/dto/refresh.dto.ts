import { IsNotEmpty, IsString } from 'class-validator'

export class RefreshDto {
  @IsString()
  @IsNotEmpty()
  refresh_token!: string
}
