import { authorizedFetch } from '@/api/http'

export interface Route {
  routeCode: string
  routeName: string
  description: string | null
  waypointsJson: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateRouteInput {
  routeCode: string
  routeName: string
  description?: string
  waypointsJson?: string
}

export interface UpdateRouteInput {
  routeName: string
  description?: string
  waypointsJson?: string
}

interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

async function request<T>(
  url: string,
  options?: RequestInit,
): Promise<T> {
  const response = await authorizedFetch(url, options)
  const result: ApiResponse<T> = await response.json()
  if (!response.ok || !result.success) {
    throw new Error(result.message || '请求失败')
  }
  return result.data
}

export function getRoutes() {
  return request<Route[]>('/api/routes')
}

export function createRoute(input: CreateRouteInput) {
  return request<Route>('/api/routes', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function updateRoute(routeCode: string, input: UpdateRouteInput) {
  return request<Route>(
    `/api/routes/${encodeURIComponent(routeCode)}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    },
  )
}
