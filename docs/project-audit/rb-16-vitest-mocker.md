# RB-16（Admin vitest mocker）实施说明

适用分支：`fix/v122-remaining`  
只升 **Admin 前端开发依赖 vitest**，关掉 `@vitest/mocker` 的路径穿越 advisory（CVE-2026-84373 / GHSA-82fw-gwwq-j7x9）。  
**不要** `npm audit fix --force`，不要打生产 `v1.2.2`。

旧编号：RB-16 开发链尾巴。生产 npm 见 [rb-16-admin-npm-advisories.md](rb-16-admin-npm-advisories.md)。

---

## 1. 一句话

vitest 3.2.4 带着 `@vitest/mocker` 3.2.4。3.x 不再维护，advisory 只修在 4.1.11 / 5.0.0-rc.2。管理前端的测试是 node environment，没有 SSR，但仍是 CI 会扫到的开发链 moderate。

本分支把 `admin-frontend` 的 `vitest` 钉到 `4.1.11`。

---

## 2. 现在怎么坏的

```text
admin-frontend vitest ^3.2.4
  @vitest/mocker 3.2.4
    GHSA-82fw-gwwq-j7x9  redirect mock 可读项目外文件
```

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| `npm audit fix --force` | 会把无关依赖打飞 |
| 升 vitest 5 rc | 预发线，独立验证 |
| 打生产 `v1.2.2` | 别的事 |

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `admin-frontend/package.json` | `vitest` 钉 `4.1.11` |
| `admin-frontend/package-lock.json` | 重新锁定 |
| 对应本文 + 发版说明 | 挂链接 |

---

## 5. 怎么确认

```bash
cd admin-frontend && npm test && npm run build
python3 - <<'PY'
import json
from pathlib import Path
lock = json.loads(Path("package-lock.json").read_text())
mocker = lock["packages"]["node_modules/@vitest/mocker"]["version"]
assert mocker == "4.1.11", mocker
print("mocker", mocker)
PY
```

- 现有 `src/**/*.test.ts` 仍绿
- lock 里 `@vitest/mocker` 是 4.1.11，不再是 3.2.4

---

## 6. 再下一刀

与 Evidence outbox、头像落盘、digest 同一次合入。合入 `main` 后打 **`v1.2.2-rc.30`**。
