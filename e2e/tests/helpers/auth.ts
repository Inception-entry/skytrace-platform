import { expect, type APIRequestContext, type Page } from '@playwright/test'

export const E2E_USERNAME = process.env.E2E_USERNAME || 'uav-operator'
export const E2E_PASSWORD =
  process.env.E2E_PASSWORD || process.env.KEYCLOAK_DEV_USER_PASSWORD || ''
export const KEYCLOAK_URL =
  process.env.KEYCLOAK_URL || 'http://127.0.0.1:8180'
export const KEYCLOAK_REALM = process.env.KEYCLOAK_REALM || 'uav'
export const KEYCLOAK_CLIENT_ID =
  process.env.KEYCLOAK_CLIENT_ID || 'uav-service'
export const KEYCLOAK_CLIENT_SECRET =
  process.env.KEYCLOAK_CLIENT_SECRET || ''
export const GATEWAY_URL =
  process.env.GATEWAY_URL || 'http://127.0.0.1:8082'

export async function loginAsOperator(page: Page) {
  if (!E2E_PASSWORD) {
    throw new Error(
      '缺少 E2E_PASSWORD 或 KEYCLOAK_DEV_USER_PASSWORD，无法登录 Keycloak',
    )
  }

  await page.goto('/drone')
  await page.waitForURL(/\/realms\/uav\/protocol\/openid-connect\/auth/, {
    timeout: 60_000,
  })

  await page.locator('#username').fill(E2E_USERNAME)
  await page.locator('#password').fill(E2E_PASSWORD)
  await page.locator('#kc-login').click()

  await page.waitForURL(/\/drone/, { timeout: 60_000 })
  await expect(page.getByRole('heading', { name: '无人机巡检任务' })).toBeVisible({
    timeout: 30_000,
  })
}

export async function fetchServiceAccessToken(
  request: APIRequestContext,
): Promise<string> {
  if (!KEYCLOAK_CLIENT_SECRET) {
    throw new Error('缺少 KEYCLOAK_CLIENT_SECRET')
  }

  const response = await request.post(
    `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`,
    {
      form: {
        grant_type: 'client_credentials',
        client_id: KEYCLOAK_CLIENT_ID,
        client_secret: KEYCLOAK_CLIENT_SECRET,
      },
    },
  )
  expect(response.ok(), 'service token request should succeed').toBeTruthy()
  const body = (await response.json()) as { access_token?: string }
  expect(body.access_token).toBeTruthy()
  return body.access_token!
}

export async function postDetection(
  request: APIRequestContext,
  token: string,
  payload: Record<string, unknown>,
) {
  const response = await request.post(`${GATEWAY_URL}/api/alarms/detections`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    data: payload,
  })
  expect(response.ok(), `detection status ${response.status()}`).toBeTruthy()
}

export async function waitForAlarm(
  request: APIRequestContext,
  token: string,
  taskCode: string,
  imageObjectKey: string,
  attempts = 45,
): Promise<string> {
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    const response = await request.get(`${GATEWAY_URL}/api/alarms/latest`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(response.ok()).toBeTruthy()
    const body = (await response.json()) as {
      data?: Array<{ taskCode?: string; imageUrl?: string; eventCode?: string }>
    }
    const match = (body.data || []).find(
      (item) =>
        item.taskCode === taskCode && item.imageUrl === imageObjectKey,
    )
    if (match?.eventCode) {
      return match.eventCode
    }
    await new Promise((resolve) => setTimeout(resolve, 1000))
  }
  throw new Error(`告警未落库：task=${taskCode} image=${imageObjectKey}`)
}
