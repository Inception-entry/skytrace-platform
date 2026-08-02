<template>
  <main class="knowledge-page">
    <section class="knowledge-shell">
      <header class="page-header">
        <div>
          <p class="eyebrow">SKYTRACE RAG KNOWLEDGE BASE</p>
          <h1>无人机知识库</h1>
          <p class="subtitle">
            上传手册和故障文档，让 AI 分析使用可追溯的内部资料
          </p>
        </div>
        <nav class="page-nav">
          <RouterLink to="/chat">AI 分析</RouterLink>
          <RouterLink to="/drone">任务列表</RouterLink>
        </nav>
      </header>

      <p v-if="successMessage" class="notice success">
        {{ successMessage }}
      </p>
      <p v-if="errorMessage" class="notice error">
        {{ errorMessage }}
      </p>

      <div class="content-grid">
        <section class="card document-card">
          <div class="card-heading">
            <div>
              <h2>知识文档</h2>
              <p>{{ documents.length }} 份文档，数据持久化在 Qdrant</p>
            </div>
            <button
              class="ghost-button"
              type="button"
              :disabled="loadingDocuments"
              @click="loadDocuments"
            >
              刷新
            </button>
          </div>

          <div v-if="canManage" class="upload-box">
            <input
              ref="fileInput"
              type="file"
              accept=".pdf,.md,.markdown,.txt,text/plain,text/markdown,application/pdf"
              :disabled="uploading"
              @change="selectFile"
            />
            <div class="upload-copy">
              <strong>{{ selectedFile?.name || '选择知识文档' }}</strong>
              <span>PDF / Markdown / TXT，单个文件不超过 10 MB</span>
            </div>
            <button
              class="primary-button"
              type="button"
              :disabled="!selectedFile || uploading"
              @click="uploadDocument"
            >
              {{ uploading ? '正在解析并向量化…' : '上传入库' }}
            </button>
          </div>

          <p v-else class="permission-hint">
            当前角色可以查询知识库；上传和删除需要 ADMIN 权限。
          </p>

          <div v-if="loadingDocuments" class="empty-state">正在加载…</div>
          <div v-else-if="documents.length === 0" class="empty-state">
            尚未上传文档。先用 ADMIN 账号加入一份无人机手册。
          </div>
          <div v-else class="document-list">
            <article
              v-for="document in documents"
              :key="document.documentId"
              class="document-item"
            >
              <div class="file-mark">{{ fileMark(document.filename) }}</div>
              <div class="document-info">
                <strong>{{ document.filename }}</strong>
                <span>
                  {{ document.chunkCount }} 个知识片段 ·
                  {{ formatDate(document.uploadedAt) }}
                </span>
                <code>{{ document.documentId.slice(0, 16) }}…</code>
              </div>
              <button
                v-if="canManage"
                class="delete-button"
                type="button"
                :disabled="deletingId === document.documentId"
                @click="removeDocument(document)"
              >
                {{ deletingId === document.documentId ? '删除中' : '删除' }}
              </button>
            </article>
          </div>
        </section>

        <section class="card search-card">
          <div class="card-heading">
            <div>
              <h2>语义检索测试</h2>
              <p>这里看到的片段，就是聊天时提供给模型的 RAG 上下文</p>
            </div>
          </div>

          <form class="search-form" @submit.prevent="runSearch">
            <textarea
              v-model="query"
              rows="3"
              maxlength="2000"
              placeholder="例如：图传中断后，操作员应该先做什么？"
              :disabled="searching"
            ></textarea>
            <button
              class="primary-button"
              type="submit"
              :disabled="!query.trim() || searching"
            >
              {{ searching ? '检索中…' : '检索知识库' }}
            </button>
          </form>

          <div v-if="hasSearched && results.length === 0" class="empty-state">
            没有达到相似度阈值的片段，请换一种问法或上传相关资料。
          </div>

          <div class="result-list">
            <article
              v-for="(result, index) in results"
              :key="`${result.documentId}-${result.chunkIndex}`"
              class="result-item"
            >
              <div class="result-meta">
                <strong>[资料{{ index + 1 }}] {{ result.filename }}</strong>
                <span v-if="result.page">第 {{ result.page }} 页</span>
                <span>相似度 {{ formatScore(result.score) }}</span>
              </div>
              <p>{{ result.content }}</p>
            </article>
          </div>
        </section>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  deleteKnowledgeDocument,
  getKnowledgeDocuments,
  searchKnowledge,
  uploadKnowledgeDocument,
  type KnowledgeDocument,
  type KnowledgeSearchResult,
} from '@/api/knowledge'
import { authenticationState } from '@/auth/keycloak'

