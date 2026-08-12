# SkyTrace Frontend（天巡智控 Web 端）

SkyTrace 平台的业务前端：基于 Vue 3 + TypeScript + Vite 构建，使用 Cesium 承载三维地图，
通过 Socket.IO 接收实时告警与遥测，通过 Keycloak 完成单点登录与角色鉴权。

本目录只包含前端；后端服务、部署编排与整体架构见仓库根目录的 [README.md](../README.md)
与 [docs/architecture.md](../docs/architecture.md)。

## 功能概览

- **三维地图（`/map`）**：Cesium 场景、设备图标与实时航迹。订阅 `device.telemetry` 事件后，
  无人机模型随遥测移动，并绘制最近一段飞行轨迹线。
- **任务管理（`/drone`）**：巡检任务列表与状态流转，行内展示航线缩略图（SVG），
  可打开「飞行回放」面板按历史遥测点回放整段航迹。
- **航线管理（`/routes`）**：航点表格编辑与地图取点编辑双向联动，支持拖拽调整航点位置。
- **设备管理（`/devices`）**：设备台账与在线状态。
- **智能问答（`/chat`）**：接入 AI 服务的对话入口，仅 `ADMIN`/`OPERATOR` 角色可见。
- **知识库（`/knowledge`）**：文档上传与检索。
- **取证中心（`/evidence`）**：告警证据链查看与导出。
- **审计（`/audit`）**：操作审计日志，仅 `ADMIN` 角色可见。
- **国际化与主题**：i18next 驱动的中英文切换；支持跟随系统深浅色，也可手动切换主题色。

## 技术栈

| 领域 | 选型 |
| --- | --- |
| 框架 | Vue 3.5 (`<script setup>`) + TypeScript 5.8 |
| 构建 | Vite 6 + `vite-plugin-cesium` + `vite-plugin-compression` |
| UI | Ant Design Vue 4 + Sass |
| 地图 | Cesium 1.133 + `cesium-navigation-es6` |
| 状态 | Pinia 3 |
| 路由 | Vue Router 4（`meta.roles` 做角色守卫） |
| 国际化 | i18next + `i18next-vue` + 浏览器语言探测 |
| 鉴权 | `keycloak-js` 26 |
| 实时 | Socket.IO 客户端（`src/realtime/socket.ts`） |

## 快速开始

环境要求：Node.js >= 22.11.0、npm >= 10.9.0。

```bash
npm install                  # postinstall 会自动执行 patch-package
cp .env.example .env.local   # 按需修改 Keycloak 地址
npm run dev                  # http://localhost:8888，默认自动打开浏览器
```

开发服务器已在 `vite.config.ts` 中配置代理，无需额外跨域处理：

- `/api` → `http://localhost:8082`（backend-node BFF）
- `/socket.io` → `ws://localhost:8082`（Socket.IO，已开启 `ws: true`）

因此本地开发前需要先把 backend-node 及其依赖起起来，最简单的方式是在仓库根目录执行
`./scripts/skytrace.sh start`（详见根 README）。

### 可用脚本

| 命令 | 说明 |
| --- | --- |
| `npm run dev` | 启动开发服务器 |
| `npm run build` | `vue-tsc -b` 类型检查后产出 `dist/` |
| `npm run preview` | 预览构建产物 |
| `npm run lint` / `npm run lint:fix` | ESLint 检查 / 自动修复 |
| `npm test` | 运行 `test/*.test.js`（Node 内置测试运行器） |

## 配置

前端支持两种配置来源，运行时配置优先于构建时环境变量：

1. **构建时**：`.env.local` 中的 `VITE_KEYCLOAK_URL`、`VITE_KEYCLOAK_REALM`、
   `VITE_KEYCLOAK_CLIENT_ID`（见 `.env.example`）。
2. **运行时**：由 Nginx 注入的 `window.__SKYTRACE_CONFIG__`，字段为
   `keycloakUrl` / `keycloakRealm` / `keycloakClientId`。这样同一份镜像可以部署到不同环境
   而无需重新构建，解析逻辑见 `src/auth/keycloak.ts`。

容器化相关文件：`Dockerfile`、`nginx.conf`、`nginx.https.conf.example`。

## 项目结构

```
frontend/
├── src/
│   ├── @types/                  # Cesium / Vue / window 的类型补充声明
│   ├── api/                     # 按域划分的 HTTP 封装
│   │   ├── http.ts              # 基于 fetch 的请求层：注入 token、401 自动续期、403 跳转
│   │   ├── inspection-task.ts   # 任务 CRUD 与历史遥测轨迹
│   │   ├── route.ts             # 航线与航点
│   │   ├── device.ts / admin.ts / knowledge.ts / evidence.ts / alarm-evidence.ts
│   ├── assets/model/            # 无人机 glTF 模型等静态资源
│   ├── auth/                    # Keycloak 初始化与按角色的导航过滤
│   ├── components/
│   │   ├── route-thumbnail/     # 航线 SVG 缩略图（任务列表内联展示）
│   │   ├── route-waypoint-editor/  # 地图取点式航点编辑器
│   │   ├── telemetry-replay/    # 历史遥测回放面板（播放/暂停/进度）
│   │   ├── st-cesium-vue/       # Cesium 容器组件
│   │   ├── st-menu-aside/ st-tool-header/ st-auth-toolbar/ st-overlay/
│   │   └── st-global-register/  # 全局组件注册
│   ├── libs/
│   │   ├── cesium/              # Cesium 封装：底图、实体、实时遥测轨迹绘制
│   │   └── route/waypoints.ts   # 航点 JSON 的解析与序列化（有单测覆盖）
│   ├── locales/                 # zh.js / en.js 文案
│   ├── realtime/socket.ts       # Socket.IO 连接与 alarm/telemetry 订阅
│   ├── router/index.ts          # 路由表与 meta.roles 守卫
│   ├── store/modules/           # Pinia：lang / layout / theme
│   ├── style/                   # reset、变量、主题 token
│   ├── theme/                   # 主题配置与注册表
│   ├── utils/                   # 语言、布局、主题工具函数
│   └── views/                   # 页面级组件（与路由一一对应）
├── test/                        # Node test runner 单测
├── Dockerfile / nginx.conf      # 生产镜像与静态服务配置
└── vite.config.ts
```

## 测试

单元测试使用 Node 内置的 `node:test`，不依赖额外测试框架：

```bash
npm test
```

覆盖范围包括航点解析（`test/waypoints.test.js`）、关键路径与取证中心流程。
端到端测试统一放在仓库根目录的 `e2e/`，由 Playwright 驱动，说明见
[docs/testing.md](../docs/testing.md)。

## 开发约定

- 组件目录使用 kebab-case，页面组件使用 PascalCase 并以 `View` 结尾（`Home.vue` 除外）。
- 路径别名 `@` 指向 `src/`，在 `vite.config.ts` 与 `tsconfig.app.json` 中均已配置。
- 新增页面需要限制角色时，在路由的 `meta.roles` 中声明，守卫会自动跳转 `/403`。
- 新增文案必须同时补 `src/locales/zh.js` 和 `en.js`，避免键缺失。
- 提交信息遵循 Conventional Commits（`feat` / `fix` / `docs` / `refactor` / `test` / `chore` 等）。

## 许可证

[MIT](LICENSE)

> 注意：不要使用 1.81.0 - 1.82.1 版本的 Cesium，该区间存在已知
> [缺陷](https://github.com/CesiumGS/cesium/issues/9590)。
