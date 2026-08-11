import { IsString, Matches, MaxLength } from 'class-validator'

export class EvidenceCodeParamDto {
  @IsString()
  @MaxLength(64)
  @Matches(/^[A-Za-z0-9_-]+$/)
  evidenceCode!: string
}