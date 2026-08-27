# 03. Node BFF 与 Admin Service 审计

实施状态：**审计建议仍未实施；审计后仅新增中文注释、文档字符串和必要的 EOF 换行，未改动有效逻辑、配置或命令。**

## 1. 总览

| 模块 | 严重/高优先问题 | 中优先问题 | 当前验证 |
| --- | --- | --- | --- |
| Node BFF | 时间契约、`includeDeleted`、JWKS 放大、上传内存、Rabbit/Socket 生命周期、root 容器 | path 参数、代理 IP、HTTP 错误、CORS/配置、DTO 边界 | build/lint/13 tests 通过；生产 npm advisory 0 |
| Admin Service | 明文秘密日志、RBAC 提权、默认管理员、super 并发、refresh token、认证限流/JWT、上传、root/PID1 | DTO 分页、唯一性竞态、健康检查、审计完整性、权限查询 | build/lint/24 tests 通过；生产 npm advisory 7 moderate |

## 2. Node BFF

### BN-01 / P0：告警时间与 Java `LocalDateTime` 不兼容

证据：

- `backend-node/src/alarm/alarm.controller.ts:36-41` 默认 `new Date().toISOString()`。
- 视频路径同类逻辑在 `alarm.controller.ts:48-61`。
- `backend-node/src/alarm/dto/create-alarm.dto.ts:65-67` 只验证为字符串。
- Java 的 `CreateAlarmRequest` 和检测消息边界使用 `LocalDateTime`；容器 JVM 与 MySQL URL 使用 `Asia/Shanghai`。

可能出现两种结果：带 `Z` 的值无法反序列化而返回 400；或者某一层简单去掉 `Z` 后把 UTC 墙钟值当上海本地值，造成 8 小时偏移。

短期兼容方案是：只接受带 offset 的输入，将它转换为上海本地墙钟格式后再发给当前 Java DTO。示例仅供说明：

```ts
import { DateTime } from 'luxon'

function toJavaLocalDateTime(value?: string): string {
  const source = value ?? new Date().toISOString()
  const parsed = DateTime.fromISO(source, { setZone: true })
  if (!parsed.isValid || !parsed.zone.isValid) {
    throw new BadRequestException('eventTime 必须包含 Z 或 UTC offset')
  }
  return parsed
    .setZone('Asia/Shanghai')
    .toFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
}
```

长期方案见 Java 文档：消息和 HTTP contract 使用 `Instant`/`OffsetDateTime`，数据库统一 UTC；上海时区只负责展示。不要用字符串裁剪 `slice(0, -1)`。

必补测试：`Z`、`+08:00`、UTC 跨日、非法无 offset 字符串、Java 真正反序列化、数据库落值和事件编号日期。

### BN-02 / P0：`includeDeleted=false` 被转换为 `true`

证据：`backend-node/src/evidence/dto/search-evidence.dto.ts:77-80` 使用 `@Type(() => Boolean)`。JavaScript 中 `Boolean('false') === true`。

建议改成严格转换：

```ts
function strictBoolean(value: unknown): unknown {
  if (value === true || value === 'true') return true
  if (value === false || value === 'false') return false
  return value
}

@Transform(({ value }) => strictBoolean(value))
@IsOptional()
@IsBoolean()
includeDeleted?: boolean
```

测试必须覆盖 `false`、`true`、空值、`0`、`yes`、重复 query 参数；非法值应 400，而不是静默猜测。

### BN-03 / P1：未知 JWT `kid` 可放大 JWKS 请求

证据：

- `backend-node/src/auth/keycloak-jwt.service.ts:79-103` 读取攻击者控制的 JWT header。
- `:157-169` 每个未知 `kid` 会触发 refresh。
- `:172-191` 发起 JWKS 网络请求。
- `:196-210` 只追加 key，没有原子替换完整 key set。

同时发生的请求虽能合并，但攻击者顺序发送不同 `kid` 仍能持续打 Keycloak。

