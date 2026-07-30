import { ProForm, ProFormText } from '@ant-design/pro-components'
import { Card, message } from 'antd'
import { useNavigate } from 'react-router-dom'
import { login } from '../api/auth'
import { me } from '../api/auth'
import { useAuthStore } from '../store/auth'

export function LoginPage() {
  const navigate = useNavigate()
  const { setTokens, setUser } = useAuthStore()

  async function handleLogin(values: { username: string; password: string }) {
    try {
      const res = await login(values)
      setTokens(res.access_token, res.refresh_token)
      const user = await me()
      setUser(user)
      navigate('/', { replace: true })
    } catch {
      message.error('用户名或密码错误')
    }
  }

  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100vh',
        background: '#f0f2f5',
      }}
    >
      <Card title="UAV 管理后台" style={{ width: 400 }}>
        <ProForm
          onFinish={handleLogin}
          submitter={{ searchConfig: { submitText: '登 录' }, resetButtonProps: { style: { display: 'none' } } }}
        >
          <ProFormText
            name="username"
            label="用户名"
            fieldProps={{ autoComplete: 'username' }}
            rules={[{ required: true }]}
          />
          <ProFormText.Password
            name="password"
            label="密码"
            fieldProps={{ autoComplete: 'current-password' }}
            rules={[{ required: true }]}
          />
        </ProForm>
      </Card>
    </div>
  )
}
