# RB-09 / FE-02 / AUTH-004（logout 撤销）实施说明

适用分支：`fix/admin-logout-revoke`  
只修 **Admin 前端登出竞态**：先捕获 access/refresh，立刻清本地，再用**不读 store 的专用请求**带 Authorization 撤销服务端 refresh。  
**不要**改半登录回滚、Cookie 协议、refresh `jti`、用过期 access 也能撤销（refresh-authenticated logout）、h2、版本号。

旧编号：RB-09、FE-02；认证清单 AUTH-004 里「注销撤销竞态」这一截。上一刀是 RB-08 refresh 挂起（`#166`）和 `v1.2.2-rc.2`。

---

## 1. 一句话

`logout()` 把 `logoutApi(refreshToken)` 丢进共享 Axios client 后立刻清 store、跳登录页。请求拦截器执行时 store 已经空了，`POST /auth/logout` 没有 `Authorization`。服务端 `JwtAuthGuard` 直接 401，refresh row 还在，旧 refresh 仍能续期。

本分支：捕获 token → 清本地 → 裸 axios 带着捕获到的 Bearer 和 body 去撤 → 无论成败再跳登录。

---

## 2. 现在怎么坏的

```text
logout()
  发出 client.post(/auth/logout)     ← 异步，还没带 header
  set(tokens = null)
  location = /login

request interceptor
  读 store.accessToken               ← 已经是 null
  不设 Authorization

JwtAuthGuard                         ← 401
refresh row 仍在
```

安全验收看 **旧 refresh 不能再用**，不看页面是不是到了 `/login`。

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| 登录写 token 后 `/auth/me` 失败的半登录回滚 | AUTH-004 后半段，独立 PR |
| access 过期时用 refresh 撤销 | 要改服务端契约，不是这一刀 |
| refresh 改 HttpOnly Cookie | `v1.3.0` |
| refresh `jti` / 并发消费（RB-10） | 别的 PR |
| 升 h2 / 打生产 `v1.2.2` | 别的事 |

撤销失败只 `console.warn`，不弹窗、不把 token 写进日志。请求 5 秒超时，避免网络挂死时永远不跳登录。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `admin-frontend/src/api/sessionRevoke.ts` | `revokeAdminSession`（裸 axios）+ `endAdminSession` |
| `admin-frontend/src/api/sessionRevoke.test.ts` | 清 store 之后仍带捕获 token；失败也跳转；无 refresh 不发请求 |
| `admin-frontend/src/store/auth.ts` | logout 走 `endAdminSession` |
| `admin-frontend/src/api/auth.ts` | 删掉吞错的共享 client `logout` |
| `admin-service/src/auth/auth.service.spec.ts` | logout 后同一 refresh 再 refresh → `令牌已撤销` |
| 本文 + 审计索引 | 挂链接 |

服务端 `AuthService.logout` 本来就会按 hash 删 row，本刀不改 Java/Nest 行为，只补「删完不能再用」的回归。

---

## 5. 怎么确认

```bash
cd admin-frontend && npm test
cd admin-service && npm test -- auth.service.spec.ts
```

- 清本地之后，logout 请求仍带捕获的 `Authorization` 和 `refresh_token`
- 撤销失败也会 `redirectToLogin`
- 没有 refresh token 时不 POST
- `logout` 之后 `refresh(旧 token)` 抛 `令牌已撤销`，且查找的 hash 与删除的 hash 相同

人工：登录管理台 → 登出 → 用刚拿到的 refresh 调 `POST /admin-api/auth/refresh` 必须 401。

---

## 6. 再下一刀

AUTH-004 剩下的半登录回滚（login 已 `setTokens` 但 `/auth/me` 失败）。然后是 RB-10（refresh `jti` / 并发消费）。`h2`、PDF 限额、redirect URI 仍各自独立。
