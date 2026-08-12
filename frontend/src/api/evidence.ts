import { authorizedFetch } from '@/api/http'

interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

export type EvidenceReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface EvidenceTag {
  id: number
  name: string
  color: string | null
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
  reviewStatus?: EvidenceReviewStatus | string
  contentHash?: string | null
  archiveStatus?: string | null
  tags?: EvidenceTag[]
  thumbnailUrl?: string | null
  posterUrl?: string | null
}

export interface EvidenceDetail extends EvidenceSummary {
  objectKey: string
  bucket: string
  contentType: string
  uploadedBy: string | null
  reviewComment?: string | null
  remark?: string | null
  reviewedByName?: string | null
  reviewedAt?: string | null
  analysisId?: string | null
  derivativeStatus?: string | null
  thumbnailObjectKey?: string | null
  posterObjectKey?: string | null
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

export type EvidenceArchiveJobStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'

export interface EvidenceArchiveJob {
  jobCode: string
  scopeType: 'TASK' | 'ALARM' | string
  scopeValue: string
  status: EvidenceArchiveJobStatus | string
  outputObjectKey: string | null
  manifestObjectKey: string | null
  packageContentHash: string | null
  totalFiles: number
  totalBytes: number
  createdAt: string
  completedAt: string | null
  errorMessage: string | null
}

export interface EvidenceSearchParams {
  page?: number
  size?: number
  taskCode?: string
  alarmEventCode?: string
  deviceCode?: string
  assetType?: string
  sourceType?: string
  reviewStatus?: string
  startTime?: string
  endTime?: string
  keyword?: string
  includeDeleted?: boolean
}

export interface UpdateEvidenceMetadataPayload {
  remark?: string
  reviewStatus?: EvidenceReviewStatus | string
  reviewComment?: string
  tagIds?: number[]
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

export function listEvidenceTags() {
  return request<EvidenceTag[]>('/api/evidence/tags')
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

export function updateEvidenceMetadata(
  evidenceCode: string,
  payload: UpdateEvidenceMetadataPayload,
) {
  return request<null>(
    `/api/evidence/${encodeURIComponent(evidenceCode)}/metadata`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
  )
}

export function batchReviewEvidence(payload: {
  evidenceCodes: string[]
  reviewStatus: EvidenceReviewStatus | string
  reviewComment?: string
}) {
  return request<null>('/api/evidence/batch/review', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function batchTagEvidence(payload: {
  evidenceCodes: string[]
  tagIds: number[]
  replace?: boolean
}) {
  return request<null>('/api/evidence/batch/tags', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function createEvidenceArchiveJob(payload: {
  scopeType: 'TASK' | 'ALARM'
  scopeValue: string
}) {
  return request<EvidenceArchiveJob>('/api/evidence/archive-jobs', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function getEvidenceArchiveJob(jobCode: string) {
  return request<EvidenceArchiveJob>(
    `/api/evidence/archive-jobs/${encodeURIComponent(jobCode)}`,
  )
}

export function createEvidenceArchiveDownloadUrl(jobCode: string) {
  return request<EvidenceAccessUrl>(
    `/api/evidence/archive-jobs/${encodeURIComponent(jobCode)}/download-url`,
    { method: 'POST' },
  )
}

export function createEvidenceArchiveManifestUrl(jobCode: string) {
  return request<EvidenceAccessUrl>(
    `/api/evidence/archive-jobs/${encodeURIComponent(jobCode)}/manifest-url`,
    { method: 'POST' },
  )
}