建议状态机：

```ts
private readonly unknownKidUntil = new Map<string, number>()
private nextRefreshAt = 0

if (this.unknownKidUntil.get(kid)! > Date.now()) {
  throw new UnauthorizedException()
}
if (Date.now() < this.nextRefreshAt) {
  this.unknownKidUntil.set(kid, Date.now() + NEGATIVE_TTL_MS)
  throw new UnauthorizedException()
}

this.nextRefreshAt = Date.now() + GLOBAL_REFRESH_COOLDOWN_MS
const nextKeys = await this.fetchAndValidateBoundedJwks()
this.keys = nextKeys // 验证完整后原子替换
```

还应限制响应字节数和 key 数量；单个坏 JWK 应跳过并告警；真实轮换在 cooldown 后必须能刷新。

### BN-04 / P1：上传只信任 MIME/文件名，并多次复制大 Buffer

证据：

- 图片/视频：`backend-node/src/alarm/alarm.controller.ts:64-133`。
- 证据：`backend-node/src/evidence/evidence.controller.ts:104-124`。
- 知识库：`backend-node/src/knowledge/knowledge.controller.ts:32-48`。
- 转发：`backend-node/src/common/java-client/java-client.service.ts:137-154` 创建 `Uint8Array`、`Blob`，大文件形成额外副本。

建议：

1. 在 BFF 和最终处理服务各自校验 magic bytes；客户端 MIME 只作为提示。
2. 服务端生成规范 MIME、扩展名和安全文件名。
3. multipart 文本字段仍走 DTO：经纬度、置信度、最大帧数、最大告警数、布尔值都应限界。
4. 50 MiB 视频使用流或受控临时文件，不在多层内存结构间复制。
5. 增加全局、用户和路由级并发限制，监控进程 heap/RSS。

### BN-05 / P1：RabbitMQ 与 Socket.IO 生命周期/恢复不完整

证据：

- `backend-node/src/realtime/redis-io.adapter.ts:63-65` 的 `close` 没接收 server，也没有 `super.close(server)`。
- `backend-node/src/main.ts:10-23` 未启用 shutdown hooks。
- `backend-node/src/messaging/alarm-realtime.consumer.ts:32-79` 部分初始化失败不会完整清理，channel close 失败会跳过 connection close，且断线无重连。

建议代码形态：

```ts
async close(server: Server): Promise<void> {
  await Promise.allSettled([
    this.subClient?.quit(),
    this.pubClient?.quit(),
  ])
  await super.close(server)
}

// bootstrap
app.enableShutdownHooks()
```

Rabbit 消费者应使用有抖动、封顶的指数退避；关闭时设置 `stopping=true`、取消 timer；connection/channel 独立 `Promise.allSettled`。测试首次失败、运行中断线、重复 close 和 SIGTERM。

### BN-06 / P2：路径参数归一化可改变下游路由

证据：

- `inspection-task.controller.ts:39-133` 多处直接插值 `taskCode`。
- `device.controller.ts:24-71`、`route.controller.ts:22-52` 主要只检查非空。
- `knowledge.controller.ts:56-63` 对 document ID 只做 `encodeURIComponent`；但 `encodeURIComponent('..')` 仍为 `..`。
- 最终 URL 拼接在 `common/java-client/java-client.service.ts:24-29,163-165,181`。

`new URL('http://host/api/knowledge/documents/..')` 会归一化到父路径。当前 Java 自身鉴权降低了直接越权风险，但仍可造成同源路径/方法混淆。

建议所有资源 code 使用明确 DTO：

```ts
export class ResourceCodeParam {
  @Matches(/^[A-Za-z0-9_-]{1,64}$/)
  code!: string
}

export class DocumentIdParam {
  @Matches(/^[a-f0-9]{64}$/)
  documentId!: string
}
```

Java client 再拒绝 `.`、`..`、斜杠、反斜杠、编码斜杠和非 `/api/` 绝对路径，形成双层边界。

