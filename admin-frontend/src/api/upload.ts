import axios from 'axios'
import { useAuthStore } from '../store/auth'

export const uploadAvatar = async (file: File): Promise<string> => {
  const form = new FormData()
  form.append('file', file)
  const token = useAuthStore.getState().accessToken
  const res = await axios.post<{ url: string }>('/admin-api/upload/avatar', form, {
    headers: {
      'Content-Type': 'multipart/form-data',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  })
  return res.data.url
}
