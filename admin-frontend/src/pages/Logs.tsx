import { useEffect, useState } from 'react'
import { Button, Card, DatePicker, Form, Input, Popconfirm, Space, Table, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { ClearOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import * as logsApi from '../api/logs'
import type { OperationLog } from '../types'
import { PageHeader } from '../components/PageHeader'
import { usePermission } from '../hooks/usePermission'

const { RangePicker } = DatePicker

const methodColor: Record<string, string> = {
  POST: 'blue',
  PUT: 'orange',
  DELETE: 'red',
  GET: 'green',
}

export function LogsPage() {
  const { hasPermission } = usePermission()
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [logs, setLogs] = useState<OperationLog[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [filters, setFilters] = useState<{
    username?: string
    module?: string
    startTime?: string
    endTime?: string
  }>({})

  async function loadData(nextPage = page, nextPageSize = pageSize, nextFilters = filters) {
    setLoading(true)
    try {
      const res = await logsApi.list({
        page: nextPage,
        pageSize: nextPageSize,
        username: nextFilters.username,
        module: nextFilters.module,
        startTime: nextFilters.startTime,
        endTime: nextFilters.endTime,
      })
      setLogs(res.data)
      setTotal(res.total)
      setPage(res.page)
      setPageSize(res.pageSize)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadData()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const columns: ColumnsType<OperationLog> = [
    { dataIndex: 'id', title: 'ID', width: 72 },
    { dataIndex: 'username', title: '操作人', width: 120 },
    { dataIndex: 'module', title: '模块', width: 120 },
    { dataIndex: 'action', title: '操作', width: 120 },
    {
      dataIndex: 'method',
      title: '请求方式',
      width: 96,
      render: value => <Tag color={methodColor[value] ?? 'default'}>{value}</Tag>,
    },
    { dataIndex: 'path', title: '请求路径', ellipsis: true },
    { dataIndex: 'ip', title: 'IP', width: 140, render: value => value || '-' },
    {
      dataIndex: 'status',
      title: '响应码',
      width: 96,
      render: value => <Tag color={value < 400 ? 'success' : 'error'}>{value}</Tag>,
    },
    { dataIndex: 'duration', title: '耗时(ms)', width: 110 },
    { dataIndex: 'createdAt', title: '时间', width: 180 },
  ]

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <PageHeader title="操作日志" subTitle="查询后台操作记录、请求状态与耗时表现" />

      <Card className="skytrace-panel">
        <Form
          form={form}
          layout="inline"
          onFinish={values => {
            const nextFilters = {
              username: values.username || undefined,
              module: values.module || undefined,
              startTime: values.timeRange?.[0]?.format?.('YYYY-MM-DD HH:mm:ss') || undefined,
              endTime: values.timeRange?.[1]?.format?.('YYYY-MM-DD HH:mm:ss') || undefined,
            }
            setFilters(nextFilters)
            loadData(1, pageSize, nextFilters)
          }}
        >
          <Form.Item name="username" label="操作人">
            <Input placeholder="搜索操作人" allowClear />
          </Form.Item>
          <Form.Item name="module" label="模块">
            <Input placeholder="搜索模块" allowClear />
          </Form.Item>
          <Form.Item name="timeRange" label="时间范围">
            <RangePicker showTime format="YYYY-MM-DD HH:mm:ss" />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                查询
              </Button>
              <Button
                icon={<ReloadOutlined />}
                onClick={() => {
                  form.resetFields()
                  const nextFilters = {}
                  setFilters(nextFilters)
                  loadData(1, pageSize, nextFilters)
                }}
              >
                重置
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card
        className="skytrace-panel"
        title="日志列表"
        extra={
          hasPermission('log:clear') ? (
            <Popconfirm
              title="确定清空所有操作日志吗？"
              onConfirm={async () => {
                const res = await logsApi.clear()
                message.success(`已清除 ${res.count} 条日志`)
                loadData()
              }}
            >
              <Button icon={<ClearOutlined />} danger>
                清空日志
              </Button>
            </Popconfirm>
          ) : null
        }
      >
        <Table
          rowKey="id"
          loading={loading}
          dataSource={logs}
          columns={columns}
          scroll={{ x: 1120 }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            onChange: (nextPage, nextPageSize) => loadData(nextPage, nextPageSize, filters),
          }}
        />
      </Card>
    </Space>
  )
}
