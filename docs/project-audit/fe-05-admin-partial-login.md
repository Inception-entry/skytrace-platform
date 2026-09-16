# FE-05 / AUTH-004（半登录回滚）实施说明

适用分支：`fix/admin-partial-login-rollback`  
只修 **Admin 登录半成功**：`/auth/login` 已经拿到 token，随后 `/auth/me` 失败时不得把 token 写进 store，并撤销刚签发的 refresh。顺手给登录按钮加 submitting 锁，避免双击打出两套 session。  
**不要**改 refresh `jti`（RB-10）、Cookie、h2、版本号。

旧编号：FE-05 半登录；认证清单 AUTH-004 最后一截。上一刀是 RB-09 登出撤销（`#167`）和 `v1.2.2-rc.3`。

---

## 1. 一句话

`Login.tsx` 先 `setTokens` 再调 `/me`。`/me` 失败时 catch 一律提示「用户名或密码错误」，localStorage 里 token 还在，页面停在「有会话、没有用户」。

本分支把 login + me 当成客户端事务：两边都成功才 commit；`/me` 失败用捕获到的 token 走已有的 `revokeAdminSession`，不写 store。

---

## 2. 现在怎么坏的

```text
POST /auth/login     → 200，拿到 access/refresh
setTokens(...)       → persist 进 localStorage
GET /auth/me         → 5xx / 网络失败
catch                → 「用户名或密码错误」
store 仍有 token
刷新页面             → AdminLayout 当成已登录
```

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| refresh `jti` / 并发消费（RB-10） | 别的 PR |
| access 过期时用 refresh 撤销 | 服务端契约，不是这一刀 |
| Cookie 协议 | `v1.3.0` |
| 升 h2 / 打生产 `v1.2.2` | 别的事 |

`/me` 用裸 axios 带捕获的 Bearer，不经过 store，这样 commit 之前拦截器读不到半套 token。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `admin-frontend/src/api/sessionEstablish.ts` | `establishAdminSession` + `loadAdminProfile`；区分登录拒绝和资料失败 |
| `admin-frontend/src/api/sessionEstablish.test.ts` | login 失败不 rollback；`/me` 失败 rollback 且不 commit；双成功才 commit |
| `admin-frontend/src/pages/Login.tsx` | 走事务；submitting 锁；错误文案分开 |
| 本文 + 审计索引 | 挂链接 |

复用 RB-09 的 `revokeAdminSession`，不新造 logout 通道。

---

## 5. 怎么确认

```bash
cd admin-frontend && npm test
cd admin-frontend && npm run build
```

- login 401：不调 `/me`、不 commit、不 revoke
- login 成功 /me 失败：rollback 带捕获 token，store 未 commit
- 两者成功：commit 一次，不 rollback
- 界面：失败时不是一律「用户名或密码错误」；提交中按钮 disabled

人工：在 `/me` 故障时登录，localStorage `skytrace-admin-auth` 必须没有 token；刚签发的 refresh 再 refresh 必须 401。

---

## 6. 再下一刀

RB-10：refresh token 加随机 `jti`，并发消费只成功一次。`h2`、PDF 限额、redirect URI 仍各自独立。

合入 `main` 后若要再发候选，打 **`v1.2.2-rc.4`**（不要打在本分支上，不要打生产 `v1.2.2`）。
