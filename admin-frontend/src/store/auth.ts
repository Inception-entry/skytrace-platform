import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { MeResponse } from '../types'

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  user: MeResponse | null
  setTokens: (access: string, refresh: string) => void
  setUser: (user: MeResponse) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    set => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      setTokens: (accessToken, refreshToken) => set({ accessToken, refreshToken }),
      setUser: user => set({ user }),
      logout: () => {
        set({ accessToken: null, refreshToken: null, user: null })
        window.location.href = '/login'
      },
    }),
    {
      name: 'uav-admin-auth',
      partialize: state => ({ accessToken: state.accessToken, refreshToken: state.refreshToken }),
    },
  ),
)
