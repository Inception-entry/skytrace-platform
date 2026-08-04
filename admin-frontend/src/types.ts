export interface User {
  id: number
  username: string
  email: string | null
  nickname: string | null
  avatar: string | null
  status: number
  createdAt: string
  updatedAt: string
  roles?: Pick<Role, 'id' | 'name' | 'code'>[]
}

export interface Role {
  id: number
  name: string
  code: string
  description: string | null
  status: number
  menuIds?: number[]
}

export interface MenuNode {
  id: number
  name: string
  code: string
  path: string | null
  component: string | null
  icon: string | null
  type: number
  parentId: number | null
  sort: number
  visible: number
  children: MenuNode[]
}

export interface FlatMenu {
  id: number
  name: string
  code: string
  type: number
  parentId: number | null
  sort: number
  visible: number
  path: string | null
  icon: string | null
}

export interface MeResponse {
  id: number
  username: string
  nickname: string | null
  email: string | null
  avatar: string | null
  roles: Pick<Role, 'id' | 'name' | 'code'>[]
  permissions: string[]
  menus: MenuNode[]
}

export interface LoginResponse {
  access_token: string
  refresh_token: string
  expires_in: number
}

export interface Paginated<T> {
  data: T[]
  total: number
  page: number
  pageSize: number
}

export interface OperationLog {
  id: number
  userId: number
  username: string
  module: string
  action: string
  method: string
  path: string
  params: string | null
  ip: string | null
  status: number
  duration: number
  createdAt: string
}

export interface DashboardStats {
  userCount: number
  roleCount: number
  menuCount: number
  sessionCount: number
  loginTrend: { date: string; count: number }[]
}
