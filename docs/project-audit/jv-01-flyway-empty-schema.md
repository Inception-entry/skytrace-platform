# JV-01（Flyway 空库能独立建出业务 Schema）实施说明

适用分支：`fix/flyway-empty-mysql`  
只让 **Java Flyway 在空 MySQL 上建出 `inspection_task`**，然后用 `ddl-auto=validate` 启动。CI 用 Testcontainers，**不要**预跑 `deploy/mysql/init`。  
**不要**做 detection 幂等键、outbox、整栈回滚、Admin 头像落盘、vitest mocker、打生产 `v1.2.2`。不要改已发布的 V2–V19。

旧编号：JV-01 / TQ-02。上一刀是 PDF 解析 cgroup（`#189`）和 `v1.2.2-rc.22`。

---

## 1. 一句话

`inspection_task` 只写在 Docker `001_init.sql` 里。托管空库只跑 Flyway 时没有这张表，`ddl-auto=validate` 启动失败。local H2 的 `ddl-auto=update` 把缺口盖住了。

本分支加 **V20**：`CREATE TABLE IF NOT EXISTS inspection_task`，并补 `device_code+status+updated_at`、`route_code` 索引。已有 init 库只加索引，不改旧 migration。

---

## 2. 现在怎么坏的

```text
空托管 MySQL
        │
        ▼
Flyway V2–V19（没有 CREATE inspection_task）
        │
        ▼
V8 发现表不存在 → 直接跳过
        │
        ▼
Hibernate ddl-auto=validate → 缺表，进程起不来
```

审计原文在 [02-java-and-gateway.md](02-java-and-gateway.md) JV-01。Schema 所有权拆在 Docker init、Flyway、Hibernate 三处。

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| 改 V2–V19 | 已发布 checksum 不能动 |
| detection 幂等唯一键 / outbox | 各自独立 |
| 改 Docker init 代替 Flyway | 空托管库根本没有 init |
| 整栈回滚、Admin 头像落盘 | 别的事 |
| 打生产 `v1.2.2` | 别的事 |

V20 是 additive。相对 `v1.2.1` 现在是 Flyway **V20**。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `backend-java/.../V20__create_inspection_task.sql` | 建表 IF NOT EXISTS + 两个索引 |
| `backend-java/pom.xml` | Testcontainers MySQL |
| `FlywayEmptyMysqlSchemaTest` | 空 MySQL → Flyway → validate 启动 |
| `application-flyway-smoke.yml` | 关掉 Rabbit/Redis/MinIO，ddl-auto=validate |
| 对应本文 + 审计索引 + 发版说明 | 单测和挂链接 |

---

## 5. 怎么确认

```bash
cd backend-java && ../scripts/ci/mvn-with-retry.sh test
```

- Testcontainers 空库（无 `deploy/mysql/init`）能启动，且有 `inspection_task`、Flyway 版本 20
- 现有 H2 测试仍绿
- 已有 Docker init 的库再 migrate 只加索引，不报 table exists

---

## 6. 再下一刀

detection 幂等第一阶段已单独实施，见 [jv-03-detection-idempotency.md](jv-03-detection-idempotency.md)。下一刀是 outbox/DLQ 或整栈回滚。Admin 头像落盘、vitest mocker 仍各自独立。

合入 `main` 后打 **`v1.2.2-rc.23`**（不要打在本分支上，不要打生产 `v1.2.2`）。
