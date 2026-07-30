import { useRef } from 'react'
import { ProTable, PageContainer } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, Tag, message } from 'antd'
import { ClearOutlined } from '@ant-design/icons'
import * as logsApi from '../api/logs'
import type { OperationLog } from '../types'

const methodColor: Record<string, string> = {
  POST: 'blue', PUT: 'orange', DELETE: 'red', GET: 'green',
}

export function LogsPage() {
  const actionRef = useRef<ActionType>()

  const columns: ProColumns<OperationLog>[] = [
    { dataIndex: 'id', title: 'ID', width: 60, search: false },
    { dataIndex: 'username', title: '操作人', width: 100 },
    { dataIndex: 'module', title: '模块', width: 100 },
    { dataIndex: 'action', title: '操作', width: 80, search: false },
    {
      dataIndex: 'method', title: '请求方式', width: 90, search: false,
      render: (_, r) => <Tag color={methodColor[r.method] ?? 'default'}>{r.method}</Tag>,
    },
    { dataIndex: 'path', title: '请求路径', search: false, ellipsis: true },
    { dataIndex: 'ip', title: 'IP', width: 120, search: false },
    {
      dataIndex: 'status', title: '响应码', width: 80, search: false,
      render: (_, r) => <Tag color={r.status < 400 ? 'success' : 'error'}>{r.status}</Tag>,
    },
    { dataIndex: 'duration', title: '耗时(ms)', width: 80, search: false },
    {
      dataIndex: 'createdAt', title: '时间', width: 160, search: false, valueType: 'dateTime',
    },
    {
      title: '时间范围', hideInTable: true, dataIndex: 'timeRange', valueType: 'dateRange',
      fieldProps: { placeholder: ['开始时间', '结束时间'] },
    },
  ]

  return (
    <PageContainer title="操作日志">
      <ProTable<OperationLog>
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        request={async params => {
          const { current, pageSize, username, module: mod, timeRange } = params as {
            current?: number; pageSize?: number; username?: string; module?: string
            timeRange?: [string, string]
          }
          const res = await logsApi.list({
            page: current,
            pageSize,
            username,
            module: mod,
            startTime: timeRange?.[0],
            endTime: timeRange?.[1],
          })
          return { data: res.data, total: res.total, success: true }
        }}
        toolBarRender={() => [
          <Popconfirm
            key="clear"
            title="确定清空所有操作日志吗？"
            onConfirm={async () => {
              const res = await logsApi.clear()
              message.success(`已清除 ${res.count} 条日志`)
              actionRef.current?.reload()
            }}
          >
            <Button icon={<ClearOutlined />} danger>清空日志</Button>
          </Popconfirm>,
        ]}
        pagination={{ pageSize: 20, showSizeChanger: true }}
      />
    </PageContainer>
  )
}
