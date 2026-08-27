# 04. 业务前端与管理前端审计

实施状态：**审计建议仍未实施；审计后仅新增中文注释、文档字符串和必要的 EOF 换行，未改动有效逻辑、配置或命令。**

## 1. 验证结果

| 模块 | lint | build | tests | 生产依赖审计 |
| --- | --- | --- | --- | --- |
| Vue 业务前端 | 通过 | 通过；主 JS 约 691.88 kB，超过 500 kB 告警 | 4 个源代码契约测试文件通过 | 0 advisory |
| React 管理前端 | 通过；package type 告警 | 通过；主 JS 约 1,389.03 kB | `package.json` 没有 test script | 2 moderate advisory |

构建产物大并不自动等于用户一定慢，但当前同步路由导入和 Cesium/Ant Design 体积说明已经有明确拆包空间。

## 2. Admin 前端高优先问题

### FE-01 / P1：刷新状态机永久死锁

证据：

- `admin-frontend/src/api/client.ts:33-39`：刷新中的后续 401 进入队列。
- `:46-48`：先设 `_retry` 和 `isRefreshing=true`。
- `:50-54`：没有 refresh token 时提前 return。
- `:73-76`：复位状态的 `finally` 因提前 return 不会执行。

第一次“401 且无 refresh token”后，所有后续 401 都进入永不 settle 的 Promise，页面持续 loading。

最低兼容修复：

```ts
if (!refreshToken) {
  isRefreshing = false
  rejectQueue(new AuthExpiredError())
  clearAuthAndRedirect()
  return Promise.reject(error)
}
```

更稳的结构是不维护布尔量和手工队列，而是共享唯一 refresh Promise：

```ts
let refreshPromise: Promise<string> | null = null

function refreshOnce(): Promise<string> {
  refreshPromise ??= requestRefreshToken()
    .then(({ accessToken }) => accessToken)
    .finally(() => { refreshPromise = null })
  return refreshPromise
}
```

每个失败请求 `await refreshOnce()` 后自行重放；所有出口自然 settle。测试无 token、刷新失败、10 个并发 401、重放再次 401 和组件卸载。

### FE-02 / P1：登出竞态导致服务端 refresh session 仍有效

- `admin-frontend/src/store/auth.ts:25-29` 发出 `logoutApi` 后立即清 token/跳转，没有等待。
- `src/api/client.ts:19-22` 到异步 interceptor 执行时才读取 access token。
- `src/api/auth.ts:13-14` 使用共享 client 且吞掉所有错误。
- 服务端 logout 要求 `JwtAuthGuard`。

因此 interceptor 可能看到已经清空的 store，登出请求没有 Authorization；用户以为退出，refresh session 实际仍在。

短期建议先捕获 token，并使用不依赖 store 的专用请求：

```ts
async function logout(): Promise<void> {
  const { accessToken, refreshToken } = useAuthStore.getState()
  clearLocalAuthImmediately()
  try {
    await revokeSession({ accessToken, refreshToken })
  } catch (error) {
    reportServerRevocationFailure(error)
  } finally {
    navigateToLogin()
  }
}
```

安全验收必须从服务端验证旧 refresh token 不能再用，而不是只看页面跳到了登录页。

### FE-03 / P1：access/refresh token 都持久化到 localStorage

`admin-frontend/src/store/auth.ts:17-35` 的 Zustand persist 保存两种 token；`admin-frontend/nginx.conf:2-29` 又没有 CSP。任意同源 XSS/依赖污染可读取长期 refresh token。

长期认证协议建议：

- refresh token：`HttpOnly; Secure; SameSite=Lax/Strict` Cookie。
- access token：内存保存、短 TTL。
- refresh/logout：校验 Origin/CSRF，CORS 仅允许管理站点。
- token rotation：服务端原子消费、复用检测、token family 撤销。
- CSP 是防御纵深，不是替代输出编码和依赖安全。

改成 `sessionStorage` 只能降低跨浏览器重启的持久性，不能防 XSS 读取。

### FE-04 / P1：Admin Nginx 上传上限与后端冲突，安全头缺失

