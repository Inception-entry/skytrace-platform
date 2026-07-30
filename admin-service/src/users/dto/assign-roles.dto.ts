import { IsArray, IsInt } from 'class-validator'
import { Type } from 'class-transformer'

export class AssignRolesDto {
  @IsArray()
  @IsInt({ each: true })
  @Type(() => Number)
  roleIds!: number[]
}
