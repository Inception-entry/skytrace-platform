# 证据中心 Phase 2：审核、联动与媒体衍生开发指南

> 这一阶段建立在 Phase 1 已完成的前提上。
> 如果分页搜索、presign 访问和软删除还没稳定，不要提前做本阶段。

逐文件改动清单与代码级实现见：

- [Phase 2：逐文件改动清单](./phase-2-file-checklist.md)
- [Phase 2：代码级实现参考（可粘贴）](./phase-2-implementation-code.md)

## 做完以后，你会看到什么

1. 证据拥有标签、备注和审核状态。
2. 告警详情和证据详情可以双向跳转。
3. AI 自动产出的截图/抽帧不再只是 URL，而是标准证据记录。
4. 图片可展示缩略图，视频可展示封面图。
5. 列表支持批量打标签、批量审核和批量归档准备动作。
6. 证据详情可以看到“来源于 AI 检测 / 视频抽帧 / 人工上传”的差异。

这一阶段主链路是：

```text
AI / manual upload            Spring Boot                  Temporal / async worker           MinIO / MySQL
        │                           │                                   │                           │
        │ create evidence           │                                   │                           │
        ├──────────────────────────►│ save base record                  │                           │
        │                           ├──── start enrichment workflow ───►│                           │
        │                           │                                   ├── thumbnail/poster ─────►│
        │                           │                                   ├── metadata backfill ────►│
        │                           │◄──────── update derivative keys ──┤                           │
```

## 先确认 Phase 1 已满足

满足以下条件后再进入本阶段：

- `/evidence` 页面已上线
- `evidenceCode` 已成为前后端主编号
- 证据访问已经改为 presigned URL
- 证据软删除与恢复已稳定
- `evidence_access_log` 已落库

如果这些还没有，请先回到 [Phase 1](./phase-1-foundation.md)。

## 这一阶段的范围

### 必做

- 证据标签
- 证据备注
- 审核状态
- 告警与证据双向联动
- AI 产出证据标准化入库
- 缩略图 / 视频封面
- 批量操作接口

### 不做

- 归档包导出
- 内容哈希强校验
- 冷热分层和生命周期迁移
- Object Lock / WORM

## 先冻结 5 条业务契约

### 1. 审核状态枚举

第一版只定义：

```text
PENDING
APPROVED
REJECTED
```

不要在这一阶段引入“已归档待复核”“已上报”等多层状态。

### 2. 标签先用“字典 + 关系表”

不要用 `tags_json` 直接塞字符串数组，否则后面筛选和统计会越来越难。

### 3. 媒体衍生是异步流程

生成缩略图和视频封面不应阻塞上传请求。上传成功后：

- 立即返回基础证据记录
- 异步补全缩略图 / 封面 / 尺寸 / 时长

### 4. AI 自动产出的图片也必须入 `evidence_asset`

不要继续让 AI 检测只把 `imageUrl` / `videoUrl` 当普通字符串流转。

正确做法：

- 先落对象
- 再建证据记录
- 告警引用 `evidenceCode`

### 5. 批量操作只处理主数据，不做大对象搬迁

Phase 2 的批量操作仅包括：

- 批量打标签
- 批量备注
- 批量审核

不在这一阶段做批量复制和批量导出对象。

---

## 关卡 A：补齐审核与标签模型

### A1. 新增 migration

建议新增：

```text
backend-java/src/main/resources/db/migration/V12__add_evidence_review_fields.sql
backend-java/src/main/resources/db/migration/V13__create_evidence_tag_tables.sql
```

`V12` 建议字段：

```sql
ALTER TABLE evidence_asset
  ADD COLUMN review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' AFTER source_type,
  ADD COLUMN review_comment VARCHAR(512) NULL AFTER review_status,
  ADD COLUMN reviewed_at DATETIME NULL AFTER review_comment,
  ADD COLUMN reviewed_by VARCHAR(128) NULL AFTER reviewed_at,
  ADD COLUMN reviewed_by_name VARCHAR(128) NULL AFTER reviewed_by,
  ADD COLUMN remark VARCHAR(512) NULL AFTER reviewed_by_name,
  ADD COLUMN analysis_id VARCHAR(64) NULL AFTER route_code;
```

`V13`：

```sql
CREATE TABLE evidence_tag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  color VARCHAR(32) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_evidence_tag_name (name)
);

CREATE TABLE evidence_tag_rel (
  evidence_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  PRIMARY KEY (evidence_id, tag_id),
  INDEX idx_evidence_tag_rel_tag_id (tag_id)
);
```

### A2. 新增 Java 模型

新增：

```text
backend-java/src/main/java/com/skytrace/backend/evidence/domain/
├── EvidenceReviewStatus.java
├── EvidenceTag.java
└── EvidenceTagRel.java
```

### A3. 新增元数据更新接口

新增：

```text
PATCH /api/evidence/{evidenceCode}/metadata
POST  /api/evidence/batch/review
POST  /api/evidence/batch/tags
```

`PATCH /metadata` 第一版只允许更新：

- `remark`
- `reviewStatus`
- `reviewComment`
- `tagIds`

不要允许在这一阶段随意改 `taskCode`、`alarmEventCode`、`deviceCode`，否则会把证据关联链路改乱。

### A4. Node BFF DTO

建议新增：

```text
backend-node/src/evidence/dto/
├── update-evidence-metadata.dto.ts
├── batch-review-evidence.dto.ts
└── batch-tag-evidence.dto.ts
```

---

## 关卡 B：让 AI 证据真正标准化

### B1. 统一 AI 产出入库入口

当前告警与视觉分析链路中还存在 `imageUrl` / `videoUrl` 形式的弱引用。Phase 2 建议改成：

