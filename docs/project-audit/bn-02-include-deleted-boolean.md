# BN-02 `includeDeleted` 严格布尔：实施说明

适用分支：`fix/include-deleted-boolean`  
只改 Node BFF 的证据搜索 DTO、共用布尔转换，以及单测。不要改 Java 搜索语义、不要做 eventTime、不要改 Keycloak。

旧编号：RB-06 / BN-02。路线图 Wave 1B 第 1 条。上一刀是 Admin super 不变量（`#158`）。

```bash
git checkout -b fix/include-deleted-boolean
```

---

## 1. 一句话

证据搜索 query 上写了 `includeDeleted=false` 时，Node 用 `@Type(() => Boolean)` 转换。`Boolean('false') === true`，于是 BFF 转发给 Java 的是 `includeDeleted=true`，已删除证据会被返回。

---

## 2. 现在怎么坏的

```text
GET /api/evidence/search?includeDeleted=false
        │
        ▼
SearchEvidenceDto
  @Type(() => Boolean)  → Boolean('false') === true
        │
        ▼
EvidenceController.search
  parameters.set('includeDeleted', 'true')
        │
        ▼
Java EvidenceQueryService
  includeDeleted == true → 不过滤 deleted
```

管理前端勾选框在 `false` 时会省略该参数（`filters.includeDeleted || undefined`），所以点 UI 默认路径不一定踩中。直连 API、脚本、或任何显式传递字符串 `false` 的调用会踩中。

Java 侧 `@RequestParam Boolean` 对字符串 `"false"` 是正确的。**只修 Node。**

---

## 3. 转换规则

| 输入 | 结果 |
| --- | --- |
| 省略 / `undefined` / `null` | 不传给 Java（Java 当作不包含已删除） |
| `true`、`'true'` | `true` |
| `false`、`'false'` | `false` |
| `0`、`1`、`'yes'`、`'false '`、数组（重复 query） | 保持原值，`@IsBoolean()` → **400** |

不要接受 `'TRUE'`、`'False'`：非法值必须 400，不能猜。

与已有的清理接口 `EvidenceCleanupDto.dryRun` 用同一套函数，避免两套布尔规则。

---

## 4. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| 改 eventTime / Evidence UTC | RB-05，下一刀 |
| 给 `includeDeleted=true` 加新权限位 | 现有网关角色已挡写接口；本 P0 是布尔语义 |
| 改 Java `Boolean.TRUE.equals` | 已经是 false 默认 |
| 改管理前端勾选框 | 不是这次的根因 |
| 改认证方案 00–08 | 无关 |

---

## 5. 要改的文件

1. 新建 `backend-node/src/common/strict-boolean.ts`（`strictBoolean`）
2. `search-evidence.dto.ts`：去掉 `@Type(() => Boolean)`，改 `@Transform(({ value }) => strictBoolean(value))`
3. `evidence-maintenance.dto.ts`：dryRun 改用同一个函数
4. 新建 `backend-node/test/search-evidence.dto.test.js`：覆盖 `false`/`true`/空/`0`/`yes`/重复参数
5. 本文 + 审计 README / `01` / `03` / `10-completion-matrix` 索引

现有 `test/evidence-maintenance.dto.test.js` 必须仍绿。

---

## 6. 怎么确认

```bash
cd backend-node && npm test
```

`includeDeleted=false` 转换后必须是布尔 `false`。源码中 `search-evidence.dto.ts` 不再出现 `@Type(() => Boolean)`。

---

## 7. 再下一刀

RB-05 告警/证据时间契约（AI、Node、Java）。AUTH-004 是 P1，排在剩余 P0 后面。
