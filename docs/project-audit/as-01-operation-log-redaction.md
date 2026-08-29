# AS-01 操作日志脱敏：实施说明

适用分支：`fix/admin-op-log-redaction`  
只改 Admin Service。读完这一份就可以动手，不必先把审计全集和认证方案读完。

旧文档把同一件事拆成了 RB-01、AS-01、AUTH-001、路线图 Wave 1A。那些是编号索引；**真正要改的代码和验收以本文为准。**

---

## 1. 一句话

管理后台每次成功操作，会把请求 JSON 原样存进 PostgreSQL 表 `sys_operation_log.params`。登录、创建用户、改用户密码时，明文密码会进这张表。能打开「操作日志」页的人就能看到密码。

这个分支只堵住 **以后新写入**。旧行里已经存进去的秘密，不在这个 PR 里清生产库。

---

## 2. 请求是怎么进数据库的

```text
浏览器 POST /auth/login
  body: { "username": "alice", "password": "Admin@123" }
        │
        ▼
AuthController.login  （上面有 @Log('系统', '登录')）
        │
        ▼
全局拦截器 OperationLogInterceptor
  发现方法上有 @Log
  成功返回后执行：
    JSON.stringify(req.body).slice(0, 500)
        │
        ▼
LogsService.record → INSERT sys_operation_log
  params = '{"username":"alice","password":"Admin@123"}'
        │
        ▼
GET /logs  （管理前端「操作日志」）
  任何人只要有 log:list 权限就能读到密码
```

相关代码现在是这样：

| 文件 | 做什么 |
| --- | --- |
| `admin-service/src/common/decorators/log.decorator.ts` | `@Log(模块, 动作)` 只是打标记 |
| `admin-service/src/common/interceptors/operation-log.interceptor.ts` | 看见标记就把 body 整段写入 |
| `admin-service/src/app.module.ts` | 把拦截器注册成全局 `APP_INTERCEPTOR` |
| `admin-service/src/logs/logs.service.ts` | `prisma.operationLog.create` |
| `admin-service/prisma/schema.prisma` 的 `OperationLog` | 表 `sys_operation_log`，字段 `params` |

拦截器里当前这一行就是漏洞：

```ts
params: JSON.stringify(req.body ?? {}).slice(0, 500),
```

先序列化再截 500 字没有用：`password` 在 JSON 开头，截不断。

拦截器 **只在成功时** 写日志（`tap.next`）。登录失败反而不会记。失败要不要记，见第 5 节。

---

## 3. 现在哪些接口会把秘密写进日志

只有打了 `@Log` 的接口才会走拦截器。当前清单：

| 接口 | 装饰器 | body 里的秘密 |
| --- | --- | --- |
| `POST /auth/login` | 有 | `password` |
| `POST /users` | 有 | `password` |
| `PUT /users/:id` | 有 | 可选 `password` |
| `PUT /users/:id/roles`、角色/菜单增删改、清空日志 | 有 | 一般没有密码 |
| `POST /auth/refresh` | **没有** | `refresh_token`（现在不会进操作日志） |
| `POST /auth/logout` | **没有** | `refresh_token` |
| `PUT /auth/password` | **没有** | `currentPassword`、`newPassword` |

本分支 **必须** 修登录和用户创建/更新（已经在写日志）。

刷新、登出、改密：本分支 **一并补上 `@Log`，并走同一套脱敏**。否则以后有人给这三个接口加上日志，会再漏一次。补日志的目的是审计「谁刷新了/谁改了密」，不是把 token 存下来。

不要记录 HTTP header 或 Cookie。现在的拦截器本来就不记，保持这样。

---

## 4. 这个分支要改的文件

### 4.1 新增脱敏函数

新建：`admin-service/src/common/utils/redact.ts`

要求：

1. 对象的每个键：先转小写，再去掉 `_` 和 `-`，再和秘密键集合比较。  
   这样 `password`、`newPassword`、`new_password`、`refresh_token`、`Authorization` 都会中。
2. 命中则把值换成字符串 `'[REDACTED]'`，键名保留。
3. 普通字段原样保留（`username`、`nickname`、`email` 等）。
4. 嵌套对象、数组都要递归。
5. 深度超过 5 层返回 `'[TRUNCATED]'`。
6. 数组最多处理前 20 项。
7. 用 `WeakSet` 处理循环引用，不要死循环，也不要让后面的 `JSON.stringify` 抛错。
8. `null`、数字、布尔、字符串原样返回（字符串本身不是键，不要把内容里碰巧出现 `password` 的备注误杀）。

