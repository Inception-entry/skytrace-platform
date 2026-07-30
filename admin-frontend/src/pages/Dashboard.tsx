import { useEffect, useState } from 'react'
import { PageContainer } from '@ant-design/pro-components'
import { Card, Col, Row, Statistic, Table, Typography } from 'antd'
import { TeamOutlined, SafetyCertificateOutlined, MenuOutlined, ClockCircleOutlined } from '@ant-design/icons'
import { useAuthStore } from '../store/auth'
import { stats } from '../api/dashboard'
import type { DashboardStats } from '../types'

export function DashboardPage() {
  const { user } = useAuthStore()
  const [data, setData] = useState<DashboardStats | null>(null)

  useEffect(() => {
    stats().then(setData).catch(() => {})
  }, [])

  const trendColumns = [
    { title: '日期', dataIndex: 'date', key: 'date' },
    { title: '登录次数', dataIndex: 'count', key: 'count' },
  ]

  return (
    <PageContainer title="仪表盘">
      <Typography.Title level={5} style={{ fontWeight: 400, marginBottom: 24 }}>
        欢迎回来，{user?.nickname ?? user?.username}
      </Typography.Title>

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="活跃用户"
              value={data?.userCount ?? '-'}
              prefix={<TeamOutlined />}
              valueStyle={{ color: '#1677ff' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="角色数量"
              value={data?.roleCount ?? '-'}
              prefix={<SafetyCertificateOutlined />}
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="菜单数量"
              value={data?.menuCount ?? '-'}
              prefix={<MenuOutlined />}
              valueStyle={{ color: '#fa8c16' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="活跃会话"
              value={data?.sessionCount ?? '-'}
              prefix={<ClockCircleOutlined />}
              valueStyle={{ color: '#722ed1' }}
            />
          </Card>
        </Col>
      </Row>

      <Card title="近 7 天登录趋势" style={{ marginTop: 16 }}>
        <Table
          dataSource={data?.loginTrend ?? []}
          columns={trendColumns}
          rowKey="date"
          pagination={false}
          size="small"
        />
      </Card>
    </PageContainer>
  )
}
