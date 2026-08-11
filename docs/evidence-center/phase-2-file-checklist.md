# 证据中心 Phase 2：逐文件改动清单

> 本清单对应 [Phase 2：审核、联动与媒体衍生](./phase-2-review-linkage.md)。
> 前提是 Phase 1 已上线并稳定。

> 完整可粘贴代码见 [phase-2-implementation-code.md](./phase-2-implementation-code.md)。

## 推荐迁移编号顺序

如果按本文三阶段连续推进，Phase 2 建议使用：

- `V12__add_evidence_review_fields.sql`
- `V13__create_evidence_tag_tables.sql`
- `V14__add_alarm_evidence_columns.sql`
- `V15__add_evidence_derivative_fields.sql`

这样 Phase 3 可以顺延到 `V16`、`V17`，避免撞号。

---

## 一、数据库与实体

### 1. 新增 [backend-java/src/main/resources/db/migration/V12__add_evidence_review_fields.sql](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/resources/db/migration/V12__add_evidence_review_fields.sql)

职责：

- 为 `evidence_asset` 增加审核与备注字段

至少包含：

- `review_status`
- `review_comment`
- `reviewed_at`
- `reviewed_by`
- `reviewed_by_name`
- `remark`
- `analysis_id`

### 2. 新增 [backend-java/src/main/resources/db/migration/V13__create_evidence_tag_tables.sql](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/resources/db/migration/V13__create_evidence_tag_tables.sql)

职责：

- 创建 `evidence_tag`
- 创建 `evidence_tag_rel`

### 3. 新增 [backend-java/src/main/resources/db/migration/V14__add_alarm_evidence_columns.sql](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/resources/db/migration/V14__add_alarm_evidence_columns.sql)

职责：

- 给告警表补证据主引用字段

建议字段：

- `primary_evidence_code`
- `primary_video_evidence_code`

原则：

- 先双写，不删除旧 `image_url` / `video_url`

### 4. 新增 [backend-java/src/main/resources/db/migration/V15__add_evidence_derivative_fields.sql](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/resources/db/migration/V15__add_evidence_derivative_fields.sql)

职责：

- 为 Phase 2 的缩略图 / 封面 / 媒体元数据补字段

建议字段：

- `thumbnail_object_key`
- `poster_object_key`
- `width`
- `height`
- `duration_ms`

### 5. 修改 [backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceAsset.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceAsset.java)

新增字段：

- `reviewStatus`
- `reviewComment`
- `reviewedAt`
- `reviewedBy`
- `reviewedByName`
- `remark`
- `analysisId`
- `thumbnailObjectKey`
- `posterObjectKey`
- `width`
- `height`
- `durationMs`

### 6. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceReviewStatus.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceReviewStatus.java)

职责：

- 固定 `PENDING`
- `APPROVED`
- `REJECTED`

### 7. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceTag.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceTag.java)

职责：

- 标签字典实体

### 8. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceTagRel.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceTagRel.java)

职责：

- 证据与标签关系实体

### 9. 修改 [backend-java/src/main/java/com/skytrace/backend/alarm/domain/AlarmEvent.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/alarm/domain/AlarmEvent.java)

新增字段：

- `primaryEvidenceCode`
- `primaryVideoEvidenceCode`

注意：

- 旧 `imageUrl` / `videoUrl` 暂不删除

---

## 二、Repository 层

### 10. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/repository/EvidenceTagRepository.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/repository/EvidenceTagRepository.java)

职责：

- 标签字典查询

### 11. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/repository/EvidenceTagRelRepository.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/repository/EvidenceTagRelRepository.java)

职责：

- 标签关系维护

### 12. 修改 [backend-java/src/main/java/com/skytrace/backend/alarm/repository/AlarmEventRepository.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/alarm/repository/AlarmEventRepository.java)

改动目标：

- 如果详情页需要按 `primaryEvidenceCode` 反查或联查，这里补查询方法

---

## 三、Java DTO 与 Controller

### 13. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/dto/UpdateEvidenceMetadataRequest.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/dto/UpdateEvidenceMetadataRequest.java)

字段建议：

- `remark`
- `reviewStatus`
- `reviewComment`
- `tagIds`

### 14. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/dto/BatchReviewEvidenceRequest.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/dto/BatchReviewEvidenceRequest.java)

字段建议：

- `evidenceCodes`
- `reviewStatus`
- `reviewComment`

### 15. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/dto/BatchTagEvidenceRequest.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/dto/BatchTagEvidenceRequest.java)

字段建议：

- `evidenceCodes`
- `tagIds`

### 16. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidenceTagResponse.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidenceTagResponse.java)

