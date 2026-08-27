# 02. Java 后端与 Gateway 审计

实施状态：**审计建议仍未实施；审计后仅新增中文注释、文档字符串和必要的 EOF 换行，未改动有效逻辑、配置或命令。**

## 1. 验证与总览

- `backend-java`: Maven 测试通过，34 个 suite、110 tests，0 failure/error。
- `gateway-java`: Maven 测试通过，4 个 suite、11 tests，0 failure/error。
- Gateway 启动测试输出大量配置迁移告警：旧 `spring.cloud.gateway.*` 键目前由兼容层临时映射到 `spring.cloud.gateway.server.webflux.*`。
- 现有测试的主要空白是：真 MySQL 空库 migration、Rabbit 重投、事务外副作用、MQTT 长故障、上传炸弹、并发状态机和生产 profile。

最高优先顺序：migration 完整性 → 时间语义 → Rabbit 幂等 → outbox/事务边界 → Temporal queue → MQTT 恢复 → 文件/图片安全。

## 2. P1：优先修复

### JV-01：Flyway 无法独立创建完整业务 Schema

证据：

- `backend-java/src/main/resources/application-docker.yml:10-12` 为 `ddl-auto: validate`。
- `backend-java/src/main/resources/application.yml:11-13` 启用 Flyway。
- `deploy/mysql/init/001_init.sql:62-73` 创建 `inspection_task`。
- `backend-java/src/main/resources/db/migration/` 没有创建 `inspection_task` 的 migration。
- local profile 用 `ddl-auto:update`，会掩盖缺失表。

影响：部署到真正空的托管 MySQL，Flyway 执行结束后 Hibernate validate 仍会因缺表启动失败。当前部署实际上把 Schema 所有权拆在 Docker init、Flyway 和 Hibernate 三处。

建议：新增 migration，不能修改已发布的旧 migration。下面只是结构示意，字段必须以实体和现有 init SQL 为准：

```sql
-- V20__create_inspection_task_if_missing.sql
CREATE TABLE IF NOT EXISTS inspection_task (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_code VARCHAR(64) NOT NULL,
  task_name VARCHAR(128) NOT NULL,
  device_code VARCHAR(64),
  route_code VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  plan_start_time DATETIME(6),
  plan_end_time DATETIME(6),
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_inspection_task_code (task_code)
);

CREATE INDEX idx_task_device_status_updated
  ON inspection_task(device_code, status, updated_at);
CREATE INDEX idx_task_route_code ON inspection_task(route_code);
```

必补门禁：Testcontainers MySQL 从空库 `flyway migrate`，然后 `ddl-auto=validate` 启动完整 Spring Context。CI 不得预执行 `deploy/mysql/init` 来帮助该测试。

### JV-02：证据 API 把上海本地 DATETIME 当 UTC

证据：

- JVM：`deploy/docker-compose.yml:198` 固定 `Asia/Shanghai`。
- JDBC：`backend-java/src/main/resources/application-docker.yml:4` 为上海时区。
- 实体：`EvidenceAsset.java:85-89`、`EvidenceArchiveJob.java:63-67` 以 `LocalDateTime.now()` 写入。
- 查询和响应：`EvidenceQueryService.java:164-174,209-230,320-325` 强制用 UTC 转换。
- 归档响应：`EvidenceArchiveService.java:175-188,217-218` 同类问题。
- 清理服务却按应用时区解释：`EvidenceCleanupService.java:80-84`。

上海本地 `2026-08-24 16:00` 被返回为 `16:00Z`，正确 instant 应为 `08:00Z`；搜索范围同样整体错 8 小时。

短期兼容实现：

```java
@ConfigurationProperties("app.database")
public record DatabaseTimeProperties(ZoneId timeZone) {}

LocalDateTime toDatabaseLocal(Instant value) {
    return LocalDateTime.ofInstant(value, databaseTimeProperties.timeZone());
}

Instant toInstant(LocalDateTime value) {
    return value.atZone(databaseTimeProperties.timeZone()).toInstant();
}
```

