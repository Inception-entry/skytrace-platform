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
          <label>
            <span>{{ $t('evidence.reviewStatus') }}</span>
            <select v-model="filters.reviewStatus" :disabled="loading">
              <option value="">{{ $t('evidence.allReviewStatuses') }}</option>
              <option value="PENDING">{{ $t('evidence.review.PENDING') }}</option>
              <option value="APPROVED">{{ $t('evidence.review.APPROVED') }}</option>
              <option value="REJECTED">{{ $t('evidence.review.REJECTED') }}</option>
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

      <div
        v-if="selectedCodes.length > 0"
        class="batch-bar st-panel"
      >
        <span class="batch-meta">
          {{ $t('evidence.selectedCount', { count: selectedCodes.length }) }}
        </span>
        <div class="batch-actions">
          <button
            v-if="canOperate"
            class="primary-button"
            type="button"
            :disabled="loading"
            @click="batchReview('APPROVED')"
          >
            {{ $t('evidence.batchApprove') }}
          </button>
          <button
            v-if="canOperate"
            class="secondary-button"
            type="button"
            :disabled="loading"
            @click="batchReview('REJECTED')"
          >
            {{ $t('evidence.batchReject') }}
          </button>
          <button
            v-if="canOperate"
            class="secondary-button"
            type="button"
            :disabled="loading || availableTags.length === 0"
            @click="batchTag"
          >
            {{ $t('evidence.batchTag') }}
          </button>
          <button
            class="text-button"
            type="button"
            :disabled="loading"
            @click="clearSelection"
          >
            {{ $t('evidence.clearSelection') }}
          </button>
        </div>
      </div>

      <div class="table-wrap st-panel">
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th class="check-col">
                  <input
                    type="checkbox"
                    :checked="allPageSelected"
                    :indeterminate.prop="somePageSelected && !allPageSelected"
                    :disabled="loading || rows.length === 0"
                    :aria-label="$t('evidence.selectAll')"
                    @change="onToggleSelectAll"
                  />
                </th>
                <th>{{ $t('evidence.previewThumb') }}</th>
                <th>{{ $t('evidence.code') }}</th>
                <th>{{ $t('evidence.filename') }}</th>
                <th>{{ $t('evidence.assetType') }}</th>
                <th>{{ $t('evidence.reviewStatus') }}</th>
                <th>{{ $t('evidence.tags') }}</th>
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
                <td colspan="14" class="empty">{{ $t('evidence.empty') }}</td>
              </tr>
              <tr
                v-for="row in rows"
                :key="row.evidenceCode"
                :class="{ deleted: row.deleted }"
              >
                <td class="check-col">
                  <input
                    type="checkbox"
                    :checked="selectedSet.has(row.evidenceCode)"
                    :disabled="loading"
                    :aria-label="row.evidenceCode"
                    @change="onToggleSelect(row.evidenceCode, $event)"
                  />
                </td>
                <td>
                  <div class="thumb">
                    <img
                      v-if="thumbUrl(row)"
                      :src="thumbUrl(row)"
                      :alt="row.originalFilename || row.evidenceCode"
                    />
                    <span v-else class="thumb-placeholder">{{ $t('evidence.noThumb') }}</span>
                  </div>
                </td>
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
                <td>
                  <span
                    class="type-pill review"
                    :class="reviewClass(row.reviewStatus)"
                  >
                    {{ reviewLabel(row.reviewStatus) }}
                  </span>
                </td>
                <td>
                  <div v-if="row.tags?.length" class="tag-list">
                    <span
                      v-for="tag in row.tags"
                      :key="tag.id"
                      class="tag-chip"
                      :style="tagStyle(tag.color)"
                    >
                      {{ tag.name }}
                    </span>
                  </div>
                  <span v-else class="muted">-</span>
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

      <div class="meta-form">
        <label>
          <span>{{ $t('evidence.remark') }}</span>
          <textarea
            v-model="metaForm.remark"
            rows="3"
            :disabled="loading || !canOperate || !!detail.deleted"
          />
        </label>
        <label>
          <span>{{ $t('evidence.reviewStatus') }}</span>
          <select
            v-model="metaForm.reviewStatus"
            :disabled="loading || !canOperate || !!detail.deleted"
          >
            <option value="PENDING">{{ $t('evidence.review.PENDING') }}</option>
            <option value="APPROVED">{{ $t('evidence.review.APPROVED') }}</option>
            <option value="REJECTED">{{ $t('evidence.review.REJECTED') }}</option>
          </select>
        </label>
        <label>
          <span>{{ $t('evidence.reviewComment') }}</span>
          <textarea
            v-model="metaForm.reviewComment"
            rows="2"
            :disabled="loading || !canOperate || !!detail.deleted"
          />
        </label>
        <fieldset class="tag-fieldset">
          <legend>{{ $t('evidence.tags') }}</legend>
          <div v-if="availableTags.length === 0" class="muted">
            {{ $t('evidence.noTags') }}
          </div>
          <label
            v-for="tag in availableTags"
            :key="tag.id"
            class="tag-check"
          >
            <input
              v-model="metaForm.tagIds"
              type="checkbox"
              :value="tag.id"
              :disabled="loading || !canOperate || !!detail.deleted"
            />
            <span class="tag-chip" :style="tagStyle(tag.color)">{{ tag.name }}</span>
          </label>
        </fieldset>
        <button
          v-if="canOperate && !detail.deleted"
          class="primary-button"
          type="button"
          :disabled="loading"
          @click="saveMetadata"
        >
          {{ $t('evidence.saveMetadata') }}
        </button>
      </div>

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
  batchReviewEvidence,
  batchTagEvidence,
  createEvidenceDownloadUrl,
  createEvidencePreviewUrl,
  deleteEvidence,
  getEvidenceDetail,
  listEvidenceTags,
  restoreEvidence,
  searchEvidence,
  updateEvidenceMetadata,
  type EvidenceDetail,
  type EvidenceReviewStatus,
  type EvidenceSummary,
  type EvidenceTag,
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
const availableTags = ref<EvidenceTag[]>([])
const selectedCodes = ref<string[]>([])

