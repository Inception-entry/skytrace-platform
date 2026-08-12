import { expect, test } from '@playwright/test'
import { loginAsOperator } from './helpers/auth'

test.describe('SkyTrace live map shell', () => {
  test('operator can open /map and see cesium host', async ({ page }) => {
    test.setTimeout(120_000)
    await loginAsOperator(page)

    await page.goto('/map')
    await expect(page.locator('.map-shell')).toBeVisible({ timeout: 30_000 })
    // Cesium 容器由 st-cesium-vue 挂载；至少保证地图壳与 canvas/容器出现
    await expect(
      page.locator('.map-shell canvas, .map-shell [class*="cesium"], .map-shell > div').first(),
    ).toBeVisible({ timeout: 60_000 })
  })
})
