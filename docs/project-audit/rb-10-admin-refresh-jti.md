# RB-10 / AS-05（refresh jti + 原子消费）实施说明

适用分支：`fix/admin-refresh-jti`  
只修 **Admin Service refresh JWT**：payload 加随机 `jti` 和 `type:'refresh'`；轮换时在事务里 `deleteMany`，受影响行数不是 1 就 401；Prisma P2002/P2025 映射成认证失败。  
**不要**加 `familyId` 列/复用撤全家、不要做 JWT fail-fast（RB-11）、不要做登录限流、不要改 Cookie、不要打生产 `v1.2.2`。

旧编号：RB-10、AS-05。上一刀是半登录回滚（`#168`）和 `v1.2.2-rc.4`。

---

## 1. 一句话

refresh JWT 和 access 用同一份 `{ sub, username }`，同秒两次 `sign` 可能得到完全相同的 refresh；并发刷新先 `findUnique` 再 `delete`，输家可能冒 Prisma 异常而不是 401。

本分支：refresh 带随机 `jti`；只接受 `type:'refresh'`；事务内条件删除，count ≠ 1 或 unique 冲突一律 401。

---

## 2. 现在怎么坏的

```text
同秒 login/refresh
  payload 相同 → JWT 字符串相同
  hash unique 冲突，或轮换后旧 token 仍等价可用

并发 refresh
  A/B 都 findUnique 成功
  A delete 成功并签发新 token
  B delete 抛 P2025，未映射成 401
```

门禁：同秒两次登录 refresh 不同；同一旧 token 并发刷新只成功一次。

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| `familyId` 列 + 复用撤全家 | 要 Prisma 迁移，独立一刀 |
| access/refresh secret 启动期加严（RB-11） | 别的 PR |
| 登录/刷新限流（AS-06） | 别的 PR |
| Cookie 协议 | `v1.3.0` |
| 升 h2 / 打生产 tag | 别的事 |

已签发、没有 `jti`/`type` 的旧 refresh **会失效**，管理员需要重新登录。库表结构不变，仍只存 token hash。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `admin-service/src/auth/auth.service.ts` | `signRefreshToken` / `verifyRefreshToken`；事务内条件删除；P2002/P2025 → 401 |
| `admin-service/src/auth/auth.service.spec.ts` | 同秒不同 jti、非 refresh JWT 拒绝、并发只赢一次、unique 冲突 401 |
| 本文 + 审计索引 | 挂链接 |

---

## 5. 怎么确认

```bash
cd admin-service && npm test -- src/auth/auth.service.spec.ts
```

- 两次 `login` 的 refresh payload 都有不同 `jti`
- 缺 `type:'refresh'` 的 JWT 不能当 refresh 用
- 同一 token 两次并发 refresh：一次成功，一次 `令牌已撤销`
- `P2002` 变成 401，不把 Prisma 错误吐给客户端

---

## 6. 再下一刀

RB-11 / AS-07：JWT secret 启动 fail-fast（长度、互不相同）。family 复用撤销、限流、`h2`、PDF 限额仍各自独立。

合入 `main` 后若要再发候选，打 **`v1.2.2-rc.5`**（不要打在本分支上，不要打生产 `v1.2.2`）。
