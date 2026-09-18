# AS-06（登录/刷新限流）实施说明

适用分支：`fix/admin-auth-rate-limit`  
只修 **Admin Service 登录、刷新的进程内滑动窗口限流**：同一 IP（登录再加同一用户名）超过阈值返回 `429` 和 `Retry-After`，挡在 bcrypt / JWT 校验前面。  
**不要**做 Redis 分布式限流、`LoginDto` 绑定、用户枚举对齐、`tokenVersion`、family 复用、issuer/audience、Cookie、h2、版本号。

旧编号：AS-06（本刀只覆盖 rate limit 这一条）。上一刀是 JWT secret fail-fast（`#170`）和 `v1.2.2-rc.6`。

---

## 1. 一句话

`POST /admin-api/auth/login` 和 `POST /admin-api/auth/refresh` 没有次数限制。弱口令可以无限试，过期 refresh 也可以打满 CPU。

本分支加进程内滑动窗口：登录每 IP / 每用户名 1 分钟 5 次；刷新每 IP 1 分钟 20 次。

---

## 2. 现在怎么坏的

```text
同一 IP 对 login 打成千上万次     → 每次都 bcrypt
多 IP 喷同一个用户名               → 没有按账号封顶
refresh 无上限                     → 过期/伪造 JWT 也能打满校验
```

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| Redis / 跨副本共享计数 | admin-service 当前没接 Redis；多副本要另开一刀 |
| 信任 `X-Forwarded-For` | 没开 `trust proxy`，伪造头会绕过 IP 桶 |
| LoginDto / dummy hash / 改密 tokenVersion | 仍是 AS-06 其余条目，独立 PR |
| family 复用、issuer/audience、Cookie、h2、打生产 `v1.2.2` | 别的事 |

单副本 Compose 下进程内计数够用；水平扩展前必须换成共享存储。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `admin-service/src/auth/auth-rate-limit.ts` | 滑动窗口和 key |
| `admin-service/src/auth/auth-rate-limiter.ts` | 登录双桶（IP + 用户名）、刷新单桶 |
| `admin-service/src/auth/guards/auth-rate-limit.guard.ts` | 超限 429 + `Retry-After` |
| `admin-service/src/auth/auth.controller.ts` | login/refresh 在认证前挂 guard |
| `admin-service/src/auth/auth.module.ts` | 注册 limiter / guard |
| 对应 `*.spec.ts` + 本文 + 审计索引 | 单测和挂链接 |

---

## 5. 怎么确认

```bash
cd admin-service && npm test -- src/auth/auth-rate-limit.spec.ts src/auth/guards/auth-rate-limit.guard.spec.ts src/auth/auth.service.spec.ts
```

- 同一 IP 第 6 次登录 429
- 同一用户名从不同 IP 喷满后 429
- 刷新超限不影响登录计数
- 窗口滑过后重新放行
- 未标 `@AuthRateLimit` 的路由不限流

负向（需本地打接口）：1 分钟内对 `/admin-api/auth/login` 连发 6 次错误密码，第 6 次 `429`，响应头有 `Retry-After`。

---

## 6. 再下一刀

AS-06 登录失败路径对齐已单独实施，见 [as-06-login-enumeration.md](as-06-login-enumeration.md)。下一刀是 LoginDto 前置校验。family 复用、`h2`、PDF 限额仍各自独立。

合入 `main` 后若要再发候选，打 **`v1.2.2-rc.7`**（不要打在本分支上，不要打生产 `v1.2.2`）。
