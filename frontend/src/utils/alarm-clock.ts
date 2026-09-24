const SHANGHAI = 'Asia/Shanghai'

export interface AlarmClock {
  wall: string
  utc: string
}

export function formatAlarmClock(input: {
  eventTime: string
  eventTimeUtc?: string | null
}): AlarmClock {
  const utc = input.eventTimeUtc?.trim() ?? ''
  if (utc) {
    const instant = new Date(utc)
    if (!Number.isNaN(instant.getTime())) {
      const wall = new Intl.DateTimeFormat('zh-CN', {
        timeZone: SHANGHAI,
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hourCycle: 'h23',
      }).format(instant)
      return { wall, utc }
    }
  }
  return { wall: input.eventTime, utc: '' }
}