- `admin-frontend/nginx.conf:2-29` 没有 `client_max_body_size`，Nginx 默认约 1 MiB。
- `admin-service/src/upload/upload.controller.ts:12-17` 接受 2 MiB 头像。
- 同一 Nginx 文件缺 server token 隐藏、nosniff、framing、referrer、permissions、CSP 和显式代理超时。

建议配置方向：

```nginx
server_tokens off;
client_max_body_size 3m;

add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "DENY" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Permissions-Policy "camera=(), microphone=(), geolocation=()" always;
add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'" always;

proxy_connect_timeout 5s;
proxy_read_timeout 30s;
proxy_send_timeout 30s;
proxy_set_header X-Forwarded-Proto $scheme;
```

CSP 必须在真实构建上 report-only 观察后收紧，尤其 Ant Design 样式、图片 blob 和 API/WebSocket connect-src。测试 1.5 MiB 合法头像、>2 MiB 拒绝、413/502 不被 SPA fallback 改写，并用正式镜像 `nginx -t`。

### FE-05 / P1：登录双击与半登录状态

`admin-frontend/src/pages/Login.tsx:12-21` 先持久化 token 再请求 `/me`；`:55-57` 没 submitting 互斥。`/me` 失败会被误报为“用户名或密码错误”，已保存 token 却不清理。

建议把 login + me 当成客户端事务：提交期间禁用；只有二者均成功才 commit store；资料失败回滚全部 token，并区分 401、网络、服务端故障。

### FE-06 / P2：列表请求没有 catch、取消和 latest-wins

涉及：

- `admin-frontend/src/pages/Users.tsx:33-49`
- `Roles.tsx:35-51`
- `Menus.tsx:32-49`
- `Logs.tsx:34-57`

多处 fire-and-forget `loadData` 只有 `finally`；快速筛选时旧请求可以后返回并覆盖新条件；失败形成 unhandled rejection。相同问题还见权限加载、清理日志和 Dashboard 静默错误。

建议共用 hook：

```ts
function useLatestRequest() {
  const sequence = useRef(0)
  const controller = useRef<AbortController>()

  return async <T>(load: (signal: AbortSignal) => Promise<T>, apply: (v: T) => void) => {
    const id = ++sequence.current
    controller.current?.abort()
    controller.current = new AbortController()
    const value = await load(controller.current.signal)
    if (id === sequence.current) apply(value)
  }
}
```

实际实现还要在 unmount abort，并用可见 error/aria-live 呈现失败。

### FE-07 / P2：头像上传绕过共享 client

`admin-frontend/src/api/upload.ts:1-14` 使用原始 axios、自取 token、手工设置 `multipart/form-data`。它绕过 refresh/401 状态机；手工 Content-Type 还可能干扰 boundary。

建议使用共享 client，并让 Axios/浏览器生成 multipart header。测试过期 token 时只 refresh 一次且上传重放成功。

### FE-08 / P2：服务端菜单 path 直接交给 navigate

`admin-frontend/src/utils/menu.ts:4-15` 和 `layouts/AdminLayout.tsx:130-137` 信任菜单 path。前端应只接受规范站内 `/...`，拒绝 `//`、scheme、反斜杠、控制字符和编码绕过。服务端权限数据也要做防御性验证。

## 3. Vue/Cesium 高优先问题

### FE-09 / P1：Cesium Viewer 卸载未 destroy

- `frontend/src/components/st-cesium-vue/index.vue:225-237` 只清全局引用和组件状态，没有 `viewer.destroy()`。
- `:187-189` 直接使用完整 devicePixelRatio。

路由反复进入/离开会泄漏 WebGL context、render loop、事件和 GPU 内存。建议只销毁本组件拥有的实例：

```ts
onBeforeUnmount(() => {
  if (ownedViewer && !ownedViewer.isDestroyed()) {
    ownedViewer.destroy()
  }
  if (document.skyViewer === ownedViewer) document.skyViewer = undefined
  ownedViewer = undefined
})
```