const filters = reactive({
  keyword: '',
  taskCode: '',
  alarmEventCode: '',
  deviceCode: '',
  assetType: '',
  reviewStatus: '',
  includeDeleted: false,
})

const metaForm = reactive({
  remark: '',
  reviewStatus: 'PENDING' as EvidenceReviewStatus | string,
  reviewComment: '',
  tagIds: [] as number[],
})

const canOperate = computed(() =>
  authenticationState.roles.some((role) =>
    ['ADMIN', 'OPERATOR'].includes(role),
  ),
)

const selectedSet = computed(() => new Set(selectedCodes.value))

const allPageSelected = computed(
  () =>
    rows.value.length > 0 &&
    rows.value.every((row) => selectedSet.value.has(row.evidenceCode)),
)

const somePageSelected = computed(() =>
  rows.value.some((row) => selectedSet.value.has(row.evidenceCode)),
)

function syncMetaForm(item: EvidenceDetail) {
  metaForm.remark = item.remark || ''
  metaForm.reviewStatus = item.reviewStatus || 'PENDING'
  metaForm.reviewComment = item.reviewComment || ''
  metaForm.tagIds = (item.tags || []).map((tag) => tag.id)
}

async function loadTags() {
  try {
    availableTags.value = await listEvidenceTags()
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : t('evidence.loadTagsFailed')
  }
}

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
      reviewStatus: filters.reviewStatus || undefined,
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
  filters.reviewStatus = ''
  filters.includeDeleted = false
  clearSelection()
  loadPage(0)
}

function clearSelection() {
  selectedCodes.value = []
}

function eventChecked(event: Event) {
  return (event.target as HTMLInputElement).checked
}

function onToggleSelect(evidenceCode: string, event: Event) {
  toggleSelect(evidenceCode, eventChecked(event))
}

function onToggleSelectAll(event: Event) {
  toggleSelectAll(eventChecked(event))
}

function toggleSelect(evidenceCode: string, checked: boolean) {
  if (checked) {
    if (!selectedCodes.value.includes(evidenceCode)) {
      selectedCodes.value = [...selectedCodes.value, evidenceCode]
    }
    return
  }
  selectedCodes.value = selectedCodes.value.filter(
    (code) => code !== evidenceCode,
  )
}

function toggleSelectAll(checked: boolean) {
  if (!checked) {
    const pageCodes = new Set(rows.value.map((row) => row.evidenceCode))
    selectedCodes.value = selectedCodes.value.filter(
      (code) => !pageCodes.has(code),
    )
    return
  }
  const merged = new Set(selectedCodes.value)
  rows.value.forEach((row) => merged.add(row.evidenceCode))
  selectedCodes.value = Array.from(merged)
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
    syncMetaForm(detail.value)
    previewUrl.value = ''
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : t('evidence.loadFailed')
  } finally {
    loading.value = false
  }
}

