export interface User {
  id: number
  username: string
  email: string | null
  nickname: string | null
  status: number
  createdAt: string
  updatedAt: string
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