`resolutionScale` 建议上限 2，并提供低性能降级。连续切图 20 次，活动 WebGL context/定时器/监听器不得增长。

### FE-10 / P1：任务轮询重复请求、不可取消、共享 loading 竞态

- `frontend/src/views/DroneView.vue:511-525` 的 `loadTasks()` 同时加载 routes。
- `:718-732` 每 0.5/1 秒轮询，最多约 20 次，因此动作可能额外拉 routes 约 20 次。
- `:759-762` 没有卸载取消。
- 公共 loading 的 `finally` 可能提前解锁按钮。

拆成 `loadTaskStatus` 和 `loadRoutes`；动作有独立 `actionPending`；使用 AbortController/disposed flag 和退避；超时给明确结果。测试卸载、超时、并发动作和请求计数。

### FE-11 / P1：Socket 并发连接可建立多个实例

`frontend/src/realtime/socket.ts:41-65` 只在加载 Socket.IO 脚本前检查全局 socket。两个调用可同时通过检查，加载结束后各自 `io()`，后者覆盖全局而前者泄漏。

建议唯一 `connectionPromise`，或脚本加载后再次 double-check：

```ts
let connectionPromise: Promise<Socket> | null = null

export function connectSocket(): Promise<Socket> {
  connectionPromise ??= createSocket()
    .catch(error => {
      connectionPromise = null
      throw error
    })
  return connectionPromise
}
```

关闭时同时清实例和 Promise。并发两次调用必须只执行一次 `io()`。

### FE-12 / P1：Chat SSE 离页后仍读取，单事件 buffer 无上限

- `frontend/src/views/ChatView.vue:183-188` 使用 animation frame。
- `:222-271` 启动 stream，没有 unmount abort。
- `frontend/src/api/inspection-task.ts:183-244` 不接收 AbortSignal，reader buffer 遇不到事件分隔符时持续增长。

建议每次生成建立 AbortController；API 接收 signal；unmount abort、reader.cancel、cancelAnimationFrame；单个 SSE event 设 1 MiB 等上限。服务端也发 keepalive 和最大事件约束。

### FE-13 / P2：设备轮询会并发重叠并被旧响应覆盖

`frontend/src/views/DeviceView.vue:193-203,300-306` 的静默刷新不设置 loading，timer 只看 loading。请求超过轮询周期时会重叠，旧响应可能覆盖新响应，并清掉用户可见错误。

建议独立 `refreshInFlight`、sequence、AbortController；静默轮询错误和用户动作错误分离。

### FE-14 / P2：Evidence 归档轮询卸载后会“复活”

`frontend/src/views/EvidenceView.vue:761-785` 在请求完成后安排下一 timer；`:1101-1104` 卸载只清理当时已有 timer。卸载时请求仍在飞行，返回后会新建 timer。

卸载设置 `disposed=true` 并 abort；响应后和 schedule 前都检查 disposed/signal。

## 4. API、安全边界与容错

### FE-15：API JSON 解析重复，HTML/204 会丢真实错误

`device.ts:29-40`、`admin.ts:55-61`、`alarm-evidence.ts:50-59`、`inspection-task.ts:73-84`、`route.ts:31-40`、`knowledge.ts:31-40`、`evidence.ts:108-115` 都先 `response.json()` 再看 `response.ok`。

HTML 502、纯文本错误、空 204 会先抛 SyntaxError，HTTP status/requestId 被掩盖。建议集中 helper：

```ts
class ApiRequestError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly requestId?: string,
  ) { super(message) }
}

async function parseResponse<T>(response: Response): Promise<T | undefined> {
  const text = await response.text()
  const contentType = response.headers.get('content-type') ?? ''
  const body = text && contentType.includes('json') ? safeJson(text) : text
  if (!response.ok) throw toApiError(response, body)
  if (response.status === 204 || !text) return undefined
  return validateEnvelope<T>(body)
}
```

### FE-16：`authorizedFetch` 可向任意 URL 附加 Bearer token

`frontend/src/api/http.ts:8-12,47-58` 接受通用 RequestInfo/URL 并无条件附 token。目前调用大多同源，因此是未来误用风险。