长期方案是 UTC at rest：协议、Java 类型和数据库统一使用 `Instant`/`OffsetDateTime`。迁移前需明确旧 `DATETIME` 的实际语义、双读/回填策略和回滚方式。

测试：固定 clock；上海 local 与 UTC 双向转换；UTC 跨日；查询 predicate；API JSON；再用一个有 DST 的 ZoneId 验证没有硬编码 8 小时。

### JV-03：Detection Rabbit 消费无业务幂等键

证据：

- `DetectionAlarmMessage.java:6-16` 没有 `messageId/detectionId`。
- `DetectionAlarmListener.java:47-123` 每次消息直接创建告警。
- `AlarmService.java:48-55` 每次生成随机 event code。
- `V14__add_alarm_evidence_columns.sql:2-17` 只对随机 `event_code` 唯一。
- Rabbit 配置 `RabbitMqConfig.java:30-65` 没有明确 DLQ、有限重试和拒绝重入策略。
- `DetectionAlarmPublisher.java:21-26`、`AlarmRealtimePublisher.java:22-41` 没有 publisher confirm/return 的完成语义。

DB commit 后、ACK 前崩溃会使 Rabbit 重投并生成第二条 alarm/实时通知；毒消息可能无限 requeue。

建议为事件协议增加稳定 ID：

```java
public record DetectionAlarmMessage(
    UUID detectionId,
    String deviceCode,
    String taskCode,
    String eventType,
    BigDecimal confidence,
    OffsetDateTime eventTime
) {}
```

```sql
ALTER TABLE alarm_event ADD COLUMN source_detection_id CHAR(36);
CREATE UNIQUE INDEX uk_alarm_source_detection
  ON alarm_event(source_detection_id);
```

消费端以唯一键/inbox 实现幂等；实时事件只能在“首次插入成功”时发布。配置 DLQ、最大尝试、`default-requeue-rejected=false`；发布端启用 confirms/returns，并让 API 的 `queued` 真正代表 broker 接受。

### JV-04：数据库事务内执行 Temporal/MinIO 副作用，并保留事务内实时发布分支

证据：

- `AlarmService.java:38-41,43-81` 在事务提交前 Signal Temporal，并保留可选的 realtime 发布分支。当前两个生产调用路径都传 `publishRealtime=false`；detection listener 是在事务方法返回后才于 `DetectionAlarmListener.java:109-123` 发布 realtime。
- `EvidenceCommandService.java:50-78` 先写 MinIO，事务内再保存并启动 workflow。
- `EvidenceRegistrationService.java:48-53,99-100` 事务内启动 workflow。
- `EvidenceArchiveService.java:61-90` 保存 job 后、提交前启动 Temporal。

DB rollback 后，Temporal 可能已看到不存在的数据；MinIO 成功但 DB 失败会产生孤儿；外部延迟还拉长事务持锁时间。当前 detection 链路不存在“事务内发布 Rabbit”，但 `publishRealtime=true` 分支如被未来调用者启用，仍会在提交前发布。

建议采用 transactional outbox：

```java
@Transactional
public AlarmEvent createAlarm(CreateAlarm command) {
    AlarmEvent alarm = alarmRepository.save(command.toEntity());
    outboxRepository.save(OutboxEvent.pending(
        "ALARM_CREATED",
        alarm.getEventCode(),
        serialize(alarm)
    ));
    return alarm;
}

// 独立 dispatcher 在 commit 后投递；event_id 唯一；失败可重试。
```

MinIO 需要显式状态机，例如 `PENDING_UPLOAD -> AVAILABLE | FAILED` 和可重试补偿记录。单纯 catch 后删除对象仍可能因删除失败留下孤儿。

### JV-05：Temporal task queue 在 Controller 中写死

