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
  objectKey: string
  bucket: string
  contentType: string
  sizeBytes: number
  taskCode: string | null
  alarmEventCode: string | null
  publicPath: string
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

export function getWorkflowStatus(taskCode: string) {
  return request<WorkflowStatus>(
    `/api/inspection-tasks/${encodeURIComponent(taskCode)}/workflow-status`,
  )
}
