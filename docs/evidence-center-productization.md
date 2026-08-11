# 证据中心产品化方案

关联技术设计与代码级实现见：

- [证据中心技术设计目录](./evidence-center/README.md)
- [Phase 1 代码级实现](./evidence-center/phase-1-implementation-code.md)
- [Phase 2 代码级实现](./evidence-center/phase-2-implementation-code.md)
- [Phase 3 代码级实现](./evidence-center/phase-3-implementation-code.md)

## 1. 文档目的

本文档用于指导 SkyTrace 现有“证据上传 + 列表查询”能力升级为可运营、可审计、可归档、可扩展的产品级“证据中心”。

目标不是单纯补几个接口，而是把证据从“任务详情里的附件能力”升级为平台内的一等业务对象，支撑以下场景：

- 巡检任务过程中的截图、短视频、抽帧图片上传与检索
- 告警事件关联证据的查询、预览、审计与处置
- AI 识别结果、人工复核结果、任务状态和证据对象之间的闭环追踪
- 后续归档、导出、保全、监管审计和案件回溯

本文基于 2026 年 8 月 10 日仓库现状编写。

## 2. 当前现状

### 2.1 已有能力

当前仓库已经具备证据最小闭环：

- Java 侧提供 `GET /evidence`、`POST /evidence`
- Node BFF 提供 `GET /api/evidence`、`POST /api/evidence`
- 前端在任务页中支持按 `taskCode` 查看证据并上传文件
- 证据文件存储在 MinIO
- MySQL `evidence_asset` 表记录对象元数据
- Temporal 文档中已经明确“证据文件保存在 MinIO，数据库只存 object key”

现状实现可参考：

- [backend-java/src/main/java/com/skytrace/backend/evidence/EvidenceController.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/EvidenceController.java)
- [backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceStorageService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceStorageService.java)
- [backend-java/src/main/resources/db/migration/V4__create_evidence_asset.sql](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/resources/db/migration/V4__create_evidence_asset.sql)
- [backend-node/src/evidence/evidence.controller.ts](/home/xdeg/workspace/skytrace-platform/backend-node/src/evidence/evidence.controller.ts)
- [frontend/src/views/DroneView.vue](/home/xdeg/workspace/skytrace-platform/frontend/src/views/DroneView.vue)
- [docs/temporal-integration.md](/home/xdeg/workspace/skytrace-platform/docs/temporal-integration.md)

### 2.2 当前数据模型

当前 `evidence_asset` 表字段：

- `id`
- `object_key`
- `bucket`
- `content_type`
- `original_filename`
- `size_bytes`
- `task_code`
- `alarm_event_code`
- `created_at`
- `updated_at`

这说明系统目前只保存“对象存储元信息 + 弱关联业务编号”，还没有形成完整的业务证据模型。

### 2.3 当前前端交互形态

当前证据能力挂载在任务页 `DroneView.vue` 中，特征如下：

- 只能在已选任务下查看证据
- 只支持简单上传
- 只支持平铺列表展示
- 没有筛选、分页、预览、批量操作
- 没有证据状态、标签、类型、来源、审核信息
- 没有单独的“证据中心”页面

### 2.4 当前痛点

#### 产品侧痛点

- 证据只能按任务查看，无法作为独立业务资产管理
- 告警、任务、设备、AI 分析之间缺少统一检索入口
- 缺少证据审核、备注、标签、保全状态、归档状态
- 无法快速回答“某个告警有哪些证据”“哪些证据待复核”“哪些证据来自 AI 自动抓拍”

#### 技术侧痛点

- `GET /evidence` 无分页，数据量增大后会拖垮接口和页面
- 公开桶策略过于粗放，证据链接默认可直连，不适合生产保全场景
- 只有 `taskCode` / `alarmEventCode` 两个弱关联键，缺少实体 ID 与业务快照
- 上传时无哈希去重、无病毒扫描、无预生成缩略图、无视频 poster
- 没有软删除、归档、下载审计、访问鉴权与水印能力

#### 运维与合规痛点

- 缺少生命周期管理，冷热分层和归档成本不可控
- 缺少证据导出包与案件归档能力
- 缺少证据访问审计，不利于审计追责

## 3. 产品化目标

### 3.1 总体目标

建设一个“证据中心”，让证据具备：

