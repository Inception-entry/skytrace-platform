import { authorizedFetch } from '@/api/http'

interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

export interface AlarmEvent {
  id: number
  eventCode: string
  deviceCode: string
  taskCode: string | null
  eventType: string
  weaponType: string | null
  confidence: number | null
  latitude: number | null
  longitude: number | null
  imageUrl: string | null
  videoUrl: string | null
  status: string
  eventTime: string
}

export interface EvidenceUpload {
  evidenceCode?: string
  objectKey: string
  bucket: string
  contentType: string
  sizeBytes: number
  originalFilename?: string | null
  taskCode: string | null
  alarmEventCode: string | null
  publicPath: string
  createdAt?: string
}

export interface EvidenceAsset extends EvidenceUpload {
  evidenceCode?: string
  originalFilename: string | null
  createdAt: string
}

export interface WorkflowStatus {
  taskCode: string
  status: string
  lastAlarmEventCode: string
}

async function request<T>(
  url: string,
  options?: RequestInit,
): Promise<T> {
  const response = await authorizedFetch(url, options)
  const result: ApiResponse<T> = await response.json()
  if (!response.ok || !result.success) {
    throw new Error(result.message || '请求失败')
  }
  return result.data
}

export function getLatestAlarms() {
  return request<AlarmEvent[]>('/api/alarms/latest')
}

export function createAlarm(input: Partial<AlarmEvent> & {
  deviceCode: string
  eventType: string
}) {
  return request<AlarmEvent>('/api/alarms', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function publishDetectionAlarm(input: {
  deviceCode: string
  taskCode?: string
  eventType: string
  weaponType?: string
  confidence?: number
  latitude?: number
  longitude?: number
  imageUrl?: string
  videoUrl?: string
  eventTime?: string
}) {
  return request<{ status: string; exchange: string }>(
    '/api/alarms/detections',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    },
  )
}

export async function uploadEvidence(
  file: File,
  taskCode?: string,
  alarmEventCode?: string,
) {
  const form = new FormData()
  form.append('file', file)
  if (taskCode) form.append('taskCode', taskCode)
  if (alarmEventCode) form.append('alarmEventCode', alarmEventCode)
  return request<EvidenceUpload>('/api/evidence', {
    method: 'POST',
    body: form,
  })
}

export function getEvidence(params: {
  taskCode?: string
  alarmEventCode?: string
}) {
  const query = new URLSearchParams()
  if (params.taskCode) query.set('taskCode', params.taskCode)
  if (params.alarmEventCode) {
    query.set('alarmEventCode', params.alarmEventCode)
  }
  return request<EvidenceAsset[]>(`/api/evidence?${query.toString()}`)
}

export interface VisionDetectResult {
  backend: string
  model: string
  detections: Array<{
    className: string
    confidence: number
    x1: number
    y1: number
    x2: number
    y2: number
  }>
  alarmCandidates: Array<{
    eventType: string
    weaponType: string | null
    className: string
    confidence: number
  }>
  publishedAlarms: Array<{
    eventType: string
    weaponType: string | null
    className: string
    confidence: number
  }>
}

export async function analyzeVisionFrame(
  file: File,
  options?: {
    deviceCode?: string
    taskCode?: string
    latitude?: number
    longitude?: number
    publishAlarms?: boolean
    maxAlarms?: number
  },
) {
  const form = new FormData()
  form.append('file', file)
  form.append('deviceCode', options?.deviceCode ?? 'UAV-001')
  if (options?.taskCode) form.append('taskCode', options.taskCode)
  if (options?.latitude != null) {
    form.append('latitude', String(options.latitude))
  }
  if (options?.longitude != null) {
    form.append('longitude', String(options.longitude))
  }
  form.append(
    'publishAlarms',
    String(options?.publishAlarms ?? true),
  )
  if (options?.maxAlarms != null) {
    form.append('maxAlarms', String(options.maxAlarms))
  }
  return request<VisionDetectResult>('/api/alarms/analyze', {
    method: 'POST',
    body: form,
  })
}

export async function analyzeVisionVideo(
  file: File,
  options?: {
    deviceCode?: string
    taskCode?: string
    latitude?: number
    longitude?: number
    publishAlarms?: boolean
    maxAlarms?: number
    frameIntervalSec?: number
    maxFrames?: number
  },
) {
  const form = new FormData()
  form.append('file', file)
  form.append('deviceCode', options?.deviceCode ?? 'UAV-001')
  if (options?.taskCode) form.append('taskCode', options.taskCode)
  if (options?.latitude != null) {
    form.append('latitude', String(options.latitude))
  }
  if (options?.longitude != null) {
    form.append('longitude', String(options.longitude))
  }
  form.append(
    'publishAlarms',
    String(options?.publishAlarms ?? true),
  )
  if (options?.maxAlarms != null) {
    form.append('maxAlarms', String(options.maxAlarms))
  }
  if (options?.frameIntervalSec != null) {
    form.append('frameIntervalSec', String(options.frameIntervalSec))
  }
  if (options?.maxFrames != null) {
    form.append('maxFrames', String(options.maxFrames))
  }
  return request<Record<string, unknown>>('/api/alarms/analyze-video', {
    method: 'POST',
    body: form,
  })
}

export function getWorkflowStatus(taskCode: string) {
  return request<WorkflowStatus>(
    `/api/inspection-tasks/${encodeURIComponent(taskCode)}/workflow-status`,
  )
}