### BN-07 / P2：客户端 IP 和 request ID 可污染审计

`backend-node/src/common/java-client/java-client.service.ts:208-223` 取 `X-Forwarded-For` 最左值，并原样信任 request ID。典型代理会保留攻击者提供的左侧值，因此不能把它当可信源地址。

建议配置固定 trusted proxy hops，使用框架解析后的 `req.ip`；或从右向左按已知代理数解析并用 `net.isIP` 验证。request ID 仅允许例如 `[A-Za-z0-9._:-]{1,128}`，不合法时重新生成。

### BN-08 / P2：下游 HTTP 重定向、错误映射和响应上限欠缺

- `backend-node/src/common/java-client/java-client.module.ts:5-8` 允许 `maxRedirects: 3`。
- `java-client.service.ts:82-90,192-200` 未统一映射网络/流错误。
- 未见统一响应体上限。

内部 API 建议 `maxRedirects: 0`，启动时校验 `JAVA_BASE_URL` 为允许的 HTTP(S) origin；timeout 映射 504，连接/DNS 映射 502，下游结构化 4xx 保留，内部错误细节不返回客户端。

### BN-09 / P2：CORS 和配置没有 fail-fast

- `backend-node/src/main.ts:12` 任意 Origin 且 credentials 开启。
- `backend-node/src/main.ts:19` 端口无校验。
- `realtime/alarm-realtime.gateway.ts:18-26` 在装饰器求值阶段直接读 `process.env`。
- `backend-node/src/app.module.ts:18` 未配置环境 schema。
- Rabbit 有弱本地默认值；Socket.IO 还启用不必要的 `serveClient`。

建议生产 allowlist、按是否使用 Cookie 决定 credentials、`serveClient:false`，并使用 Joi/Zod 在启动时验证 URL、端口、bool 和 production secret。

### BN-10 / P2：接口绕过或弱化 DTO 约束

重点包括：

- `device.controller.ts:32-47`、`route.controller.ts:30-45` 接收 `Record<string, unknown>`。
- `create-alarm.dto.ts:9-67` 缺字符串长度、code 格式、confidence 0–1、经纬度范围和时间格式。
- `batch-review-evidence.dto.ts:12-24` 缺单项长度/格式/去重。
- `batch-tag-evidence.dto.ts:13-23` 已有数组上限，但缺 evidence code 单项长度/格式/去重以及 tag ID 正整数约束。
- `save-inspection-task.dto.ts:27-31` 未验证 `planStart <= planEnd`。

建议用 OpenAPI/JSON Schema 或共享 contract 测试保持 Node 和 Java 约束一致，避免手工重复 DTO 继续漂移。

### BN-11 / P1：运行容器为 root

`backend-node/Dockerfile:21-31` runtime stage 没有 `USER node`。建议确保构建产物只读后切换 `USER node`，Compose 进一步 `read_only:true`、`cap_drop:[ALL]`，需要写的临时目录显式 tmpfs。

## 3. Admin Service

### AS-01 / P0：操作日志保存明文密码/token

证据链：

- 全局拦截器：`admin-service/src/common/interceptors/operation-log.interceptor.ts:23-38`。
- `:42` 执行 `JSON.stringify(req.body).slice(0, 500)`。
- 登录接口也带日志装饰器：`src/auth/auth.controller.ts:15-20`。
- 创建/更新用户：`src/users/users.controller.ts:42-57`。
- 落库和可查询：`src/logs/logs.service.ts:22-27`、`prisma/schema.prisma:91-105`、`src/logs/logs.controller.ts:14-17`。

先序列化再截断不能保护 secret。建议立即分两步：

1. 只读排查历史 `params` 是否包含密码/token；若有，清理历史、识别受影响账号并轮换凭据。
2. 上线递归脱敏器；字段名先小写并去除 `_`/`-` 再匹配，以覆盖 `newPassword`、`new_password`、`Authorization` 等变体。

