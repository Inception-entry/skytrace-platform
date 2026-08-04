import type { ReactNode } from 'react'
import { Breadcrumb, Space, Typography } from 'antd'
import { Link, useLocation } from 'react-router-dom'

interface PageHeaderProps {
  title: string
  subTitle?: string
  extra?: ReactNode
}

const titleMap: Record<string, string> = {
  '/': '仪表盘',
  '/admin/users': '用户管理',
  '/admin/roles': '角色管理',
  '/admin/menus': '菜单管理',
  '/admin/logs': '操作日志',
}

export function PageHeader({ title, subTitle, extra }: PageHeaderProps) {
  const location = useLocation()
  const currentTitle = titleMap[location.pathname] ?? title

  return (
    <div className="skytrace-page-header">
      <Space direction="vertical" size={8}>
        <Breadcrumb
          items={[
            { title: <Link to="/">控制台</Link> },
            { title: currentTitle },
          ]}
        />
        <div>
          <Typography.Title level={3} style={{ margin: 0 }}>
            {title}
          </Typography.Title>
          {subTitle ? (
            <Typography.Paragraph type="secondary" style={{ margin: '6px 0 0' }}>
              {subTitle}
            </Typography.Paragraph>
          ) : null}
        </div>
      </Space>
      {extra ? <div>{extra}</div> : null}
    </div>
  )
}
