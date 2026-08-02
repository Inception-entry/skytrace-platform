import { expect, test } from '@playwright/test'

test.describe('SkyTrace smoke', () => {
  test('business frontend entry responds', async ({ page }) => {
    const response = await page.goto('/')
    expect(response, 'homepage should respond').not.toBeNull()
    expect(response!.status()).toBeLessThan(500)

    // Unauthenticated users may land on Keycloak or the app shell.
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