const documents = ref<KnowledgeDocument[]>([])
const results = ref<KnowledgeSearchResult[]>([])
const selectedFile = ref<File>()
const fileInput = ref<HTMLInputElement>()
const query = ref('')
const loadingDocuments = ref(false)
const uploading = ref(false)
const searching = ref(false)
const hasSearched = ref(false)
const deletingId = ref('')
const errorMessage = ref('')
const successMessage = ref('')

const canManage = computed(() =>
  authenticationState.roles.includes('ADMIN'),
)

function resetMessages() {
  errorMessage.value = ''
  successMessage.value = ''
}

async function loadDocuments() {
  loadingDocuments.value = true
  errorMessage.value = ''
  try {
    documents.value = await getKnowledgeDocuments()
  } catch (error) {
    errorMessage.value = errorText(error, '知识文档加载失败')
  } finally {
    loadingDocuments.value = false
  }
}

function selectFile(event: Event) {
  selectedFile.value = (event.target as HTMLInputElement).files?.[0]
}

async function uploadDocument() {
  if (!selectedFile.value) return
  resetMessages()
  uploading.value = true
  try {
    const document = await uploadKnowledgeDocument(selectedFile.value)
    successMessage.value = `${document.filename} 已完成解析和向量入库`
    selectedFile.value = undefined
    if (fileInput.value) fileInput.value.value = ''
    await loadDocuments()
  } catch (error) {
    errorMessage.value = errorText(error, '文档上传失败')
  } finally {
    uploading.value = false
  }
}

async function runSearch() {
  const currentQuery = query.value.trim()
  if (!currentQuery) return
  resetMessages()
  searching.value = true
  hasSearched.value = false
  try {
    results.value = await searchKnowledge(currentQuery)
    hasSearched.value = true
  } catch (error) {
    errorMessage.value = errorText(error, '知识库检索失败')
  } finally {
    searching.value = false
  }
}

async function removeDocument(document: KnowledgeDocument) {
  if (!window.confirm(`确定删除知识文档“${document.filename}”吗？`)) return
  resetMessages()
  deletingId.value = document.documentId
  try {
    const result = await deleteKnowledgeDocument(document.documentId)
    successMessage.value = `已删除 ${document.filename} 的 ${result.deletedChunks} 个知识片段`
    results.value = results.value.filter(
      item => item.documentId !== document.documentId,
    )
    await loadDocuments()
  } catch (error) {
    errorMessage.value = errorText(error, '文档删除失败')
  } finally {
    deletingId.value = ''
  }
}

const fileMark = (filename: string) =>
  filename.split('.').pop()?.toUpperCase().slice(0, 4) || 'DOC'

const formatDate = (value: string) =>
  value ? new Date(value).toLocaleString('zh-CN') : '未知时间'

const formatScore = (score: number) => `${(score * 100).toFixed(1)}%`

const errorText = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message : fallback

onMounted(loadDocuments)
</script>

