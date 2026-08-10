<template>
  <main class="evidence-page st-page">
    <section class="evidence-panel">
      <header class="panel-header">
        <div>
          <p class="eyebrow">{{ $t('evidence.eyebrow') }}</p>
          <h1>{{ $t('evidence.title') }}</h1>
          <p class="subtitle">{{ $t('evidence.subtitle') }}</p>
        </div>

        <div class="header-actions">
          <RouterLink class="nav-link" to="/drone">{{ $t('nav.tasks') }}</RouterLink>
          <RouterLink class="nav-link" to="/devices">{{ $t('nav.devices') }}</RouterLink>
        </div>
      </header>

      <form class="filter-form st-panel" @submit.prevent="loadPage(0)">
        <div class="form-grid">
          <label>
            <span>{{ $t('evidence.keyword') }}</span>
            <input
              v-model.trim="filters.keyword"
              :placeholder="$t('evidence.keywordPlaceholder')"
              :disabled="loading"
            />
          </label>
          <label>
            <span>{{ $t('evidence.taskCode') }}</span>
            <input
              v-model.trim="filters.taskCode"
              :placeholder="$t('evidence.taskCodePlaceholder')"
              :disabled="loading"
            />
          </label>
          <label>
            <span>{{ $t('evidence.alarmEventCode') }}</span>
            <input
              v-model.trim="filters.alarmEventCode"
              :placeholder="$t('evidence.alarmPlaceholder')"
              :disabled="loading"
            />
          </label>
          <label>
            <span>{{ $t('evidence.deviceCode') }}</span>
            <input
              v-model.trim="filters.deviceCode"
              :placeholder="$t('evidence.devicePlaceholder')"
              :disabled="loading"
            />
          </label>
          <label>
            <span>{{ $t('evidence.assetType') }}</span>
            <select v-model="filters.assetType" :disabled="loading">
              <option value="">{{ $t('evidence.allTypes') }}</option>
              <option value="IMAGE">{{ $t('evidence.typeImage') }}</option>
              <option value="VIDEO">{{ $t('evidence.typeVideo') }}</option>
            </select>
          </label>
          <label class="checkbox-field">
            <span>{{ $t('evidence.options') }}</span>
            <span class="checkbox-row">
              <input
                id="include-deleted"
                v-model="filters.includeDeleted"
                type="checkbox"
                :disabled="loading"
              />
              <label for="include-deleted">{{ $t('evidence.includeDeleted') }}</label>
            </span>
          </label>
        </div>

        <div class="form-actions">
          <button class="primary-button" type="submit" :disabled="loading">
            {{ $t('evidence.search') }}
          </button>
          <button
            class="secondary-button"
            type="button"
            :disabled="loading"
            @click="resetFilters"
          >
            {{ $t('evidence.reset') }}
          </button>
        </div>
      </form>

      <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
      <p v-if="loading" class="loading-text">{{ $t('evidence.loading') }}</p>

      <div class="table-wrap st-panel">
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>{{ $t('evidence.code') }}</th>
                <th>{{ $t('evidence.filename') }}</th>
                <th>{{ $t('evidence.assetType') }}</th>
                <th>{{ $t('evidence.sourceType') }}</th>
                <th>{{ $t('evidence.taskCode') }}</th>
                <th>{{ $t('evidence.alarmEventCode') }}</th>
                <th>{{ $t('evidence.deviceCode') }}</th>
                <th>{{ $t('evidence.uploadedBy') }}</th>
                <th>{{ $t('evidence.createdAt') }}</th>
                <th>{{ $t('evidence.actions') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!loading && rows.length === 0">
                <td colspan="10" class="empty">{{ $t('evidence.empty') }}</td>
              </tr>
              <tr
                v-for="row in rows"
                :key="row.evidenceCode"
                :class="{ deleted: row.deleted }"
              >
                <td class="mono">{{ row.evidenceCode }}</td>
                <td>{{ row.originalFilename || '-' }}</td>
                <td>
                  <span
                    class="type-pill"
                    :class="row.assetType === 'VIDEO' ? 'video' : 'image'"
                  >
                    {{
                      row.assetType === 'VIDEO'
                        ? $t('evidence.typeVideo')
                        : $t('evidence.typeImage')
                    }}
                  </span>
                </td>
                <td>{{ sourceLabel(row.sourceType) }}</td>
                <td class="mono">{{ row.taskCode || '-' }}</td>
                <td class="mono">{{ row.alarmEventCode || '-' }}</td>
                <td class="mono">{{ row.deviceCode || '-' }}</td>
                <td>{{ row.uploadedByName || '-' }}</td>
                <td>{{ formatTime(row.createdAt) }}</td>
                <td class="actions">
                  <button
                    type="button"
                    class="text-button"
                    :disabled="loading"
                    @click="openDetail(row.evidenceCode)"
                  >
                    {{ $t('evidence.detail') }}
                  </button>
                  <button
                    v-if="canOperate && !row.deleted"
                    type="button"
                    class="text-button"
                    :disabled="loading"
                    @click="preview(row.evidenceCode)"
                  >
                    {{ $t('evidence.preview') }}
                  </button>
                  <button
                    v-if="canOperate && !row.deleted"
                    type="button"
                    class="text-button"
                    :disabled="loading"
                    @click="download(row.evidenceCode)"
                  >
                    {{ $t('evidence.download') }}
                  </button>
                  <button
                    v-if="canOperate && !row.deleted"
                    type="button"
                    class="text-button danger"
                    :disabled="loading"
                    @click="remove(row.evidenceCode)"
                  >
                    {{ $t('evidence.delete') }}
                  </button>
                  <button
                    v-if="canOperate && row.deleted"
                    type="button"
                    class="text-button"
                    :disabled="loading"
                    @click="restore(row.evidenceCode)"
                  >
                    {{ $t('evidence.restore') }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="pager">
          <button
            class="secondary-button"
            type="button"
            :disabled="page <= 0 || loading"
            @click="loadPage(page - 1)"
          >
            {{ $t('evidence.prev') }}
          </button>
          <span class="pager-meta">
            {{ $t('evidence.pageMeta', { current: page + 1, total: Math.max(totalPages, 1) }) }}
          </span>
          <button
            class="secondary-button"
            type="button"
            :disabled="page + 1 >= totalPages || loading"
            @click="loadPage(page + 1)"
          >
            {{ $t('evidence.next') }}
          </button>
        </div>
      </div>
    </section>

    <aside v-if="detail" class="detail-drawer st-panel">
      <header class="drawer-header">
        <div>
          <p class="eyebrow">{{ $t('evidence.detail') }}</p>
          <h2>{{ detail.evidenceCode }}</h2>
        </div>
        <button class="text-button" type="button" @click="closeDetail">
          {{ $t('common.close') }}
        </button>
      </header>

      <dl class="detail-grid">
        <div>
          <dt>{{ $t('evidence.filename') }}</dt>
          <dd>{{ detail.originalFilename || '-' }}</dd>
        </div>
        <div>
          <dt>{{ $t('evidence.assetType') }}</dt>
          <dd>{{
            detail.assetType === 'VIDEO'
              ? $t('evidence.typeVideo')
              : $t('evidence.typeImage')
          }}</dd>
        </div>
        <div>
          <dt>{{ $t('evidence.sourceType') }}</dt>
          <dd>{{ sourceLabel(detail.sourceType) }}</dd>
        </div>
        <div>
          <dt>{{ $t('evidence.taskCode') }}</dt>
          <dd class="mono">{{ detail.taskCode || '-' }}</dd>
        </div>
        <div>
          <dt>{{ $t('evidence.alarmEventCode') }}</dt>
          <dd class="mono">{{ detail.alarmEventCode || '-' }}</dd>
        </div>
        <div>
          <dt>{{ $t('evidence.deviceCode') }}</dt>
          <dd class="mono">{{ detail.deviceCode || '-' }}</dd>
        </div>
        <div>
          <dt>{{ $t('evidence.uploadedBy') }}</dt>
          <dd>{{ detail.uploadedByName || '-' }}</dd>
        </div>
        <div>
          <dt>{{ $t('evidence.createdAt') }}</dt>
          <dd>{{ formatTime(detail.createdAt) }}</dd>
        </div>
      </dl>

      <div class="drawer-actions">
        <button
          v-if="canOperate && !detail.deleted"
          class="primary-button"
          type="button"
          :disabled="loading"
          @click="preview(detail.evidenceCode)"
        >
          {{ $t('evidence.preview') }}
        </button>
        <button
          v-if="canOperate && !detail.deleted"
          class="secondary-button"
          type="button"
          :disabled="loading"
          @click="download(detail.evidenceCode)"
        >
          {{ $t('evidence.download') }}
        </button>
      </div>

      <div v-if="previewUrl" class="preview">
        <img
          v-if="detail.assetType === 'IMAGE'"
          :src="previewUrl"
          :alt="detail.originalFilename || detail.evidenceCode"
        />
        <video v-else :src="previewUrl" controls />
      </div>
    </aside>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useTranslation } from 'i18next-vue'
import { authenticationState } from '@/auth/keycloak'
import {
  createEvidenceDownloadUrl,
  createEvidencePreviewUrl,
  deleteEvidence,
  getEvidenceDetail,
  restoreEvidence,
  searchEvidence,
  type EvidenceDetail,
  type EvidenceSummary,
} from '@/api/evidence'

const { t } = useTranslation()

const loading = ref(false)
const errorMessage = ref('')
const rows = ref<EvidenceSummary[]>([])
const page = ref(0)
const size = ref(20)
const totalPages = ref(0)
const detail = ref<EvidenceDetail | null>(null)
const previewUrl = ref('')

const filters = reactive({
  keyword: '',
  taskCode: '',
  alarmEventCode: '',
  deviceCode: '',
  assetType: '',
  includeDeleted: false,
})

const canOperate = computed(() =>
  authenticationState.roles.some((role) =>
    ['ADMIN', 'OPERATOR'].includes(role),
  ),
)

async function loadPage(nextPage: number) {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await searchEvidence({
      page: nextPage,
      size: size.value,
      keyword: filters.keyword || undefined,
      taskCode: filters.taskCode || undefined,
      alarmEventCode: filters.alarmEventCode || undefined,
      deviceCode: filters.deviceCode || undefined,
      assetType: filters.assetType || undefined,
      includeDeleted: filters.includeDeleted || undefined,
    })
    rows.value = result.content
    page.value = result.page
    totalPages.value = result.totalPages
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : t('evidence.loadFailed')
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.taskCode = ''
  filters.alarmEventCode = ''
  filters.deviceCode = ''
  filters.assetType = ''
  filters.includeDeleted = false
  loadPage(0)
}

function closeDetail() {
  detail.value = null
  previewUrl.value = ''
}

async function openDetail(evidenceCode: string) {
  loading.value = true
  errorMessage.value = ''
  try {
    detail.value = await getEvidenceDetail(evidenceCode)
    previewUrl.value = ''
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : t('evidence.loadFailed')
  } finally {
    loading.value = false
  }
}

async function preview(evidenceCode: string) {
  loading.value = true
  errorMessage.value = ''
  try {
    const access = await createEvidencePreviewUrl(evidenceCode)
    if (!detail.value || detail.value.evidenceCode !== evidenceCode) {
      detail.value = await getEvidenceDetail(evidenceCode)
    }
    previewUrl.value = access.url
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : t('evidence.previewFailed')
  } finally {
    loading.value = false
  }
}

async function download(evidenceCode: string) {
  loading.value = true
  errorMessage.value = ''
  try {
    const access = await createEvidenceDownloadUrl(evidenceCode)
    window.open(access.url, '_blank', 'noopener')
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : t('evidence.downloadFailed')
  } finally {
    loading.value = false
  }
}

async function remove(evidenceCode: string) {
  if (!window.confirm(t('evidence.confirmDelete', { code: evidenceCode }))) {
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    await deleteEvidence(evidenceCode)
    if (detail.value?.evidenceCode === evidenceCode) {
      closeDetail()
    }
    await loadPage(page.value)
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : t('evidence.deleteFailed')
  } finally {
    loading.value = false
  }
}

async function restore(evidenceCode: string) {
  loading.value = true
  errorMessage.value = ''
  try {
    await restoreEvidence(evidenceCode)
    await loadPage(page.value)
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : t('evidence.restoreFailed')
  } finally {
    loading.value = false
  }
}

function sourceLabel(sourceType: string) {
  const key = `evidence.source.${sourceType}`
  const translated = t(key)
  return translated === key ? sourceType : translated
}

function formatTime(value: string) {
  return new Date(value).toLocaleString()
}

onMounted(() => loadPage(0))
</script>

<style scoped>
.evidence-page {
  padding: 32px 24px 48px;
}

.evidence-panel {
  max-width: 1280px;
  margin: 0 auto;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
  margin-bottom: 24px;
}

h1 {
  margin: 0 0 8px;
  font-size: 32px;
}

.subtitle {
  margin: 0;
  max-width: 42rem;
  line-height: 1.5;
}

.header-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}

.nav-link,
.secondary-button,
.primary-button,
.text-button {
  border: 1px solid var(--st-border);
  border-radius: 8px;
  background: var(--st-bg-elevated);
  color: var(--st-text);
  text-decoration: none;
  padding: 8px 12px;
  cursor: pointer;
  font: inherit;
}

.primary-button {
  background: var(--st-color-primary);
  border-color: var(--st-color-primary);
  color: #fff;
}

.text-button.danger {
  color: var(--st-danger);
  border-color: color-mix(in srgb, var(--st-danger) 35%, var(--st-border));
}

.filter-form,
.table-wrap {
  margin-bottom: 20px;
  padding: 18px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
}

label,
.checkbox-field {
  display: grid;
  gap: 6px;
}

label > span,
.checkbox-field > span:first-child {
  font-size: 13px;
  color: var(--st-text-muted);
}

input,
select {
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--st-border);
  background: var(--st-input-bg);
  color: var(--st-text);
  min-height: 40px;
  box-sizing: border-box;
}

