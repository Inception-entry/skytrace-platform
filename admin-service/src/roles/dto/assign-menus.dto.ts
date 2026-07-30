import { IsArray, IsInt } from 'class-validator'
import { Type } from 'class-transformer'

export class AssignMenusDto {
  @IsArray()
  @IsInt({ each: true })
  @Type(() => Number)
  menuIds!: number[]
}