async function saveMetadata() {
  if (!detail.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    await updateEvidenceMetadata(detail.value.evidenceCode, {
      remark: metaForm.remark,
      reviewStatus: metaForm.reviewStatus,
      reviewComment: metaForm.reviewComment,
      tagIds: [...metaForm.tagIds],
    })
    detail.value = await getEvidenceDetail(detail.value.evidenceCode)
    syncMetaForm(detail.value)
    await loadPage(page.value)
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : t('evidence.saveMetadataFailed')
  } finally {
    loading.value = false
  }
}

async function batchReview(reviewStatus: EvidenceReviewStatus) {
  if (selectedCodes.value.length === 0) return
  loading.value = true
  errorMessage.value = ''
  try {
    await batchReviewEvidence({
      evidenceCodes: [...selectedCodes.value],
      reviewStatus,
    })
    clearSelection()
    await loadPage(page.value)
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : t('evidence.batchFailed')
  } finally {
    loading.value = false
  }
}

async function batchTag() {
  if (selectedCodes.value.length === 0 || availableTags.value.length === 0) {
    return
  }
  const options = availableTags.value
    .map((tag) => `${tag.id}:${tag.name}`)
    .join(', ')
  const raw = window.prompt(t('evidence.batchTagPrompt', { options }))
  if (raw == null) return
  const tagIds = raw
    .split(/[\s,]+/)
    .map((part) => Number(part.trim()))
    .filter((id) => Number.isFinite(id) && id > 0)
  if (tagIds.length === 0) {
    errorMessage.value = t('evidence.batchTagInvalid')
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    await batchTagEvidence({
      evidenceCodes: [...selectedCodes.value],
      tagIds,
    })
    clearSelection()
    await loadPage(page.value)
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : t('evidence.batchFailed')
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
      syncMetaForm(detail.value)
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
    selectedCodes.value = selectedCodes.value.filter(
      (code) => code !== evidenceCode,
    )
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

function thumbUrl(row: EvidenceSummary) {
  return row.thumbnailUrl || row.posterUrl || ''
}

function tagStyle(color: string | null | undefined) {
  if (!color) return undefined
  return {
    background: color,
    color: '#fff',
  }
}

function reviewClass(status?: string) {
  const value = (status || 'PENDING').toUpperCase()
  if (value === 'APPROVED') return 'approved'
  if (value === 'REJECTED') return 'rejected'
  return 'pending'
}

function reviewLabel(status?: string) {
  const value = (status || 'PENDING').toUpperCase()
  const key = `evidence.review.${value}`
  const translated = t(key)
  return translated === key ? value : translated
}

function sourceLabel(sourceType: string) {
  const key = `evidence.source.${sourceType}`
  const translated = t(key)
  return translated === key ? sourceType : translated
}

function formatTime(value: string) {
  return new Date(value).toLocaleString()
}

onMounted(async () => {
  await loadTags()
  await loadPage(0)
})
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
.batch-bar,
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
select,
textarea {
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--st-border);
  background: var(--st-input-bg);
  color: var(--st-text);
  min-height: 40px;
  box-sizing: border-box;
  font: inherit;
}

textarea {
  resize: vertical;
  min-height: 72px;
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

.batch-bar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.batch-meta {
  color: var(--st-text-muted);
  font-size: 14px;
}

.batch-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.table-scroll {
  overflow-x: auto;
}

table {
  width: 100%;
  min-width: 1180px;
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

.check-col {
  width: 36px;
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

.thumb {
  width: 56px;
  height: 40px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid var(--st-border);
  background: color-mix(in srgb, var(--st-bg-elevated) 70%, #000);
  display: grid;
  place-items: center;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb-placeholder,
.muted {
  color: var(--st-text-muted);
  font-size: 11px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.tag-chip {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  background: color-mix(in srgb, var(--st-color-primary) 16%, transparent);
  color: var(--st-color-accent);
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

.type-pill.review.pending {
  background: color-mix(in srgb, var(--st-text-muted) 18%, transparent);
  color: var(--st-text-muted);
}

.type-pill.review.approved {
  background: color-mix(in srgb, var(--st-success) 18%, transparent);
  color: var(--st-success);
}

.type-pill.review.rejected {
  background: color-mix(in srgb, var(--st-danger) 18%, transparent);
  color: var(--st-danger);
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

.meta-form {
  display: grid;
  gap: 12px;
  margin-bottom: 16px;
}

.tag-fieldset {
  border: 1px solid var(--st-border);
  border-radius: 8px;
  padding: 10px 12px;
  margin: 0;
  display: grid;
  gap: 8px;
}

.tag-fieldset legend {
  padding: 0 4px;
  font-size: 13px;
  color: var(--st-text-muted);
}

.tag-check {
  display: flex;
  align-items: center;
  gap: 8px;
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
