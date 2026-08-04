<template>
  <main class="knowledge-page st-page">
    <section class="knowledge-shell">
      <header class="page-header st-panel">
        <div>
          <p class="eyebrow">{{ $t('knowledge.eyebrow') }}</p>
          <h1>{{ $t('knowledge.titleAlt') }}</h1>
          <p class="subtitle">{{ $t('knowledge.subtitleDetail') }}</p>
        </div>
        <nav class="page-nav">
          <RouterLink to="/chat">{{ $t('nav.chat') }}</RouterLink>
          <RouterLink to="/drone">{{ $t('nav.tasks') }}</RouterLink>
          <RouterLink to="/devices">{{ $t('nav.devices') }}</RouterLink>
        </nav>
      </header>

      <p v-if="successMessage" class="notice success">
        {{ successMessage }}
      </p>
      <p v-if="errorMessage" class="notice error">
        {{ errorMessage }}
      </p>

      <div class="content-grid">
        <section class="card document-card st-panel">
          <div class="card-heading">
            <div>
              <h2>{{ $t('knowledge.documentsTitle') }}</h2>
              <p>{{ $t('knowledge.documentsCount', { count: documents.length }) }}</p>
            </div>
            <button
              class="ghost-button"
              type="button"
              :disabled="loadingDocuments"
              @click="loadDocuments"
            >
              {{ $t('common.refresh') }}
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
              <strong>{{ selectedFile?.name || $t('knowledge.selectFile') }}</strong>
              <span>{{ $t('knowledge.fileTypesHint') }}</span>
            </div>
            <button
              class="primary-button"
              type="button"
              :disabled="!selectedFile || uploading"
              @click="uploadDocument"
            >
              {{ uploading ? $t('knowledge.uploading') : $t('knowledge.uploadIngest') }}
            </button>
          </div>

          <p v-else class="permission-hint">
            {{ $t('knowledge.permissionHint') }}
          </p>

          <div v-if="loadingDocuments" class="empty-state">{{ $t('common.loading') }}</div>
          <div v-else-if="documents.length === 0" class="empty-state">
            {{ $t('knowledge.emptyHint') }}
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
                  {{ $t('knowledge.chunkMeta', {
                    count: document.chunkCount,
                    time: formatDate(document.uploadedAt),
                  }) }}
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
                {{ deletingId === document.documentId ? $t('common.deleting') : $t('common.delete') }}
              </button>
            </article>
          </div>
        </section>

        <section class="card search-card st-panel">
          <div class="card-heading">
            <div>
              <h2>{{ $t('knowledge.searchTitle') }}</h2>
              <p>{{ $t('knowledge.searchHint') }}</p>
            </div>
          </div>

          <form class="search-form" @submit.prevent="runSearch">
            <textarea
              v-model="query"
              rows="3"
              maxlength="2000"
              :placeholder="$t('knowledge.queryPlaceholderExample')"
              :disabled="searching"
            ></textarea>
            <button
              class="primary-button"
              type="submit"
              :disabled="!query.trim() || searching"
            >
              {{ searching ? $t('knowledge.searching') : $t('knowledge.searchButton') }}
            </button>
          </form>

          <div v-if="hasSearched && results.length === 0" class="empty-state">
            {{ $t('knowledge.noResults') }}
          </div>

          <div class="result-list">
            <article
              v-for="(result, index) in results"
              :key="`${result.documentId}-${result.chunkIndex}`"
              class="result-item"
            >
              <div class="result-meta">
                <strong>{{ $t('knowledge.sourceLabel', { index: index + 1, filename: result.filename }) }}</strong>
                <span v-if="result.page">{{ $t('knowledge.pageLabel', { page: result.page }) }}</span>
                <span>{{ $t('knowledge.scoreLabel', { score: formatScore(result.score) }) }}</span>
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
import { useTranslation } from 'i18next-vue'
import {
  deleteKnowledgeDocument,
  getKnowledgeDocuments,
  searchKnowledge,
  uploadKnowledgeDocument,
  type KnowledgeDocument,
  type KnowledgeSearchResult,
} from '@/api/knowledge'
import { authenticationState } from '@/auth/keycloak'
import i18n from '@/i18n'

const { t } = useTranslation()

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
    errorMessage.value = errorText(error, t('knowledge.loadFailed'))
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
    successMessage.value = t('knowledge.uploaded', { filename: document.filename })
    selectedFile.value = undefined
    if (fileInput.value) fileInput.value.value = ''
    await loadDocuments()
  } catch (error) {
    errorMessage.value = errorText(error, t('knowledge.uploadFailed'))
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
    errorMessage.value = errorText(error, t('knowledge.searchFailed'))
  } finally {
    searching.value = false
  }
}

