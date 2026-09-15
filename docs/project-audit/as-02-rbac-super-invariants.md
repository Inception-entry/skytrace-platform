# AS-02 / AS-03 super 不变量：实施说明

适用分支：`fix/admin-rbac-super-invariants`  
只改 Admin Service 的用户/角色服务、角色 Controller 入参，以及单测。读完这一份就可以动手。

旧文档把同一件事拆成了 RB-02、AS-02、AS-03、AUTH-003。那些是编号索引；**真正要改的代码和验收以本文为准。**

上一刀是去掉公开 seed 密码（`fix/admin-seed-password` / AS-04，已合入 `#157`）。从当前 `main` 拉本分支，不要把 RBAC 塞进 seed 那个 PR。

```bash
git checkout -b fix/admin-rbac-super-invariants
```

AS-03（最后一名 super 的并发 TOCTOU）和 AS-02 同一把锁域，**本分支一并做**。不加 Prisma migration，不加 `mustChangePassword`，不改 Keycloak，不改管理前端隐藏按钮（前端隐藏不是授权）。

---

## 1. 一句话

有 `user:assign-roles` / `user:update` / `role:update` 的普通管理员，可以给自己或别人加上自己没有的权限，也可以改、禁用、删除超级管理员，或把 `super_admin` 角色的菜单改瘦。最后一个 super 的“先 count 再改”不在同一把锁里，两个并发请求可能把 super 清零。

---

## 2. 现在怎么坏的

```text
非 super 持有 user:assign-roles
  → PUT /users/:id/roles 任意非 super 角色（含自己没有的菜单）
  → 或从 super 用户身上拿掉 super 角色（只要不是“表面上的”最后一人）

非 super 持有 user:update / user:delete
  → 禁用或删除 super 用户（只要 count>1）

非 super 持有 role:update
  → 改 super_admin 的名称/描述；role:assign-menus 可把 super 菜单替换成自己那一小撮
```

证据文件：

| 文件 | 缺口 |
| --- | --- |
| `users.service.ts` `assignRoles` | 只拦“分配 super 角色”；不拦权限并集、不拦改已有 super 用户 |
| `users.service.ts` `update` / `remove` | 只拦最后一个 super；非 super 仍能动其他 super |
| `roles.controller.ts` `update` / `remove` | 不传 `actorId` |
| `roles.service.ts` `update` | 只拦禁用 super 角色，不拦非 super 改名/改描述 |
| `roles.service.ts` `assignMenus` | 非 super 只要菜单是自己权限子集，就能改写 `super_admin` 的菜单 |
| `users.service.ts` 先 `count` 再 `update`/`delete` | 两次查询不在同一事务/锁；AS-03 |

---

## 3. 服务层不变量（必须全部成立）

1. 只有 **当前仍持有启用中 `super_admin` 角色** 的用户，才能改任何 super 用户或 `super_admin` 角色（含改资料、改密、禁用、删除、分配/收回角色、改角色名、分配菜单）。
2. 非 super 给目标用户分配完之后，这些角色上的菜单 code 并集必须是 **actor 当前权限的子集**。
3. 因此非 super 不能给自己增加新权限。
4. `status !== 1` 的角色不能分配。
5. Controller 传来的 `actor` 只使用 **id**；是否 super、有哪些权限，必须按 id 重新查库。不要信 JWT 里可能被写上的 `roles` / `permissions`。
6. 禁用、删除、收回 super 角色时，必须在同一事务里先拿 `pg_advisory_xact_lock(734921)`，再 count，再改。并发下至少留一个 **启用中** 的 super。

`734921` 是本项目 Admin「最后一名 super」专用 advisory key，不要拿去锁别的业务。

---