职责：

- 返回标签字典和详情页标签列表

### 17. 修改 [backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidenceDetailResponse.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidenceDetailResponse.java)

新增字段：

- `reviewStatus`
- `reviewComment`
- `remark`
- `tags`
- `thumbnailObjectKey`
- `posterObjectKey`
- `width`
- `height`
- `durationMs`

### 18. 修改 [backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidenceSummaryResponse.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidenceSummaryResponse.java)

新增字段：

- `reviewStatus`
- `thumbnailAvailable`
- `posterAvailable`

### 19. 修改 [backend-java/src/main/java/com/skytrace/backend/evidence/EvidenceController.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/EvidenceController.java)

新增接口：

- `PATCH /evidence/{evidenceCode}/metadata`
- `POST /evidence/batch/review`
- `POST /evidence/batch/tags`

如你打算给字典管理留入口，也可追加：

- `GET /evidence/tags`

---

## 四、Java Service 层

### 20. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceMetadataService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceMetadataService.java)

职责：

- 更新备注
- 更新审核状态
- 绑定标签

### 21. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceTagService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceTagService.java)

职责：

- 标签字典查询
- 标签关系维护

### 22. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceRegistrationService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceRegistrationService.java)

职责：

- 注册 AI 自动产出的证据
- 注册视频抽帧证据

建议方法：

- `registerGeneratedEvidence(...)`

### 23. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceDerivativeJobService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceDerivativeJobService.java)

职责：

- 上传后发起异步衍生处理任务

### 24. 修改 [backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceCommandService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceCommandService.java)

改动目标：

- 上传成功后触发 `EvidenceDerivativeJobService`
- 人工上传默认 `reviewStatus=PENDING`

### 25. 修改 [backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceQueryService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceQueryService.java)

改动目标：

- 列表与详情返回审核状态、标签、衍生字段

### 26. 修改 [backend-java/src/main/java/com/skytrace/backend/alarm/service/AlarmService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/alarm/service/AlarmService.java)

改动目标：

- 告警创建时支持写入 `primaryEvidenceCode`
- 保持旧 URL 字段兼容

### 27. 修改 [backend-java/src/main/java/com/skytrace/backend/alarm/controller/DetectionController.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/alarm/controller/DetectionController.java)

改动目标：

- AI / detection 链路中，如果有对象键或生成对象，要注册成标准证据记录

### 28. 修改 [backend-java/src/main/java/com/skytrace/backend/alarm/controller/VisionDetectionController.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/alarm/controller/VisionDetectionController.java)

改动目标：

- 视频分析/抽帧结果向证据中心沉淀

---

## 五、Temporal 与异步媒体衍生

### 29. 新增 [backend-java/src/main/java/com/skytrace/backend/temporal/workflow/EvidenceDerivativeWorkflow.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/temporal/workflow/EvidenceDerivativeWorkflow.java)

职责：

- 定义媒体衍生工作流接口

### 30. 新增 [backend-java/src/main/java/com/skytrace/backend/temporal/workflow/EvidenceDerivativeWorkflowImpl.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/temporal/workflow/EvidenceDerivativeWorkflowImpl.java)

职责：

- 编排缩略图 / 封面 / 元数据回写

### 31. 新增 [backend-java/src/main/java/com/skytrace/backend/temporal/activity/EvidenceDerivativeActivities.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/temporal/activity/EvidenceDerivativeActivities.java)

职责：

- 定义活动接口

### 32. 新增 [backend-java/src/main/java/com/skytrace/backend/temporal/activity/EvidenceDerivativeActivitiesImpl.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/temporal/activity/EvidenceDerivativeActivitiesImpl.java)

职责：

- 真正调用 AI 服务或媒体处理逻辑

### 33. 修改 [backend-java/src/main/resources/application.yml](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/resources/application.yml)

改动目标：

- 在 `spring.temporal.workers[].workflow-classes` 中注册 `EvidenceDerivativeWorkflowImpl`
- 在 `activity-beans` 中注册 `evidenceDerivativeActivities`

---

## 六、AI 服务

### 34. 修改 [backend-ai/app/main.py](/home/xdeg/workspace/skytrace-platform/backend-ai/app/main.py)

改动目标：

- 新增内部媒体衍生接口

建议新增：

- `POST /api/internal/evidence/derive-image`
- `POST /api/internal/evidence/derive-video`

### 35. 修改 [backend-ai/app/config.py](/home/xdeg/workspace/skytrace-platform/backend-ai/app/config.py)

改动目标：

- 增加 Phase 2 的媒体衍生配置

