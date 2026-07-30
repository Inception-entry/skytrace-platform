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

export const updateProfile = (data: { nickname?: string; email?: string; avatar?: string }) =>
  client.put<{ id: number; username: string; nickname: string | null; email: string | null; avatar: string | null }>(
    '/auth/profile',
    data,
  ).then(r => r.data)

export const changePassword = (data: { currentPassword: string; newPassword: string }) =>
  client.put('/auth/password', data)
