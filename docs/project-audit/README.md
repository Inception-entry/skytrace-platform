# SkyTrace 项目只读审计

审计日期：2026-08-24  
审计基线：`main` / `2c89349`（相对 `v1.2.1` 前进 11 个提交）  
审计方式：静态代码复核、现有测试、编译与 lint、Compose 解析、在线依赖漏洞审计  
实施状态：**审计建议均未实施；审计后全仓只补充中文注释和必要的文件末尾换行，未改有效逻辑、配置值、测试断言或部署命令**

> 本目录中展示的修复代码和 diff 仍是“建议实现”，不是已经落库的功能修改。当前源码的注释变更不代表风险已修复；是否实施、如何拆分 PR，仍需由项目负责人确认。

## 推荐阅读顺序

1. [00-基线与审计方法](00-baseline-and-method.md)：本次检查了什么、跑了什么、哪些未覆盖。
2. [01-发布阻断项](01-release-blockers.md)：上线前必须先处理的问题。
3. [02-Java 后端与 Gateway](02-java-and-gateway.md)：Spring Boot、数据一致性、鉴权、网关问题与建议代码。
4. [03-Node BFF 与 Admin Service](03-node-and-admin-service.md)：Node 转发层和管理后台 API 的问题与建议代码。
5. [04-业务前端与管理前端](04-frontends.md)：Vue、React、Cesium、认证状态机、可访问性和性能问题。
6. [05-AI 服务](05-ai-service.md)：上传、PDF、视频、RabbitMQ、RAG、会话隔离和资源治理。
7. [06-部署、安全与 CI](06-deployment-security-ci.md)：Keycloak、Compose、镜像、发布脚本和供应链。
8. [07-测试与工程质量](07-testing-and-quality.md)：测试结果、缺口、建议测试金字塔和质量门禁。
9. [08-分阶段整改路线图](08-remediation-roadmap.md)：按 PR/阶段拆分，不把所有风险塞进一次大改。
10. [09-版本与发版建议](09-release-recommendation.md)：现在是否能发、应发什么版本、验收和回滚条件。
11. [10-本次完成清单](10-completion-matrix.md)：明确“已审查”和“已修复”的区别。

## 专项方案

- [AS-01 操作日志脱敏实施说明](as-01-operation-log-redaction.md)：第一项代码修复的逐步说明（登录/用户请求不再把明文密码写入操作日志）。
- [AS-04 去掉公开默认管理员实施说明](as-04-bootstrap-credentials.md)：第二项代码修复（seed 不再写死 `admin / Admin@123`，首次建号用 `ADMIN_INITIAL_PASSWORD`）。
- [AS-02 / AS-03 super 不变量实施说明](as-02-rbac-super-invariants.md)：第三项代码修复（非 super 不能提权或改 super 边界；最后一名 super 带事务锁）。
- [BN-02 includeDeleted 严格布尔实施说明](bn-02-include-deleted-boolean.md)：第四项代码修复（证据搜索 `includeDeleted=false` 不再变成 `true`）。
- [BN-01 / AI-02 告警 eventTime 上海兼容实施说明](bn-01-eventtime-shanghai-compat.md)：第五项代码修复（AI/Node 写入前转到上海墙钟；Java 兼容读取 Z/offset）。
- [JV-02 证据 API 上海 DATETIME Instant 实施说明](jv-02-evidence-shanghai-instant.md)：第六项代码修复（证据查询/归档不再把上海墙钟当成 UTC）。
- [RB-07 / AI-01 升级 pypdf 实施说明](ai-01-pypdf-upgrade.md)：第七项代码修复（知识库 PDF 解析升到 `pypdf >= 6.15.0`）。
- [RB-04 / DP-01 生产 Keycloak 不导入开发账号实施说明](dp-01-keycloak-prod-realm.md)：第八项代码修复（生产 realm 不再带 `skytrace-admin/operator/viewer`）。
- [RB-08 / FE-01 Admin 刷新状态机实施说明](rb-08-admin-refresh-hang.md)：第九项代码修复（无 refresh token 的 401 不再把后续请求挂死）。
- [RB-09 / FE-02 Admin 登出撤销实施说明](rb-09-admin-logout-revoke.md)：第十项代码修复（登出带着捕获到的 token 撤销服务端 refresh）。
- [FE-05 / AUTH-004 半登录回滚实施说明](fe-05-admin-partial-login.md)：第十一项代码修复（login 成功但 `/me` 失败时不留下半套 token）。
- [RB-10 / AS-05 refresh jti 与原子消费实施说明](rb-10-admin-refresh-jti.md)：第十二项代码修复（refresh JWT 带随机 jti，并发轮换只成功一次）。
- [RB-11 / AS-07 JWT secret 启动 fail-fast 实施说明](rb-11-jwt-secret-failfast.md)：第十三项代码修复（两把密钥启动期校验长度且互异）。
- [AS-06 登录/刷新限流实施说明](as-06-auth-rate-limit.md)：第十四项代码修复（login/refresh 进程内滑动窗口，超限 429）。
- [AS-06 登录失败路径对齐实施说明](as-06-login-enumeration.md)：第十五项代码修复（不存在/禁用/错密同一 bcrypt 失败路径）。
- [AS-09 / RB-12 Admin 头像 magic-byte 实施说明](as-09-admin-avatar-magic.md)：第十六项代码修复（头像按文件头识别，对象名用规范扩展名）。
- [BN-03 / RB-13 JWKS kid 冷却实施说明](bn-03-jwks-kid-cooldown.md)：第十七项代码修复（未知 kid 负缓存、全局 JWKS 冷却、原子替换）。
- [AI-03 / AI-05 / RB-14 有界读入与 FFmpeg 超时实施说明](ai-03-upload-ffmpeg-bounds.md)：第十八项代码修复（视觉上传有界读、抽帧 timeout）。
- [AI-04 图片像素炸弹实施说明](ai-04-image-pixel-budget.md)：第十九项代码修复（解码前检查 header 像素预算）。
- [RB-15 / DP-02 / DP-03 真实域名 OIDC 实施说明](rb-15-oidc-public-domain.md)：第二十项代码修复（staging/production 从 SKYTRACE_DOMAIN 派生 redirect、CORS、issuer）。
- [RB-12 Java/Node 上传 magic-byte 实施说明](rb-12-java-node-upload-magic.md)：第二十一项代码修复（证据/知识库/视觉按文件头识别，对象名用规范扩展名）。
- [AI-16 升级 h2 实施说明](ai-16-h2-upgrade.md)：第二十二项代码修复（`h2` 升到 4.4.1，关闭 CVE-2026-71554）。
- [RB-16 Admin npm advisory 实施说明](rb-16-admin-npm-advisories.md)：第二十三项代码修复（Admin Nest 11.2.3、react-router 7.18.4，生产 audit 为 0）。
- [AI-01 PDF 页数/超时实施说明](ai-01-pdf-parse-bounds.md)：第二十四项代码修复（知识库解析页数、字数、切片和 timeout）。
- [BN-04 上传链去掉整文件内存复制实施说明](bn-04-upload-stream.md)：第二十五项代码修复（Java 流转发 AI，Node 不再额外拷 Uint8Array）。
- [AI-01 可终止 PDF 解析进程实施说明](ai-01-pdf-killable-process.md)：第二十六项代码修复（PDF 解析进 spawn 子进程，超时 terminate/kill）。
- [BN-04 multer 落盘实施说明](bn-04-multer-disk.md)：第二十七项代码修复（证据/知识库/视觉上传写临时文件，只嗅探文件头再流转发）。
- [AI-01 PDF 解析 cgroup 内存上限实施说明](ai-01-pdf-cgroup-memory.md)：第二十八项代码修复（PDF 解析子进程 memory.max，超限 400）。
- [认证机制统一调整方案（00 总览）](../authentication-unification/00-overview-and-reading-order.md)：按现状决策、目标架构、身份与数据、API/前端迁移、安全运维、测试回滚、任务版本和后续方向拆分；同样仅为文档，尚未实施。