- Worker queue 可配置：`backend-java/src/main/resources/application.yml:31-42`。
- `EvidenceDerivativeJobService.java:20-35`、`EvidenceArchiveService.java:43-58` 正确注入。
- `InspectionWorkflowController.java:27,42-47,100-106` 写死默认 queue。

只要设置非默认 `TEMPORAL_TASK_QUEUE`，worker 监听新队列，Controller 仍把工作投旧队列，任务会永久等待。

建议统一类型化配置：

```java
@ConfigurationProperties("spring.temporal")
public record SkyTraceTemporalProperties(String namespace, String taskQueue) {}
```

所有 workflow starter 只依赖同一属性对象。测试自定义 queue 时捕获 `WorkflowOptions`，巡检、聊天、归档和 derivative 必须一致。

### JV-06：MQTT 首次连接连续失败 30 次后停止重试

`DeviceMqttSubscriber.java:34-35,51-54,111-128,153-172` 最多尝试 30 次；最终异常后连接线程结束。重试间隔是 1 秒，但单次 connection timeout 最长 10 秒，因此总时长不是固定 30 秒。`automaticReconnect` 只有首次成功后才有效，无法覆盖 broker 晚启动。

建议 `SmartLifecycle + TaskScheduler`，无限但可取消的指数退避+jitter：

```java
Duration delay = min(maxDelay, initial.multipliedBy(1L << cappedAttempt));
Duration jittered = addJitter(delay);
scheduled = scheduler.schedule(this::connectOnce, clock.instant().plus(jittered));
```

成功后清零 attempt；`stop()`/`@PreDestroy` 取消 future 并断开。若 QoS1 状态消息不可丢，使用稳定 clientId 和持久 session。readiness 应呈现 MQTT disconnected/degraded。

### JV-07：原始扩展名未规范化就进入 ZIP entry，内容类型未验证

证据：

- `EvidenceStorageService.java:82-106,347-357` 信任 `getContentType()`，扩展名取自原始文件名。
- `EvidenceManifestService.java:52-53,109-113` 再取原扩展名。
- `EvidenceArchivePackageService.java:276-281` 直接构造 `ZipEntry`。
- `EvidenceAccessService.java:43-56` 下载名只删双引号，没有统一处理 CR/LF、斜杠和反斜杠。

当前 ZIP 路径有固定 `files/` 前缀，且主文件名使用内部 `evidenceCode`，因此现有证据不足以直接断定存在可逃离解压根目录的经典 Zip Slip。已确认的问题是：取自原文件名最后一个点之后的未受控后缀，仍可把斜杠、反斜杠、控制字符或过长片段带入 entry。

建议：

```java
String basename = sanitizeBasename(originalFilename, 128);
DetectedType detected = contentSniffer.detect(stream);
String extension = extensionFor(detected); // 不信任原扩展名
String archiveEntry = "files/" + validateEvidenceCode(code) + extension;
if (!SAFE_ZIP_ENTRY.matcher(archiveEntry).matches()) {
    throw new InvalidUploadException();
}
```

图片用 ImageIO/Tika，视频用 ffprobe，并限制 parser 资源。Content-Disposition 用框架的 RFC 5987 编码能力，不手拼 header。

测试：`../`、反斜杠、CRLF、绝对路径、NUL、超长名、MIME/magic 不一致；ZIP 中每个 entry 必须是固定安全相对路径。

### JV-08：图片衍生存在压缩炸弹/OOM

- `EvidenceStorageService.java:180-187` 对 MinIO 对象 `readAllBytes()`。
- `EvidenceDerivativeActivitiesImpl.java:54-59,83-104` 全量加载后才 `ImageIO.read`，没有先限制宽高/像素。
- `AiVisionClient.java:101-112`、`AiKnowledgeClient.java:53-62` 通过 `getBytes()` 整体复制 multipart。

