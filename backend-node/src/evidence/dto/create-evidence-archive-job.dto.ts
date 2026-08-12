import {
  IsIn,
  IsNotEmpty,
  IsString,
  MaxLength,
} from 'class-validator'

export class CreateEvidenceArchiveJobDto {
  // 第一版只允许后端已经实现的 TASK 和 ALARM 两种确定性范围。
  @IsIn(['TASK', 'ALARM'])
  scopeType!: 'TASK' | 'ALARM'

  // scopeValue 对应 taskCode 或 alarmEventCode，禁止空白和超长输入。
  @IsString()
  @IsNotEmpty()
  @MaxLength(128)
  scopeValue!: string
}