## 风险等级

| 等级 | 定义 | 发布策略 |
| --- | --- | --- |
| P0 / 严重 | 可直接导致凭据泄露、权限提升、生产默认账号暴露或关键数据语义错误 | 阻止生产发布，优先热修并排查历史数据 |
| P1 / 高 | 可导致拒绝服务、会话安全破坏、数据可见性错误、持续不可用或高概率资源泄漏 | 原则上阻止发布；若接受风险，必须有书面豁免和补偿控制 |
| P2 / 中 | 可靠性、性能、可维护性、审计完整性或防御纵深明显不足 | 进入最近一个迭代，并建立测试防回归 |
| P3 / 低 | 文档、规范、体验、构建告警和长期债务 | 排入常规维护，不与紧急修复混发 |

## 结论先行

当前 `main` **不建议直接发布到生产**。主要原因不是“测试不通过”——现有可运行测试总体通过——而是测试没有覆盖到若干真实安全与契约问题：

- Admin Service 的操作日志会持久化登录、创建用户、修改用户请求中的明文密码或 token。
- 非超级管理员的角色分配和超级角色维护存在纵向提权/破坏超级管理员边界的路径。
- Admin seed 包含公开的默认超级管理员密码并打印出来。
- AI、Node 与 Java 对 `eventTime` 的 UTC/上海时间解释不一致，可造成 8 小时偏移或请求反序列化失败。
- Node 的 `includeDeleted=false` 会因 JavaScript 布尔转换规则变成 `true`。
- Admin 前端刷新 token 状态机存在永久挂起分支，登出又可能没有真正撤销服务端 refresh session。
- Keycloak 生产仍复用包含三个开发用户的 realm 导入文件；公开域名 redirect URI 也未随 staging/production overlay 完整配置。
- 在线审计确认 AI、Admin 前端和 Admin Service 存在当前已知依赖漏洞，其中 `pypdf` 漏洞与项目的非可信 PDF 上传路径直接相关。

## 版本一句话建议

- **本次只生成审计/认证文档和解释性注释，不应因此发布新的产品版本。**
- 完成发布阻断项且保持 API/Schema 向后兼容后，建议统一发布 **`v1.2.2` 补丁版**。
- 若同时把时间协议正式迁移为 UTC `Instant`/`OffsetDateTime`、调整认证 Cookie 协议或事件 Schema，应作为 **`v1.3.0` 的版本化兼容升级**；若直接破坏现有外部契约，则应评估 `v2.0.0`。

详细判定见 [09-版本与发版建议](09-release-recommendation.md)。
