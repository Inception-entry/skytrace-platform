# AS-04 去掉公开默认管理员：实施说明

适用分支：`fix/admin-seed-password`  
只改 Admin Service 的 seed、密码校验、`.env.example` 注释，以及仓库里怎么告诉别人第一次怎么建账号。读完这一份就可以动手，不必先把审计全集和认证方案读完。

旧文档把同一件事拆成了 RB-03、AS-04、AUTH-002、路线图 Wave 1A 第 4 条。那些是编号索引；**真正要改的代码和验收以本文为准。**

上一刀是操作日志脱敏（`fix/admin-op-log-redaction` / AS-01）。那个 PR 合入或至少提交之后，再从当时的 `main`（或已合入 AS-01 的基线）拉本分支。不要把 seed 改动塞进脱敏那个 PR。

```bash
# AS-01 已经在当前基线上之后：
git checkout -b fix/admin-seed-password
```

---

## 1. 一句话

`admin-service/prisma/seed.ts` 里写死了超级管理员 `admin / Admin@123`，跑完 seed 还会把这串密码打印到终端。任何人只要读过仓库或旧日志，就能用最高权限登录。

这个分支要做到：

1. 仓库和镜像里 **不再出现** 这组固定凭据。
2. 第一次建 `admin` 时，密码必须来自环境变量 `ADMIN_INITIAL_PASSWORD`，并且够强、不能是已知默认值。
3. seed **绝不打印密码**。
4. 已经存在的 `admin`：**不要改密码**（避免把别人已经轮换过的密码改回去）。
5. seed 失败必须 **非 0 退出**。现在 `.catch(console.error)` 会把失败吞掉，进程照样成功。

**改 seed 不会动已经跑过的数据库。** 你本机 Docker 里如果已经能用 `Admin@123` 登录，合入本分支之后旧库仍然是那组密码，必须按第 7 节自己轮换。生产同理，是运维动作，不要写成自动 SQL 提交进这个 PR。

---

## 2. 现在密码是怎么进库的

```text
开发者或 CI 执行：
  cd admin-service && npm run prisma:seed
        │
        ▼
prisma/seed.ts
  菜单 upsert（无密码，这部分保留）
  角色 super_admin upsert
  把全部菜单绑到 super_admin
  user upsert username=admin
    create: bcrypt.hash('Admin@123')
    update: {}          ← 再跑 seed 也不会改掉已经种进去的旧密码
  打印：Default credentials: admin / Admin@123
        │
        ▼
PostgreSQL skytrace_admin.sys_user
  任何人用 admin / Admin@123 登录管理后台 :8889
```

Docker **不会**在容器启动时自动 seed。`admin-service` 的 Dockerfile 只做 `prisma migrate deploy` 再启动 Nest。所以：

- 默认密码只在有人 **显式跑 seed** 时出现。
- 生产镜像里 **没有 ts-node**（它是 `devDependency`），不能指望在瘦镜像里 `npm run prisma:seed`。本地/CI 在 `admin-service` 目录、带完整 `node_modules` 的环境跑。

相关代码现在是这样：

| 文件 | 做什么 |
| --- | --- |
| `admin-service/prisma/seed.ts` | 种菜单、角色、`admin / Admin@123`，并打印密码 |
| `admin-service/package.json` 的 `prisma:seed` | `ts-node prisma/seed.ts` |
| `admin-service/Dockerfile` | **不**跑 seed |
| `deploy/docker-compose.yml` 的 `admin-service` | **没有** `ADMIN_INITIAL_PASSWORD`；本分支也 **不要** 加进去 |

当前漏洞就这几行（行号随文件可能略有偏移，以内容为准）：

```ts
const admin = await prisma.user.upsert({
  where: { username: 'admin' },
  update: {},
  create: {
    username: 'admin',
    password: await bcrypt.hash('Admin@123', 10),
    nickname: '管理员',
    email: 'admin@example.com',
  },
})
// ...
console.log('Seed complete. Default credentials: admin / Admin@123')
```

以及文件末尾：

```ts
main()
  .catch(console.error)
  .finally(() => prisma.$disconnect())
```