建议实现形态：

```ts
const SECRET_KEYS = new Set([
  'password', 'currentpassword', 'newpassword',
  'token', 'accesstoken', 'refreshtoken',
  'secret', 'authorization', 'cookie',
])

function redact(value: unknown, depth = 0): unknown {
  if (depth > 5) return '[TRUNCATED]'
  if (Array.isArray(value)) return value.slice(0, 20).map(v => redact(v, depth + 1))
  if (!value || typeof value !== 'object') return value
  return Object.fromEntries(Object.entries(value as Record<string, unknown>).map(([k, v]) => {
    const normalized = k.toLowerCase().replace(/[_-]/g, '')
    return [k, SECRET_KEYS.has(normalized) ? '[REDACTED]' : redact(v, depth + 1)]
  }))
}
```

最终 JSON 仍需长度上限和循环对象保护；不记录 header/cookie；失败操作也应审计，但成功/失败只能写一条。

### AS-02 / P0：非 super 可纵向提权或破坏 super 边界

证据：

- `src/users/users.service.ts:102-114` 非 super 可分配任意非 super 角色，包括给自己，没有检查权限并集是否为操作者权限子集。
- `:123-138` 未阻止非 super 从已有 super 用户移除 super role。
- `:83-111,158-165` 非 super 可禁用/删除 super，只要不是最后一个。
- `src/roles/roles.controller.ts:49-61` 更新/删除角色没有传 actor。
- `src/roles/roles.service.ts:57-96` 普通角色管理员可修改 super role 名称或菜单。

安全规则应写成服务层不变量：

```text
1. 只有 active super 能修改任何 super user 或 super role。
2. 非 super 给目标用户分配后的权限并集，必须是 actor 权限集合的子集。
3. 非 super 不得给自己增加新权限。
4. inactive role 不可分配。
5. controller 传入的 actor 不能作为可信权限结果；service 必须按 actor id 重查。
```

必须补 service 单测和真实 PostgreSQL 集成测试；前端隐藏按钮不能作为授权控制。

### AS-03 / P1：最后一个 super 保护存在并发 TOCTOU

`src/users/users.service.ts:77-86,95-100,116-149` 先 count 后 update/delete，二者不在同一串行化事务/锁域中。两个并发请求都可能看到 count=2，然后同时成功，最终归零。

建议使用 PostgreSQL advisory transaction lock，或 Prisma `Serializable` interactive transaction 并对 P2034 做有限重试：

```ts
await prisma.$transaction(async tx => {
  await tx.$executeRaw`SELECT pg_advisory_xact_lock(734921)`
  const activeSuperCount = await countActiveSupers(tx)
  assertOperationKeepsAtLeastOne(activeSuperCount, target)
  await mutateTarget(tx)
}, { isolationLevel: Prisma.TransactionIsolationLevel.Serializable })
```

这里的固定 advisory key 应有项目级命名约定，避免与其他锁冲突。

### AS-04 / P0：seed 内置公开默认管理员

`admin-service/prisma/seed.ts:79-101` 创建 `admin / Admin@123`、打印密码、`update:{}` 长期保留旧值，并可能吞掉异常让进程成功退出。

建议：

```ts
const initialPassword = process.env.ADMIN_INITIAL_PASSWORD
if (!initialPassword || isKnownDefault(initialPassword) || utf8Length(initialPassword) < 16) {
  throw new Error('ADMIN_INITIAL_PASSWORD is required and must be strong')
}
// 仅当账号不存在时创建；不打印密码；设置 mustChangePassword=true
```

生产更稳妥的做法是单独的一次性 bootstrap job；运行完即撤销 secret。已经部署过的环境必须主动核查并轮换，修改 seed 不会改变现有数据库。

### AS-05 / P1：refresh token 缺随机 `jti`，轮换竞争错误未稳定映射