<style scoped>
.knowledge-page {
  min-height: 100vh;
  padding: 34px;
  box-sizing: border-box;
  color: #172033;
  background:
    radial-gradient(circle at 10% 0, #dbeafe 0, transparent 28%),
    radial-gradient(circle at 92% 100%, #d1fae5 0, transparent 26%),
    #f4f7fb;
}

.knowledge-shell {
  width: min(1320px, 100%);
  margin: 0 auto;
}

.page-header,
.page-nav,
.card-heading,
.upload-box,
.document-item,
.result-meta {
  display: flex;
  align-items: center;
}

.page-header {
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
  padding: 26px 28px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  box-shadow: 0 16px 46px rgba(15, 23, 42, 0.07);
}

.page-header h1 {
  margin: 3px 0 5px;
  font-size: 27px;
}

.eyebrow,
.subtitle,
.card-heading p {
  margin: 0;
}

.eyebrow {
  color: #2563eb;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.subtitle,
.card-heading p {
  color: #718096;
}

.page-nav {
  gap: 8px;
  padding-right: 220px;
}

.page-nav a,
.ghost-button,
.primary-button,
.delete-button {
  padding: 9px 13px;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
  border: 1px solid #d7dfeb;
  border-radius: 9px;
}

.page-nav a {
  color: #334155;
  text-decoration: none;
  background: #f8fafc;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 22px;
}

.card {
  min-width: 0;
  padding: 24px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  box-shadow: 0 16px 46px rgba(15, 23, 42, 0.06);
}

.card-heading {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.card-heading h2 {
  margin: 0 0 4px;
  font-size: 19px;
}

.card-heading p {
  font-size: 13px;
}

.ghost-button {
  color: #334155;
  background: #f8fafc;
}

.primary-button {
  color: white;
  background: #2563eb;
  border-color: #2563eb;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.upload-box {
  position: relative;
  gap: 12px;
  margin-bottom: 18px;
  padding: 14px;
  background: #f8faff;
  border: 1px dashed #93b4e7;
  border-radius: 12px;
}

.upload-box input {
  position: absolute;
  inset: 0;
  width: 100%;
  opacity: 0;
  cursor: pointer;
}

.upload-copy {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  pointer-events: none;
}

.upload-copy strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.upload-copy span,
.permission-hint,
.document-info span,
.document-info code {
  color: #718096;
  font-size: 12px;
}

.upload-box button {
  position: relative;
  z-index: 1;
}

.permission-hint {
  margin: 0 0 18px;
  padding: 11px 13px;
  background: #f8fafc;
  border-radius: 9px;
}

.document-list,
.result-list {
  display: grid;
  gap: 11px;
}

.document-item {
  gap: 12px;
  padding: 13px;
  border: 1px solid #e4eaf2;
  border-radius: 11px;
}

.file-mark {
  display: grid;
  flex: 0 0 44px;
  width: 44px;
  height: 44px;
  place-items: center;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 900;
  background: #dbeafe;
  border-radius: 10px;
}

.document-info {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 3px;
}

.document-info strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.delete-button {
  color: #b91c1c;
  background: #fff;
  border-color: #fecaca;
}

.search-form {
  display: grid;
  gap: 10px;
  margin-bottom: 18px;
}

.search-form textarea {
  width: 100%;
  padding: 12px;
  box-sizing: border-box;
  color: #172033;
  font: inherit;
  line-height: 1.6;
  resize: vertical;
  border: 1px solid #d7dfeb;
  border-radius: 10px;
  outline: none;
}

.search-form textarea:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12);
}

.search-form button {
  justify-self: end;
}

.result-item {
  padding: 15px;
  background: #fbfcff;
  border: 1px solid #e4eaf2;
  border-radius: 11px;
}

.result-meta {
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 9px;
}

.result-meta strong {
  margin-right: auto;
  color: #1e40af;
}

.result-meta span {
  padding: 3px 7px;
  color: #526077;
  font-size: 11px;
  background: #eef2f7;
  border-radius: 999px;
}

.result-item p {
  margin: 0;
  color: #334155;
  line-height: 1.72;
  white-space: pre-wrap;
}

.empty-state {
  padding: 34px 18px;
  color: #8490a3;
  text-align: center;
  background: #fbfcff;
  border: 1px dashed #d8e0ec;
  border-radius: 11px;
}

.notice {
  margin: 0 0 16px;
  padding: 11px 14px;
  border-radius: 10px;
}

.notice.success {
  color: #047857;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
}

.notice.error {
  color: #b91c1c;
  background: #fef2f2;
  border: 1px solid #fecaca;
}

@media (max-width: 980px) {
  .content-grid {
    grid-template-columns: 1fr;
  }

  .page-nav {
    padding-right: 0;
  }
}

@media (max-width: 680px) {
  .knowledge-page {
    padding: 14px;
  }

  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .upload-box {
    align-items: stretch;
    flex-direction: column;
  }

  .document-item {
    align-items: flex-start;
  }
}
</style>