- 可上传：人工上传、AI 自动产出、系统自动沉淀
- 可关联：任务、告警、设备、航线、分析记录、操作人
- 可检索：按时间、任务、设备、告警、状态、来源、标签组合查询
- 可预览：图片预览、视频播放、缩略图、时空信息展示
- 可审计：谁上传、谁查看、谁下载、谁归档、谁删除全部可追溯
- 可归档：支持证据包导出、案件归档、生命周期管理
- 可扩展：为 OCR、视频抽帧、案件管理、监管报送预留模型

### 3.2 范围边界

本次产品化建议分层推进，不建议第一阶段就把所有高级能力一次做完。

#### 第一阶段必须做

- 独立证据中心页面
- 分页查询
- 证据详情页/抽屉
- 多维筛选
- 证据分类与来源建模
- 预签名访问替代公开桶直链
- 上传审计、下载审计、删除审计
- 软删除与恢复

#### 第二阶段建议做

- 标签、备注、审核状态
- 缩略图、视频封面、视频基础元数据
- 批量操作
- 告警处置与证据联动
- 案件/归档包导出

#### 第三阶段可选做

- 内容哈希去重
- OCR / ASR / EXIF 提取
- 水印预览
- 合规保全、不可篡改策略、对象锁
- 智能聚类和相似证据检索

## 4. 目标产品形态

### 4.1 页面结构

建议新增独立业务页：`/evidence`

页面由四块组成：

1. 全局筛选区
2. 证据列表区
3. 证据详情抽屉
4. 关联业务上下文区

#### 全局筛选区

支持：

- 时间范围
- 任务编号
- 告警编号
- 设备编号
- 文件类型：图片 / 视频
- 来源：人工上传 / AI 检测 / 视频抽帧 / 系统归档
- 状态：正常 / 待审核 / 已归档 / 已删除 / 保全中
- 标签
- 上传人

#### 列表区

建议提供两种视图：

- 表格视图：适合管理与筛选
- 卡片视图：适合图片/视频直观浏览

列表字段建议：

- 缩略图
- 证据编号
- 原始文件名
- 证据类型
- 来源
- 任务编号
- 告警编号
- 设备编号
- 上传时间
- 上传人
- 大小
- 状态

#### 详情区

点击证据后打开抽屉或详情页，展示：

- 大图 / 视频播放器
- 元数据
- 关联任务与告警
- AI 检测摘要
- 审核记录
- 操作日志
- 标签与备注

### 4.2 与现有页面的关系

不是替换任务页中的证据面板，而是形成“双入口”：

- 任务页继续保留轻量证据面板，适合现场操作
- 新增证据中心页，适合后台检索、处置、审计、导出

## 5. 核心业务对象设计

### 5.1 证据实体升级原则

现有 `evidence_asset` 更像“对象存储元数据表”，不够支撑产品化。建议升级为真正的证据主表。

保守做法：

- 保留现有表
- 在其上增量扩字段
- 如后续模型复杂，再拆分附属表

建议主表新增字段：

- `evidence_code`：业务证据编号，便于检索和沟通
- `source_type`：`MANUAL_UPLOAD`、`AI_DETECTION`、`VIDEO_FRAME`、`SYSTEM_GENERATED`
- `asset_type`：`IMAGE`、`VIDEO`
- `review_status`：`PENDING`、`APPROVED`、`REJECTED`
- `archive_status`：`ACTIVE`、`ARCHIVED`
- `deleted`：软删除标记
- `uploaded_by`
- `uploaded_by_name`
- `device_code`
- `route_code`
- `analysis_id`
- `case_code`
- `storage_class`：`HOT`、`COLD`
- `content_hash`：SHA-256
- `thumbnail_object_key`
- `poster_object_key`
- `duration_ms`
- `width`
- `height`
- `captured_at`
- `reviewed_by`
- `reviewed_at`
- `review_comment`
- `tags_json` 或独立标签关系表

### 5.2 附属表建议

当证据中心进入第二阶段后，建议拆出以下表：

#### `evidence_access_log`

记录：

- 查看
- 下载
- 导出
- 删除
- 恢复
- 归档

字段建议：

- `id`
- `evidence_id`
- `action`
- `operator`
- `operator_name`
- `request_id`
- `ip`
- `user_agent`
- `created_at`

#### `evidence_tag`

- `id`
- `name`
- `color`