- `admin-service/src/auth/auth.service.ts:52-63` 的 refresh payload 没有随机 `jti`，数据库已只保存 token hash。
- `admin-service/prisma/schema.prisma:70-78` 的 token 列有 unique。
- `admin-service/src/auth/auth.service.ts:68-100` 确实用 Prisma transaction 原子执行“删旧+建新”；问题不是缺少事务，而是事务外先查询、新 token 可能在同一秒与旧 token 完全相同，且竞争失败可能向上暴露 Prisma P2025/P2002，而不是稳定 401。

同一用户同秒签名相同 payload 可能得到完全相同 token，导致旧 token 在轮换后仍然等价可用，或在登录/并发路径上触发 unique 冲突。

建议 payload 至少有 `jti`、`type:'refresh'`、`familyId`；继续只在数据库保存 token hash；在事务里用条件删除/更新消费旧 token，受影响行数不是 1 就返回 401，并把 P2025/P2002 稳定映射为认证失败或明确冲突。检测复用时撤销整个 token family。

### AS-06 / P1：认证端点与会话生命周期缺口

- 登录/刷新无 rate limit：`src/auth/auth.controller.ts:15-27`。
- 登录 controller 未绑定 `LoginDto`；guard/strategy 在 DTO pipe 前拿到原始 body。
- 用户不存在与 disabled 返回路径不同，存在枚举/时序差异。
- 密码最短只有 6；bcrypt 只处理前 72 UTF-8 bytes。
- 改密撤销 refresh，但已签 access token 最长仍有效约 15 分钟。
- logout 需要未过期 access token；access 过期时无法用 refresh token 撤销会话。

建议：分布式限流；认证前先验证类型/字节长度；不存在用户走 dummy hash；外部统一错误；引入 `tokenVersion`/`passwordChangedAt`；提供 refresh-authenticated revoke。长期把 refresh token 放 `HttpOnly; Secure; SameSite` Cookie，并明确 CSRF/Origin/CORS 策略。

### AS-07 / P1：JWT 配置没有启动期强校验

- `admin-service/src/auth/auth.module.ts:18-29` 只拒绝一个默认 access secret，单字符 secret 仍可能启动。
- refresh secret 到 `auth.service.ts:30-40` 才检查，服务可能健康启动后登录 500。
- `jwt.strategy.ts:18-36` 没明确算法、issuer、audience、token type，也缺 payload 结构约束。

建议 access/refresh secret 各至少 32 个高熵字符、彼此不同；启动 fail-fast；签发和验证均固定算法、issuer、audience 和 `type`。

### AS-08 / P1：管理员重置密码和撤销 refresh 不原子

`src/users/users.service.ts:82-90` 先更新用户，再单独删除 refresh token；删除失败时旧 refresh 仍可续期。应与 `auth.service.ts:133-136` 自助改密一样放入单事务，并用故障注入测试回滚。

### AS-09 / P1：头像上传信任客户端元数据并公开读取

`src/upload/upload.service.ts:51-63` 只看客户端 MIME，又使用原扩展名；`:31-43,46-49,81-95` 允许公开 `GetObject`；初始化异常被吞掉。

建议 magic-byte 白名单、规范扩展名和对象名、空文件/大小二次检查。优先 private bucket + 短期签名 URL；至少返回 `nosniff` 且禁止 inline 活跃内容。bucket 初始化用共享 Promise，正确处理并发 already-exists。

### AS-10 / P2：分页、数组、日期和字符串没有资源边界

证据：`query-user.dto.ts:15-19`、`query-role.dto.ts:15-19`、`query-log.dto.ts:4-10`、`assign-roles.dto.ts:4-8`、`assign-menus.dto.ts:4-8`。

统一建议：`pageSize <= 100`，整数 `Type + IsInt + Min`，日期 `IsISO8601` 且 start <= end，数组 `ArrayMaxSize + ArrayUnique + IsPositive({each:true})`，数据库 varchar 对应字段加 `MaxLength`。

