import { useEffect, useMemo, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import {
  Avatar,
  Button,
  Divider,
  Drawer,
  Dropdown,
  Form,
  Input,
  Layout,
  Menu,
  Switch,
  Upload,
  message,
} from 'antd'
import { LogoutOutlined, MenuFoldOutlined, MenuUnfoldOutlined, MoonOutlined, SunOutlined, UploadOutlined, UserOutlined } from '@ant-design/icons'
import type { UploadChangeParam } from 'antd/es/upload'
import { useAuthStore } from '../store/auth'
import { useThemeStore } from '../store/theme'
import { me, updateProfile, changePassword } from '../api/auth'
import { uploadAvatar } from '../api/upload'
import { collectOpenKeys, menuTreeToItems } from '../utils/menu'
import { SkyTraceLogo } from '../components/SkyTraceLogo'

const { Header, Sider, Content } = Layout

export function AdminLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const { accessToken, user, setUser, logout } = useAuthStore()
  const { mode, toggleMode } = useThemeStore()
  const [loading, setLoading] = useState(!user)
  const [collapsed, setCollapsed] = useState(false)
  const [openKeys, setOpenKeys] = useState<string[]>([])
  const [profileOpen, setProfileOpen] = useState(false)
  const [profileForm] = Form.useForm()
  const [passwordForm] = Form.useForm()
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)

  useEffect(() => {
    if (!accessToken) {
      navigate('/login', { replace: true })
      return
    }
    if (user) {
      setLoading(false)
      return
    }
    me()
      .then(setUser)
      .catch(() => {
        message.error('获取用户信息失败')
        logout()
      })
      .finally(() => setLoading(false))
  }, [accessToken, logout, navigate, setUser, user])

  const menuItems = useMemo(() => menuTreeToItems(user?.menus ?? []), [user?.menus])
  const derivedOpenKeys = useMemo(() => collectOpenKeys(user?.menus ?? [], location.pathname), [location.pathname, user?.menus])

  useEffect(() => {
    setOpenKeys(derivedOpenKeys)
  }, [derivedOpenKeys])

  function openProfile() {
    profileForm.setFieldsValue({ nickname: user?.nickname ?? '', email: user?.email ?? '' })
    setAvatarUrl(user?.avatar ?? null)
    setProfileOpen(true)
  }

  async function handleAvatarChange(info: UploadChangeParam) {
    const file = info.file.originFileObj
    if (!file) return
    setUploading(true)
    try {
      const url = await uploadAvatar(file)
      setAvatarUrl(url)
      message.success('头像上传成功')
    } catch {
      message.error('头像上传失败')
    } finally {
      setUploading(false)
    }
  }

  async function handleSaveProfile() {
    const values = await profileForm.validateFields()
    try {
      const updated = await updateProfile({ ...values, avatar: avatarUrl ?? undefined })
      setUser({ ...user!, ...updated })
      message.success('资料保存成功')
      setProfileOpen(false)
    } catch {
      message.error('保存失败')
    }
  }

  async function handleChangePassword() {
    const values = await passwordForm.validateFields()
    try {
      await changePassword(values)
      message.success('密码修改成功，请重新登录')
      passwordForm.resetFields()
      setProfileOpen(false)
      logout()
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
      message.error(msg ?? '密码修改失败')
    }
  }

  if (loading) return null

  return (
    <>
      <Layout className="skytrace-admin-shell">
        <Sider
          className="skytrace-admin-sider"
          collapsed={collapsed}
          collapsible
          trigger={null}
          width={248}
        >
          <button className="skytrace-admin-brand" type="button" onClick={() => navigate('/')}>
            <SkyTraceLogo size={28} />
            {!collapsed ? <span>SkyTrace</span> : null}
          </button>
          <Menu
            mode="inline"
            items={menuItems}
            selectedKeys={[location.pathname]}
            openKeys={collapsed ? [] : openKeys}
            onOpenChange={keys => setOpenKeys(keys as string[])}
            onClick={({ key }) => !String(key).startsWith('/_cat_') && navigate(String(key))}
            className="skytrace-admin-menu"
          />
        </Sider>

        <Layout>
          <Header className="skytrace-admin-header">
            <div className="skytrace-admin-header__left">
              <Button
                type="text"
                icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                onClick={() => setCollapsed(value => !value)}
                aria-label="切换侧边栏"
              />
            </div>

            <div className="skytrace-admin-header__right">
              <span className="skytrace-theme-switch-label">Admin Console</span>
              <div className="skytrace-theme-switch">
                <SunOutlined className={mode === 'light' ? 'is-active' : undefined} />
                <Switch size="small" checked={mode === 'dark'} onChange={toggleMode} aria-label="切换深浅主题" />
                <MoonOutlined className={mode === 'dark' ? 'is-active' : undefined} />
              </div>

              <Dropdown
                menu={{
                  items: [
                    { key: 'profile', icon: <UserOutlined />, label: '个人中心', onClick: openProfile },
                    { type: 'divider' },
                    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: logout },
                  ],
                }}
              >
                <button type="button" className="skytrace-admin-account">
                  <Avatar src={user?.avatar ?? undefined} icon={!user?.avatar ? <UserOutlined /> : undefined} size="small" />
                  <span>{user?.nickname ?? user?.username ?? ''}</span>
                </button>
              </Dropdown>
            </div>
          </Header>

          <Content className="skytrace-admin-content">
            <Outlet />
          </Content>
        </Layout>
      </Layout>

      <Drawer
        title="个人中心"
        open={profileOpen}
        onClose={() => setProfileOpen(false)}
        width={420}
        footer={
          <Button type="primary" block onClick={handleSaveProfile}>
            保存资料
          </Button>
        }
      >
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <Avatar src={avatarUrl ?? undefined} icon={!avatarUrl ? <UserOutlined /> : undefined} size={80} />
          <div style={{ marginTop: 8 }}>
            <Upload accept="image/*" showUploadList={false} beforeUpload={() => false} onChange={handleAvatarChange}>
              <Button icon={<UploadOutlined />} size="small" loading={uploading}>
                更换头像
              </Button>
            </Upload>
          </div>
        </div>

        <Form form={profileForm} layout="vertical">
          <Form.Item name="nickname" label="昵称">
            <Input placeholder="请输入昵称" />
          </Form.Item>
          <Form.Item name="email" label="邮箱" rules={[{ type: 'email' }]}>
            <Input placeholder="请输入邮箱" />
          </Form.Item>
        </Form>

        <Divider>修改密码</Divider>

        <Form form={passwordForm} layout="vertical">
          <Form.Item name="currentPassword" label="当前密码" rules={[{ required: true }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="newPassword" label="新密码" rules={[{ required: true }, { min: 6, message: '至少 6 位' }]}>
            <Input.Password />
          </Form.Item>
          <Button onClick={handleChangePassword} block>
            修改密码
          </Button>
        </Form>
      </Drawer>
    </>
  )
}
