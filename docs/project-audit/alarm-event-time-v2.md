# 告警时间 v2（UTC 双栈）

适用分支：`feat/alarm-event-time-v2`  
AI 和 Node 发出的 `eventTime` 改为带 `Z` 或 offset 的时刻，并带 `schemaVersion: 2`。Java 仍把库里的 `event_time` 存成上海墙钟。响应增加 `eventTimeUtc`。旧行没有这一列时，读取时从上海墙钟推导。

不要改证据时间、不要停掉无 offset 的直连 Java 写入、不要改界面展示。

## 契约

| 方向 | v1 | v2 |
| --- | --- | --- |
| 写入 | 无 offset 的字符串仍按上海墙钟 | `2026-08-24T02:00:00Z` 与上海 `10:00` 是同一时刻 |
| 落库 | `event_time` | 同上，并写 `event_instant_utc` |
| 读出 | `eventTime` 仍是上海墙钟 | 多一个 `eventTimeUtc`，例如 `2026-08-24T02:00:00Z` |

`2026-08-24T16:30:00Z` 的上海墙钟仍是 `2026-08-25T00:30:00`，事件编号日期不变。

Flyway：`V24__alarm_event_instant_utc.sql`。
