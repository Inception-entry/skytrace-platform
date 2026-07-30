import client from './client'
import type { Role, Paginated } from '../types'

interface ListParams { page?: number; pageSize?: number; name?: string }
interface CreatePayload { name: string; code: string; description?: string }
interface UpdatePayload { name?: string; description?: string; status?: number }

export const list = (p: ListParams) =>
  client.get<Paginated<Role>>('/roles', { params: p }).then(r => r.data)

export const get = (id: number) =>
  client.get<Role & { menuIds: number[] }>(`/roles/${id}`).then(r => r.data)

export const create = (data: CreatePayload) =>
  client.post<Role>('/roles', data).then(r => r.data)

export const update = (id: number, data: UpdatePayload) =>
  client.put<Role>(`/roles/${id}`, data).then(r => r.data)

export const remove = (id: number) =>
  client.delete(`/roles/${id}`)

export const getMenuIds = (id: number) =>
  client.get<number[]>(`/roles/${id}/menus`).then(r => r.data)

export const assignMenus = (id: number, menuIds: number[]) =>
  client.put(`/roles/${id}/menus`, { menuIds }).then(r => r.data)