秘密键集合（规范化之后）：

```text
password
currentpassword
newpassword
token
accesstoken
refreshtoken
secret
authorization
cookie
```

推荐实现（可直接落文件，单测按它写）：

```ts
const SECRET_KEYS = new Set([
  'password',
  'currentpassword',
  'newpassword',
  'token',
  'accesstoken',
  'refreshtoken',
  'secret',
  'authorization',
  'cookie',
])

function normalizeKey(key: string): string {
  return key.toLowerCase().replace(/[_-]/g, '')
}

export function redact(value: unknown, depth = 0, seen = new WeakSet<object>()): unknown {
  if (depth > 5) {
    return '[TRUNCATED]'
  }
  if (value === null || typeof value !== 'object') {
    return value
  }
  if (seen.has(value)) {
    return '[CIRCULAR]'
  }
  seen.add(value)

  if (Array.isArray(value)) {
    return value.slice(0, 20).map((item) => redact(item, depth + 1, seen))
  }

  return Object.fromEntries(
    Object.entries(value as Record<string, unknown>).map(([key, nested]) => {
      const secret = SECRET_KEYS.has(normalizeKey(key))
      return [key, secret ? '[REDACTED]' : redact(nested, depth + 1, seen)]
    }),
  )
}

export function serializeRedacted(value: unknown, maxLength = 500): string {
  return JSON.stringify(redact(value ?? {})).slice(0, maxLength)
}
```

顺序必须是：**先 redact，再 stringify，再截长度。**

### 4.2 改拦截器

文件：`admin-service/src/common/interceptors/operation-log.interceptor.ts`

整份替换成下面这样。要点：

- `params` 用 `serializeRedacted(req.body)`，不要再 `JSON.stringify(req.body).slice(0, 500)`。
- `tap` 同时写 `next`（成功）和 `error`（失败），每个请求只记一条。
- 失败时：若是 Nest 的 `HttpException`，用 `getStatus()`（例如 401）；否则用 `500`。
- 没有 `req.user` 时仍用 `anonymous` / `userId: 0`。
- 不要把 `req.headers`、`req.cookies` 写进 `params`。

```ts
import {
  Injectable,
  NestInterceptor,
  ExecutionContext,
  CallHandler,
  HttpException,
} from '@nestjs/common'
import { Reflector } from '@nestjs/core'
import { Observable } from 'rxjs'
import { tap } from 'rxjs/operators'
import type { Request, Response } from 'express'
import { LOG_METADATA, LogMeta } from '../decorators/log.decorator'
import { LogsService } from '../../logs/logs.service'
import { serializeRedacted } from '../utils/redact'

type AuthedRequest = Request & { user?: { id: number; username: string } }

function statusFromError(error: unknown): number {
  if (error instanceof HttpException) {
    return error.getStatus()
  }
  return 500
}

@Injectable()
export class OperationLogInterceptor implements NestInterceptor {
  constructor(
    private readonly reflector: Reflector,
    private readonly logsService: LogsService,
  ) {}

  intercept(context: ExecutionContext, next: CallHandler): Observable<unknown> {
    const meta = this.reflector.get<LogMeta>(LOG_METADATA, context.getHandler())
    if (!meta) return next.handle()

    const start = Date.now()
    const req = context.switchToHttp().getRequest<AuthedRequest>()

    return next.handle().pipe(
      tap({
        next: () => {
          const res = context.switchToHttp().getResponse<Response>()
          this.writeLog(req, meta, start, res.statusCode)
        },
        error: (error: unknown) => {
          this.writeLog(req, meta, start, statusFromError(error))
        },
      }),
    )
  }

  private writeLog(
    req: AuthedRequest,
    meta: LogMeta,
    start: number,
    status: number,
  ) {
    void this.logsService.record({
      userId: req.user?.id ?? 0,
      username: req.user?.username ?? 'anonymous',
      module: meta.module,
      action: meta.action,
      method: req.method,
      path: req.path,
      params: serializeRedacted(req.body),
      ip: req.ip ?? '',
      status,
      duration: Date.now() - start,
    })
  }
}
```

`tap.error` 不会把异常吃掉，请求该失败还是失败，只是多写一条脱敏日志。

### 4.3 给刷新、登出、改密补 `@Log`

文件：`admin-service/src/auth/auth.controller.ts`

建议：