`console.error` 之后没有 `process.exitCode = 1`，Prisma 失败时 CI/脚本仍会当成成功。

---

## 3. 设计约束（不要自作主张改这些）

| 要遵守 | 原因 |
| --- | --- |
| 引导用户名仍然叫 `admin` | 改名会让所有已有文档和本地习惯一起断；本分支只去掉 **公开密码** |
| 菜单 / `super_admin` / `role_menu` 的 upsert 逻辑保留 | 这是合法的结构 seed；缺菜单（例如「操作日志」）要靠再跑 seed 补，不是靠写死密码 |
| 已存在的 `admin` 不更新 `password` | 否则下次 seed 会用环境变量覆盖已经轮换过的密码 |
| **不要** 把 `ADMIN_INITIAL_PASSWORD` 写进 `docker-compose.yml` | 长期跑着的容器 `docker inspect` 就能看到引导密码；API 进程也不需要这个变量 |
| **不要** 在 `.env.example` 里填写真实密码 | example 会被复制进仓库和截图；只放注释 |
| **不要** 加 `mustChangePassword` 列 | 那要 Prisma migration、登录拦截、管理前端跳转改密，是另一条 PR |
| **不要** 改 Keycloak / `auth-users` | 那是业务端 `:8888`，和 Admin `:8889` 不是同一套账号 |
| **不要** 改 `auth.service.spec.ts`、脱敏测试里的 `'Admin@123'` | 那是单元测试夹具，不是 seed 默认账号 |

新建用户以后仍走 `POST /users`，密码由管理员在界面上设。本分支 **只** 管 seed 引导账号。

---

## 4. 要改哪些文件（按这个顺序贴）

### 4.1 新建：`admin-service/src/common/utils/bootstrap-password.ts`

校验逻辑放在 `src/` 下，这样现有 Jest（`rootDir: src`）能直接测。`seed.ts` 会 import 它。

整文件如下，覆盖保存：

```ts
export const BOOTSTRAP_PASSWORD_MIN_LENGTH = 16

const KNOWN_DEFAULT_PASSWORDS = new Set(
  [
    'Admin@123',
    'admin',
    'admin123',
    'admin123456',
    'password',
    'Password123',
    '12345678',
    'change-me',
    'changeme',
  ].map((value) => value.toLowerCase()),
)

/**
 * 首次创建引导管理员时使用。
 * 不要把传入的密码写进异常消息或日志。
 */
export function assertBootstrapPassword(
  raw: string | undefined,
  username = 'admin',
): string {
  if (raw === undefined || raw.length === 0) {
    throw new Error(
      'ADMIN_INITIAL_PASSWORD is required when creating the bootstrap admin. Use a unique password of at least 16 characters. Published defaults are not allowed.',
    )
  }
  if (raw !== raw.trim()) {
    throw new Error('ADMIN_INITIAL_PASSWORD must not start or end with whitespace')
  }
  if ([...raw].length < BOOTSTRAP_PASSWORD_MIN_LENGTH) {
    throw new Error(
      `ADMIN_INITIAL_PASSWORD must be at least ${BOOTSTRAP_PASSWORD_MIN_LENGTH} characters`,
    )
  }
  if (KNOWN_DEFAULT_PASSWORDS.has(raw.toLowerCase())) {
    throw new Error('ADMIN_INITIAL_PASSWORD matches a known published default and is not allowed')
  }
  if (raw.toLowerCase() === username.toLowerCase()) {
    throw new Error('ADMIN_INITIAL_PASSWORD must not equal the bootstrap username')
  }
  return raw
}
```

`[...raw].length` 按 Unicode 码点计长度，避免把一个 emoji 当成很多字节却仍然很短。

### 4.2 新建：`admin-service/src/common/utils/bootstrap-password.spec.ts`