在读取 token 前把输入解析为 URL，只允许 `http/https` 且 origin 等于 `window.location.origin`；外部签名 URL 使用无认证 helper。拒绝 `javascript:`、`data:`、跨源和 credential-in-URL。

### FE-17：后端返回的媒体 URL 缺协议白名单

`DroneView.vue:636-648`、`EvidenceView.vue:322-324,592-597,795-804,969,978-984` 将后端 URL 用于 `window.open`/媒体 source。

集中 `resolveSafeMediaUrl`，只允许相对同源或解析后的 `http:`/`https:`；新窗口用 `noopener,noreferrer`。测试大小写、空白、协议相对、javascript/data/file/custom scheme。

### FE-18：Keycloak 恢复链可能产生 unhandled rejection

`frontend/src/api/http.ts:19,29,36`、`auth/keycloak.ts:84,141-154`、`realtime/socket.ts:58-63,152-163` 多处 `void beginAuthenticationRecovery()`；IdP 不可用或导航失败时 Promise rejection 没有最终消费。

提供去重、永不向调用点泄露 rejection 的 `safeAuthenticationRecovery`，内部记录失败并更新可见认证状态，避免页面既不跳转也不给提示。

### FE-19：Keycloak 角色来源和生产配置缺失行为不明确

- `frontend/src/auth/keycloak.ts:48-58` 只读 `realm_access.roles`；如果角色配置在 `resource_access[clientId].roles`，合法用户被误判。
- `:34` 生产漏配置时回退到 `http://localhost:8180`，HTTPS 页面会 mixed-content/错误 IdP。

明确只支持 realm roles，或按 client ID 合并并测试冲突规则。生产缺 Keycloak 配置必须 fail-fast，localhost 默认只存在开发环境。

### FE-20：缺少路由级和应用级恢复

- `frontend/src/router/index.ts:13-79` 无 catch-all 404。
- Admin 登录跳转不保留/校验原路径。
- `admin-frontend/src/App.tsx:17-68` 无 ErrorBoundary。
- `frontend/src/main.ts:48-55` 只处理 bootstrap 失败，没有全局可恢复错误页。

建议 404、React ErrorBoundary、Vue `app.config.errorHandler`、可重试错误页，以及严格站内 return path。

### FE-21：localStorage 异常会破坏 store 初始化

`frontend/src/utils/theme.ts:3-12`、`frontend/src/utils/layout.ts:3-12`、`frontend/src/utils/lang.ts:15-29` 直接访问 localStorage。隐私模式、SecurityError 或 quota error 会抛异常。建议 safe storage adapter + 内存 fallback；删除用 `removeItem`，不要写空字符串。

## 5. 性能与资源

### FE-22：两端路由全部同步导入

- `frontend/src/router/index.ts:1-10,13-79` 同步导入全部页面，包括 Cesium。
- `admin-frontend/src/App.tsx:1-8` 同步导入全部后台页。

Vue 使用 `() => import(...)`；React 使用 `lazy + Suspense`。对 Ant Design、Cesium 和各路由做 manual chunks 时，以真实首屏指标和缓存命中验证，避免只为消除 warning 随意拆碎。

### FE-23：轨迹计算产生高频全量复制

- `frontend/src/components/telemetry-replay/index.vue:118-125,172-178` 每帧重建整条 Cartesian 路径，复杂度接近点数 × 帧数。
- `frontend/src/libs/cesium/cesium-libs.ts:82-85` 每次 render `positions.slice()`。

props 变化时缓存 Cartesian；播放只移动索引/切片；长轨迹做下采样；实时轨迹只在版本变化时更新并考虑 Cesium requestRenderMode。

### FE-24：Ion token 硬编码

`frontend/src/components/st-cesium-vue/index.vue:89-93`。浏览器 token 本来可见，但应环境化、限制资产权限和允许域名、定期轮换并监控额度。不要把“移到环境变量”误写成保密措施。

### FE-25：全局组件注册 glob 疑似空匹配