async function removeDocument(document: KnowledgeDocument) {
  if (!window.confirm(t('knowledge.deleteConfirmNamed', { name: document.filename }))) return
  resetMessages()
  deletingId.value = document.documentId
  try {
    const result = await deleteKnowledgeDocument(document.documentId)
    successMessage.value = t('knowledge.deleted', {
      filename: document.filename,
      count: result.deletedChunks,
    })
    results.value = results.value.filter(
      item => item.documentId !== document.documentId,
    )
    await loadDocuments()
  } catch (error) {
    errorMessage.value = errorText(error, t('knowledge.deleteFailed'))
  } finally {
    deletingId.value = ''
  }
}

const fileMark = (filename: string) =>
  filename.split('.').pop()?.toUpperCase().slice(0, 4) || 'DOC'

const formatDate = (value: string) =>
  value
    ? new Date(value).toLocaleString(i18n.language === 'en' ? 'en-US' : 'zh-CN')
    : t('knowledge.unknownTime')

const formatScore = (score: number) => `${(score * 100).toFixed(1)}%`

const errorText = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message : fallback

onMounted(loadDocuments)
</script>

<style scoped>
.knowledge-page {
  padding: 34px;
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
}

.page-header h1 {
  margin: 3px 0 5px;
  font-size: 27px;
}

.card-heading p {
  margin: 0;
  color: var(--st-text-muted);
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
  border: 1px solid var(--st-border);
  border-radius: 9px;
}

.page-nav a {
  color: var(--st-text);
  text-decoration: none;
  background: var(--st-bg-elevated);
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 22px;
}

.card {
  min-width: 0;
  padding: 24px;
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
  color: var(--st-text);
  background: var(--st-bg-elevated);
}

.primary-button {
  color: #fff;
  background: var(--st-color-primary);
  border-color: var(--st-color-primary);
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
  background: var(--st-bg-elevated);
  border: 1px dashed var(--st-border-strong);
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
  color: var(--st-text-muted);
  font-size: 12px;
}

.upload-box button {
  position: relative;
  z-index: 1;
}

.permission-hint {
  margin: 0 0 18px;
  padding: 11px 13px;
  background: var(--st-bg-elevated);
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
  border: 1px solid var(--st-border);
  border-radius: 11px;
}

.file-mark {
  display: grid;
  flex: 0 0 44px;
  width: 44px;
  height: 44px;
  place-items: center;
  color: var(--st-color-accent);
  font-size: 11px;
  font-weight: 900;
  background: var(--st-color-primary-soft);
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
  color: var(--st-danger);
  background: var(--st-bg-panel);
  border-color: color-mix(in srgb, var(--st-danger) 35%, transparent);
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
  color: var(--st-text);
  font: inherit;
  line-height: 1.6;
  resize: vertical;
  background: var(--st-input-bg);
  border: 1px solid var(--st-border);
  border-radius: 10px;
  outline: none;
}

.search-form textarea:focus {
  border-color: var(--st-color-primary);
  box-shadow: 0 0 0 3px var(--st-color-primary-soft);
}

.search-form button {
  justify-self: end;
}

.result-item {
  padding: 15px;
  background: var(--st-bg-elevated);
  border: 1px solid var(--st-border);
  border-radius: 11px;
}

.result-meta {
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 9px;
}

.result-meta strong {
  margin-right: auto;
  color: var(--st-color-accent);
}

.result-meta span {
  padding: 3px 7px;
  color: var(--st-text-muted);
  font-size: 11px;
  background: var(--st-bg-elevated);
  border-radius: 999px;
}

.result-item p {
  margin: 0;
  color: var(--st-text);
  line-height: 1.72;
  white-space: pre-wrap;
}

.empty-state {
  padding: 34px 18px;
  color: var(--st-text-muted);
  text-align: center;
  background: var(--st-bg-elevated);
  border: 1px dashed var(--st-border);
  border-radius: 11px;
}

.notice {
  margin: 0 0 16px;
  padding: 11px 14px;
  border-radius: 10px;
}

.notice.success {
  color: var(--st-success);
  background: color-mix(in srgb, var(--st-success) 14%, transparent);
  border: 1px solid color-mix(in srgb, var(--st-success) 35%, transparent);
}

.notice.error {
  color: var(--st-danger);
  background: color-mix(in srgb, var(--st-danger) 14%, transparent);
  border: 1px solid color-mix(in srgb, var(--st-danger) 35%, transparent);
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
