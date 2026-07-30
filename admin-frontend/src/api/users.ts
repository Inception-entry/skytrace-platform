import client from './client'
import type { User, Paginated } from '../types'

interface ListParams { page?: number; pageSize?: number; username?: string }
interface CreatePayload { username: string; password: string; email?: string; nickname?: string; status?: number }
interface UpdatePayload { password?: string; email?: string; nickname?: string; status?: number }

export const list = (p: ListParams) =>
  client.get<Paginated<User>>('/users', { params: p }).then(r => r.data)

export const get = (id: number) =>
  client.get<User>(`/users/${id}`).then(r => r.data)

export const create = (data: CreatePayload) =>
  client.post<User>('/users', data).then(r => r.data)

export const update = (id: number, data: UpdatePayload) =>
  client.put<User>(`/users/${id}`, data).then(r => r.data)

export const remove = (id: number) =>
  client.delete(`/users/${id}`)

export const assignRoles = (id: number, roleIds: number[]) =>
  client.put(`/users/${id}/roles`, { roleIds }).then(r => r.data)
