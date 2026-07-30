import { useEffect, useState } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { ProLayout } from '@ant-design/pro-components'
import { Dropdown, message } from 'antd'
import { LogoutOutlined } from '@ant-design/icons'
import { useAuthStore } from '../store/auth'
import { me } from '../api/auth'
import { menuTreeToProLayout } from '../utils/menu'

export function AdminLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const { accessToken, user, setUser, logout } = useAuthStore()
  const [loading, setLoading] = useState(!user)

  useEffect(() => {
    if (!accessToken) { navigate('/login', { replace: true }); return }
    if (user) { setLoading(false); return }
    me()
      .then(setUser)
      .catch(() => { message.error('获取用户信息失败'); logout() })
      .finally(() => setLoading(false))
  }, [accessToken, user, navigate, setUser, logout])

  if (loading) return null

  const routes = { path: '/', routes: menuTreeToProLayout(user?.menus ?? []) }

  return (
    <ProLayout
      title="UAV 管理后台"
      logo={false}
      route={routes}
      location={{ pathname: location.pathname }}
      onMenuHeaderClick={() => navigate('/')}
      menuItemRender={(item, dom) => (
        <span onClick={() => item.path && !item.path.startsWith('/_cat_') && navigate(item.path)}>
          {dom}
        </span>
      )}
      avatarProps={{
        title: user?.nickname ?? user?.username ?? '',
        size: 'small',
        render: (_, dom) => (
          <Dropdown
            menu={{
              items: [{ key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: logout }],
            }}
          >
            {dom}
          </Dropdown>
        ),
      }}
    >
      <Outlet />
    </ProLayout>
  )
}