### AS-11 / P2：唯一性预检查有竞态

用户、角色、菜单创建分别在 `users.service.ts:64-70`、`roles.service.ts:48-54`、`menus.service.ts:26-30` 先查后建。并发仍会撞 P2002 并返回 500。

数据库 unique 才是最终权威；建立全局 Prisma exception mapper，把 P2002 稳定映射 409，同时保留友好预检查。

### AS-12 / P1/P2：CORS、优雅退出、root 和 PID 1

- `admin-service/src/main.ts:9-19` 开放 CORS，未启用 shutdown hooks，端口无校验。
- `src/prisma/prisma.service.ts:10-12` 的 disconnect hook 依赖应用正确进入 shutdown。
- `admin-service/Dockerfile:26-39` runtime 为 root；用 `sh -c` 执行 migrate 后启动 Node，PID 1 信号转发不可靠。

建议迁移作为独立 deployment job；若必须 entrypoint，最终 `exec node dist/main.js`。运行用户切到 node，关闭 `x-powered-by`，配置 trusted proxy 和 CORS allowlist。

### AS-13 / P2：失败操作未审计，健康检查是假健康

- operation log 只在成功的 `tap.next` 写日志，失败请求无记录。
- log service 吞掉写日志异常。
- `admin-service/src/health/health.controller.ts:3-8` 永远返回 ok。
- MinIO 初始化异常也被吞掉。
- Dashboard 只有 JWT、没有独立 `dashboard:view` 权限。
- avatar 字段允许任意字符串，可能形成外部跟踪 URL。

建议拆分 liveness/readiness；readiness 检查 DB 和必需依赖；审计失败产生安全 metric/warn；失败请求同样写状态；确认 dashboard 授权；avatar 限制为本服务对象 key 或 HTTPS allowlist。

### AS-14 / P2：每次权限检查重复加载角色关系图

`src/common/permissions/permissions.guard.ts:40-43` 和 `src/common/permissions/permissions.service.ts:10-15,23-41` 重复查询 super/permission graph。建议一次查询完成；如加缓存，用户角色、角色菜单和状态变更必须精确失效。

## 4. 依赖审计结论

### 4.1 Node BFF

- 生产依赖：0 个当前 npm advisory。
- 全依赖：1 个 high，位于 `@typescript-eslint/typescript-estree -> brace-expansion` 开发链。
- 处理：升级锁文件并在 CI 扫描 dev dependencies；它不是线上运行时远程漏洞，但会影响开发/CI 输入处理。

### 4.2 Admin Service

2026-08-24 的 `npm audit --omit=dev` 报告 7 个 moderate 节点，涉及：

- 直接依赖 `@nestjs/common`、`@nestjs/core`、`@nestjs/platform-express`。
- 传递依赖 `file-type`、`body-parser`、`express`、`qs`。

全依赖报告 20 个节点，包括 Nest CLI/dev toolchain 的 4 high。不能直接执行不经审查的 `npm audit fix --force`；应按 Nest 兼容矩阵升级 direct dependencies，重新生成 lock，执行 24 个现有测试、新增安全测试并做完整 E2E。

## 5. 建议 PR 拆分

1. `security(admin): redact operation logs and remediate historical secrets`
2. `security(admin): enforce super and permission-subset invariants`
3. `security(admin): remove default bootstrap credentials`
4. `fix(admin-auth): make refresh rotation unique and atomic`
5. `fix(node): normalize alarm time contract and strict booleans`
6. `security(node): bound JWKS refresh and validate path params`
7. `reliability(node): fix Rabbit/Socket shutdown and reconnect`
8. `security(upload): validate content and bound memory/concurrency`
9. `chore(deps): update Admin runtime and dev dependencies`
10. `hardening(containers): non-root, shutdown, config validation`

每个 PR 都应独立可回滚；不要把日志历史清理、RBAC、时间协议、依赖大升级和 Docker hardening 混成一个无法定位回归的大提交。