1. AI 或视频分析先把对象放入 MinIO
2. Java 创建 `evidence_asset`
3. Java 返回 `evidenceCode`
4. 告警记录保存 `evidenceCode` 或新增 `primaryEvidenceCode`

这意味着后续告警详情才能稳定跳转证据详情。

### B2. 告警模型升级建议

如果当前 `alarm_event` 还只有 `imageUrl` / `videoUrl`，建议新增：

```text
primary_evidence_code
primary_video_evidence_code
```

第一阶段不要强删旧字段，可先双写：

- 兼容现有前端
- 逐步迁移到证据中心

### B3. Java 服务切分建议

建议新增一个明确的证据注册入口：

```text
EvidenceRegistrationService.registerGeneratedEvidence(...)
```

这个方法只服务于：

- AI 检测
- 视频抽帧
- 系统自动生成截图

不要让这些路径继续调用“面向人工上传”的方法并硬塞参数。

---

## 关卡 C：引入媒体衍生与异步补全

### C1. 为什么这一阶段要异步

缩略图、封面、时长、尺寸提取都不是用户下一个页面渲染必须同步拿到的。

同步做会导致：

- 上传接口变慢
- 大视频更容易超时
- 前端错误体验变差

### C2. 推荐实现方式

推荐复用现有 Temporal 基础设施，而不是先引入新队列系统。

建议新增：

```text
backend-java/src/main/java/com/skytrace/backend/temporal/workflow/EvidenceDerivativeWorkflow.java
backend-java/src/main/java/com/skytrace/backend/temporal/activity/EvidenceDerivativeActivities.java
backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceDerivativeJobService.java
```

工作流职责：

1. 读取证据元数据
2. 生成内部处理用短时效 URL
3. 调用处理端生成缩略图/封面
4. 回写 `thumbnailObjectKey` / `posterObjectKey` / `width` / `height` / `durationMs`

### C3. 谁来真正生成缩略图

推荐优先使用 `backend-ai`，原因：

- Python 生态更适合图像/视频处理
- 仓库里已有视频分析和 `ffmpeg` 相关能力
- 不需要在 Java 服务里硬塞多媒体依赖

建议新增 AI 内部接口：

```text
POST /api/internal/evidence/derive-image
POST /api/internal/evidence/derive-video
```

输入：

- 源对象短时效 URL
- 目标桶
- 目标对象前缀

输出：

- `thumbnailObjectKey`
- `posterObjectKey`
- `width`
- `height`
- `durationMs`

### C4. 前端回退策略

在衍生图还没生成前：

- 图片列表直接调用 `preview-url`
- 视频列表先显示通用占位图

不要因为封面未生成就阻塞证据展示。

---

## 关卡 D：做批量操作和联动页面

### D1. 批量接口

新增：

```text
POST /api/evidence/batch/review
POST /api/evidence/batch/tags
POST /api/evidence/batch/remark
```

统一请求体建议：

```json
{
  "evidenceCodes": ["EV-20260810-000001", "EV-20260810-000002"],
  "action": "APPROVE",
  "comment": "现场复核通过"
}
```

### D2. 告警页联动

证据中心完成后，告警详情页至少要支持：

- 从告警跳到证据详情
- 从证据详情跳到告警详情

如果当前业务端还没有独立告警页，也可以先从任务页或实时告警侧边栏进入证据详情抽屉。

### D3. 前端页面增强

`EvidenceView.vue` 第二阶段建议补：

- 批量选择
- 标签渲染
- 审核状态筛选
- 备注编辑
- 缩略图 / 视频封面展示

---

## 从外到内的排错方法

| 检查点 | 怎么检查 | 正常结果 | 常见原因 |
| --- | --- | --- | --- |
| 标签字典正常 | 查 `evidence_tag` | 能看到标签 | migration 未执行 |
| 元数据更新正常 | 调 `/metadata` | remark / reviewStatus 更新 | DTO 校验过严或 controller 未放行 |
| 批量审核正常 | 提交 2 条记录 | 2 条都更新 | 事务边界不正确 |
| AI 产出入证据表 | 触发视频分析 | 有新 `evidence_asset` 行 | 仍在沿用旧 imageUrl 写法 |
| 衍生任务被创建 | 查 Temporal workflow | 有 derivative workflow | 上传成功后未触发 |
| 封面回写成功 | 查 evidence 表 | `posterObjectKey` 有值 | AI 内部派生接口失败 |

### 高频坑

**不要在 Phase 2 把告警和证据模型彻底重做。**

Phase 2 的重点是联动，不是重构整条告警链。

**不要让封面生成失败把上传整体判失败。**

基础证据记录已经成功，就应该先可见。

**不要在第一版标签里支持自由文本海量创建。**

先有字典，再放开管理入口。

---

## 完成定义（Definition of Done）

- [ ] 证据支持标签、备注、审核状态
- [ ] 支持批量审核与批量打标签
- [ ] AI 自动产出图片 / 视频可沉淀为标准证据记录
- [ ] 告警能关联 `evidenceCode`
- [ ] 图片列表支持缩略图
- [ ] 视频列表支持封面或占位图
- [ ] 衍生处理失败不影响基础证据记录可查

## 建议提交顺序

```text
feat(evidence): add review and tag schema
feat(evidence): add metadata and batch apis
feat(evidence): register ai-generated evidence
feat(evidence): add derivative workflow and media preview enhancements
feat(frontend): add review tag and batch operations to evidence center
```

## 这一阶段先不要做

- 归档包导出
- 文件内容哈希强校验
- 冷热分层
- 对象锁
- OCR / ASR 全量提取

这些全部留给 Phase 3 或更后续版本。