建议先用 `ImageReader` 读 metadata，限制单边和 `width * height`，再解码；服务层再次检查 byte limit；AI 转发用 streaming Resource；为 derivative task queue 设置并发和 JVM memory budget。

### JV-09：默认 local profile 会在错误部署时静默使用内存 H2

- `backend-java/src/main/resources/application.yml:9-10` 默认 `local`。
- `application-local.yml:4-19` 使用 `jdbc:h2:mem`、`ddl-auto:update` 并启 H2 console。
- Compose 虽明确覆盖 `docker`，其他部署方式漏配时会回退到临时库。

生产 artifact 不应默认 local；开发命令显式传 profile。非 test 环境检测到内存数据库应拒绝启动，而不是让数据在重启后消失。

## 3. P2：可靠性、性能和契约问题

### JV-10：Redis `KEYS` 阻塞共享实例

`DevicePresenceService.java:63-76` 调用 `redisTemplate.keys("skytrace:device:online:*")`，设备/任务列表频繁触发。建议使用 SCAN，或更适合在线状态的 ZSET：

```text
ZADD skytrace:device:last-seen <epochMillis> <deviceCode>
ZRANGEBYSCORE skytrace:device:last-seen <now-ttl> +inf
ZREMRANGEBYSCORE skytrace:device:last-seen -inf <now-ttl>
```

### JV-11：高频查询缺复合索引

- 每条 telemetry 查询 `deviceCode + status + updatedAt desc`：`DeviceTelemetryHistoryService.java:40-63`。
- 当前 `inspection_task` 只有 task code 唯一索引。
- `AlarmEventRepository.java:8-9` 查 latest，告警表缺 `event_time` 索引。

建议用新 migration 增加：

```sql
CREATE INDEX idx_task_device_status_updated
  ON inspection_task(device_code, status, updated_at);
CREATE INDEX idx_alarm_event_time ON alarm_event(event_time);
CREATE INDEX idx_alarm_device_time ON alarm_event(device_code, event_time);
```

在目标数据量上跑 MySQL `EXPLAIN ANALYZE`，不能只凭索引名称判断生效。

### JV-12：列表 N+1、无分页、轨迹无上限

- 任务：`InspectionTaskController.java:33-35` 无分页；`InspectionTaskService.java:187-207` 每条查 device/route，最多 `1+2N`。
- 证据：`EvidenceQueryService.java:80-104,209-230` 与 `EvidenceTagService.java:33-43` 每条查 relation/tag，并生成两个签名 URL。
- Device/Route 使用 `findAll`。
- 遥测轨迹：`InspectionTaskController.java:43-50`、`DeviceTelemetryHistoryService.java:70-81` 完全无分页。

建议 DTO projection/join 或批量 `IN`；所有列表 page size 有上限；轨迹提供时间窗、cursor、最大点数和下采样；签名 URL 详情按需生成。用 Hibernate statistics 断言 100 条记录 SQL 数仍接近常数。

### JV-13：遥测值缺少范围、有限值和长度验证

`DeviceMqttMessageHandler.java:109-156` 只判断 JSON number，随后写 Redis/Rabbit/DB；`DeviceTelemetryPoint.java:39-40` 的 source 数据库长度只有 32。

至少验证：

```java
Double.isFinite(latitude) && latitude >= -90 && latitude <= 90
Double.isFinite(longitude) && longitude >= -180 && longitude <= 180
Double.isFinite(heading) && heading >= 0 && heading < 360
source != null && source.length() <= 32
```

时间戳解析失败不应静默替换 `now()` 而没有指标；应拒绝，或明确记录 `eventTimeSource=RECEIVED_AT`。

### JV-14：Evidence metadata 缺 Bean Validation，客户端错参变 500

