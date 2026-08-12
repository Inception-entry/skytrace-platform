# Evidence Center 联调当天操作单

> 适用范围：`phase 3` 上线前真实环境联调日。
>
> 使用方式：联调当天按顺序执行，不要跳步。每完成一项就截图或记录结果。
>
> 文档更新时间：2026-08-11

## 1. 联调当天目标

当天的目标不是“随便测几下”，而是完成一轮可留档的真实闭环：

1. 确认环境健康
2. 确认权限正确
3. 跑通前端归档主链路
4. 跑通下载链路
5. 跑通维护接口只读/演练链路
6. 跑一轮真实压测
7. 留下截图、`jobCode`、日志和结论

## 2. 联调参与角色

建议当天至少有这 3 类人在线：

- 前端：负责页面操作、浏览器截图、交互确认
- 后端：负责接口、日志、数据库、MinIO、Temporal 核查
- 测试或业务：负责确认结果符合真实业务预期

## 3. 联调前 10 分钟准备

先确认下面这些信息已经准备好：

- [ ] 当前环境名称
- [ ] 当前分支 / 镜像版本
- [ ] 当前日期：2026-08-11
- [ ] `ADMIN`、`OPERATOR`、`VIEWER` 账号
- [ ] 1 个用于归档的真实 `TASK`
- [ ] 1 个用于归档的真实 `ALARM`
- [ ] 压测参数计划

建议提前准备一个联调记录模板：

```text
环境：
日期：
参与人：
TASK_CODE：
ALARM_CODE：
ARCHIVE_JOB_CODE：
截图目录：
结论：
```

## 4. 第 1 步：环境健康检查

### 现场操作

1. 执行服务状态检查
2. 确认 Gateway、Node、Java、Temporal、MinIO、Keycloak 都在线
3. 确认前端页面能打开

### 建议使用

查看服务状态：

```bash
./scripts/skytrace.sh status
```

### 需要留档

- 服务状态截图
- 若有异常服务，记录服务名和时间

### 通过标准

- [ ] 关键服务全部在线
- [ ] 前端首页与证据页可打开

## 5. 第 2 步：权限验收

### 现场操作

1. 用 `ADMIN` 登录
2. 用 `OPERATOR` 登录
3. 用 `VIEWER` 登录
4. 验证三类角色的行为边界

### 建议使用

权限验收脚本：

```bash
./scripts/skytrace.sh auth-verify
```

### 当天重点确认

#### `ADMIN`

- [ ] 能创建归档任务
- [ ] 能下载 ZIP / manifest
- [ ] 能查看维护策略

#### `OPERATOR`

- [ ] 能创建归档任务
- [ ] 不能访问维护接口

#### `VIEWER`

- [ ] 只能查看
- [ ] 不能上传 / 删除 / 恢复 / 归档

### 需要留档

- `auth-verify` 输出
- 至少 1 张 401 或 403 的截图

## 6. 第 3 步：前端归档主链路

### 现场操作

1. 用 `ADMIN` 或 `OPERATOR` 进入证据页
2. 在归档控制台选择 `TASK`
3. 输入真实 `TASK_CODE`
4. 点击创建归档
5. 记录生成的 `jobCode`
6. 观察状态轮询
7. 等待任务完成

### 页面关注点

- [ ] 归档入口可见
- [ ] `PENDING -> RUNNING -> COMPLETED` 状态变化正常
- [ ] 完成后显示 `packageContentHash`
- [ ] 如果失败，页面能显示错误信息

### 需要留档

- 创建归档前页面截图
- 任务运行中截图
- 任务完成截图
- `jobCode`

## 7. 第 4 步：告警归档主链路

### 现场操作

1. 在归档控制台选择 `ALARM`
2. 输入真实 `ALARM_CODE`
3. 创建归档任务
4. 记录新的 `jobCode`
5. 等待完成

### 通过标准

- [ ] 告警范围归档可用
- [ ] 和任务归档一样能完整结束

### 需要留档

- 告警归档页面截图
- 告警归档 `jobCode`

## 8. 第 5 步：下载链路验收

### 现场操作

对刚才成功完成的归档任务：

1. 点击下载 ZIP
2. 点击下载 manifest
3. 验证下载链接可用
4. 验证下载域名是否正确

### 当天重点确认

- [ ] ZIP 可下载
- [ ] manifest 可下载
- [ ] URL 不是内部容器地址
- [ ] URL 可以在有效期内使用

### 单证据下载

1. 在证据列表中选中一条正常证据
2. 触发单证据下载
3. 确认下载正常

### 需要留档

- ZIP 下载成功截图
- manifest 下载成功截图
- 1 个下载 URL 样例