#### `evidence_tag_rel`

- `evidence_id`
- `tag_id`

#### `evidence_archive_job`

用于证据包导出与归档任务：

- `job_code`
- `scope_type`
- `scope_value`
- `status`
- `output_object_key`
- `created_by`
- `created_at`

## 6. 数据流与链路设计

### 6.1 人工上传链路

```text
前端证据中心 / 任务页
  -> Gateway
  -> Node BFF
  -> Java Evidence API
  -> MinIO
  -> MySQL evidence_asset
  -> 审计日志
```

建议增强点：

- 上传前校验文件大小、类型、数量
- 上传后异步生成缩略图/封面
- 记录上传人和来源
- 生成证据编号

### 6.2 AI 自动产出链路

```text
AI 检测 / 视频抽帧
  -> 生成图片/视频片段
  -> 入 MinIO
  -> Java 创建 evidence_asset
  -> 关联 taskCode / alarmEventCode / analysisId
  -> 触发告警或审核待办
```

建议把“AI 结果中的图片对象”统一沉淀为证据，而不是只把 URL 带过链路。

### 6.3 访问链路

当前 `/files/bucket/objectKey` 公开访问方式不适合产品化。建议改造为：

```text
前端请求 evidence download/view API
  -> Java 校验权限
  -> 返回短时效 presigned URL
  -> 浏览器访问 MinIO
```

这样可以：

- 控制访问权限
- 控制有效期
- 记录查看/下载审计
- 后续支持加水印代理

## 7. API 设计建议

### 7.1 保留并升级现有接口

#### `POST /api/evidence`

保留上传入口，但增强请求参数：

- `taskCode`
- `alarmEventCode`
- `deviceCode`
- `sourceType`
- `capturedAt`
- `tags`
- `remark`

返回值增强：

- `evidenceCode`
- `assetType`
- `sourceType`
- `reviewStatus`
- `thumbnailUrl`
- `previewUrl`

#### `GET /api/evidence`

从“简单查询”升级为“分页搜索”：

查询参数建议：

- `page`
- `size`
- `taskCode`
- `alarmEventCode`
- `deviceCode`
- `sourceType`
- `assetType`
- `reviewStatus`
- `archiveStatus`
- `keyword`
- `startTime`
- `endTime`
- `uploadedBy`
- `includeDeleted`

返回：

- `content`
- `page`
- `size`
- `totalElements`
- `totalPages`

### 7.2 新增接口建议

#### 证据详情

`GET /api/evidence/{evidenceCode}`

返回完整元数据、关联业务对象、审计摘要、标签、备注。

#### 证据预览地址

`POST /api/evidence/{evidenceCode}/preview-url`

返回短时效预览地址。

#### 证据下载地址

`POST /api/evidence/{evidenceCode}/download-url`

返回短时效下载地址，并记访问审计。

#### 证据软删除

`DELETE /api/evidence/{evidenceCode}`

默认软删除，不直接物理删除 MinIO 对象。

#### 证据恢复

`POST /api/evidence/{evidenceCode}/restore`

恢复误删证据。

#### 更新标签与备注

`PATCH /api/evidence/{evidenceCode}/metadata`

修改：

- 标签
- 备注
- 审核状态
- 归档状态

#### 批量操作

`POST /api/evidence/batch`

支持：

- 批量打标签
- 批量归档
- 批量删除

#### 归档导出

`POST /api/evidence/archive-jobs`

创建归档任务，异步输出 zip 包或证据清单。

### 7.3 权限建议

按现有角色体系建议：

- `ADMIN`：全部能力
- `OPERATOR`：上传、查看、下载、备注、有限删除
- `VIEWER`：查看与下载

进一步可拆细权限点：

- `EVIDENCE_VIEW`
- `EVIDENCE_UPLOAD`
- `EVIDENCE_DOWNLOAD`
- `EVIDENCE_REVIEW`
- `EVIDENCE_DELETE`
- `EVIDENCE_ARCHIVE`

## 8. 后端改造方案

### 8.1 Java 服务

#### 数据层改造

- 为 `evidence_asset` 增加产品字段
- 引入分页查询 Repository
- 增加动态筛选能力
- 为访问日志、标签、归档任务建表

建议使用新增 Flyway 迁移，例如：

- `V10__upgrade_evidence_asset.sql`
- `V11__create_evidence_access_log.sql`
- `V12__create_evidence_tag.sql`