`frontend/src/components/st-global-register/index.ts:3` 从自身目录匹配 `./components/**/*.vue`，当前结构疑似无该子目录；`frontend/src/main.ts:6,41` 仍调用。用测试输出注册数；若无用途删除，若有用途修正相对路径。

## 6. 可访问性

| 位置 | 问题 | 建议 |
| --- | --- | --- |
| `st-menu-aside/index.vue:1-5` | 可点击 `<label>` 无键盘语义/名称/展开状态 | 改 `<button>`，加 `aria-expanded`/controls |
| `telemetry-replay/index.vue:11-19` | range 无 label/value text；播放按钮无 pressed | 关联 label、`aria-valuetext`、`aria-pressed` |
| `ChatView.vue:44,81-92,106` | 流式回答/错误无 live region | `aria-live` / `role=alert`，避免每 token 过度播报 |
| `EvidenceView.vue:454` | 自制详情侧栏无 dialog、焦点陷阱、Escape 和焦点归还 | 使用成熟 Drawer/Dialog 或完整实现 WAI-ARIA 行为 |
| 多个业务表格 | 无 caption 和显式 `scope=col` | 加可见/视觉隐藏 caption 和 th scope |
| `AdminLayout.tsx:114` | 加载时 return null | skeleton + `role=status` |
| `frontend/index.html:3` | 初始 `lang=en`，应用主要中文 | 首屏设 `zh-CN` 或启动前同步真实语言 |

建议在 Playwright 加 axe，覆盖登录、菜单、地图工具栏、表格、聊天和 Drawer；自动扫描仍不能替代键盘与读屏人工走查。

## 7. 类型、测试和仓库卫生

- `frontend/src/vite-env.d.ts:12-25` 多个浏览器/Cesium扩展为 `any`，还冗余声明 setTimeout。
- `st-cesium-vue/index.vue:134,201` 依赖 Cesium 私有 `_element`，升级容易破坏。
- `admin-frontend/src/types.ts:36-46` 的 FlatMenu 与 DTO/组件概念不完全一致；建议 OpenAPI 生成或共享 schema。
- `admin-frontend/eslint.config.js:20-21` unused/explicit-any 只是 warning；CI 可逐步收紧为 error 并启 type-aware lint。
- Admin 完全没有行为测试。最优先覆盖 refresh/logout、权限路由、菜单 path、登录回滚和请求竞态。
- Vue 当前 `frontend/test/*.test.js` 多为 `readFileSync + assert.match` 的源代码字符串断言；可保护代码形状，但不能证明并发、生命周期和网络行为。引入 Vitest、Vue Testing Library、MSW；保留现有契约测试作补充。
- `frontend/src/assets/model/CesiumDrone.glb:Zone.Identifier` 是 Windows 下载旁车元数据，不是模型资源；建议删除并加入 ignore。

## 8. 当前依赖 advisory

2026-08-24 在线 `npm audit --omit=dev`：

- Vue frontend：0。
- Admin frontend：2 moderate，涉及 `react-router` 和直接依赖 `react-router-dom`，包含 open redirect/XSS 类 advisory；`fixAvailable=true`。
- Admin 全依赖另有一个开发链 high `nanoid`。

升级 `react-router-dom` 前验证 React Router 版本兼容、登录 return path、防开放跳转和全部路由。不能因为 CI 只卡 `--audit-level=high` 就忽略生产 moderate。

## 9. 建议 PR 拆分

1. `fix(admin-web): make token refresh state machine total and tested`
2. `fix(admin-web): reliably revoke logout and rollback partial login`
3. `security(auth): migrate refresh token to hardened cookie contract`
4. `hardening(admin-nginx): align upload size and add security headers`
5. `fix(frontend): destroy Cesium and cancel all polling/SSE resources`
6. `fix(frontend): singleton Socket connection and safe auth recovery`
7. `refactor(web): shared API response/error/safe-URL helpers`
8. `perf(web): lazy routes and cache telemetry geometry`
9. `test(web): add component/MSW/auth concurrency and axe coverage`
10. `chore(deps): remediate React Router advisories`
