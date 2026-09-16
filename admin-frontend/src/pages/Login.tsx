import { useState } from 'react'
import { Button, Card, Form, Input, message } from 'antd'
import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { login } from '../api/auth'
import {
  establishAdminSession,
  loadAdminProfile,
  loginFailureMessage,
} from '../api/sessionEstablish'
import {
  reportServerRevocationFailure,
  revokeAdminSession,
} from '../api/sessionRevoke'
import { useAuthStore } from '../store/auth'
import { SkyTraceLogo } from '../components/SkyTraceLogo'

export function LoginPage() {
  const navigate = useNavigate()
  const { setTokens, setUser } = useAuthStore()
  const [submitting, setSubmitting] = useState(false)

  async function handleLogin(values: { username: string; password: string }) {
    if (submitting) return
    setSubmitting(true)
    try {
      await establishAdminSession(
        {
          login,
          loadMe: loadAdminProfile,
          commit: (tokens, user) => {
            setTokens(tokens.access_token, tokens.refresh_token)
            setUser(user)
          },
          rollback: revokeAdminSession,
          onRollbackFailure: reportServerRevocationFailure,
        },
        values,
      )
      navigate('/', { replace: true })
    } catch (error) {
      message.error(loginFailureMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="skytrace-login">
      <aside className="skytrace-login__brand">
        <div className="skytrace-login__brand-inner">
          <div className="skytrace-login__eyebrow">
            <SkyTraceLogo size={28} />
            SkyTrace Platform
          </div>
          <h1 className="skytrace-login__title">
            天巡智控
            <br />
            管理控制台
          </h1>
          <p className="skytrace-login__desc">统一账号、角色与菜单权限，支撑巡检业务与运维后台的安全值守。</p>
        </div>
        <div className="skytrace-login__footer">SkyTrace Admin · Internal Use</div>
      </aside>

      <main className="skytrace-login__panel">
        <Card className="skytrace-login__card" bordered={false}>
          <h2>欢迎登录</h2>
          <p>请使用管理员账号进入后台</p>
          <Form layout="vertical" onFinish={handleLogin}>
            <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
              <Input size="large" prefix={<UserOutlined className="skytrace-input-icon" />} autoComplete="username" placeholder="用户名" />
            </Form.Item>
            <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password size="large" prefix={<LockOutlined className="skytrace-input-icon" />} autoComplete="current-password" placeholder="密码" />
            </Form.Item>
            <Button type="primary" htmlType="submit" size="large" block loading={submitting} disabled={submitting}>
              登 录
            </Button>
          </Form>
        </Card>
      </main>
    </div>
  )
}
