# Admin refresh Cookie 双协议

适用分支：`feat/admin-refresh-cookie`

登录和刷新仍在 JSON 里返回 `refresh_token`。同时写 HttpOnly Cookie `skytrace_admin_refresh`，路径 `/admin-api`。旧客户端继续把令牌放在 body 里，不需要 CSRF 头。

只用 Cookie、body 里没有令牌时，请求必须带 `X-Skytrace-CSRF: 1`。登出会清掉 Cookie。body 和 Cookie 不是同一枚令牌时，两枚都撤销。

`NODE_ENV=production` 时 Cookie 带 `Secure`。本地要关掉时设 `ADMIN_REFRESH_COOKIE_SECURE=false`。

管理台前端这一刀不改，仍然提交 body。Access token 仍走 `Authorization`。
