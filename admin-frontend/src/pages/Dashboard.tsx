import { useEffect, useState } from 'react'
import { Card, Col, Row, Space, Statistic, Table, Typography } from 'antd'
import { ClockCircleOutlined, MenuOutlined, SafetyCertificateOutlined, TeamOutlined } from '@ant-design/icons'
import { useAuthStore } from '../store/auth'
import { stats } from '../api/dashboard'
import type { DashboardStats } from '../types'
import { PageHeader } from '../components/PageHeader'

const cards = [
  { key: 'user', title: '活跃用户', icon: <TeamOutlined />, color: '#1677ff', get: (d: DashboardStats | null) => d?.userCount },
  { key: 'role', title: '角色数量', icon: <SafetyCertificateOutlined />, color: '#16a34a', get: (d: DashboardStats | null) => d?.roleCount },
  { key: 'menu', title: '菜单数量', icon: <MenuOutlined />, color: '#f59e0b', get: (d: DashboardStats | null) => d?.menuCount },
  { key: 'session', title: '活跃会话', icon: <ClockCircleOutlined />, color: '#6366f1', get: (d: DashboardStats | null) => d?.sessionCount },
] as const

export function DashboardPage() {
  const { user } = useAuthStore()
  const [data, setData] = useState<DashboardStats | null>(null)

  useEffect(() => {
    stats().then(setData).catch(() => {})
  }, [])

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <PageHeader title="仪表盘" subTitle={`欢迎回来，${user?.nickname ?? user?.username ?? ''}`} />

      <Row gutter={[16, 16]}>
        {cards.map(card => (
          <Col xs={24} sm={12} md={6} key={card.key}>
            <Card className="skytrace-stat-card" bordered={false}>
              <Statistic
                title={card.title}
                value={card.get(data) ?? '-'}
                prefix={
                  <span
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      width: 32,
                      height: 32,
                      borderRadius: 8,
                      marginRight: 4,
                      background: `${card.color}14`,
                      color: card.color,
                      fontSize: 16,
                    }}
                  >
                    {card.icon}
                  </span>
                }
                valueStyle={{ color: 'var(--sky-title)', fontWeight: 650 }}
              />
            </Card>
          </Col>
        ))}
      </Row>

      <Card
        className="skytrace-stat-card"
        bordered={false}
        title={<Typography.Text strong>近 7 天登录趋势</Typography.Text>}
      >
        <Table
          dataSource={data?.loginTrend ?? []}
          columns={[
            { title: '日期', dataIndex: 'date', key: 'date' },
            { title: '登录次数', dataIndex: 'count', key: 'count' },
          ]}
          rowKey="date"
          pagination={false}
          size="middle"
        />
      </Card>
    </Space>
  )
}
