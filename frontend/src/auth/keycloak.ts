import Keycloak from 'keycloak-js'
import { reactive, readonly } from 'vue'

interface UavConfig {
  keycloakUrl?: string
  keycloakRealm?: string
  keycloakClientId?: string
}
const rc = ((window as unknown as Record<string, unknown>).__UAV_CONFIG__ ?? {}) as UavConfig

const keycloak = new Keycloak({
  url: rc.keycloakUrl ?? import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8180',
  realm: rc.keycloakRealm ?? import.meta.env.VITE_KEYCLOAK_REALM ?? 'uav',
  clientId: rc.keycloakClientId ?? import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'uav-web',
})

interface AuthenticationState {
  initialized: boolean
  authenticated: boolean
  username: string
  roles: string[]
}

const mutableAuthenticationState = reactive<AuthenticationState>({
  initialized: false,
  authenticated: false,
  username: '',
  roles: [],
})

export const authenticationState = readonly(
  mutableAuthenticationState,
)

let refreshPromise: Promise<boolean> | null = null

function synchronizeAuthenticationState() {
  const token = keycloak.tokenParsed
  mutableAuthenticationState.initialized = keycloak.didInitialize
  mutableAuthenticationState.authenticated =
    keycloak.authenticated === true
  mutableAuthenticationState.username =
    token?.preferred_username ?? token?.sub ?? ''
  mutableAuthenticationState.roles = [
    ...(token?.realm_access?.roles ?? []),
  ].sort()
}

async function refreshToken(minValidity: number) {
  if (!refreshPromise) {
    refreshPromise = keycloak.updateToken(minValidity)
      .then((refreshed) => {
        synchronizeAuthenticationState()
        return refreshed
      })
      .catch((error: unknown) => {
        keycloak.clearToken()
        synchronizeAuthenticationState()
        throw error
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

export async function initializeAuthentication() {
  keycloak.onAuthSuccess = synchronizeAuthenticationState
  keycloak.onAuthRefreshSuccess = synchronizeAuthenticationState
  keycloak.onAuthLogout = synchronizeAuthenticationState
  keycloak.onTokenExpired = () => {
    void refreshToken(30).catch(() => login())
  }

  const authenticated = await keycloak.init({
    onLoad: 'login-required',
    flow: 'standard',
    pkceMethod: 'S256',
    checkLoginIframe: false,
    enableLogging: import.meta.env.DEV,
  })
  synchronizeAuthenticationState()

  if (!authenticated) {
    await login()
  }
}

export async function getAccessToken(forceRefresh = false) {
  if (!keycloak.didInitialize) {
    throw new Error('身份认证尚未初始化')
  }
  if (!keycloak.authenticated) {
    await login()
    throw new Error('用户尚未登录')
  }

  await refreshToken(forceRefresh ? -1 : 30)
  if (!keycloak.token) {
    throw new Error('没有可用的访问令牌')
  }
  return keycloak.token
}

export function login() {
  return keycloak.login({ redirectUri: window.location.href })
}

export function reauthenticate(redirectPath = '/') {
  const redirectUrl = new URL(redirectPath, window.location.origin)
  if (redirectUrl.origin !== window.location.origin) {
    redirectUrl.href = `${window.location.origin}/`
  }
  keycloak.clearToken()
  return keycloak.login({
    redirectUri: redirectUrl.href,
    prompt: 'login',
  })
}

export function logout() {
  return keycloak.logout({ redirectUri: `${window.location.origin}/` })
}
