import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { MeResponse } from '../types'
import { logout as logoutApi } from '../api/auth'

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  user: MeResponse | null
  // eslint-disable-next-line no-unused-vars
  setTokens: (access: string, refresh: string) => void
  // eslint-disable-next-line no-unused-vars
  setUser: (user: MeResponse) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      setTokens: (accessToken, refreshToken) => set({ accessToken, refreshToken }),
      setUser: user => set({ user }),
      logout: () => {
        const { refreshToken } = get()
        if (refreshToken) logoutApi(refreshToken)
        set({ accessToken: null, refreshToken: null, user: null })
        window.location.href = '/login'
      },
    }),
    {
      name: 'skytrace-admin-auth',
      partialize: state => ({ accessToken: state.accessToken, refreshToken: state.refreshToken }),
    },
  ),
)