```ts
@Post('refresh')
@Log('系统', '刷新令牌')

@Post('logout')
@Log('系统', '登出')

@Put('password')
@Log('系统', '修改密码')
```

登录已有 `@Log('系统', '登录')`，不要重复加。

### 4.4 单测

Admin Service 用 Jest，测试文件放在 `src` 下、以 `.spec.ts` 结尾。

**必须有：** `admin-service/src/common/utils/redact.spec.ts`

把下面整份存成该文件即可，对上 4.1 的 `redact.ts`。

```ts
import { redact, serializeRedacted } from './redact'

describe('redact', () => {
  it('replaces password', () => {
    expect(redact({ password: 'x' })).toEqual({ password: '[REDACTED]' })
  })

  it('replaces newPassword', () => {
    expect(redact({ newPassword: 'x' })).toEqual({ newPassword: '[REDACTED]' })
  })

  it('replaces new_password', () => {
    expect(redact({ new_password: 'x' })).toEqual({ new_password: '[REDACTED]' })
  })

  it('replaces refresh_token', () => {
    expect(redact({ refresh_token: 'abc' })).toEqual({ refresh_token: '[REDACTED]' })
  })

  it('redacts nested secrets and keeps sibling fields', () => {
    expect(redact({ user: { password: 'x', name: 'a' } })).toEqual({
      user: { password: '[REDACTED]', name: 'a' },
    })
  })

  it('leaves ordinary fields unchanged', () => {
    expect(redact({ username: 'alice', nickname: '阿莉' })).toEqual({
      username: 'alice',
      nickname: '阿莉',
    })
  })

  it('does not throw on circular objects', () => {
    const body: Record<string, unknown> = { username: 'alice' }
    body.self = body
    expect(redact(body)).toEqual({
      username: 'alice',
      self: '[CIRCULAR]',
    })
  })
})

describe('serializeRedacted', () => {
  it('does not include the plaintext password', () => {
    const params = serializeRedacted({
      username: 'alice',
      password: 'Admin@123',
    })
    expect(params).toContain('[REDACTED]')
    expect(params).not.toContain('Admin@123')
  })
})
```

各条在测什么：

| 用例 | 期望 |
| --- | --- |
| `{ password: 'x' }` | `password` 为 `[REDACTED]` |
| `{ newPassword: 'x' }` | 同上 |
| `{ new_password: 'x' }` | 同上 |
| `{ refresh_token: 'abc' }` | 同上 |
| `{ user: { password: 'x', name: 'a' } }` | 嵌套 `password` 脱敏，`name` 仍为 `a` |
| `{ username: 'alice', nickname: '阿莉' }` | 两个字段都不动 |
| 对象自己引用自己 | 不抛错，出现 `[CIRCULAR]` |
| `serializeRedacted` 之后的字符串 | 不含明文 `Admin@123` 这类密码 |

**建议有：** `admin-service/src/common/interceptors/operation-log.interceptor.spec.ts`。mock `LogsService.record`，模拟带 `@Log` 的 handler 和 `req.body.password`，断言传给 `record` 的 `params` 不含明文密码。可直接用下面这份：