.checkbox-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  color: var(--st-text);
}

.checkbox-row label {
  display: inline;
  font-size: 14px;
  color: var(--st-text);
  cursor: pointer;
}

.form-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 16px;
}

.table-scroll {
  overflow-x: auto;
}

table {
  width: 100%;
  min-width: 980px;
  border-collapse: collapse;
}

th,
td {
  padding: 12px 10px;
  border-bottom: 1px solid var(--st-border);
  text-align: left;
  vertical-align: middle;
  font-size: 14px;
}

th {
  color: var(--st-text-muted);
  font-weight: 600;
  white-space: nowrap;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}

.empty {
  color: var(--st-text-muted);
  text-align: center;
  padding: 28px 10px;
}

tr.deleted td {
  opacity: 0.62;
}

.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.type-pill {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
}

.type-pill.image {
  background: color-mix(in srgb, var(--st-color-primary) 18%, transparent);
  color: var(--st-color-accent);
}

.type-pill.video {
  background: color-mix(in srgb, var(--st-success) 18%, transparent);
  color: var(--st-success);
}

.pager {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: flex-end;
  margin-top: 16px;
}

.pager-meta {
  color: var(--st-text-muted);
  font-size: 13px;
}

.loading-text,
.error-message {
  margin: 0 0 16px;
}

.error-message {
  padding: 10px 12px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--st-danger) 16%, transparent);
  color: var(--st-danger);
}

.loading-text {
  color: var(--st-text-muted);
}

.detail-drawer {
  position: fixed;
  right: 24px;
  top: 96px;
  width: min(440px, calc(100vw - 32px));
  max-height: calc(100vh - 120px);
  overflow: auto;
  z-index: 30;
  padding: 18px;
}

.drawer-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 16px;
}

.drawer-header h2 {
  margin: 0;
  font-size: 20px;
  word-break: break-all;
}

.detail-grid {
  display: grid;
  gap: 12px;
  margin: 0 0 16px;
}

.detail-grid dt {
  margin: 0 0 4px;
  font-size: 12px;
  color: var(--st-text-muted);
}

.detail-grid dd {
  margin: 0;
  font-size: 14px;
}

.drawer-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.preview img,
.preview video {
  width: 100%;
  border-radius: 12px;
  border: 1px solid var(--st-border);
  background: #000;
}

@media (max-width: 760px) {
  .evidence-page {
    padding: 14px;
  }

  .panel-header {
    flex-direction: column;
  }

  .detail-drawer {
    right: 12px;
    left: 12px;
    width: auto;
    top: 80px;
  }
}
</style>
