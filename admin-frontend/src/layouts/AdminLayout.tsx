import { useEffect, useState } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { ProLayout, ProForm, ProFormText } from '@ant-design/pro-components'
import { Dropdown, Drawer, Avatar, Upload, Divider, Form, Input, Button, message } from 'antd'
import { LogoutOutlined, UserOutlined, UploadOutlined } from '@ant-design/icons'
import type { UploadChangeParam } from 'antd/es/upload'
import { useAuthStore } from '../store/auth'
import { me, updateProfile, changePassword } from '../api/auth'
import { uploadAvatar } from '../api/upload'
import { menuTreeToProLayout } from '../utils/menu'

export function AdminLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const { accessToken, user, setUser, logout } = useAuthStore()
  const [loading, setLoading] = useState(!user)
  const [profileOpen, setProfileOpen] = useState(false)
  const [profileForm] = Form.useForm()
  const [passwordForm] = Form.useForm()
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)

  useEffect(() => {
    if (!accessToken) { navigate('/login', { replace: true }); return }
    if (user) { setLoading(false); return }
    me()
      .then(setUser)
      .catch(() => { message.error('获取用户信息失败'); logout() })
      .finally(() => setLoading(false))
  }, [accessToken, user, navigate, setUser, logout])

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

  const routes = { path: '/', routes: menuTreeToProLayout(user?.menus ?? []) }

  return (
    <>
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
          src: user?.avatar ?? undefined,
          icon: !user?.avatar ? <UserOutlined /> : undefined,
          title: user?.nickname ?? user?.username ?? '',
          size: 'small',
          render: (_, dom) => (
            <Dropdown
              menu={{
                items: [
                  { key: 'profile', icon: <UserOutlined />, label: '个人中心', onClick: openProfile },
                  { type: 'divider' },
                  { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: logout },
                ],
              }}
            >
              {dom}
            </Dropdown>
          ),
        }}
      >
        <Outlet />
      </ProLayout>

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
        {/* Avatar */}
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <Avatar src={avatarUrl ?? undefined} icon={!avatarUrl ? <UserOutlined /> : undefined} size={80} />
          <div style={{ marginTop: 8 }}>
            <Upload
              accept="image/*"
              showUploadList={false}
              beforeUpload={() => false}
              onChange={handleAvatarChange}
            >
              <Button icon={<UploadOutlined />} size="small" loading={uploading}>
                更换头像
              </Button>
            </Upload>
          </div>
        </div>

        {/* Profile form */}
        <ProForm form={profileForm} submitter={false}>
          <ProFormText name="nickname" label="昵称" placeholder="请输入昵称" />
          <ProFormText name="email" label="邮箱" placeholder="请输入邮箱" rules={[{ type: 'email' }]} />
        </ProForm>

        <Divider>修改密码</Divider>

        <Form form={passwordForm} layout="vertical">
          <Form.Item name="currentPassword" label="当前密码" rules={[{ required: true }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[{ required: true }, { min: 6, message: '至少 6 位' }]}
          >
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
