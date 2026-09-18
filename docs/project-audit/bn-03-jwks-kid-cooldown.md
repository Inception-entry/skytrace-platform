# BN-03 / RB-13（JWKS kid 冷却）实施说明

适用分支：`fix/node-jwks-kid-cooldown`  
只修 **Node BFF 校验 Keycloak JWT 时的 JWKS 刷新**：未知 `kid` 负缓存、全局 refresh 冷却、成功后原子替换 key set；限制 JWKS 字节数和 key 数量。  
**不要**做 Java/Gateway JWKS、AI/Java 上传、FFmpeg、h2、OIDC redirect、版本号。

旧编号：BN-03、RB-13。上一刀是 Admin 头像 magic-byte（`#173`）和 `v1.2.2-rc.9`。

---

## 1. 一句话

每个未知 JWT `kid` 都会打一次 Keycloak JWKS。攻击者顺序换 `kid` 就能把 Node BFF 变成 JWKS 放大器。

本分支：冷却期内未知 `kid` 直接 401；同一 `kid` 负缓存；拉取成功后整表替换；真实轮换等冷却结束仍能刷新。

---

## 2. 现在怎么坏的

```text
未知 kid A → fetch JWKS
未知 kid B → 再 fetch
只追加 key，旧 key 不删
超大 JWKS / 坏 JWK     → 可能拖垮解析
```

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| Java / Gateway Nimbus JWKS | 另一套客户端，独立验证 |
| AI 上传、FFmpeg、h2、OIDC、打生产 `v1.2.2` | 别的事 |

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `backend-node/src/auth/keycloak-jwt.service.ts` | 冷却、负缓存、原子替换、响应/key 上限、过长 kid 拒绝 |
| `backend-node/test/keycloak-jwt.service.test.js` | 连续未知 kid 不追加 fetch；过长 kid 零 fetch；冷却后轮换成功 |
| 本文 + 审计索引 | 挂链接 |

默认冷却 10s，未知 kid 负缓存 30s。测试可用 `AUTH_JWKS_REFRESH_COOLDOWN_MS`。

---

## 5. 怎么确认

```bash
cd backend-node && npm test
```

- 合法 token 仍过
- 连续两个未知 kid：JWKS 只打与合法 token 相同的次数（不再追加）
- `kid` 超长：401 且零次 JWKS
- 冷却结束后，JWKS 换成新 kid，新 token 能过

---

## 6. 再下一刀

RB-14：AI 有界读入与 FFmpeg 超时已单独实施，见 [ai-03-upload-ffmpeg-bounds.md](ai-03-upload-ffmpeg-bounds.md)。下一刀是 AI-04（像素炸弹）。Java/Node 上传、`h2`、OIDC 仍各自独立。

合入 `main` 后若要再发候选，打 **`v1.2.2-rc.10`**（不要打在本分支上，不要打生产 `v1.2.2`）。
