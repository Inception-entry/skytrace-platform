import { PageContainer } from '@ant-design/pro-components'
import { Typography } from 'antd'
import { useAuthStore } from '../store/auth'

export function DashboardPage() {
  const { user } = useAuthStore()
  return (
    <PageContainer title="仪表盘">
      <Typography.Title level={4} style={{ fontWeight: 400 }}>
        欢迎回来，{user?.nickname ?? user?.username}
      </Typography.Title>
    </PageContainer>
  )
}
