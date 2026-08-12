import { expect, test } from '@playwright/test'
import { loginAsOperator } from './helpers/auth'

test.describe('SkyTrace live map shell', () => {
  test('operator can open /map and see cesium host', async ({ page }) => {
    test.setTimeout(120_000)
    await loginAsOperator(page)

    await page.goto('/map')
    await expect(page.locator('.map-shell')).toBeVisible({ timeout: 30_000 })
    // 不要用 `.map-shell > div`：Cesium loaded 后首个子节点是隐藏的 UI overlay，
    // `.first()` 会命中它并误报 hidden。只认 Cesium 自己的 viewer / canvas。
    await expect(
      page.locator('.map-shell .cesium-viewer, .map-shell canvas').first(),
    ).toBeVisible({ timeout: 60_000 })
  })
})