- `BatchReviewEvidenceRequest.java:5-9`、`BatchTagEvidenceRequest.java:5-9`、`UpdateEvidenceMetadataRequest.java:5-10` 无约束。
- `EvidenceController.java:143-163` 未加 `@Valid`。
- `EvidenceMetadataService.java:53-86` 可能对 null `trim()`，批量大小无上限。
- `GlobalExceptionHandler.java:18-75` 没完整处理参数绑定、multipart 超限；所有 DataIntegrityViolation 都错误写成“任务编号重复”。

建议 `@NotEmpty @Size(max=100)`、元素约束、enum 和文本长度；业务冲突使用明确 409 exception；补 400/413/409 异常矩阵，错误体包含 requestId。

### JV-15：500 不记录堆栈，MinIO 原始错误却回给客户端

`GlobalExceptionHandler.java:71-75` 不接收 Exception，因此无法记录 cause。多个 `EvidenceStorageService` catch 把 SDK message 拼进 API。

建议服务端日志记录 exception、稳定 errorCode、requestId 和安全上下文；客户端只返回固定业务消息与 requestId，禁止 bucket/endpoint/object key/SDK 信息泄漏。

### JV-16：Workflow 启动可绕过任务校验并造“幽灵任务”

- `InspectionWorkflowController.java:39-51` 启动前不查任务。
- `InspectionWorkflowImpl.java:43-45` 调 `createTaskIfAbsent`。
- `InspectionTaskActivitiesImpl.java:19-27` 自动创建只有 taskCode 的残缺任务。
- 分析端点反而会先验证任务。

建议生产 workflow 删除自动创建行为；Controller 统一查 task；重复 workflow ID 映射 409。演示自动创建只能放明确 dev-only 接口。

### JV-17：任务状态是任意字符串且无乐观锁

`InspectionTask.java:27-28,103-105` 使用 String 且直接赋值；实体无 `@Version`。HTTP 与 Temporal activity 可并发覆盖。

建议 enum + 领域 transition：

```java
@Version
private long version;

public void transitionTo(TaskStatus next) {
    if (!ALLOWED_TRANSITIONS.get(status).contains(next)) {
        throw new InvalidTaskTransition(status, next);
    }
    this.status = next;
}
```

OptimisticLockException 映射 409；如一个设备只能有一个 RUNNING task，还需数据库锁/约束保证。

### JV-18：证据归档无配额或同 scope 并发控制

`EvidenceArchiveService.java:61-76` 只验证 scope；Activity 一次加载全部证据（`EvidenceArchiveActivitiesImpl.java:137-150`），打包将完整 ZIP 写本地临时盘（`EvidenceArchivePackageService.java:111-131,195-203`）；同 task/alarm 可并发创建多个 job 并覆盖 `archiveBatchCode`（`EvidenceArchiveActivitiesImpl.java:104-111`）。

建议最大文件数、原始总字节、预计 ZIP、磁盘保留余量；同 scope 的 PENDING/RUNNING job 唯一认领；worker 独立并发；启动前检查可用空间，失败保留可恢复状态。

### JV-19：安全配置在 property 缺失时 fail-open

- `GatewaySecurityConfig.java:30-43` 和 `ApiSecurityConfig.java:24-37` 的 permit-all 条件用 `matchIfMissing=true`。
- 当前 yml 虽显式默认 true，因此标准启动通常安全；但配置文件丢失/键改名时可能静默全放行。

permit-all 只能在显式 local/test profile 且显式 false 时启用；安全缺失应 fail closed。用 `ApplicationContextRunner` 测 property 缺失、true、false，缺失必须得到 JWT 安全链或启动失败。

### JV-20：Java 容器为 root，镜像默认端口依赖 Compose 修正

`gateway-java/Dockerfile:13-25`、`backend-java/Dockerfile:12-17` 未设置 USER。Dockerfile 暴露 8080，而应用本地默认端口为 8082/8081；Compose 通过 `SERVER_PORT=8080` 才对齐。

建议：

