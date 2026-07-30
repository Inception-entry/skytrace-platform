import axios from 'axios'
import { useAuthStore } from '../store/auth'

const client = axios.create({ baseURL: '/admin-api' })

let isRefreshing = false
let failedQueue: Array<{
  resolve: (token: string) => void
  reject: (err: unknown) => void
}> = []

function flushQueue(err: unknown, token: string | null = null) {
  failedQueue.forEach(p => (err ? p.reject(err) : p.resolve(token!)))
  failedQueue = []
}

client.interceptors.request.use(config => {
  const token = useAuthStore.getState().accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

client.interceptors.response.use(
  r => r,
  async (error: { config: { _retry?: boolean; headers: Record<string, string> }; response?: { status: number } }) => {
    const original = error.config
    if (error.response?.status !== 401 || original._retry) {
      return Promise.reject(error)
    }

    if (isRefreshing) {
      return new Promise<string>((resolve, reject) => {
        failedQueue.push({ resolve, reject })
      }).then(token => {
        original.headers.Authorization = `Bearer ${token}`
        return client(original)
      })
    }

    original._retry = true
    isRefreshing = true

    const { refreshToken } = useAuthStore.getState()
    if (!refreshToken) {
      useAuthStore.getState().logout()
      window.location.href = '/login'
      return Promise.reject(error)
    }

    try {
      const { data } = await axios.post<{ access_token: string; refresh_token: string }>(
        '/admin-api/auth/refresh',
        { refresh_token: refreshToken },
      )
      const newToken = data.access_token
      useAuthStore.getState().setTokens(newToken, data.refresh_token)
      flushQueue(null, newToken)
      original.headers.Authorization = `Bearer ${newToken}`
      return client(original)
    } catch (err) {
      flushQueue(err)
      useAuthStore.getState().logout()
      window.location.href = '/login'
      return Promise.reject(err)
    } finally {
      isRefreshing = false
    }
  },
)

export default client
