# 01. 发布阻断项

实施状态：**以下问题均未修复，发布门禁建议仍未实施；审计后仅新增中文注释、文档字符串和必要的 EOF 换行，未改动有效逻辑、配置或命令。**

## 1. 当前判定

`main@2c89349` 不建议直接进入生产。建议把下面 P0 全部关闭、P1 至少关闭与当前部署路径相关的项目，再生成 release candidate。

## 2. P0：必须关闭

| ID | 问题 | 直接证据 | 为什么阻断 |
| --- | --- | --- | --- |
| RB-01 | Admin 操作日志持久化明文密码/token | `admin-service/src/common/interceptors/operation-log.interceptor.ts:23-38`，登录接口 `auth.controller.ts:15-20`。实施说明：[as-01-operation-log-redaction.md](as-01-operation-log-redaction.md) | 数据库或日志查看权限可直接获得账号凭据；还必须清理历史数据并轮换已暴露凭据 |
| RB-02 | 非 super 可分配更高权限角色并修改 super 边界 | `admin-service/src/users/users.service.ts:74-149`、`roles/roles.service.ts:57-96` | 形成纵向提权、禁用/删除 super 或篡改 super 角色路径 |
| RB-03 | seed 内置并打印 `admin / Admin@123` | `admin-service/prisma/seed.ts:79-101` | 一旦生产误跑 seed，公开凭据可直接获得最高权限 |
| RB-04 | 生产 Keycloak realm 导入三个永久开发账号 | `deploy/keycloak/skytrace-realm.json:90-143`，生产仍 `start --import-realm` | 与 RB-03 叠加形成默认账号风险；旧 realm 不会因修改 JSON 自动清掉已有用户 |
| RB-05 | AI/Node/Java 告警时间契约冲突 | `backend-ai/app/detection_publisher.py:44-57`、`backend-node/src/alarm/alarm.controller.ts:36-61`、Java `LocalDateTime` DTO、Compose `Asia/Shanghai` | 可直接 400，或产生 8 小时偏移，破坏告警顺序、事件编码和审计证据时间 |
| RB-06 | `includeDeleted=false` 实际变成 `true` | `backend-node/src/evidence/dto/search-evidence.dto.ts:77-80` | 已删除证据可能被意外返回，属于数据可见性错误 |
| RB-07 | AI 正在解析非可信 PDF，而锁定的 `pypdf 6.14.2` 有两个资源耗尽漏洞 | `knowledge_base.py:74-83`、`uv.lock:1094-1095` | 上传接口可触发解析，漏洞与真实攻击面直接重合 |

## 3. P1：高优先级发布门禁

| ID | 问题 | 建议发布门禁 |
| --- | --- | --- |
| RB-08 | Admin 前端无 refresh token 的 401 分支永久保持 `isRefreshing=true` | 必须补状态机单测和并发 401 集成测试 |
| RB-09 | Admin 登出可能不带 access token，服务端 refresh session 未撤销 | 必须验证登出后旧 refresh token 不可再用 |
| RB-10 | Admin refresh token 无随机 `jti` 且并发消费不原子 | 必须验证同秒 token 不同、并发刷新只成功一次 |
| RB-11 | Admin JWT secret 启动校验太弱，登录/刷新无分布式限流 | 生产必须 fail-fast 检查强 secret，并配置限流 |
| RB-12 | 上传链普遍信任 MIME/扩展名，大文件多次内存复制 | 至少对公网入口做 magic-byte、并发和内存上限；AI/Java 仍独立验证 |
| RB-13 | 任意 JWT `kid` 可持续触发 JWKS refresh | 加全局冷却和未知 kid 负缓存，并验证真实轮换 |
| RB-14 | AI 图片/视频先完整读取后才检查大小；FFmpeg 无 timeout | 公网或内网可达时必须修；否则要用网关/网络策略明确隔离并书面接受风险 |
| RB-15 | Keycloak 只含 localhost redirect URI，staging overlay 未完整覆盖 issuer/CORS/前端 URL | release candidate 必须在真实域名完成登录和 token 验证 |
| RB-16 | Admin Service 存在 7 个生产依赖 advisory，Admin 前端存在 2 个 | 更新锁文件、复审 advisory 适用性、全部回归；不能只把 CI 阈值设为 high 来忽略 moderate |
| RB-17 | 生产发布失败只回滚当前服务，会留下混合版本 | 发布脚本必须能回滚本次已更新的全部服务，或采用蓝绿/双栈切换 |

## 4. P0 修复后的最低验证矩阵

### 4.1 凭据与权限

- 对现有 `sys_operation_log.params` 做只读扫描，确认是否已存密码/token；如存在，清理并轮换相关凭据。
- 登录、创建用户、修改用户、修改密码、刷新、登出成功和失败日志都不得包含秘密。
- 非 super 给自己分配更高权限、移除 super、禁用 super、删除 super、编辑 super role，全部返回 403。
- 并发禁用/删除最后两个 active super，最终必须至少保留一个。
- 生产 realm 中不存在开发账号；生产管理员首次凭据走独立 secret 和强制改密。

### 4.2 时间与证据

- 用同一 instant 分别发送 `Z`、`+08:00` 和兼容无 offset 格式，落库结果必须代表同一时刻。
- 验证 UTC 跨日：`2026-08-24T16:30:00Z` 在上海为 `2026-08-25 00:30:00`，事件编码日期和 UI 显示符合明确约定。
- `includeDeleted=false` 不返回删除记录；`true` 仅对拥有相应权限的调用方生效；非法字符串必须 400。

### 4.3 文件与依赖

- 恶意/畸形 PDF、解压炸弹、极端字体映射、超页数/超 chunk 文档均在受控资源内失败。
- HTML/脚本伪装图片、MIME 与 magic bytes 不一致、空文件、超尺寸、并发大视频均被稳定拒绝。
- 在 release SHA 上重新运行 npm/pip/镜像扫描；对暂不能升级的 advisory 写明不可利用证据、补偿控制和到期日。

### 4.4 会话与发布

- 10 个并发 401 只触发一次 refresh，成功时全部重放，失败或无 token 时全部及时 reject。
- 同秒两次登录/刷新得到不同 refresh token；同一旧 token 并发刷新只允许一次成功。
- 服务端登出完成后旧 refresh token 无法使用。
- 模拟第 4 个服务健康检查失败，前 3 个已经更新的服务也必须恢复到上一不可变镜像 tag。

## 5. 临时豁免模板

确实无法在本版关闭某个 P1 时，至少记录：

```text
风险 ID：RB-xx
适用环境：
不可利用/暂缓依据：
补偿控制：
监控与告警：
责任人：
最晚修复日期：
回滚触发条件：
审批人：
```

P0 不建议使用“接受风险”绕过，因为它们涉及直接凭据、权限或关键证据时间语义。
