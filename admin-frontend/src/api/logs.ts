import client from './client'
import type { OperationLog, Paginated } from '../types'

export const list = (params: {
  page?: number
  pageSize?: number
  username?: string
  module?: string
  action?: string
  startTime?: string
  endTime?: string
}) => client.get<Paginated<OperationLog>>('/logs', { params }).then(r => r.data)

export const clear = () =>
  client.delete<{ count: number }>('/logs').then(r => r.data)
