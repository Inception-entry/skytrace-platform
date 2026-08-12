# 证据中心技术设计：分阶段开发指南

> 这组文档记录从最小上传能力演进到产品级证据中心的设计过程。
> 截至 2026-08-11，Phase 1 → Phase 3 主链路已经落地；早期“当前现状”段落用于解释
> 当时为何选择兼容旧接口、私有桶、软删除和分阶段实施，不代表现在仍缺少这些能力。

关联产品方案见：

- [证据中心产品化方案](../evidence-center-productization.md)

## 全部做完以后，你会得到什么

1. 业务端新增独立的 `/evidence` 页面，不再只能从任务页附件面板查看证据。
2. 证据列表支持分页、筛选、详情抽屉、图片预览、视频播放和安全下载。
3. 证据访问改为私有桶 + presigned URL，不再长期暴露公开对象地址。
4. 证据拥有稳定的业务编号 `evidenceCode`，并且能关联任务、告警、设备、分析记录和上传人。
5. `ADMIN`、`OPERATOR`、`VIEWER` 对证据的查看、上传、删除、恢复、导出行为可被审计。
6. 第二阶段补齐审核状态、标签、备注、告警联动、缩略图和视频封面。
7. 第三阶段补齐归档包导出、内容哈希、生命周期管理和合规扩展位。

最终主链路会从现在的“上传文件”演进为：

```text
Vue Evidence Center            Node BFF                  Spring Boot                MinIO / MySQL / Temporal
        │                         │                           │                                  │
        │ search / detail         │                           │                                  │
        ├────────► /api/evidence ─┼────────► /evidence/search │                                  │
        │                         │                           ├──── query evidence_asset ───────►│
        │                         │                           │◄──────── paged result ───────────┤
        │◄──────── paged result ──┼◄──────────────────────────┤                                  │
        │                         │                           │                                  │
        │ preview-url / download  │                           │                                  │
        ├────────► /api/evidence/{code}/preview-url           │                                  │
        │                         ├────────► /evidence/{code}/preview-url                        │
        │                         │                           ├──── auth + access log ──────────►│
        │                         │                           ├──── presign object ─────────────►│
        │◄──────── short-lived URL┼◄──────────────────────────┤                                  │
        │──────────── browser fetch object from MinIO ──────────────────────────────────────────►│
```

## 先看懂 Phase 1 开始前的仓库基线

Phase 1 开始前，证据能力只有最小闭环：

- Java 证据入口在
  [backend-java/src/main/java/com/skytrace/backend/evidence/EvidenceController.java](../../backend-java/src/main/java/com/skytrace/backend/evidence/EvidenceController.java)
- 证据存储逻辑在
  [backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceStorageService.java](../../backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceStorageService.java)
- MinIO 客户端配置在
  [backend-java/src/main/java/com/skytrace/backend/evidence/MinioConfig.java](../../backend-java/src/main/java/com/skytrace/backend/evidence/MinioConfig.java)
- 当前表结构只有对象元数据：
  [backend-java/src/main/resources/db/migration/V4__create_evidence_asset.sql](../../backend-java/src/main/resources/db/migration/V4__create_evidence_asset.sql)
- Node BFF 透传入口在
  [backend-node/src/evidence/evidence.controller.ts](../../backend-node/src/evidence/evidence.controller.ts)
- 前端当前只在任务页中使用：
  [frontend/src/views/DroneView.vue](../../frontend/src/views/DroneView.vue)

当时的几个关键事实：

1. `GET /api/evidence` 返回数组，没有分页。
2. 证据对象默认通过 `/files/{bucket}/{objectKey}` 暴露。
3. 证据表只有 `task_code` / `alarm_event_code` 两个弱关联字段。
4. 审计拦截器默认不会记录 `GET` 请求，因此“查看证据”目前天然不可审计。
5. 任务页已经依赖老接口，因此不能粗暴把 `GET /api/evidence` 改成分页返回。

这 5 点决定了 Phase 1 的技术路线。

## 这组文档的边界

这组技术设计只处理“证据中心”自身，不在第一阶段同时引入：

- 案件管理系统
- 浏览器直连对象存储 SDK
- 复杂转码集群
- 对象锁 / WORM 合规能力
- OCR / ASR 全流程

先把证据中心作为稳定业务对象立住，再逐阶段外扩。

## 先冻结 7 条技术约束

写代码前先把这些契约定死，否则 Phase 1 做完后会反复返工。

### 1. 保留旧接口，新增新接口

第一阶段不直接破坏任务页依赖的 `GET /api/evidence`。

- 旧接口：继续返回数组，供任务页轻量面板使用
- 新接口：新增分页搜索和详情能力，供证据中心使用

### 2. 用户可见编号必须是 `evidenceCode`

对象键 `objectKey` 是存储实现细节，不适合作为产品编号。

- 数据库主键继续用 `id`
- 前后端使用 `evidenceCode`
- MinIO 仍使用 `objectKey`

### 3. 分页规范与现有后台接口保持一致

分页统一采用：

- `page`：从 `0` 开始
- `size`：默认 `20`
- `size` 上限 `100`
- 返回 `content`、`totalElements`、`totalPages`、`page`、`size`

这样与当前后台审计页的接口风格一致。

### 4. Phase 1 访问控制使用“私有桶 + presigned URL”

第一阶段不引入文件流代理下载服务，先用最小改动实现安全访问：

- Java 校验权限
- Java 记录访问行为
- Java 生成短时效 presigned URL
- 浏览器直接访问 MinIO

### 5. Phase 1 的删除是软删除

第一阶段删除语义：