```ts
import { readFileSync } from 'fs'
import { join } from 'path'
import {
  BOOTSTRAP_PASSWORD_MIN_LENGTH,
  assertBootstrapPassword,
} from './bootstrap-password'

describe('assertBootstrapPassword', () => {
  const strong = 'local-dev-only-ok!!'

  it('returns the password when it is strong and not a known default', () => {
    expect(assertBootstrapPassword(strong)).toBe(strong)
  })

  it('rejects missing values', () => {
    expect(() => assertBootstrapPassword(undefined)).toThrow(/required/)
    expect(() => assertBootstrapPassword('')).toThrow(/required/)
  })

  it('rejects published default Admin@123', () => {
    expect(() => assertBootstrapPassword('Admin@123')).toThrow(/known published default/)
  })

  it('rejects other denylisted defaults case-insensitively', () => {
    expect(() => assertBootstrapPassword('PASSWORD')).toThrow(/known published default/)
    expect(() => assertBootstrapPassword('change-me')).toThrow(/known published default/)
  })

  it('rejects short passwords', () => {
    expect(() => assertBootstrapPassword('short-password')).toThrow(
      new RegExp(String(BOOTSTRAP_PASSWORD_MIN_LENGTH)),
    )
  })

  it('rejects surrounding whitespace', () => {
    expect(() => assertBootstrapPassword(`  ${strong}  `)).toThrow(/whitespace/)
  })

  it('rejects password equal to username', () => {
    expect(() => assertBootstrapPassword('administrator-ok!', 'administrator-ok!')).toThrow(
      /username/,
    )
  })

  it('does not echo the secret in the error message', () => {
    expect(() => assertBootstrapPassword('Admin@123')).toThrow()
    try {
      assertBootstrapPassword('Admin@123')
    } catch (error) {
      expect((error as Error).message).not.toContain('Admin@123')
    }
  })
})

describe('prisma/seed.ts source', () => {
  it('does not hardcode the published default password', () => {
    const seed = readFileSync(join(__dirname, '../../../prisma/seed.ts'), 'utf8')
    expect(seed).not.toMatch(/Admin@123/)
  })
})
```

最后这条测的是 **seed 源码** 不再写死 `Admin@123`。拒绝名单可以留在 `bootstrap-password.ts` 里，那是为了挡住有人把旧密码填进环境变量。

### 4.3 整文件替换：`admin-service/prisma/seed.ts`

菜单数组和 upsert **原样保留**，只改：import、创建用户、日志、失败退出。`tsconfig.json` 的 `rootDir` 是 `src`，seed 在 `prisma/` 下，所以 `prisma:seed` 要加 `--transpile-only`（见 4.4），否则 ts-node 可能抱怨 rootDir。

