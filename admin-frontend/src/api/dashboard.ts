import client from './client'
import type { DashboardStats } from '../types'

export const stats = () =>
  client.get<DashboardStats>('/dashboard/stats').then(r => r.data)
