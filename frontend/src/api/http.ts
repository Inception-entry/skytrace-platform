import {
  beginAuthenticationRecovery,
  getAccessToken,
  isAuthenticationRequiredError,
} from '@/auth/keycloak'
import { redirectToAuthorizationPage } from '@/auth/authorization-navigation'
import { logClientError, logClientWarn } from '@/utils/client-log'

export async function authorizedFetch(
  input: RequestInfo | URL,
  init: RequestInit = {},
) {
  const request = new Request(input, init)
  let response: Response

  try {
    response = await sendAuthorizedRequest(request, false)
  } catch (error) {
    logClientError('http', error)
    if (isAuthenticationRequiredError(error)) {
      void beginAuthenticationRecovery()
    }
    throw error
  }

  if (response.status === 401) {
    try {
      response = await sendAuthorizedRequest(request, true)
    } catch (error) {
      logClientError('http.refresh', error)
      if (isAuthenticationRequiredError(error)) {
        void beginAuthenticationRecovery()
      }
      throw error
    }
  }

  if (response.status === 401) {
    logClientWarn('http', `${request.method} ${requestPath(request)} 401`)
    void beginAuthenticationRecovery()
  } else if (response.status === 403) {
    logClientWarn('http', `${request.method} ${requestPath(request)} 403`)
    redirectToAuthorizationPage(403)
  } else if (response.status >= 500) {
    logClientError('http', new Error(`${request.method} ${requestPath(request)} ${response.status}`))
  } else if (response.status >= 400) {
    logClientWarn('http', `${request.method} ${requestPath(request)} ${response.status}`)
  }
  return response
}

function requestPath(request: Request): string {
  try {
    return new URL(request.url, window.location.origin).pathname
  } catch {
    return request.url
  }
}

async function sendAuthorizedRequest(
  request: Request,
  forceRefresh: boolean,
) {
  const headers = new Headers(request.headers)
  headers.set(
    'Authorization',
    `Bearer ${await getAccessToken(forceRefresh)}`,
  )
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json')
  }

  return fetch(request.clone(), {
    headers,
    signal: request.signal,
  })
}
