# RB-16（Admin npm advisory）实施说明

适用分支：`fix/admin-npm-advisories`  
只升 **Admin Service / Admin 前端的生产依赖**，让 `npm audit --omit=dev` 为 0。  
**不要**做 PDF 页数、整栈回滚、Flyway 空库、detection 幂等、打生产 `v1.2.2`。不要 `npm audit fix --force`。

旧编号：RB-16。上一刀是 AI `h2`（`#182`）和 `v1.2.2-rc.16`。

---

## 1. 一句话

Admin Service 锁在 Nest 10，生产链带着 body-parser / qs / file-type，MinIO 还带着 decode-uri-component / stream-json。管理前端 `react-router-dom` 6.x 有开放重定向。CI 只扫 high，moderate 一直绿着漏过去。

本分支把 Nest 对齐 BFF 的 `11.2.3`，`react-router-dom` 升到 `7.18.4`，MinIO/Jest 传递依赖用 overrides 钉到修复版。生产 `npm audit --omit=dev` 为 0。Admin 的 CI 改成 moderate。

---

## 2. 现在怎么坏的

```text
admin-service Nest 10.4
  @nestjs/core          GHSA-36xv-jgw5-4q75  SSE 注入（≤11.1.17）
  @nestjs/common        file-type 20.4.1 死循环 / ZIP bomb
  platform-express      body-parser <1.20.6、qs ≤6.15.3
  minio 8               decode-uri-component、stream-json DoS

admin-frontend react-router-dom 6.28
  GHSA-wrjc-x8rr-h8h6  反斜杠开放重定向
  GHSA-337j-9hxr-rhxg  SSR hydrate 构造注入（本应用是 SPA，仍会扫到）
```

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| `npm audit fix --force` | 会把 MinIO 打回 7.x，Nest 打到 12 |
管理前端 vitest 已升到 4.1.11，见 [rb-16-vitest-mocker.md](rb-16-vitest-mocker.md)。
| PDF 页数、整栈回滚、Flyway | 各自独立 |
| 打生产 `v1.2.2` | 别的事 |

管理前端仍用 `BrowserRouter` / `Routes`，没有上 SSR。vitest 已升到 4.1.11，见 [rb-16-vitest-mocker.md](rb-16-vitest-mocker.md)。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `admin-service/package.json` | Nest 11.2.3、config 4、jwt/passport 11；overrides 钉 body-parser/qs/file-type/decode-uri-component/stream-json/js-yaml |
| `admin-service/package-lock.json` | 重新锁定 |
| `admin-frontend/package.json` | `react-router-dom@7.18.4`；overrides 钉 js-yaml/nanoid |
| `admin-frontend/package-lock.json` | 重新锁定 |
| `.github/workflows/ci.yml` | Admin 两岗 `npm audit --audit-level=moderate --omit=dev` |
| 本文 + 审计索引 + 发版说明 | 挂链接 |

---

## 5. 怎么确认

```bash
cd admin-service && npm audit --omit=dev && npm test && npm run build
cd admin-frontend && npm audit --omit=dev && npm test && npm run build
```

- 两个目录生产 audit 都是 0
- Admin Service 13 个测试套件绿
- Admin 前端 vitest + `tsc -b && vite build` 绿

---

## 6. 再下一刀

PDF 页数/超时已单独实施，见 [ai-01-pdf-parse-bounds.md](ai-01-pdf-parse-bounds.md)。vitest mocker 见 [rb-16-vitest-mocker.md](rb-16-vitest-mocker.md)。

合入 `main` 后打 **`v1.2.2-rc.17`**（不要打在本分支上，不要打生产 `v1.2.2`）。
