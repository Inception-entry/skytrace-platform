# RB-11 / AS-07（JWT secret 启动 fail-fast）实施说明

适用分支：`fix/admin-jwt-secret-failfast`  
只修 **Admin Service JWT 密钥启动校验**：access/refresh 都必须是非默认、至少 32 字符、且互不相同；模块、strategy、AuthService 构造时就检查，不再拖到第一次 refresh。签发/校验固定 `HS256`；access strategy 拒绝 `type:'refresh'` 的 JWT。  
**不要**做登录限流（AS-06）、issuer/audience、family 复用、Cookie、h2、版本号。

旧编号：RB-11、AS-07。上一刀是 refresh `jti`（`#169`）和 `v1.2.2-rc.5`。

---

## 1. 一句话

`JWT_SECRET` 只拒绝一个默认字符串，长度为 1 也能启动；`JWT_REFRESH_SECRET` 要到第一次签发/校验 refresh 才检查。服务可以“健康”起来，登录却 500，或 access/refresh 共用一个弱密钥。

本分支把两把密钥的规则收成 `resolveJwtSecrets()`，启动期 fail-fast。

---

## 2. 现在怎么坏的

```text
JWT_SECRET=x                         → 能启动
JWT_REFRESH_SECRET 未配              → /health 200，login/refresh 才炸
两把密钥相同                         → 一个泄漏等于两种 token 都能造
passport-jwt 未钉 HS256              → 算法可能被降
refresh JWT 若被当成 Bearer          → strategy 只看 sub，可能当 access 用
```

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| 登录/刷新限流 | AS-06，独立 PR |
| issuer / audience | 要额外配置和旧 token 窗口，下一截再做 |
| family 复用撤销 | 要 Schema |
| Cookie 协议 | `v1.3.0` |
| 升 h2 / 打生产 `v1.2.2` | 别的事 |

CI 已经用 `openssl rand` 生成足够长且不同的 `ADMIN_JWT_*`。本地 `deploy/.env.example` 里两把占位密钥已经 ≥32 且不同，复制后仍建议换成自己的随机值。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `admin-service/src/auth/jwt-secrets.ts` | 默认值/长度/互异校验 |
| `admin-service/src/auth/jwt-secrets.spec.ts` | 缺省、短、相同、合法 |
| `admin-service/src/auth/auth.module.ts` | 启动时校验；HS256 |
| `admin-service/src/auth/auth.service.ts` | 构造时校验 refresh secret；refresh 签验 HS256 |
| `admin-service/src/auth/strategies/jwt.strategy.ts` | 同样校验；拒绝 refresh JWT |
| 本文 + 审计索引 | 挂链接 |

---

## 5. 怎么确认

```bash
cd admin-service && npm test -- src/auth/jwt-secrets.spec.ts src/auth/auth.service.spec.ts src/auth/strategies/jwt.strategy.spec.ts
```

- 默认/空/短/相同密钥抛错，不返回 secrets
- 32 字符且不同则通过
- refresh JWT 不能当 access 用

负向（需本地改 env）：只留 1 字符 `JWT_SECRET` 时进程不能起来。

---

## 6. 再下一刀

AS-06 登录/刷新限流已单独实施，见 [as-06-auth-rate-limit.md](as-06-auth-rate-limit.md)。登录失败路径对齐见 [as-06-login-enumeration.md](as-06-login-enumeration.md)。下一刀是 LoginDto 前置校验。family 复用、`h2`、PDF 限额仍各自独立。

合入 `main` 后若要再发候选，打 **`v1.2.2-rc.6`**（不要打在本分支上，不要打生产 `v1.2.2`）。
