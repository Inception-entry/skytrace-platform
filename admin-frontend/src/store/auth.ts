import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { MeResponse } from '../types'
import {
  endAdminSession,
  reportServerRevocationFailure,
  revokeAdminSession,
} from '../api/sessionRevoke'

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
        void endAdminSession({
          getTokens: () => {
            const { accessToken, refreshToken } = get()
            return { accessToken, refreshToken }
          },
          clearLocalAuth: () =>
            set({ accessToken: null, refreshToken: null, user: null }),
          revokeSession: revokeAdminSession,
          redirectToLogin: () => {
            window.location.href = '/login'
          },
          onRevokeFailure: reportServerRevocationFailure,
        })
      },
    }),
    {
      name: 'skytrace-admin-auth',
      partialize: state => ({ accessToken: state.accessToken, refreshToken: state.refreshToken }),
    },
  ),
)