## 9. 第 6 步：后端链路核查

这一步由后端同学负责确认“页面看起来成功”背后，真实链路也成功。

### 要查的东西

#### Java

- [ ] 归档任务创建日志
- [ ] Workflow 启动日志
- [ ] Activity 执行日志
- [ ] 下载接口访问日志

#### Temporal

- [ ] Workflow 真正存在
- [ ] 状态已完成
- [ ] 若有重试，次数符合预期

#### MinIO

- [ ] ZIP 对象存在
- [ ] manifest 对象存在

### 需要留档

- Java 日志截图
- Temporal 截图
- MinIO 对象截图

## 10. 第 7 步：维护接口联调

这一步当天建议做“安全查询 + 演练”，不建议一上来就做正式物理清理。

### 现场操作

1. 用 `ADMIN` 查看当前策略
2. 触发 `cleanup-preview`
3. 如有必要，触发一次小批量 `hash-backfill`

### 重点确认

- [ ] `policy` 接口可用
- [ ] `cleanup-preview` 有返回
- [ ] `OPERATOR` 无法访问这些接口

### 需要留档

- `policy` 返回结果
- `cleanup-preview` 返回结果

## 11. 第 8 步：真实压测

当天压测建议分两轮，不要一上来跑最大量。

### 第一轮：常规压测

建议参数：

```bash
KEYCLOAK_CLIENT_SECRET='***' \
ARCHIVE_FILE_COUNT=20 \
ARCHIVE_FILE_SIZE_MB=4 \
./scripts/ci/verify-evidence-archive.sh
```

### 第二轮：高一点的数据量

建议参数：

```bash
KEYCLOAK_CLIENT_SECRET='***' \
ARCHIVE_FILE_COUNT=50 \
ARCHIVE_FILE_SIZE_MB=8 \
./scripts/ci/verify-evidence-archive.sh
```

### 故障恢复压测

建议参数：

```bash
KEYCLOAK_CLIENT_SECRET='***' \
ARCHIVE_FILE_COUNT=2 \
ARCHIVE_FILE_SIZE_MB=1 \
ARCHIVE_MINIO_OUTAGE_SECONDS=10 \
./scripts/ci/verify-evidence-archive.sh
```

### 当天重点记录

- [ ] 总耗时
- [ ] 文件数量
- [ ] 单文件大小
- [ ] 是否成功
- [ ] 是否出现重试
- [ ] 是否出现失败样本

## 12. 第 9 步：联调结论汇总

当天结束前必须做一次结论收口，不然信息会散。

建议输出格式：

```text
1. 环境是否健康：
2. 权限是否正确：
3. TASK 归档是否通过：
4. ALARM 归档是否通过：
5. ZIP / manifest 下载是否通过：
6. Temporal 是否正常：
7. MinIO 产物是否完整：
8. 维护接口演练是否通过：
9. 压测是否通过：
10. 当前阻塞项：
11. 是否建议上线：
```

## 13. 联调当天常见阻塞项

### 页面能点，但任务一直不结束

优先查：

1. Java 日志
2. Temporal Workflow
3. MinIO 连通性

### 下载地址返回了，但浏览器打不开

优先查：

1. `MINIO_PUBLIC_ENDPOINT`
2. Nginx 转发
3. presigned URL 域名

### `OPERATOR` 不该能做的操作做成功了

优先查：

1. Keycloak 角色
2. Gateway / Java 权限配置
3. BFF 是否透传了不该开放的接口

### 维护接口全都 401 / 403

优先查：

1. 是否用了 `ADMIN`
2. Token audience / issuer 是否正确
3. Keycloak 用户是否带 `ADMIN`

## 14. 联调当天不要做的事

- 不要一开始就执行正式物理清理
- 不要未备份就改生产 Flyway 元数据
- 不要跳过权限验收直接测业务
- 不要只测前端不看后端链路
- 不要只记“通过/失败”不记 `jobCode` 和截图

## 15. 一天版推荐节奏

### 上午

1. 环境健康检查
2. 权限验收
3. 前端任务归档
4. 前端告警归档

### 下午

1. 下载链路
2. 后端链路核查
3. 维护接口演练
4. 压测
5. 总结和阻塞项收口

## 16. 和其他文档的关系

- 验收项总清单见 [integration-acceptance-checklist.md](./integration-acceptance-checklist.md)
- 上线闭环顺序见 [go-live-checklist.md](./go-live-checklist.md)
- 维护与压测策略见 [evidence-maintenance-runbook.md](./evidence-maintenance-runbook.md)
- 清理审批规则见 [retention-policy.md](./retention-policy.md)
