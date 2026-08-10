import { authorizedFetch } from '@/api/http'

interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

export interface EvidenceSummary {
  evidenceCode: string
  originalFilename: string | null
  assetType: string
  sourceType: string
  taskCode: string | null
  alarmEventCode: string | null
  deviceCode: string | null
  uploadedByName: string | null
  sizeBytes: number
  createdAt: string
  deleted: boolean
}

export interface EvidenceDetail extends EvidenceSummary {
  objectKey: string
  bucket: string
  contentType: string
  uploadedBy: string | null
}

export interface EvidencePage {
  content: EvidenceSummary[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

export interface EvidenceAccessUrl {
  url: string
  expiresAt: string
}

export interface EvidenceSearchParams {
  page?: number
  size?: number
  taskCode?: string
  alarmEventCode?: string
  deviceCode?: string
  assetType?: string
  sourceType?: string
  startTime?: string
  endTime?: string
  keyword?: string
  includeDeleted?: boolean
}

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await authorizedFetch(url, options)
  const result: ApiResponse<T> = await response.json()
  if (!response.ok || !result.success) {
    throw new Error(result.message || '请求失败')
  }
  return result.data
}

export function searchEvidence(params: EvidenceSearchParams = {}) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return
    query.set(key, String(value))
  })
  return request<EvidencePage>(`/api/evidence/search?${query.toString()}`)
}

export function getEvidenceDetail(evidenceCode: string) {
  return request<EvidenceDetail>(
    `/api/evidence/${encodeURIComponent(evidenceCode)}`,
  )
}

export function createEvidencePreviewUrl(evidenceCode: string) {
  return request<EvidenceAccessUrl>(
    `/api/evidence/${encodeURIComponent(evidenceCode)}/preview-url`,
    { method: 'POST' },
  )
}

export function createEvidenceDownloadUrl(evidenceCode: string) {
  return request<EvidenceAccessUrl>(
    `/api/evidence/${encodeURIComponent(evidenceCode)}/download-url`,
    { method: 'POST' },
  )
}

export function deleteEvidence(evidenceCode: string) {
  return request<null>(
    `/api/evidence/${encodeURIComponent(evidenceCode)}`,
    { method: 'DELETE' },
  )
}

export function restoreEvidence(evidenceCode: string) {
  return request<null>(
    `/api/evidence/${encodeURIComponent(evidenceCode)}/restore`,
    { method: 'POST' },
  )
}