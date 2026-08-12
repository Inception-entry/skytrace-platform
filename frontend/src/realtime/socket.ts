import {
  beginAuthenticationRecovery,
  getAccessToken,
} from '@/auth/keycloak'
import { redirectToAuthorizationPage } from '@/auth/authorization-navigation'

interface RealtimeConnectionError extends Error {
  data?: {
    status?: number
    code?: string
    message?: string
  }
}

interface RealtimeSocket {
  connected: boolean
  connect(): RealtimeSocket
  disconnect(): RealtimeSocket
  on(event: string, listener: (...args: any[]) => void): RealtimeSocket
  off(event: string, listener?: (...args: any[]) => void): RealtimeSocket
}

interface SocketIoOptions {
  path: string
  autoConnect: boolean
  transports: string[]
  auth: (callback: (data: { token: string }) => void) => void
}

declare global {
  interface Window {
    io?: (options: SocketIoOptions) => RealtimeSocket
  }
}

let socket: RealtimeSocket | null = null
let authenticationRetryUsed = false
let reconnectTimer: number | null = null
let clientScriptPromise: Promise<void> | null = null

export async function connectAlarmRealtime() {
  if (socket) {
    if (!socket.connected) {
      socket.connect()
    }
    return socket
  }
  await loadSocketIoClient()
  if (!window.io) {
    throw new Error('Socket.IO 客户端脚本加载失败')
  }

  socket = window.io({
    path: '/socket.io',
    autoConnect: false,
    transports: ['websocket', 'polling'],
    auth: (callback) => {
      void getAccessToken()
        .then((token) => callback({ token }))
        .catch(() => {
          callback({ token: '' })
          void beginAuthenticationRecovery()
        })
    },
  })

  socket.on('connect', () => {
    authenticationRetryUsed = false
  })
  socket.on('connect_error', (error: RealtimeConnectionError) => {
    void handleConnectionError(error)
  })
  socket.on('disconnect', (reason: string) => {
    if (reason === 'io server disconnect') {
      scheduleReconnect()
    }
  })
  socket.connect()
  return socket
}

export function disconnectAlarmRealtime() {
  if (reconnectTimer !== null) {
    window.clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  socket?.disconnect()
  socket = null
  authenticationRetryUsed = false
}

export function onAlarmCreated(listener: (alarm: unknown) => void) {
  let active = true
  void connectAlarmRealtime()
    .then((currentSocket) => {
      if (active) {
        currentSocket.on('alarm.created', listener)
      }
    })
    .catch((error: unknown) => {
      console.error('无法订阅实时告警', error)
    })
  return () => {
    active = false
    socket?.off('alarm.created', listener)
  }
}

export interface DeviceTelemetryPayload {
  type?: string
  deviceCode: string
  ts?: string
  source?: string
  latitude: number
  longitude: number
  altitude?: number | null
  heading?: number | null
}

export function onDeviceTelemetry(
  listener: (telemetry: DeviceTelemetryPayload) => void,
) {
  let active = true
  void connectAlarmRealtime()
    .then((currentSocket) => {
      if (active) {
        currentSocket.on('device.telemetry', listener)
      }
    })
    .catch((error: unknown) => {
      console.error('无法订阅设备遥测', error)
    })
  return () => {
    active = false
    socket?.off('device.telemetry', listener)
  }
}

async function handleConnectionError(error: RealtimeConnectionError) {
  const status = error.data?.status
  if (status === 403) {
    redirectToAuthorizationPage(403)
    return
  }
  if (status !== 401) {
    console.error('Socket.IO 连接失败', error.data?.message ?? error.message)
    if (status === 503) {
      scheduleReconnect(2_000)
    }
    return
  }
  if (authenticationRetryUsed) {
    void beginAuthenticationRecovery()
    return
  }

  authenticationRetryUsed = true
  try {
    await getAccessToken(true)
    socket?.connect()
  } catch {
    void beginAuthenticationRecovery()
  }
}

function scheduleReconnect(delay = 250) {
  if (reconnectTimer !== null) {
    return
  }
  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = null
    socket?.connect()
  }, delay)
}

function loadSocketIoClient() {
  if (window.io) {
    return Promise.resolve()
  }
  if (!clientScriptPromise) {
    clientScriptPromise = new Promise<void>((resolve, reject) => {
      const script = document.createElement('script')
      script.src = '/socket.io/socket.io.min.js'
      script.async = true
      script.onload = () => resolve()
      script.onerror = () => reject(
        new Error('Socket.IO 客户端脚本加载失败'),
      )
      document.head.appendChild(script)
    }).catch((error: unknown) => {
      clientScriptPromise = null
      throw error
    })
  }
  return clientScriptPromise
}
