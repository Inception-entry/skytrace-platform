import { expect, test } from '@playwright/test'
import path from 'node:path'
import {
  fetchServiceAccessToken,
  loginAsOperator,
  postDetection,
  waitForAlarm,
} from './helpers/auth'

const fixtureImage = path.join(process.cwd(), 'tests/fixtures/evidence.png')

test.describe('SkyTrace smoke', () => {
  test('business frontend entry responds', async ({ page }) => {
    const response = await page.goto('/')
    expect(response, 'homepage should respond').not.toBeNull()
    expect(response!.status()).toBeLessThan(500)

    await expect(page.locator('body')).toBeVisible()
    const content = await page.content()
    expect(
      /SkyTrace|天巡|Keycloak|sign in|登录|username/i.test(content),
    ).toBeTruthy()
  })

  test('drone route is reachable after navigation attempt', async ({ page }) => {
    const response = await page.goto('/drone')
    expect(response, '/drone should respond').not.toBeNull()
    expect(response!.status()).toBeLessThan(500)
    await expect(page.locator('body')).toBeVisible()
  })
})

test.describe('SkyTrace login + alarm/evidence loop', () => {
  test('operator can create task, upload evidence, and alarm lands', async ({
    page,
    request,
  }) => {
    test.setTimeout(180_000)
    const taskCode = `E2E-${Date.now()}`

    await loginAsOperator(page)

    await page.getByRole('button', { name: '新建任务' }).click()
    await page.locator('input[placeholder="例如 TASK-006"]').fill(taskCode)
    await page
      .locator('input[placeholder="例如 东区输电线路巡检"]')
      .fill(`E2E 任务 ${taskCode}`)

    await page.locator('label', { hasText: '关联设备' }).locator('select')
      .selectOption({ label: /UAV-001/ })

    await page.locator('input[type="datetime-local"]').nth(0).fill('2030-01-01T08:00')
    await page.locator('input[type="datetime-local"]').nth(1).fill('2030-01-01T09:00')

    await page.getByRole('button', { name: '保存任务' }).click()
    await expect(page.getByText(taskCode).first()).toBeVisible({
      timeout: 30_000,
    })

    const row = page.locator('tr', { hasText: taskCode })
    await row.getByRole('button', { name: '启动' }).click()
    await expect(row.getByText('执行中')).toBeVisible({ timeout: 30_000 })

    await row.getByRole('button', { name: '证据' }).click()
    await expect(
      page.getByRole('heading', { name: new RegExp(`任务证据 · ${taskCode}`) }),
    ).toBeVisible()

    await page.locator('.evidence-actions input[type="file"]').setInputFiles(
      fixtureImage,
    )
    await expect(page.getByText('evidence.png')).toBeVisible({
      timeout: 30_000,
    })

    const openLink = page.locator('.evidence-list a.evidence-link').first()
    const href = await openLink.getAttribute('href')
    expect(href).toBeTruthy()
    // publicPath looks like /files/<bucket>/<objectKey>
    const objectKey = decodeURIComponent(
      (href || '').replace(/^\/files\/[^/]+\//, ''),
    )
    expect(objectKey.length).toBeGreaterThan(3)
    expect(objectKey).not.toContain('://')

    const token = await fetchServiceAccessToken(request)
    await postDetection(request, token, {
      deviceCode: 'UAV-001',
      taskCode,
      eventType: 'WEAPON_DETECTED',
      weaponType: 'KNIFE',
      confidence: 0.96,
      latitude: 31.2304,
      longitude: 121.4737,
      imageObjectKey: objectKey,
      eventTime: '2030-01-01T08:15:00',
    })

    const eventCode = await waitForAlarm(request, token, taskCode, objectKey)
    expect(eventCode).toBeTruthy()

    await row.getByRole('button', { name: '完成' }).click()
    await expect(row.getByText('已完成')).toBeVisible({ timeout: 30_000 })
  })
})
