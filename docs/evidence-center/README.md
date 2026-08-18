# 证据中心

业务端独立页面 `/evidence`：分页检索、预览/下载（私有桶 + 短时效 URL）、
软删除与恢复、审核与告警联动、归档导出与保留期清理。Phase 1 → Phase 3 主链路已落地。

## 能力概览

1. 独立证据中心，不再只能从任务页附件面板查看。
2. 列表支持分页、筛选、详情、图片预览、视频播放与安全下载。
3. 访问走私有桶 + presigned URL，不长期暴露公开对象地址。
4. 稳定业务编号 `evidenceCode`，可关联任务、告警、设备、分析记录与上传人。
5. `ADMIN` / `OPERATOR` / `VIEWER` 的查看、上传、删除、恢复、导出行为可审计。
6. 审核状态、标签、备注、告警联动、缩略图与视频封面。
7. 归档包导出、内容哈希、生命周期管理与清单校验。

主链路：

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

## 运维与上线文档

| 文档 | 用途 |
| --- | --- |
| [evidence-maintenance-runbook.md](./evidence-maintenance-runbook.md) | 哈希回填、归档清理与压测 |
| [retention-policy.md](./retention-policy.md) | 归档后物理清理与保留期 |
| [go-live-checklist.md](./go-live-checklist.md) | Phase 3 上线前闭环清单 |
| [integration-acceptance-checklist.md](./integration-acceptance-checklist.md) | 真实环境联调与验收 |
| [release-switch-and-rollback.md](./release-switch-and-rollback.md) | 上线开关与回滚 |

平台级数据治理见 [../data-governance.md](../data-governance.md)；Temporal 背景见 [../temporal-integration.md](../temporal-integration.md)。

## 交付对照

- [x] 证据可通过独立页面分页检索
- [x] 图片/视频访问使用短时效地址，不再长期公开
- [x] 查看、下载、删除、恢复、导出和物理清理可审计
- [x] 证据能关联任务、告警、设备、上传人和分析记录
- [x] 支持审核状态、标签、批量处置
- [x] 支持归档任务、导出包、清单与包级哈希校验
- [x] 支持历史哈希回填、清理预览、保留期和受保护物理清理
- [x] 旧任务页不因证据中心升级而中断
