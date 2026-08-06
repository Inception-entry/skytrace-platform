import { authorizedFetch } from '@/api/http'

export interface Device {
  deviceCode: string
  deviceName: string
  deviceType: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface CreateDeviceInput {
  deviceCode: string
  deviceName: string
  deviceType: string
}

export interface UpdateDeviceInput {
  deviceName: string
  deviceType: string
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

export function getDevices() {
  return request<Device[]>('/api/devices')
}

export function getDevice(deviceCode: string) {
  return request<Device>(
    `/api/devices/${encodeURIComponent(deviceCode)}`,
  )
}

export function createDevice(input: CreateDeviceInput) {
  return request<Device>('/api/devices', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(input),
  })
}

export function updateDevice(
  deviceCode: string,
  input: UpdateDeviceInput,
) {
  return request<Device>(
    `/api/devices/${encodeURIComponent(deviceCode)}`,
    {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(input),
    },
  )
}

export function heartbeatDevice(deviceCode: string) {
  return request<{
    deviceCode: string
    status: string
    presence: string
  }>(
    `/api/devices/${encodeURIComponent(deviceCode)}/heartbeat`,
    { method: 'POST' },
  )
}

export function deleteDevice(deviceCode: string) {
  return request<null>(
    `/api/devices/${encodeURIComponent(deviceCode)}`,
    { method: 'DELETE' },
  )
}