- 普通查询默认不返回软删除记录
- 删除只打标，不立即物理删对象
- 恢复只清除删除标记

### 6. “证据访问审计”不能只依赖现有 `AuditInterceptor`

因为：

- 现有拦截器默认不审计 `GET`
- 真正内容访问发生在 MinIO，不走 Java controller

所以必须新增独立的 `evidence_access_log` 记录。

### 7. Phase 2 才引入异步衍生处理

第一阶段不要同时做：

- 图片缩略图
- 视频封面
- 视频转码
- OCR / EXIF / 哈希补全任务

否则 Phase 1 范围会失控。

## 分阶段说明

| 阶段 | 目标 | 核心交付 |
| --- | --- | --- |
| Phase 1 | 先把证据从“附件能力”升级成“可查、可看、可删、可恢复”的独立业务能力 | 分页搜索、详情、presign、安全访问、软删除、访问日志、独立 `/evidence` 页面 |
| Phase 2 | 再把证据从“可管理”升级为“可处置、可联动” | 标签、备注、审核状态、告警联动、缩略图、视频封面、批量操作 |
| Phase 3 | 最后把证据从“可处置”升级为“可归档、可保全” | 内容哈希、导出包、归档任务、生命周期管理、清单校验 |

详细设计分别见：

- [Phase 1：查询、安全访问与软删除](./phase-1-foundation.md)
- [Phase 1：逐文件改动清单](./phase-1-file-checklist.md)
- [Phase 1：代码级实现参考（可粘贴）](./phase-1-implementation-code.md)
- [Phase 2：审核、联动与媒体衍生](./phase-2-review-linkage.md)
- [Phase 2：逐文件改动清单](./phase-2-file-checklist.md)
- [Phase 2：代码级实现参考（可粘贴）](./phase-2-implementation-code.md)
- [Phase 3：归档、导出与合规扩展](./phase-3-archive-compliance.md)
- [Phase 3：逐文件改动清单](./phase-3-file-checklist.md)
- [Phase 3：代码级实现参考（可粘贴）](./phase-3-implementation-code.md)
- [证据哈希回填、归档清理与压测 Runbook](./evidence-maintenance-runbook.md)
- [Phase 3 上线前闭环清单](./go-live-checklist.md)
- [归档后清理策略](./retention-policy.md)
- [真实环境联调与验收清单](./integration-acceptance-checklist.md)
- [联调当天操作单](./integration-day-playbook.md)
- [上线开关与回滚方案](./release-switch-and-rollback.md)

### 文档怎么用

| 文档类型 | 回答什么 | 不回答什么 |
| --- | --- | --- |
| foundation / review / archive | 为什么做、契约、关卡顺序 | 完整源码 |
| file-checklist | 改哪些文件、检查点 | 完整源码 |
| **implementation-code** | **核心代码结构与教学节选** | 生产运维步骤 |
| **runbook** | **当前生效策略、上线顺序、审计与压测** | Java 基础教学 |

学习时建议顺序：先读 foundation → 对照 checklist → 阅读真实源码和测试 → 用 Runbook 做
验收。实现已经继续演进时，不要用教学节选覆盖真实源码。

## 会改到哪些代码区域

### Java

预计新增或改动：

- `backend-java/src/main/resources/db/migration/`
- `backend-java/src/main/java/com/skytrace/backend/evidence/`
- `backend-java/src/main/java/com/skytrace/backend/audit/`
- `backend-java/src/test/java/com/skytrace/backend/evidence/`

### Node BFF

预计新增或改动：

- `backend-node/src/evidence/`

### 前端

预计新增或改动：

- `frontend/src/router/index.ts`
- `frontend/src/views/`
- `frontend/src/api/`
- `frontend/src/components/st-menu-aside/index.vue`
- `frontend/src/locales/en.js`
- `frontend/src/locales/zh.js`

## 推荐开发顺序

不要横向同时改三端。推荐顺序：

1. 先完成 Phase 1 的 DB migration、实体和 Java 查询能力。
2. 再补 Node BFF DTO 和透传接口。
3. 再做前端 `/evidence` 页面。
4. 最后把任务页的旧证据面板迁移到新模型的轻量查询接口。
5. Phase 2 和 Phase 3 只有在 Phase 1 稳定后再启动。

## 总体完成定义

全部阶段完成后，证据中心才算真正交付：

- [x] 证据可通过独立页面分页检索
- [x] 图片/视频访问使用短时效地址，不再长期公开
- [x] 查看、下载、删除、恢复、导出和物理清理可审计
- [x] 证据能关联任务、告警、设备、上传人和分析记录
- [x] 支持审核状态、标签、批量处置
- [x] 支持归档任务、导出包、清单与包级哈希校验
- [x] 支持历史哈希回填、清理预览、保留期和受保护物理清理
- [x] 旧任务页不因证据中心升级而中断

## 建议提交顺序

```text
docs(evidence): 新增证据中心产品化与技术设计文档
feat(evidence): phase 1 schema and query foundation
feat(evidence): phase 1 secure preview and soft delete
feat(frontend): add evidence center page
feat(evidence): phase 2 review and linkage
feat(evidence): phase 3 archive and export
```

## 这一期先不要做

- 把 `objectKey` 直接暴露给业务用户当主编号
- 一步到位重写任务页证据面板
- 在第一阶段直接把 MinIO 改成浏览器 SDK 直传
- 为了“安全”一开始就做文件流代理、转码代理和水印代理三套服务
- 在第一阶段引入 OCR、ASR、封面生成、哈希计算四种异步作业

先把第一阶段做稳，再加媒体处理和归档。
