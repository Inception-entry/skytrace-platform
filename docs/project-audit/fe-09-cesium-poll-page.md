# Cesium 销毁、轮询取消、任务列表分页

适用分支：`feat/cesium-poll-pagination`

Cesium 组件卸载时销毁自己创建的 Viewer，像素比上限为 2。设备静默刷新用序号丢掉过期响应，离开页面会中止请求。证据归档和任务状态轮询在卸载后不再预约下一次。任务状态轮询不再顺带重拉航线。

`GET /inspection-tasks` 不带 `page` 时仍返回完整列表。带 `page` 时返回 `{content,page,size,total}`，`size` 最大 100。设备和航线按编号批量查询。任务轨迹最多返回 2000 个点。
