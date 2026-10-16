import { formatAdminClientLog, ClientLogService } from './client-log.service'
import { ClientLogDto } from './client-log.dto'

describe('admin client log', () => {
  it('redacts secrets and drops query strings before stdout', () => {
    const entry: ClientLogDto = {
      level: 'error',
      scope: 'auth.login',
      message: 'password=secret failed',
      path: '/login',
      detail: {
        status: 401,
        method: 'post',
        url: '/admin-api/auth/login?access_token=abc',
        code: 'UNAUTHORIZED',
        serverMessage: 'bad credential',
      },
    }
    const line = formatAdminClientLog(entry)
    expect(line).toContain('[skytrace-admin]')
    expect(line).toContain('[redacted]=[redacted]')
    expect(line).toContain('url=/admin-api/auth/login')
    expect(line).not.toContain('abc')
    expect(line).toContain('serverMessage=bad [redacted]')
  })

  it('drops logs after 30 accepts from the same address in one minute', () => {
    const service = new ClientLogService()
    const entry: ClientLogDto = { level: 'warn', scope: 'menus.load', message: '菜单加载失败' }
    const lines: string[] = []
    const original = console.warn
    console.warn = (line?: unknown) => {
      lines.push(String(line))
    }
    try {
      for (let i = 0; i < 31; i += 1) {
        service.accept(entry, '10.0.0.8', 1_000 + i)
      }
    } finally {
      console.warn = original
    }
    expect(lines).toHaveLength(30)
  })
})