```ts
import { PrismaClient } from '@prisma/client'
import * as bcrypt from 'bcryptjs'
import { assertBootstrapPassword } from '../src/common/utils/bootstrap-password'

const prisma = new PrismaClient()

const menus = [
  { name: '系统管理', code: 'system', type: 1, sort: 1, icon: 'SettingOutlined' },
  { name: '用户管理', code: 'user:list', path: '/admin/users', component: 'UserList', type: 2, sort: 1, icon: 'UserOutlined', parentCode: 'system' },
  { name: '新增用户', code: 'user:create', type: 3, sort: 1, parentCode: 'user:list' },
  { name: '编辑用户', code: 'user:update', type: 3, sort: 2, parentCode: 'user:list' },
  { name: '删除用户', code: 'user:delete', type: 3, sort: 3, parentCode: 'user:list' },
  { name: '分配角色', code: 'user:assign-roles', type: 3, sort: 4, parentCode: 'user:list' },
  { name: '角色管理', code: 'role:list', path: '/admin/roles', component: 'RoleList', type: 2, sort: 2, icon: 'TeamOutlined', parentCode: 'system' },
  { name: '新增角色', code: 'role:create', type: 3, sort: 1, parentCode: 'role:list' },
  { name: '编辑角色', code: 'role:update', type: 3, sort: 2, parentCode: 'role:list' },
  { name: '删除角色', code: 'role:delete', type: 3, sort: 3, parentCode: 'role:list' },
  { name: '分配权限', code: 'role:assign-menus', type: 3, sort: 4, parentCode: 'role:list' },
  { name: '菜单管理', code: 'menu:list', path: '/admin/menus', component: 'MenuList', type: 2, sort: 3, icon: 'MenuOutlined', parentCode: 'system' },
  { name: '新增菜单', code: 'menu:create', type: 3, sort: 1, parentCode: 'menu:list' },
  { name: '编辑菜单', code: 'menu:update', type: 3, sort: 2, parentCode: 'menu:list' },
  { name: '删除菜单', code: 'menu:delete', type: 3, sort: 3, parentCode: 'menu:list' },
  { name: '操作日志', code: 'log:list', path: '/admin/logs', component: 'LogList', type: 2, sort: 4, icon: 'FileSearchOutlined', parentCode: 'system' },
  { name: '清空日志', code: 'log:clear', type: 3, sort: 1, parentCode: 'log:list' },
]

async function main() {
  console.log('Seeding database...')

  const parents = menus.filter(m => !m.parentCode)
  const children = menus.filter(m => m.parentCode)

  for (const m of parents) {
    await prisma.menu.upsert({
      where: { code: m.code },
      update: {
        name: m.name,
        path: m.path ?? null,
        component: m.component ?? null,
        icon: m.icon ?? null,
        type: m.type,
        sort: m.sort,
      },
      create: { name: m.name, code: m.code, path: m.path ?? null, component: m.component ?? null, icon: m.icon ?? null, type: m.type, sort: m.sort },
    })
  }

  for (const m of children) {
    const parent = await prisma.menu.findUniqueOrThrow({ where: { code: m.parentCode! } })
    await prisma.menu.upsert({
      where: { code: m.code },
      update: {
        name: m.name,
        path: m.path ?? null,
        component: m.component ?? null,
        icon: m.icon ?? null,
        type: m.type,
        sort: m.sort,
        parentId: parent.id,
      },
      create: { name: m.name, code: m.code, path: m.path ?? null, component: m.component ?? null, icon: m.icon ?? null, type: m.type, sort: m.sort, parentId: parent.id },
    })
  }

  const role = await prisma.role.upsert({
    where: { code: 'super_admin' },
    update: {},
    create: { name: '超级管理员', code: 'super_admin', description: '拥有全部权限' },
  })

  const allMenus = await prisma.menu.findMany()
  for (const menu of allMenus) {
    await prisma.roleMenu.upsert({
      where: { roleId_menuId: { roleId: role.id, menuId: menu.id } },
      update: {},
      create: { roleId: role.id, menuId: menu.id },
    })
  }

  const existingAdmin = await prisma.user.findUnique({ where: { username: 'admin' } })
  const admin = existingAdmin ?? await prisma.user.create({
    data: {
      username: 'admin',
      password: await bcrypt.hash(assertBootstrapPassword(process.env.ADMIN_INITIAL_PASSWORD), 10),
      nickname: '管理员',
      email: 'admin@example.com',
    },
  })

  await prisma.userRole.upsert({
    where: { userId_roleId: { userId: admin.id, roleId: role.id } },
    update: {},
    create: { userId: admin.id, roleId: role.id },
  })

  if (existingAdmin) {
    console.log("Seed complete. User 'admin' already existed; password was not changed.")
  } else {
    console.log("Seed complete. Created user 'admin'. Password was not printed.")
  }
}

main()
  .catch((err) => {
    console.error(err)
    process.exitCode = 1
  })
  .finally(async () => {
    await prisma.$disconnect()
  })
```

要点：

- **只有创建新 `admin` 时** 才读 `ADMIN_INITIAL_PASSWORD`。库里已经有 `admin` 时，只补菜单/角色，不要求环境变量，也不会改密码。
- 日志两句都不能出现密码明文。
- `process.exitCode = 1` 放在 `catch` 里，让 `finally` 仍能断开 Prisma，然后进程以失败码结束。

### 4.4 改 `admin-service/package.json`

只动 `prisma:seed` 这一行，并在文件里 **增加** Prisma 的 seed 配置（和 scripts 同级，不要塞进 `scripts` 里）。

`scripts` 里把：

```json
    "prisma:seed": "ts-node prisma/seed.ts",
```

改成：

```json
    "prisma:seed": "ts-node --transpile-only prisma/seed.ts",
```

在 `devDependencies` 那个对象 **后面**（`overrides` 前面）加上：

