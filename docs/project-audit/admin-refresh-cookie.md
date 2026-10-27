# Admin refresh Cookie 双协议

官方管理前端已切换为 Cookie 会话；旧客户端的 JSON body 协议继续保留。

官方前端登录携带 `X-Skytrace-Session: cookie`，只接收 `access_token` 和 `expires_in`。
刷新令牌通过 HttpOnly Cookie `skytrace_admin_refresh` 保存，路径 `/admin-api`。
Cookie 刷新请求必须带 `X-Skytrace-CSRF: 1`，body 为空，响应不会回传 `refresh_token`；
不带会话模式头也不能把 Cookie 换成 JS 可读的刷新令牌。登录和刷新响应均设置 `Cache-Control: no-store`。

旧客户端不带会话模式头登录时，JSON 仍包含 `refresh_token`；通过 body 中的
`refresh_token` 刷新时，JSON 响应和旧协议一致，也不要求 CSRF 头。
原 `/admin-api/auth/logout` 保留原来的 Bearer + body 协议。

官方前端使用 `/admin-api/auth/session/logout`，以刷新 Cookie 和 CSRF 头撤销会话并清除 Cookie，
无需有效的 access token。刷新返回 401 时清除失效 Cookie；暂时的服务故障不会清除它。

前端的 access token 只存内存，启动时删除旧 `skytrace-admin-auth` localStorage 记录，
不影响主题等其他偏好。进入受保护页面时，先通过 Cookie 恢复，再读取 `/auth/me`；
Cookie 失效才回登录页，网络或服务故障显示可重试状态。登录页不会自动恢复刚退出的会话。

同一页面的恢复和 API 刷新共用一个请求；支持 Web Locks 的浏览器会跨标签页串行执行
登录、刷新和登出。显式登录/登出通过 BroadcastChannel 通知其他标签页清除内存会话，
通知中不包含令牌。登出等待当前刷新完成后再撤销 Cookie，晚到的响应不能恢复已结束的会话。

`NODE_ENV=production` 时 Cookie 带 `Secure`。本地要关掉时设 `ADMIN_REFRESH_COOKIE_SECURE=false`。

部署顺序：先升级管理 API，再升级管理前端。没有可用 Cookie 的旧会话需要重新登录一次。
这次不迁移到 Keycloak，也不改变 PostgreSQL RBAC 或数据库结构。

验证：管理服务的 `auth-cookie-session.spec.ts` 覆盖 HTTP 协议、Cookie/CSRF 和旧 body 客户端；
管理前端单测覆盖恢复、并发刷新、登出竞争、存储清理和跨标签页通知。

Access token 仍通过 `Authorization: Bearer …` 发送。登录期间若收到其他标签页的会话变更通知，
晚到的登录或用户信息响应也不会重新激活旧会话，失败回滚不会撤销已变更的共享会话。