```ts
import { ExecutionContext, UnauthorizedException } from '@nestjs/common'
import { Reflector } from '@nestjs/core'
import { lastValueFrom, of, throwError } from 'rxjs'
import { OperationLogInterceptor } from './operation-log.interceptor'
import { LogsService } from '../../logs/logs.service'
import { LOG_METADATA } from '../decorators/log.decorator'

describe('OperationLogInterceptor', () => {
  const logsService = { record: jest.fn().mockResolvedValue(undefined) }
  const reflector = { get: jest.fn() }
  let interceptor: OperationLogInterceptor

  beforeEach(() => {
    jest.clearAllMocks()
    interceptor = new OperationLogInterceptor(
      reflector as unknown as Reflector,
      logsService as unknown as LogsService,
    )
  })

  function contextWith(body: unknown, statusCode = 200): ExecutionContext {
    return {
      getHandler: () => ({}),
      switchToHttp: () => ({
        getRequest: () => ({
          method: 'POST',
          path: '/auth/login',
          body,
          ip: '127.0.0.1',
          user: { id: 1, username: 'alice' },
        }),
        getResponse: () => ({ statusCode }),
      }),
    } as unknown as ExecutionContext
  }

  it('does not log when the handler has no @Log metadata', async () => {
    reflector.get.mockReturnValue(undefined)
    await lastValueFrom(interceptor.intercept(contextWith({ password: 'x' }), { handle: () => of(null) }))
    expect(logsService.record).not.toHaveBeenCalled()
  })

  it('redacts the password before recording a successful login', async () => {
    reflector.get.mockReturnValue({ module: '系统', action: '登录' })
    await lastValueFrom(
      interceptor.intercept(
        contextWith({ username: 'alice', password: 'Admin@123' }),
        { handle: () => of({ access_token: 't' }) },
      ),
    )
    expect(logsService.record).toHaveBeenCalledTimes(1)
    const params = logsService.record.mock.calls[0][0].params as string
    expect(params).toContain('[REDACTED]')
    expect(params).not.toContain('Admin@123')
    expect(logsService.record.mock.calls[0][0].status).toBe(200)
  })

  it('still records one redacted row when the handler fails', async () => {
    reflector.get.mockReturnValue({ module: '系统', action: '登录' })
    await expect(
      lastValueFrom(
        interceptor.intercept(
          contextWith({ username: 'alice', password: 'wrong' }),
          { handle: () => throwError(() => new UnauthorizedException()) },
        ),
      ),
    ).rejects.toBeInstanceOf(UnauthorizedException)
    expect(logsService.record).toHaveBeenCalledTimes(1)
    const recorded = logsService.record.mock.calls[0][0]
    expect(recorded.params).not.toContain('wrong')
    expect(recorded.status).toBe(401)
  })

  it('looks up metadata with the log decorator key', async () => {
    reflector.get.mockReturnValue(undefined)
    await lastValueFrom(interceptor.intercept(contextWith({}), { handle: () => of(null) }))
    expect(reflector.get).toHaveBeenCalledWith(LOG_METADATA, expect.anything())
  })
})
```

在 `admin-service` 目录执行：

```bash
npm test
```

现有 `auth.service.spec.ts` / `users.service.spec.ts` 应继续通过。

---

## 5. 改完之后日志长什么样

登录成功，库里应类似：

```json
{"username":"alice","password":"[REDACTED]"}
```

创建用户：

```json
{"username":"bob","password":"[REDACTED]","nickname":"鲍勃"}
```

改密：

```json
{"currentPassword":"[REDACTED]","newPassword":"[REDACTED]"}
```

刷新：

```json
{"refresh_token":"[REDACTED]"}
```

管理前端操作日志页应能看到谁做了什么，但看不到密码和 token。

---

## 6. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| 改 `docs/authentication-unification/` 正文 | 那是 Keycloak 大方案，不是这次 |
| 清空全部操作日志（现有 `DELETE /logs`） | 会毁掉审计记录；不是脱敏 |
| 修 RB-02 提权、seed 默认密码、Keycloak 开发用户 | 下一个分支 |
| 提交 Typora 日志 | 编辑器垃圾文件 |
| 为了这件事去改 `package-lock.json` | 不需要新依赖 |
| 连生产库删历史 `params`、轮换已泄漏密码 | 运维动作；代码合入后另做 |

历史数据：若本地/测试库方便，可以用 SQL 只读看一眼：

```sql
SELECT id, username, path, params, created_at
FROM sys_operation_log
WHERE params ILIKE '%password%'
   OR params ILIKE '%token%'
LIMIT 50;
```

看到明文再决定要不要人工清理。不要把「删光表」写进这个 PR。

---

## 7. 本地怎么确认

1. `cd admin-service && npm test` 全绿。
2. 启动 Admin Service，用已知密码登录一次。
3. 打开管理前端操作日志，或查库：`params` 里是 `[REDACTED]`，不是那串密码。
4. 再创建一个带密码的用户，同样检查。

---

## 8. 和旧编号的对应（不用再翻那些文件）

| 旧编号 | 在说什么 |
| --- | --- |
| AS-01 | 就是本文这个漏洞，原文在 `03-node-and-admin-service.md` |
| RB-01 | 发布阻断表里的同一条，在 `01-release-blockers.md` |
| AUTH-001 | 认证方案里的同一条，还多了「查历史、轮换」——轮换不在本 PR |
| 完成矩阵第 59 行 | 「没有修 Admin 日志秘密」——本 PR 合入后可以勾掉「新写入」；历史清理仍未做 |

合入后如果要改完成矩阵，只改 `10-completion-matrix.md` 里「没有修 Admin 日志秘密」那一行，注明新写入已脱敏、历史未扫。不要改认证方案 00–08 的正文。
