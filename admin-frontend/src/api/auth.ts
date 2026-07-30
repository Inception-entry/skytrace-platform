import client from './client'
import type { LoginResponse, MeResponse } from '../types'

export const login = (data: { username: string; password: string }) =>
  client.post<LoginResponse>('/auth/login', data).then(r => r.data)

export const refresh = (refreshToken: string) =>
  client.post<{ access_token: string; refresh_token: string; expires_in: number }>('/auth/refresh', { refresh_token: refreshToken }).then(r => r.data)

export const me = () =>
  client.get<MeResponse>('/auth/me').then(r => r.data)

export const logout = (refreshToken: string) =>
  client.post('/auth/logout', { refresh_token: refreshToken }).catch(() => {})
