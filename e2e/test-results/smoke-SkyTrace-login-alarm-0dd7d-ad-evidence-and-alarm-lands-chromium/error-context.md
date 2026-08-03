# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: smoke.spec.ts >> SkyTrace login + alarm/evidence loop >> operator can create task, upload evidence, and alarm lands
- Location: tests/smoke.spec.ts:34:7

# Error details

```
TimeoutError: page.waitForURL: Timeout 60000ms exceeded.
=========================== logs ===========================
waiting for navigation until "load"
  navigated to "http://localhost:8180/realms/uav/login-actions/authenticate?execution=7aac3c7e-776d-4a75-9256-51ebc55637cc&client_id=uav-web&tab_id=AAcbKP_siHw&client_data=eyJydSI6Imh0dHA6Ly8xMjcuMC4wLjE6ODg4OC9kcm9uZSIsInJ0IjoiY29kZSIsInJtIjoiZnJhZ21lbnQiLCJzdCI6IjBmNjJjMzFkLTYzNzktNGIwNS04ZDg0LWQ1ZDg2NDM2MDgwZCJ9"
============================================================
```

# Page snapshot

```yaml
- generic [ref=f2e3]:
  - banner [ref=f2e4]:
    - generic [ref=f2e5]: SkyTrace 天巡智控
  - main [ref=f2e6]:
    - heading "We are sorry..." [level=1] [ref=f2e8]
    - paragraph [ref=f2e11]: Unexpected error when handling authentication request to identity provider.
```

# Test source

```ts
  1   | import { expect, type APIRequestContext, type Page } from '@playwright/test'
  2   | 
  3   | export const E2E_USERNAME = process.env.E2E_USERNAME || 'uav-operator'
  4   | export const E2E_PASSWORD =
  5   |   process.env.E2E_PASSWORD || process.env.KEYCLOAK_DEV_USER_PASSWORD || ''
  6   | export const KEYCLOAK_URL =
  7   |   process.env.KEYCLOAK_URL || 'http://127.0.0.1:8180'
  8   | export const KEYCLOAK_REALM = process.env.KEYCLOAK_REALM || 'uav'
  9   | export const KEYCLOAK_CLIENT_ID =
  10  |   process.env.KEYCLOAK_CLIENT_ID || 'uav-service'
  11  | export const KEYCLOAK_CLIENT_SECRET =
  12  |   process.env.KEYCLOAK_CLIENT_SECRET || ''
  13  | export const GATEWAY_URL =
  14  |   process.env.GATEWAY_URL || 'http://127.0.0.1:8082'
  15  | 
  16  | export async function loginAsOperator(page: Page) {
  17  |   if (!E2E_PASSWORD) {
  18  |     throw new Error(
  19  |       '缺少 E2E_PASSWORD 或 KEYCLOAK_DEV_USER_PASSWORD，无法登录 Keycloak',
  20  |     )
  21  |   }
  22  | 
  23  |   await page.goto('/drone')
  24  |   await page.waitForURL(/\/realms\/uav\/protocol\/openid-connect\/auth/, {
  25  |     timeout: 60_000,
  26  |   })
  27  | 
  28  |   await page.locator('#username').fill(E2E_USERNAME)
  29  |   await page.locator('#password').fill(E2E_PASSWORD)
  30  |   await page.locator('#kc-login').click()
  31  | 
> 32  |   await page.waitForURL(/\/drone/, { timeout: 60_000 })
      |              ^ TimeoutError: page.waitForURL: Timeout 60000ms exceeded.
  33  |   await expect(page.getByRole('heading', { name: '无人机巡检任务' })).toBeVisible({
  34  |     timeout: 30_000,
  35  |   })
  36  | }
  37  | 
  38  | export async function fetchServiceAccessToken(
  39  |   request: APIRequestContext,
  40  | ): Promise<string> {
  41  |   if (!KEYCLOAK_CLIENT_SECRET) {
  42  |     throw new Error('缺少 KEYCLOAK_CLIENT_SECRET')
  43  |   }
  44  | 
  45  |   const response = await request.post(
  46  |     `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`,
  47  |     {
  48  |       form: {
  49  |         grant_type: 'client_credentials',
  50  |         client_id: KEYCLOAK_CLIENT_ID,
  51  |         client_secret: KEYCLOAK_CLIENT_SECRET,
  52  |       },
  53  |     },
  54  |   )
  55  |   expect(response.ok(), 'service token request should succeed').toBeTruthy()
  56  |   const body = (await response.json()) as { access_token?: string }
  57  |   expect(body.access_token).toBeTruthy()
  58  |   return body.access_token!
  59  | }
  60  | 
  61  | export async function postDetection(
  62  |   request: APIRequestContext,
  63  |   token: string,
  64  |   payload: Record<string, unknown>,
  65  | ) {
  66  |   const response = await request.post(`${GATEWAY_URL}/api/alarms/detections`, {
  67  |     headers: {
  68  |       Authorization: `Bearer ${token}`,
  69  |       'Content-Type': 'application/json',
  70  |     },
  71  |     data: payload,
  72  |   })
  73  |   expect(response.ok(), `detection status ${response.status()}`).toBeTruthy()
  74  | }
  75  | 
  76  | export async function waitForAlarm(
  77  |   request: APIRequestContext,
  78  |   token: string,
  79  |   taskCode: string,
  80  |   imageObjectKey: string,
  81  |   attempts = 45,
  82  | ): Promise<string> {
  83  |   for (let attempt = 1; attempt <= attempts; attempt += 1) {
  84  |     const response = await request.get(`${GATEWAY_URL}/api/alarms/latest`, {
  85  |       headers: { Authorization: `Bearer ${token}` },
  86  |     })
  87  |     expect(response.ok()).toBeTruthy()
  88  |     const body = (await response.json()) as {
  89  |       data?: Array<{ taskCode?: string; imageUrl?: string; eventCode?: string }>
  90  |     }
  91  |     const match = (body.data || []).find(
  92  |       (item) =>
  93  |         item.taskCode === taskCode && item.imageUrl === imageObjectKey,
  94  |     )
  95  |     if (match?.eventCode) {
  96  |       return match.eventCode
  97  |     }
  98  |     await new Promise((resolve) => setTimeout(resolve, 1000))
  99  |   }
  100 |   throw new Error(`告警未落库：task=${taskCode} image=${imageObjectKey}`)
  101 | }
  102 | 
```