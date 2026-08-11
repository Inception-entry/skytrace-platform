import {
  beginAuthenticationRecovery,
  getAccessToken,
  isAuthenticationRequiredError,
} from '@/auth/keycloak'
import { redirectToAuthorizationPage } from '@/auth/authorization-navigation'

export async function authorizedFetch(
  input: RequestInfo | URL,
  init: RequestInit = {},
) {
  const request = new Request(input, init)
  let response: Response

  try {
    response = await sendAuthorizedRequest(request, false)
  } catch (error) {
    if (isAuthenticationRequiredError(error)) {
      void beginAuthenticationRecovery()
    }
    throw error
  }

  if (response.status === 401) {
    try {
      response = await sendAuthorizedRequest(request, true)
    } catch (error) {
      if (isAuthenticationRequiredError(error)) {
        void beginAuthenticationRecovery()
      }
      throw error
    }
  }

  if (response.status === 401) {
    void beginAuthenticationRecovery()
  } else if (response.status === 403) {
    redirectToAuthorizationPage(403)
  }
  return response
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
  })
}
