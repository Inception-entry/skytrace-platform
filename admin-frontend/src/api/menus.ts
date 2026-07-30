import client from './client'
import type { MenuNode, FlatMenu } from '../types'

interface CreatePayload {
  name: string; code: string; type: number; path?: string
  component?: string; icon?: string; parentId?: number; sort?: number; visible?: number
}
interface UpdatePayload {
  name?: string; path?: string; component?: string
  icon?: string; sort?: number; visible?: number
}

export const tree = () =>
  client.get<MenuNode[]>('/menus').then(r => r.data)

export const flat = () =>
  client.get<FlatMenu[]>('/menus/flat').then(r => r.data)

export const get = (id: number) =>
  client.get<FlatMenu>(`/menus/${id}`).then(r => r.data)

export const create = (data: CreatePayload) =>
  client.post<FlatMenu>('/menus', data).then(r => r.data)

export const update = (id: number, data: UpdatePayload) =>
  client.put<FlatMenu>(`/menus/${id}`, data).then(r => r.data)

export const remove = (id: number) =>
  client.delete(`/menus/${id}`)