建议新增：

- 最大缩略图尺寸
- 视频封面抽帧参数
- 内部处理上传大小限制

### 36. 修改 [backend-ai/app/schemas.py](/home/xdeg/workspace/skytrace-platform/backend-ai/app/schemas.py)

改动目标：

- 增加衍生处理入参与响应模型

### 37. 视情况修改 [backend-ai/app/vision/video_frames.py](/home/xdeg/workspace/skytrace-platform/backend-ai/app/vision/video_frames.py)

改动目标：

- 如果视频封面抽帧可以复用当前工具，这里抽公共方法

---

## 七、Node BFF

### 38. 新增 [backend-node/src/evidence/dto/update-evidence-metadata.dto.ts](/home/xdeg/workspace/skytrace-platform/backend-node/src/evidence/dto/update-evidence-metadata.dto.ts)

### 39. 新增 [backend-node/src/evidence/dto/batch-review-evidence.dto.ts](/home/xdeg/workspace/skytrace-platform/backend-node/src/evidence/dto/batch-review-evidence.dto.ts)

### 40. 新增 [backend-node/src/evidence/dto/batch-tag-evidence.dto.ts](/home/xdeg/workspace/skytrace-platform/backend-node/src/evidence/dto/batch-tag-evidence.dto.ts)

### 41. 修改 [backend-node/src/evidence/evidence.controller.ts](/home/xdeg/workspace/skytrace-platform/backend-node/src/evidence/evidence.controller.ts)

新增透传接口：

- `patchMetadata()`
- `batchReview()`
- `batchTags()`

### 42. 修改 [backend-node/src/alarm/dto/create-alarm.dto.ts](/home/xdeg/workspace/skytrace-platform/backend-node/src/alarm/dto/create-alarm.dto.ts)

新增字段：

- `primaryEvidenceCode`
- `primaryVideoEvidenceCode`

### 43. 修改 [backend-node/src/alarm/alarm.controller.ts](/home/xdeg/workspace/skytrace-platform/backend-node/src/alarm/alarm.controller.ts)

改动目标：

- 告警创建请求向 Java 透传新证据引用字段

---

## 八、前端

### 44. 修改 [frontend/src/api/evidence.ts](/home/xdeg/workspace/skytrace-platform/frontend/src/api/evidence.ts)

新增接口：

- `updateEvidenceMetadata()`
- `batchReviewEvidence()`
- `batchTagEvidence()`

新增类型：

- `EvidenceTag`
- `EvidenceReviewStatus`

### 45. 修改 [frontend/src/api/alarm-evidence.ts](/home/xdeg/workspace/skytrace-platform/frontend/src/api/alarm-evidence.ts)

改动目标：

- 如果旧告警链路要开始回传 `primaryEvidenceCode`，这里补类型与透传

### 46. 修改 [frontend/src/views/EvidenceView.vue](/home/xdeg/workspace/skytrace-platform/frontend/src/views/EvidenceView.vue)

新增能力：

- 标签展示
- 审核状态筛选
- 备注编辑
- 批量勾选
- 批量审核
- 批量打标签
- 缩略图 / 视频封面展示

### 47. 修改 [frontend/src/locales/zh.js](/home/xdeg/workspace/skytrace-platform/frontend/src/locales/zh.js)

新增文案：

- 审核状态
- 标签
- 备注
- 批量操作
- 封面 / 缩略图状态

### 48. 修改 [frontend/src/locales/en.js](/home/xdeg/workspace/skytrace-platform/frontend/src/locales/en.js)

与 `zh.js` 同步。

---

## 九、测试

### 49. 新增 [backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceMetadataServiceTest.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceMetadataServiceTest.java)

### 50. 新增 [backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceRegistrationServiceTest.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceRegistrationServiceTest.java)

### 51. 新增 [backend-java/src/test/java/com/skytrace/backend/temporal/activity/EvidenceDerivativeActivitiesImplTest.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/test/java/com/skytrace/backend/temporal/activity/EvidenceDerivativeActivitiesImplTest.java)

### 52. 新增 [frontend/test/evidence-center-phase2.test.js](/home/xdeg/workspace/skytrace-platform/frontend/test/evidence-center-phase2.test.js)

覆盖重点：

- 批量接口路径
- 审核状态筛选
- 标签与备注提交

---

## Phase 2 完成定义

- [ ] 审核状态、备注、标签可编辑
- [ ] 告警可关联 `primaryEvidenceCode`
- [ ] AI 自动产物可沉淀为标准证据记录
- [ ] 缩略图 / 视频封面有异步补全链路
- [ ] 列表支持批量审核与批量打标签

