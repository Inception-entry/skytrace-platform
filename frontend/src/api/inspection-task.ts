import { authorizedFetch } from '@/api/http'

export interface InspectionTask {
  taskCode: string
  taskName: string
  deviceCode: string | null
  deviceName: string | null
  deviceStatus: string | null
  status: string
  planStartTime: string | null
  planEndTime: string | null
  createdAt: string
  updatedAt: string
}

export interface InspectionTaskDetailsInput {
  taskName: string
  deviceCode: string
  planStartTime: string
  planEndTime: string
}

export interface CreateInspectionTaskInput
  extends InspectionTaskDetailsInput {
  taskCode: string
}

export interface InspectionAnalysis {
  taskCode: string
  workflowId: string
  analysis: string
}

export interface InspectionAnalysisRecord {
  analysisId: string
  taskCode: string
  sessionId: string
  channel: 'TEMPORAL' | 'STREAM'
  question: string
  answer: string
  model: string | null
  sources: KnowledgeSource[]
  createdAt: string
}

export interface KnowledgeSource {
  documentId: string
  filename: string
  page: number | null
  score: number
}

export interface InspectionStreamMetadata {
  model: string
  sources: KnowledgeSource[]
}

export interface InspectionStreamHandlers {
  onMeta?: (metadata: InspectionStreamMetadata) => void
  onToken: (content: string) => void
  onDone?: () => void
}

interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
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

export function getInspectionTasks() {
  return request<InspectionTask[]>('/api/inspection-tasks')
}

export function getInspectionTask(taskCode: string) {
  return request<InspectionTask>(
    `/api/inspection-tasks/${encodeURIComponent(taskCode)}`,
  )
}

export function createInspectionTask(
  input: CreateInspectionTaskInput,
) {
  return request<InspectionTask>('/api/inspection-tasks', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(input),
  })
}

export function updateInspectionTask(
  taskCode: string,
  input: InspectionTaskDetailsInput,
) {
  return request<InspectionTask>(
    `/api/inspection-tasks/${encodeURIComponent(taskCode)}`,
    {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(input),
    },
  )
}

export function startInspectionTask(taskCode: string) {
  return request<{ workflowId: string; runId: string }>(
    `/api/inspection-tasks/${encodeURIComponent(taskCode)}/start`,
    { method: 'POST' },
  )
}

export function completeInspectionTask(taskCode: string) {
  return request<void>(
    `/api/inspection-tasks/${encodeURIComponent(taskCode)}/complete`,
    { method: 'POST' },
  )
}

export function cancelInspectionTask(taskCode: string) {
  return request<void>(
    `/api/inspection-tasks/${encodeURIComponent(taskCode)}/cancel`,
    { method: 'POST' },
  )
}

export function analyzeInspectionTask(
  taskCode: string,
  sessionId: string,
  question: string,
) {
  return request<InspectionAnalysis>(
    `/api/inspection-tasks/${encodeURIComponent(taskCode)}/analysis`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ sessionId, question }),
    },
  )
}

export function getInspectionAnalysisHistory(taskCode: string) {
  return request<InspectionAnalysisRecord[]>(
    `/api/inspection-tasks/${encodeURIComponent(taskCode)}/analyses`,
  )
}

export async function streamInspectionAnalysis(
  taskCode: string,
  sessionId: string,
  question: string,
  handlers: InspectionStreamHandlers,
) {
  const response = await authorizedFetch(
    `/api/inspection-tasks/${encodeURIComponent(taskCode)}/analysis/stream`,
    {
      method: 'POST',
      headers: {
        Accept: 'text/event-stream',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ sessionId, question }),
    },
  )

  if (!response.ok) {
    throw new Error(await responseErrorMessage(response))
  }
  if (!response.body) {
    throw new Error('浏览器未提供流式响应，请更换现代浏览器')
  }

  await consumeEventStream(response.body, handlers)
}

async function consumeEventStream(
  body: ReadableStream<Uint8Array>,
  handlers: InspectionStreamHandlers,
) {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let completed = false

  try {
    while (!completed) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      let boundary = eventBoundary(buffer)
      while (boundary) {
        const block = buffer.slice(0, boundary.index)
        buffer = buffer.slice(boundary.index + boundary.length)
        completed = handleStreamEvent(block, handlers) || completed
        boundary = eventBoundary(buffer)
      }
    }
  } catch (error) {
    await reader.cancel().catch(() => undefined)
    throw error
  } finally {
    reader.releaseLock()
  }

  if (!completed) {
    throw new Error('AI 流式连接提前结束，请重试')
  }
}

function eventBoundary(buffer: string) {
  const match = /\r?\n\r?\n/.exec(buffer)
  return match?.index === undefined
    ? undefined
    : { index: match.index, length: match[0].length }
}

function handleStreamEvent(
  block: string,
  handlers: InspectionStreamHandlers,
) {
  let event = 'message'
  const dataLines: string[] = []

  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }
  if (dataLines.length === 0) return false

  const data = JSON.parse(dataLines.join('\n')) as Record<string, unknown>
  if (event === 'meta') {
    handlers.onMeta?.(data as unknown as InspectionStreamMetadata)
  } else if (event === 'token' && typeof data.content === 'string') {
    handlers.onToken(data.content)
  } else if (event === 'error') {
    throw new Error(
      typeof data.message === 'string'
        ? data.message
        : 'AI 流式生成失败',
    )
  } else if (event === 'done') {
    handlers.onDone?.()
    return true
  }
  return false
}

async function responseErrorMessage(response: Response) {
  try {
    const result = await response.json() as {
      message?: string
      detail?: string
    }
    return result.message || result.detail || `请求失败：HTTP ${response.status}`
  } catch {
    return `请求失败：HTTP ${response.status}`
  }
}