```json
  "prisma": {
    "seed": "ts-node --transpile-only prisma/seed.ts"
  },
```

这样 `npm run prisma:seed` 和 `npx prisma db seed` 走同一条命令。`--transpile-only` 让 seed 可以 import `src/` 而不跟 `rootDir` 打架。

不要为这件事改 `package-lock.json` 的依赖版本。

### 4.5 改 `deploy/.env.example`

在已有的 `ADMIN_JWT_REFRESH_SECRET=...` **下面**加注释，**不要**写成带默认值的赋值：

```bash
# 仅在首次执行 admin-service 的 prisma:seed、且库里还没有 admin 用户时需要。
# 至少 16 个字符；禁止 Admin@123 以及 bootstrap-password.ts 拒绝名单里的值。
# 不要写进 docker-compose 长期环境，也不要提交真实值。
# ADMIN_INITIAL_PASSWORD=
```

### 4.6 改仓库根目录 `README.md`

找到「Docker 部署时，管理页面和管理 API 会随完整 Compose 环境一起启动。」那一段（大约在管理后台说明附近），在确认 JWT/PostgreSQL/MinIO 的那句后面加：

```text
管理后台（:8889）的引导账号用户名为 `admin`。仓库不再提供默认密码。
第一次空库需要在 `admin-service` 目录执行：

  DATABASE_URL=postgresql://admin_user:<postgres密码>@127.0.0.1:5433/skytrace_admin \
  ADMIN_INITIAL_PASSWORD='<至少16位且非已知默认的密码>' \
  npm run prisma:seed

密码不会打印到终端。已经有 `admin` 的库再跑 seed 只同步菜单/角色，不会改密码。
```

把 `<postgres密码>` 理解成你本机 `deploy/.env` 里的 `POSTGRES_PASSWORD`，不要把 example 里的示例密码再抄进 README 当「官方默认」。

---

## 5. 本地怎么跑 seed

Postgres 映射在宿主机 `5433`（容器内仍是 `5432`）。在 **仓库根目录以外、`admin-service` 目录内** 执行：

```bash
cd admin-service
npm install   # 若还没有 node_modules

DATABASE_URL=postgresql://admin_user:你的Postgres密码@127.0.0.1:5433/skytrace_admin \
ADMIN_INITIAL_PASSWORD='local-dev-only-ok!!' \
npm run prisma:seed
```

`local-dev-only-ok!!` 只是长度和拒绝名单都过关的 **例子**。你自己换一串，不要用 `Admin@123`。

预期：

| 情况 | 终端 | 退出码 | 库里的密码 |
| --- | --- | --- | --- |
| 空库，变量合法 | `Created user 'admin'. Password was not printed.` | 0 | 你设的那串的 bcrypt |
| 空库，没设变量 | `ADMIN_INITIAL_PASSWORD is required...` | 非 0 | 没有 admin 行 |
| 空库，变量是 `Admin@123` | `known published default` | 非 0 | 没有 admin 行 |
| 已有 admin | `already existed; password was not changed.` | 0 | **还是旧哈希** |

用错误密码验证拒绝名单（应失败）：

```bash
DATABASE_URL=postgresql://admin_user:你的Postgres密码@127.0.0.1:5433/skytrace_admin \
ADMIN_INITIAL_PASSWORD='Admin@123' \
npm run prisma:seed
echo $?    # 非 0
```

这条请在 **临时空库** 或确认不会误伤现有数据时再试。已有 `admin` 时根本不会读这个变量。

---

## 6. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| 改 `docs/authentication-unification/` 正文 | Keycloak 大方案，不是这次 |
| 给 `User` 加 `mustChangePassword` | 要 migration + 登录/前端，下一波认证加固再做 |
| 把 seed 塞进 Dockerfile `CMD` | 每次启动都依赖引导密码，密钥会长期留在容器环境里 |
| 在 `docker-compose.yml` 增加 `ADMIN_INITIAL_PASSWORD` | 同上；API 不需要它 |
| 修 RB-02 / AS-02 提权 | 下一个代码分支，见文末 |
| 修 RB-04 Keycloak 三个开发用户 | 另一套身份，AUTH-007 |
| 提交 Typora 日志、真实 `.env` | 编辑器垃圾 / 秘密 |
| 写一个「自动 UPDATE 生产 admin 密码」的 SQL 进仓库 | 生产轮换必须人工、可审计；哈希不能进 Git |
| 为了这件事升级 Nest / Prisma 大版本 | 与 seed 无关 |