## 4. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| 改 `docs/authentication-unification/` 正文 | Keycloak 大方案 |
| 给 User 加字段 / Prisma migration | 不需要新表 |
| 改管理前端按钮显隐当授权 | 服务端必须拒绝 |
| 修 AUTH-004 refresh 挂起、限流、jti | 下一波 P1 |
| 修 `includeDeleted`、eventTime、pypdf、Keycloak 开发用户 | 别的 P0，独立 PR |
| 引入 Testcontainers / 新依赖 | 本分支用单测覆盖不变量和“会去拿 advisory lock”；真 PG 并发放后续 CI |
| 提交 Typora 日志、真实 `.env` | 垃圾 / 秘密 |

---

## 5. 要改哪些文件

### 5.1 新建 `admin-service/src/common/permissions/super-admin-lock.ts`

对 `PrismaClientKnownRequestError` code `P2034` 最多重试 3 次。事务隔离级别 `Serializable`。事务开头：

```ts
await tx.$executeRaw`SELECT pg_advisory_xact_lock(${SUPER_ADMIN_ADVISORY_LOCK})`
```

`SUPER_ADMIN_ADVISORY_LOCK = 734921`。

### 5.2 改 `permissions.service.ts`

- `getRoleCodes` / `isSuperAdmin` / `userHasSuperAdmin` / `countActiveSuperAdmins` 增加可选 `db` 参数，事务内必须走 `tx`。
- 新增 `getPermissionCodesForRoleIds(roleIds)`，供分配角色做并集检查。

### 5.3 改 `users.service.ts`

- 目标用户当前是 super（持有启用中的 `super_admin`）时：actor 必须是 super，否则 `403`。
- `assignRoles`：无效 ID → 400；停用角色 → 400；涉及 super 用户或 super 角色时非 super → 403；非 super 的菜单并集不是子集 → 403；收回最后一个启用 super 的角色 → 400，且在 lock 事务里判断。
- `update` 禁用 super、`remove` 删除 super：同样 403 + lock + 最后一名保护。
- 不能禁用/删除自己：保持现有 400。

### 5.4 改 `roles.controller.ts` / `roles.service.ts`

- `update` / `remove` 传入 `@CurrentUser() actor`，service 按 `actor.id` 重查。
- 非 super 不能 `update` / `assignMenus` 目标为 `super_admin` 的角色。
- 仍然任何人不能删除 `super_admin` 角色、不能禁用该角色（现有 400 保留）。

### 5.5 测试

在 `users.service.spec.ts` 增加负向用例；新建 `roles.service.spec.ts`。`$transaction` mock 要能跑 callback，并能断言禁用/删除/降级 super 时调用了 `pg_advisory_xact_lock`。

现有 create/find/self-disable 用例保持绿。`auth.service.spec.ts` 里的 `'Admin@123'` 夹具不要改。

### 5.6 文档索引

- 本文件
- `docs/project-audit/README.md` 专项方案加一条
- `03-node-and-admin-service.md` 的 AS-02 / AS-03 指向本文
- `01-release-blockers.md` 的 RB-02 已有 AS-01 那种实施说明链接则补上
- `10-completion-matrix.md`：RBAC 那一行改为已做服务端不变量；Keycloak 开发用户仍未动

不要改认证方案 00–08 正文。

---

## 6. 怎么确认改对了

1. `cd admin-service && npm test` 全绿。
2. 非 super 给自己分配更高权限角色 → 403。
3. 非 super 改/禁用/删除 super 用户 → 403。
4. 非 super 改 super 角色或改其菜单 → 403。
5. 停用角色不能分配 → 400。
6. super 禁用倒数第二个 super 成功；再禁用最后一个 → 400。
7. 管理前端不必 rebuild。

---

## 7. 和旧编号的对应

| 旧编号 | 在说什么 |
| --- | --- |
| AS-02 | 非 super 提权 / 破坏 super 边界 |
| AS-03 | 最后一名 super 并发保护（本分支用 advisory lock） |
| RB-02 | 发布阻断表同一条 |
| AUTH-003 | 认证 Wave 0 第 3 项 |

---

## 8. 再下一刀（本分支不要开做）

认证清单下一项是 AUTH-004（Admin 前端 refresh 挂起）。产品侧其余 P0 还有 `includeDeleted`、告警时间契约、pypdf、Keycloak 开发用户，各自独立 PR。
