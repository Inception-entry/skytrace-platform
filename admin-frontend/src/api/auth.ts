import client from './client'
import type { MeResponse } from '../types'
import { loginCookieSession } from './cookieSession'

export const login = loginCookieSession

export const me = () =>
  client.get<MeResponse>('/auth/me').then(r => r.data)

export const updateProfile = (data: { nickname?: string; email?: string; avatar?: string }) =>
  client.put<{ id: number; username: string; nickname: string | null; email: string | null; avatar: string | null }>(
    '/auth/profile',
    data,
  ).then(r => r.data)

export const changePassword = (data: { currentPassword: string; newPassword: string }) =>
  client.put('/auth/password', data)
