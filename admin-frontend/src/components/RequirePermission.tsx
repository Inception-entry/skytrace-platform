import { Result, Button } from 'antd'
import { Link } from 'react-router-dom'
import { usePermission } from '../hooks/usePermission'

export function RequirePermission({
  code,
  children,
}: {
  code: string
  children: React.ReactNode
}) {
  const { hasPermission } = usePermission()
  if (!hasPermission(code)) {
    return (
      <Result
        status="403"
        title="403"
        subTitle="当前账号没有访问该页面的权限"
        extra={
          <Link to="/">
            <Button type="primary">返回首页</Button>
          </Link>
        }
      />
    )
  }
  return <>{children}</>
}