#### 服务层改造

将 `EvidenceStorageService` 拆分为更清晰的职责：

- `EvidenceCommandService`
- `EvidenceQueryService`
- `EvidenceStorageService`
- `EvidenceAccessLogService`
- `EvidenceArchiveService`

这样后续扩展不会把所有逻辑塞进单个类。

#### 存储层改造

当前 `ensureBucket()` 中会自动创建桶并设置公开读策略。产品化后建议改为：

- 桶由部署流程预创建
- 默认私有
- 下载走 presign 或代理
- 是否公开由环境配置显式控制

#### 审计与安全

所有以下动作进入审计：

- 上传
- 查看原图/原视频
- 下载
- 删除
- 恢复
- 归档
- 导出

### 8.2 Node BFF

Node 侧主要做透传和协议收敛，建议：

- 对分页参数做 DTO 校验
- 对上传参数做更严格校验
- 将新接口统一暴露为 `/api/evidence/**`
- 预览/下载接口由 Node 保持前端契约稳定

### 8.3 AI / 告警 / Temporal 联动

证据中心产品化后，建议把以下链路真正打通：

- AI 检测命中后自动生成证据记录
- 告警创建时引用 `evidenceCode`
- Temporal 工作流在关键节点记录证据对象
- 归档任务可按 `taskCode`、`alarmEventCode`、`caseCode` 聚合

这一步会显著提升“告警 -> 证据 -> 审计 -> 回溯”的一致性。

## 9. 前端改造方案

### 9.1 页面与路由

新增：

- 路由：`/evidence`
- 页面：`frontend/src/views/EvidenceView.vue`
- 导航入口：主业务导航中加入“证据中心”

保留：

- `DroneView.vue` 中的轻量证据面板

### 9.2 组件建议

建议拆分组件：

- `EvidenceFilterBar`
- `EvidenceTable`
- `EvidenceCardGrid`
- `EvidencePreviewDrawer`
- `EvidenceUploadModal`
- `EvidenceTagEditor`

### 9.3 用户体验建议

- 图片支持悬停预览或抽屉大图
- 视频支持封面与内嵌播放
- 大文件上传显示进度
- 筛选条件支持 URL 同步，便于分享排查链接
- 批量选择后提供批量打标和归档

### 9.4 前端接口层

建议从当前 [frontend/src/api/alarm-evidence.ts](/home/xdeg/workspace/skytrace-platform/frontend/src/api/alarm-evidence.ts) 中拆出独立 `evidence.ts`，避免“告警 API”和“证据 API”长期混在一起。

## 10. 安全与合规设计

### 10.1 下载安全

不建议长期保留公开桶策略。建议：

- 默认私有桶
- 预签名 URL 5 分钟有效
- 下载前做角色校验
- 下载行为写审计日志

### 10.2 删除策略

第一阶段只做软删除：

- 数据记录标记删除
- 对象不立即物理移除
- 提供恢复窗口

第二阶段再引入异步物理清理任务。

### 10.3 完整性校验

建议为每个上传对象计算 `content_hash`：

- 便于去重
- 便于保全
- 便于归档核验

### 10.4 合规扩展

如果后续面向政企或监管：

- 引入对象锁或 WORM
- 引入水印预览
- 引入导出签章与清单
- 引入保全时间戳

## 11. 性能与存储策略

### 11.1 分页与索引

必须新增分页，否则证据量上来后列表不可用。

建议索引：

- `(task_code, created_at desc)`
- `(alarm_event_code, created_at desc)`
- `(device_code, created_at desc)`
- `(source_type, created_at desc)`
- `(review_status, created_at desc)`

### 11.2 缩略图与封面

为减轻前端和带宽压力，建议异步生成：

- 图片缩略图
- 视频封面图

### 11.3 生命周期

建议对象存储采用：

- 热数据：近 30 天
- 温数据：31-180 天
- 冷归档：180 天后按策略转冷存储或归档桶

## 12. 分阶段实施计划

### Phase 1：从附件能力升级为证据中心

目标：

- 独立页面
- 分页筛选
- 详情预览
- 私有桶 + presign
- 软删除
- 访问审计

交付项：

- 新增 DB 迁移
- Java 查询分页接口
- Java 预签名接口
- Node BFF 新 DTO
- 前端 `EvidenceView.vue`
- 导航入口

