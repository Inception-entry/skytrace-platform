import { expect, test } from '@playwright/test'
import { loginAsOperator } from './helpers/auth'

test.describe('SkyTrace routes + task state UI', () => {
  test('operator can open routes page and see seeded route preview', async ({
    page,
  }) => {
    test.setTimeout(120_000)
    await loginAsOperator(page)

    await page.goto('/routes')
    await expect(page.getByRole('heading', { name: '航线管理' })).toBeVisible({
      timeout: 30_000,
    })
    await expect(page.getByText('ROUTE-001').first()).toBeVisible({
      timeout: 30_000,
    })
    // 列表缩略图列：有航点时渲染 SVG，无航点显示「无航点」
    await expect(page.locator('.route-thumb').first()).toBeVisible()
  })

  test('operator can create task with route, complete it, and see replay button', async ({
    page,
  }) => {
    test.setTimeout(180_000)
    const taskCode = `E2E-RT-${Date.now()}`

    await loginAsOperator(page)

    await page.getByRole('button', { name: '新建任务' }).click()
    await page.locator('input[placeholder="例如 TASK-006"]').fill(taskCode)
    await page
      .locator('input[placeholder="例如 东区输电线路巡检"]')
      .fill(`航线回放任务 ${taskCode}`)

    await page.locator('label', { hasText: '关联设备' }).locator('select')
      .selectOption({ value: 'UAV-001' })
    await page.locator('label', { hasText: '关联航线' }).locator('select')
      .selectOption({ value: 'ROUTE-001' })

    await page.locator('input[type="datetime-local"]').nth(0).fill('2030-02-01T08:00')
    await page.locator('input[type="datetime-local"]').nth(1).fill('2030-02-01T09:00')

    await page.getByRole('button', { name: '保存任务' }).click()
    await expect(page.getByText(taskCode).first()).toBeVisible({
      timeout: 30_000,
    })

    const row = page.locator('tr', { hasText: taskCode })
    await expect(row.getByText('ROUTE-001')).toBeVisible()
    // 绑定航线后应有缩略图
    await expect(row.locator('.route-thumb')).toBeVisible()

    await row.getByRole('button', { name: '启动' }).click()
    await expect(row.getByText('执行中')).toBeVisible({ timeout: 30_000 })
    await expect(row.getByRole('link', { name: '实时地图' })).toBeVisible()

    await row.getByRole('button', { name: '完成' }).click()
    await expect(row.getByText('已完成')).toBeVisible({ timeout: 30_000 })

    await row.getByRole('button', { name: '轨迹回放' }).click()
    await expect(
      page.getByRole('heading', { name: new RegExp(`飞行轨迹回放 · ${taskCode}`) }),
    ).toBeVisible({ timeout: 30_000 })
    // 无 MQTT 落点时为空态；有点则显示地图。两种都算 UI 接通。
    await expect(
      page.getByText(/该任务暂无遥测轨迹|加载轨迹中|播放|高度/),
    ).toBeVisible({ timeout: 30_000 })
  })
})