```dockerfile
RUN groupadd -g 10001 skytrace \
 && useradd -r -u 10001 -g skytrace skytrace
COPY --chown=10001:10001 --from=build /app/target/*.jar /app/app.jar
USER 10001:10001
ENV SERVER_PORT=8080
```

归档 temp 目录要显式赋权。CI 检查 `docker inspect ... Config.User` 非 root，并验证 health、SIGTERM 和临时目录写入。

### JV-21：`REQUIRES_NEW` 因 self-invocation 实际不生效

`EvidenceAccessLogService.record` 标注 `REQUIRES_NEW`，但同 bean 的 `recordPreview/Download/...` 直接调用它，绕过 Spring AOP proxy。

把 `record` 移到独立 bean，或将事务注解放在真正的外部入口。先形成审计 ADR：失败访问是否也必须在独立事务中保留。

### JV-22：后端审计 IP 可伪造

`AuditInterceptor.java:179-184`、`EvidenceActorContextService.java:90-98` 盲信 XFF 最左值。Gateway 有 trusted hop resolver，但 backend 没有对应边界。

只有 remote peer 属于可信 proxy CIDR 时才解析 forwarded header；按右侧可信 hop 数选择。边缘代理要覆盖/清洗用户 XFF。覆盖 IPv4、IPv6、伪造左值、直连和多代理测试。

### JV-23：Gateway、Java 与 Temporal 超时预算互相矛盾

- Gateway response timeout 默认 180 秒。
- Java AI read timeout 250 秒。
- Temporal chat activity 单次 270 秒且最多 3 次。
- Controller 同步等待 workflow 返回。

用户在 180 秒已看到超时，后端仍可能执行/计费；重试又生成新 workflow。首选 `202 Accepted + workflowId + poll/SSE`。若保留同步，必须让全部重试预算小于 edge deadline，并用幂等 requestId 复用运行中的任务。

## 4. P3 与维护项

- 生产 CORS 不应总带 localhost；将 localhost 放 local profile，生产从严格 allowlist 读取。
- `GatewayAccessLogFilter.java:60-72` 应使用 `doFinally` 记录 CANCEL，覆盖 SSE/WebSocket 客户端断开，同时防重复日志。
- Backend 401/403 应与 Gateway 一样添加 `Cache-Control: no-store`。
- Rabbit/MinIO/MySQL 弱开发默认应移入 local profile；生产缺 secret 必须 fail-fast；MySQL 生产应启用 TLS 验证，而不是通用 `useSSL=false`。
- MinIO `ensureBucket` 不应每次上传都查 bucket/重复设 policy；启动期幂等初始化并正确处理多副本竞态。
- route waypoints 不应是无界、未验证 JSON 字符串；改成类型化 waypoint DTO，限制点数和坐标。
- POM/CI 缺 JaCoCo、SpotBugs/PMD/格式门禁和 Java dependency CVE 门禁。
- 将 Gateway 配置键迁移到 `spring.cloud.gateway.server.webflux.*`，消除兼容层依赖；升级 Spring Cloud 前必须完成。

## 5. 建议 PR 拆分

1. `fix(db): make Flyway schema self-contained and add index migration`
2. `fix(time): correct database local-time conversion with regression tests`
3. `feat(events): version detection event with idempotency key`
4. `feat(reliability): transactional outbox for Rabbit and Temporal`
5. `fix(temporal): centralize task queue configuration`
6. `fix(mqtt): lifecycle-aware indefinite reconnect with readiness`
7. `security(evidence): sniff uploads, sanitize archive entries, bound image pixels`
8. `perf(java): remove Redis KEYS, batch list queries, bound telemetry`
9. `fix(api): validation and stable exception mapping`
10. `hardening(java): fail-closed security and non-root images`

时间协议的完整 UTC 迁移最好单独写 ADR 和兼容计划，不与本地 8 小时修复混在同一个不可回滚 PR 中。