### Phase 2：从可管理升级为可处置

目标：

- 标签、备注、审核状态
- 批量操作
- 视频封面和缩略图
- 告警联动

交付项：

- 标签表
- 审核与元数据更新接口
- 批量操作接口
- 异步媒体处理任务

### Phase 3：从可处置升级为可归档

目标：

- 案件级导出
- 归档包
- 生命周期管理
- 内容哈希

交付项：

- 归档任务表
- 异步导出流程
- ZIP 包输出
- 清单文件与校验文件

## 13. 验收标准

### 13.1 Phase 1 验收

- 证据中心可按任务、告警、设备、时间分页检索
- 列表 10k 级数据下仍能稳定分页查询
- 图片和视频可通过短时效地址预览
- `VIEWER` 无上传删除权限
- `ADMIN` / `OPERATOR` 上传后可在任务页和证据中心同时看到记录
- 删除后证据默认不在普通列表出现，但可恢复
- 查看和下载行为可审计

### 13.2 Phase 2 验收

- 支持批量打标签与批量归档
- 告警详情能跳转关联证据
- 视频默认展示封面
- AI 自动生成的证据带有来源标记

### 13.3 Phase 3 验收

- 可按任务或告警导出证据包
- 导出结果包含证据文件与元数据清单
- 归档任务失败可重试

## 14. 风险与注意事项

### 14.1 兼容性风险

现有前端 `getEvidence()` 直接假设返回数组，升级为分页后会影响当前任务页逻辑。建议：

- 新任务页继续调用轻量接口
- 或让列表接口支持 `paged=false`
- 或先新增 `/api/evidence/search`

推荐做法是“先新增分页接口，再逐步迁移旧接口”，避免一次性破坏现有页面。

### 14.2 存储安全风险

当前自动创建公开桶的方式适合本地联调，不适合生产。改成私有桶后，前端所有直接拼接的 `publicPath` 用法都要同步改。

### 14.3 媒体处理成本

视频封面、转码、缩略图、OCR 都会带来 CPU 成本。建议分阶段引入，不要和第一阶段捆绑。

### 14.4 业务模型膨胀风险

证据、告警、案件、审核、归档容易互相缠绕。建议第一阶段只把“证据中心”做好，不要同时扩成“案件管理系统”。

## 15. 推荐实施顺序

如果我们要在当前仓库里真实推进，我建议按下面顺序做：

1. 先补文档和数据模型，定字段、状态枚举、权限边界。
2. 新增 DB migration，升级 `evidence_asset`。
3. 在 Java 层补分页查询、详情、presign、软删除、审计。
4. 在 Node 层补 BFF DTO 和透传接口。
5. 新建 `EvidenceView.vue` 和导航入口。
6. 最后再把 `DroneView.vue` 的任务证据面板接入新能力。

这个顺序的好处是：

- 对现有链路扰动最小
- 可以先做后端闭环再做前端升级
- 旧任务页不必立即推翻重写

## 16. 建议的首个开发切片

如果要开始编码，我建议第一刀只做一个可控切片：

### 切片 A：证据查询产品化基础版

范围：

- 新增 `EvidenceView.vue`
- 新增分页查询接口
- 新增证据详情接口
- 新增 presign 预览接口
- 新增软删除

暂不做：

- 标签
- 批量操作
- 归档导出
- OCR
- 视频封面

这是因为这个切片能最快把“证据中心”从无到有立起来，并且不会把范围拉得太大。

## 17. 结论

SkyTrace 当前已经有证据链路，但它本质上仍是“对象上传能力”，还不是“证据中心产品”。

要把它做成产品，关键不在于再加一个上传按钮，而在于同时补齐 5 件事：

- 独立业务入口
- 证据主数据模型
- 分页检索与详情预览
- 私有访问与审计
- 软删除、归档和后续扩展位

如果按投入产出比排序，最值得先做的是：

1. 分页检索
2. 独立页面
3. presign 安全访问
4. 软删除与审计
5. 标签/归档扩展

---

如果你认可这份方案，下一步我可以继续帮你出两份更落地的文档之一：

- `技术设计稿`：数据库迁移、接口定义、DTO、前后端文件改动清单
- `实施排期稿`：按 1 周 / 2 周 / 4 周拆任务、人天估算、优先级和风险
