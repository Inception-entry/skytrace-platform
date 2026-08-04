import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AdminLayout } from './layouts/AdminLayout'
import { LoginPage } from './pages/Login'
import { DashboardPage } from './pages/Dashboard'
import { UsersPage } from './pages/Users'
import { RolesPage } from './pages/Roles'
import { MenusPage } from './pages/Menus'
import { LogsPage } from './pages/Logs'
import { RequirePermission } from './components/RequirePermission'
import { useAuthStore } from './store/auth'

function RequireAuth({ children }: { children: React.ReactNode }) {
  const { accessToken } = useAuthStore()
  return accessToken ? <>{children}</> : <Navigate to="/login" replace />
}

export default function App() {
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