---

## 7. 已经有库时怎么处理（本 PR 的代码不会替你做）

改 seed **之后**，旧数据里的 `admin / Admin@123` 仍然有效。这是预期行为。

### 7.1 先看库里有没有这个用户

```bash
docker exec -it skytrace-postgres \
  psql -U admin_user -d skytrace_admin \
  -c "SELECT id, username, status, created_at FROM sys_user WHERE username = 'admin';"
```

有行：说明曾经 seed 过。没有行：按第 5 节做一次带环境变量的 seed。

### 7.2 本机能登录时（推荐）

用当前密码登录 `http://localhost:8889`，打开改密接口对应的页面（`PUT /admin-api/auth/password`），改成新密码。改密会清掉该用户的 refresh token。

### 7.3 本机登不进去、只能改哈希时

在 `admin-service` 目录生成哈希（把新密码换成你的，**不要**把输出的哈希贴进 Git 或聊天记录长期保存）：

```bash
node -e "require('bcryptjs').hash(process.argv[1], 10).then(console.log)" '你的新密码至少十六位'
```

然后（把哈希整段替换进去）：

```sql
UPDATE sys_user
SET password = '这里粘贴bcrypt哈希'
WHERE username = 'admin';
```

再删掉该用户的 refresh，避免旧会话续期：

```sql
DELETE FROM sys_refresh_token
WHERE user_id = (SELECT id FROM sys_user WHERE username = 'admin');
```

### 7.4 生产

有权限的人做：确认没有第二个仍用 `Admin@123` 的账号；轮换后记事件。审计文档不授权本 PR 自动删用户。

---

## 8. 怎么确认改对了

1. `cd admin-service && npm test`  
   新增的 `bootstrap-password.spec.ts` 必须全绿；原有测试保持绿。
2. `rg 'Admin@123' admin-service/prisma/seed.ts` 无结果。
3. `rg 'Default credentials' admin-service` 无结果。
4. 空库或新 schema 上跑第 5 节：不设变量失败；设 `Admin@123` 失败；设合法密码成功且终端 **没有** 密码。
5. 用新密码能登录 `:8889`；用 `Admin@123` 不能登录（仅针对这次新建的库）。
6. **不要** 用「再跑一次 seed」来验证旧库密码变了——旧库本来就不会变。

管理前端和 Nest 进程都 **不必** 为这次改动 rebuild，除非你只是为了自己重新 seed。seed 是一次性 CLI，不是跑在 `node dist/main.js` 里的。

---

## 9. 和旧编号的对应（不用再翻那些文件）

| 旧编号 | 在说什么 |
| --- | --- |
| AS-04 | 就是本文，原文草稿在 `03-node-and-admin-service.md` |
| RB-03 | 发布阻断表里的同一条，在 `01-release-blockers.md` |
| AUTH-002 | 认证方案 Wave 0 第 2 项；「生产盘点为零」是运维，不在本 PR 的 diff 里 |
| Wave 1A 第 4 条 | 路线图把 RBAC 写在 seed 前面；认证清单把 seed 排在第 2、RBAC 第 3。本分支按 **AUTH-002**，因为体积是 M、和 AS-01 一样可独立验收。RBAC 下一刀再做 |

合入后如果要改完成矩阵，只动 `10-completion-matrix.md` 里「没有移除 seed/Keycloak 默认身份」：改成 seed 固定密码已移除、Keycloak 开发用户未动。不要改认证方案 00–08 正文。

---

## 10. 再下一刀（本分支不要开做）

分支名：`fix/admin-rbac-super-invariants`  
内容：RB-02 / AS-02 / AUTH-003（非 super 提权、改 super 边界）。AS-03 的最后一名 super 并发保护可以跟那一刀一起，也可以再拆。等本分支合入后再写那份实施说明。
