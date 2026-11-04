import { create } from 'zustand'
import type { LoginResponse, MeResponse } from '../types'
import { isSessionRejected, refreshCookieSession } from '../api/cookieSession'
import { loadAdminProfile } from '../api/sessionEstablish'
import {
  endAdminSession,
  reportServerRevocationFailure,
  revokeAdminSession,
} from '../api/sessionRevoke'
import { clearLegacyAuthStorage } from './sessionStorage'
import { notifySessionChange } from './sessionSignals'

type SessionStatus = 'unknown' | 'restoring' | 'authenticated' | 'anonymous' | 'unavailable' | 'logging-out'

interface AuthState {
  accessToken: string | null
  user: MeResponse | null
  status: SessionStatus
  sessionVersion: number
  // eslint-disable-next-line no-unused-vars
  setSession: (tokens: LoginResponse, user: MeResponse) => void
  // eslint-disable-next-line no-unused-vars
  setUser: (user: MeResponse) => void
  refreshAccessToken: () => Promise<string>
  restoreSession: () => Promise<void>
  expireSession: () => void
  logout: () => Promise<void>
}

type SessionDeps = {
  refresh: () => Promise<LoginResponse>
  // eslint-disable-next-line no-unused-vars
  loadMe: (accessToken: string) => Promise<MeResponse>
  revoke: () => Promise<void>
  redirect: () => void
  notify: () => void
  // eslint-disable-next-line no-unused-vars
  onRevokeFailure: (error: unknown) => void
}

export function createAdminAuthStore(deps: SessionDeps) {
  let generation = 0
  let refreshing: Promise<string> | null = null
  let restoring: Promise<void> | null = null
  let loggingOut: Promise<void> | null = null

  return create<AuthState>()((set, get) => ({
    accessToken: null,
    user: null,
    status: 'unknown',
    sessionVersion: 0,
    setSession: (tokens, user) => {
      generation += 1
      refreshing = null
      set({ accessToken: tokens.access_token, user, status: 'authenticated', sessionVersion: generation })
      deps.notify()
    },
    setUser: user => set({ user }),
    expireSession: () => {
      generation += 1
      refreshing = null
      set({ accessToken: null, user: null, status: 'anonymous', sessionVersion: generation })
    },
    refreshAccessToken: () => {
      if (refreshing) return refreshing
      if (get().status === 'logging-out') return Promise.reject(new Error('SESSION_CHANGED'))
      const started = generation
      const pending = deps.refresh().then(tokens => {
        if (generation !== started) throw new Error('SESSION_CHANGED')
        set({ accessToken: tokens.access_token })
        return tokens.access_token
      }).catch(error => {
        if (generation !== started) throw new Error('SESSION_CHANGED')
        throw error
      }).finally(() => {
        if (refreshing === pending) refreshing = null
      })
      refreshing = pending
      return pending
    },
    restoreSession: () => {
      if (restoring) return restoring
      if (get().status === 'authenticated' || get().status === 'logging-out') return Promise.resolve()
      const started = generation
      set({ status: 'restoring' })
      const pending = (async () => {
        try {
          const accessToken = await get().refreshAccessToken()
          const user = await deps.loadMe(accessToken)
          if (generation === started) set({ accessToken, user, status: 'authenticated' })
        } catch (error) {
          if (generation !== started) return
          if (isSessionRejected(error)) get().expireSession()
          else set({ accessToken: null, user: null, status: 'unavailable' })
        }
      })().finally(() => {
        if (restoring === pending) restoring = null
      })
      restoring = pending
      return pending
    },
    logout: () => {
      if (loggingOut) return loggingOut
      const pendingRefresh = refreshing
      generation += 1
      refreshing = null
      const pending = endAdminSession({
        clearLocalAuth: () => set({ accessToken: null, user: null, status: 'logging-out', sessionVersion: generation }),
        waitForRefresh: () => pendingRefresh ?? Promise.resolve(),
        revokeSession: deps.revoke,
        redirectToLogin: () => {
          deps.notify()
          set({ status: 'anonymous' })
          deps.redirect()
        },
        onRevokeFailure: deps.onRevokeFailure,
      }).finally(() => {
        if (loggingOut === pending) loggingOut = null
      })
      loggingOut = pending
      return pending
    },
  }))
}

clearLegacyAuthStorage()

export const useAuthStore = createAdminAuthStore({
  refresh: refreshCookieSession,
  loadMe: loadAdminProfile,
  revoke: revokeAdminSession,
  redirect: () => { window.location.href = '/login' },
  notify: notifySessionChange,
  onRevokeFailure: reportServerRevocationFailure,
})
