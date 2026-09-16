# RB-08 / FE-01 / AUTH-004（refresh 挂起）实施说明

适用分支：`fix/admin-refresh-hang`  
只修 **Admin 前端 401 刷新状态机**：无 refresh token、刷新失败、并发 401 都必须 settle，不能把后续请求挂死。  
**不要**改 logout 等待服务端撤销（RB-09 / FE-02）、半登录回滚、Cookie 协议、h2、Keycloak、版本号。

旧编号：RB-08、FE-01；认证清单 AUTH-004 里「无 refresh token 永久队列 / 并发刷新」这一截。上一刀是生产 Keycloak 拆分（`#164`）和 `v1.2.2-rc.1`（`#165`）。

---

## 1. 一句话

`admin-frontend/src/api/client.ts` 在 401 时先把 `isRefreshing=true`，没有 refresh token 就 `return`，走不到 `finally`。队列里的 Promise 永不 settle，页面一直 loading。

本分支改成和业务前端 Keycloak 一样的 **唯一 refresh Promise**：所有 401 等同一个 `refreshOnce()`，成功重放、失败/无 token 立刻 reject，`finally` 清掉 in-flight。

---

## 2. 现在怎么坏的

```text
请求 A 401
  isRefreshing = true
  没有 refresh token
  logout + 跳转
  return          ← 不进 finally，isRefreshing 一直 true

请求 B 401
  看到 isRefreshing
  进 failedQueue
  永远等不到 flushQueue
```

审计门禁：10 个并发 401 只刷新一次；无 token / 刷新失败时全部及时 reject。

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| 等服务端 logout 撤销再清 store（RB-09） | FE-02，独立 PR |
| 登录成功写 token 后 `/auth/me` 失败的半登录回滚 | AUTH-004 后半段，跟挂起不是同一出口 |
| refresh 改 HttpOnly Cookie | `v1.3.0` 认证统一 |
| 升 h2 / Admin npm advisory | RB-16，别的 PR |
| 打生产 `v1.2.2` tag | RC 已发；本刀只关 refresh 挂起 |

`/auth/login|refresh|logout` 的 401 **不走刷新**，避免 logout 自己的 401 再进状态机。这是让状态机封闭的护栏，不是 RB-09 的“先捕获 token 再专用请求撤销”。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `admin-frontend/src/api/client.ts` | 去掉布尔量 + 手工队列；`attachAdminAuthInterceptors` + `refreshOnce()` |
| `admin-frontend/src/api/client.test.ts` | 无 token、刷新失败、10 并发、重放仍 401、handshake 不刷新 |
| `admin-frontend/package.json` + lock | 增加 `vitest` 和 `npm test` |
| `.github/workflows/ci.yml` | Admin frontend job 跑 `npm test` |
| 本文 + 审计索引 | 挂链接 |

默认 client 仍 `baseURL: '/admin-api'`；刷新继续用 **裸 axios** POST `/admin-api/auth/refresh`，不经过自己的拦截器。过期时仍调用现有 `logout()`（内部会跳 `/login`），拦截器不再自己写第二次 `window.location`。

---

## 5. 怎么确认

```bash
cd admin-frontend && npm test
cd admin-frontend && npm run build
```

- 无 refresh token：两次 401 都 reject，不调用 refresh
- 刷新失败：并发 401 都拿到同一个错误，不 pending
- 10 个并发 401：`refreshTokens` 只调用一次，全部重放成功
- 重放仍 401：只刷新一次，不死循环
- `/auth/login|refresh|logout` 的 401 不触发 refresh

---

## 6. 再下一刀

RB-09 / FE-02 已单独实施，见 [rb-09-admin-logout-revoke.md](rb-09-admin-logout-revoke.md)。下一刀是半登录回滚。`h2`、PDF 限额、redirect URI 仍各自独立。
