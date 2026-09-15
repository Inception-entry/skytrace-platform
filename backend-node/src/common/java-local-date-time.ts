import { BadRequestException } from '@nestjs/common'

export const JAVA_DATABASE_ZONE = 'Asia/Shanghai'

const OFFSET_SUFFIX = /(?:Z|[+-]\d{2}:\d{2})$/i

export function toJavaLocalDateTime(value?: string): string {
  const source = (value ?? new Date().toISOString()).trim()
  if (!OFFSET_SUFFIX.test(source)) {
    throw new BadRequestException('eventTime 必须包含 Z 或 UTC offset')
  }
  const instant = new Date(source)
  if (Number.isNaN(instant.getTime())) {
    throw new BadRequestException('eventTime 必须包含 Z 或 UTC offset')
  }
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: JAVA_DATABASE_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
    fractionalSecondDigits: 3,
  }).formatToParts(instant)
  const pick = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value
  const year = pick('year')
  const month = pick('month')
  const day = pick('day')
  const hour = pick('hour')
  const minute = pick('minute')
  const second = pick('second')
  const fraction = pick('fractionalSecond') ?? '000'
  if (!year || !month || !day || !hour || !minute || !second) {
    throw new BadRequestException('eventTime 必须包含 Z 或 UTC offset')
  }
  return `${year}-${month}-${day}T${hour}:${minute}:${second}.${fraction}`
}
