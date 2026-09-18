# AS-06（登录失败路径对齐）实施说明

适用分支：`fix/admin-login-enumeration`  
只修 **Admin 登录用户枚举/时序差异**：用户不存在、已禁用、密码错误都走 bcrypt，对外同一失败。  
**不要**做 `LoginDto` 前置校验、密码最短长度、`tokenVersion`、Redis 限流、family 复用、issuer/audience、Cookie、h2、版本号。

旧编号：AS-06（本刀只覆盖失败路径对齐）。上一刀是登录/刷新限流（`#171`）和 `v1.2.2-rc.7`。

---

## 1. 一句话

不存在的用户直接 `return null`，禁用账号先抛「账号已被禁用」，都不跑 bcrypt。攻击者可以靠报文和耗时判断用户名是否存在、账号是否被关。

本分支对缺失/禁用账号用同一份 dummy bcrypt hash 再 `compare`，失败一律交给 LocalStrategy 返回「用户名或密码错误」。

---

## 2. 现在怎么坏的

```text
用户名不存在     → 立刻 null，无 bcrypt
账号 status!=1   → 401「账号已被禁用」，无 bcrypt
密码错误         → bcrypt 后再 null，「用户名或密码错误」
```

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| LoginDto 在 passport 前校验 | guard 先于 ValidationPipe，要另写前置 guard |
| 密码最短长度 / bcrypt 72 字节 | 独立契约，别和枚举混 |
| tokenVersion / 改密后立刻作废 access | Schema/JWT 声明 |
| Redis 限流、family、issuer/audience、Cookie、h2、打生产 `v1.2.2` | 别的事 |

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `admin-service/src/auth/dummy-password-hash.ts` | 固定 dummy bcrypt（明文已丢弃） |
| `admin-service/src/auth/auth.service.ts` | 缺失/禁用走 dummy `compare`，失败返回 null |
| `admin-service/src/auth/auth.service.spec.ts` | 不存在、禁用正确密码、错误密码都是 null 且跑 bcrypt |
| `admin-service/src/auth/strategies/local.strategy.spec.ts` | 对外同一 401 文案 |
| 本文 + 审计索引 | 挂链接 |

---

## 5. 怎么确认

```bash
cd admin-service && npm test -- src/auth/auth.service.spec.ts src/auth/strategies/local.strategy.spec.ts
```

- 用户不存在：`compare` 打在 dummy hash 上，返回 null
- 禁用账号即使用对密码：仍 null，且 `compare` 打 dummy，不打真实 hash
- 正确凭据仍能登录
- LocalStrategy 失败文案只有「用户名或密码错误」

负向（需本地打接口）：对不存在用户、禁用用户、错误密码，`POST /admin-api/auth/login` 都是 401，body 都是「用户名或密码错误」，不能再出现「账号已被禁用」。

---

## 6. 再下一刀

AS-09 / RB-12 Admin 头像 magic-byte 已单独实施，见 [as-09-admin-avatar-magic.md](as-09-admin-avatar-magic.md)。下一刀是 JWKS `kid` 冷却。family 复用、`h2`、AI/Java 上传仍各自独立。

合入 `main` 后若要再发候选，打 **`v1.2.2-rc.8`**（不要打在本分支上，不要打生产 `v1.2.2`）。
