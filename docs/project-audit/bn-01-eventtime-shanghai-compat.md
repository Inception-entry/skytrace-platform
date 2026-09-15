# BN-01 / AI-02 告警 eventTime 上海 DATETIME 兼容：实施说明

适用分支：`fix/eventtime-shanghai-compat`  
只修 **告警检测链路** 的时间转换：AI 发布、Node BFF、Java 反序列化兼容读取。  
**不要**改证据 API 的 UTC 错转（JV-02，下一刀），不要把协议改成 UTC Instant（那是 `v1.3.0`），不要加 luxon，不要改 Keycloak。

旧编号：RB-05、BN-01、AI-02。路线图 Wave 1B 第 2 条。上一刀是 `includeDeleted`（`#160`）。

---

## 1. 一句话

当前 Java 把 `eventTime` 当成 **Asia/Shanghai 的 `LocalDateTime` / MySQL DATETIME**。AI 用 UTC 时刻再 `replace(tzinfo=None)`，Node 默认 `toISOString()`（带 `Z`）。结果要么 Jackson 400，要么把 UTC 墙钟当上海本地，整体偏 8 小时，事件编号日期也会错。

本分支约定：

1. **写入 Java 之前**，把带 offset 的 instant 转成上海墙钟、**去掉 offset**。
2. **Java 读取**同时接受「无 offset 的上海本地」和「带 Z/+08:00 的 instant」（instant 先转到上海再存）。
3. 无 offset 的字符串在 **Node BFF** 直接 400；AI 发布遇到 naive datetime 直接失败。直连 Java 的无 offset 仍按旧语义（上海本地）。

---

## 2. 现在怎么坏的

```text
AI: datetime.now(UTC) = 2026-08-24 02:00:00+00:00
    replace(tzinfo=None) → "2026-08-24T02:00:00"
    Java 当上海本地 → 实际 instant 早 8 小时

Node: new Date().toISOString() → "2026-08-24T02:00:00.000Z"
    Java LocalDateTime 往往无法解析 → 400
    若某层 slice 掉 Z → 同样偏 8 小时
```

正确对应：`2026-08-24T02:00:00Z` 的上海墙钟是 `2026-08-24T10:00:00`。跨日：`2026-08-24T16:30:00Z` → `2026-08-25T00:30:00`（事件编号日期是 25 日）。

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| 证据 `toInstant(..., UTC)` / JV-02 | 独立 PR，见 [jv-02-evidence-shanghai-instant.md](jv-02-evidence-shanghai-instant.md) |
| 数据库改 `TIMESTAMP`、DTO 改 `OffsetDateTime` | 无版本切契约，应走 `v1.3.0` |
| 加 luxon / 新运行时依赖 | Node 用 `Intl`，Python 用 `zoneinfo` |
| 修 pypdf、Keycloak 开发用户、AUTH-004 | 别的 PR |
| `slice(0, -1)` 去 Z | 审计明确禁止 |

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `backend-ai/app/detection_publisher.py` | `to_legacy_java_local`：先 `astimezone(Asia/Shanghai)` 再去 tz |
| `backend-node/src/common/java-local-date-time.ts` | 只接受 Z/offset，格式化为上海 `yyyy-MM-dd'T'HH:mm:ss.SSS` |
| `backend-node/src/alarm/alarm.controller.ts` | create / detections 都走该函数 |
| Java `DatabaseTimes` + `ShanghaiLocalDateTimeDeserializer` | 告警相关 `eventTime` 兼容读取 |
| `CreateAlarmRequest` / `DetectionAlarmMessage` / `PublishDetectionRequest` | 加上 deserializer |
| 测试 | Python / Node / Java：`Z`、`+08:00`、UTC 跨日、无 offset |
| `e2e/tests/smoke.spec.ts`、`scripts/ci/verify-alarm-evidence.sh` | 带 `+08:00`，避免 Node 400 |

Java 默认时区仍是 Compose 的 `Asia/Shanghai`。转换用 `ZoneId.of("Asia/Shanghai")`，不要写死 `plusHours(8)`。

---

## 5. 怎么确认

- `cd backend-ai && uv run pytest tests/test_detection_publisher.py -v`
- `cd backend-node && npm test`
- `cd backend-java && mvn -q -Dtest=DatabaseTimesTest test`
- `2026-08-24T02:00:00Z` → `2026-08-24T10:00:00`
- `2026-08-24T16:30:00Z` → `2026-08-25T00:30:00`
- Node 对无 offset 字符串 400

---

## 6. 再下一刀

JV-02 证据 Instant 已单独实施，见 [jv-02-evidence-shanghai-instant.md](jv-02-evidence-shanghai-instant.md)。下一刀是 RB-07 pypdf。AUTH-004 仍是 P1。
