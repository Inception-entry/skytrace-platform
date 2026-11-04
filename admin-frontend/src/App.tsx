import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useEffect } from 'react'
import { Button, Result, Spin } from 'antd'
import { AdminLayout } from './layouts/AdminLayout'
import { LoginPage } from './pages/Login'
import { DashboardPage } from './pages/Dashboard'
import { UsersPage } from './pages/Users'
import { RolesPage } from './pages/Roles'
import { MenusPage } from './pages/Menus'
import { LogsPage } from './pages/Logs'
import { RequirePermission } from './components/RequirePermission'
import { useAuthStore } from './store/auth'
import { subscribeToSessionChange } from './store/sessionSignals'

function RequireAuth({ children }: { children: React.ReactNode }) {
  const { accessToken, user, status, restoreSession } = useAuthStore()
  useEffect(() => {
    if (status === 'unknown') void restoreSession()
  }, [status, restoreSession])

  if (status === 'unknown' || status === 'restoring' || status === 'logging-out') {
    return <div role="status" style={{ padding: 48, textAlign: 'center' }}>
      <Spin /> {status === 'logging-out' ? '正在退出登录…' : '正在恢复会话…'}
    </div>
  }
  if (status === 'unavailable') {
    return <Result status="warning" title="暂时无法恢复会话" subTitle="请检查网络后重试。"
      extra={<Button onClick={() => void restoreSession()}>重试</Button>} />
  }
  return accessToken && user ? <>{children}</> : <Navigate to="/login" replace />
}

export default function App() {
  useEffect(() => subscribeToSessionChange(() => {
    useAuthStore.getState().expireSession()
  }), [])
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/"
          element={
            <RequireAuth>
              <AdminLayout />
            </RequireAuth>
          }
        >
          <Route index element={<DashboardPage />} />
          <Route
            path="admin/users"
            element={
              <RequirePermission code="user:list">
                <UsersPage />
              </RequirePermission>
            }
          />
          <Route
            path="admin/roles"
            element={
              <RequirePermission code="role:list">
                <RolesPage />
              </RequirePermission>
            }
          />
          <Route
            path="admin/menus"
            element={
              <RequirePermission code="menu:list">
                <MenusPage />
              </RequirePermission>
            }
          />
          <Route
            path="admin/logs"
            element={
              <RequirePermission code="log:list">
                <LogsPage />
              </RequirePermission>
            }
          />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
