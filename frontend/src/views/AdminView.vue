<template>
  <main class="admin-page st-page">
    <section class="admin-shell">
      <header class="page-header st-panel">
        <div>
          <p class="eyebrow">{{ $t('audit.eyebrow') }}</p>
          <h1>{{ $t('audit.title') }}</h1>
          <p class="subtitle">{{ $t('audit.subtitleDetail') }}</p>
        </div>
        <nav>
          <RouterLink to="/drone">{{ $t('nav.tasks') }}</RouterLink>
          <RouterLink to="/devices">{{ $t('nav.devices') }}</RouterLink>
          <RouterLink to="/knowledge">{{ $t('nav.knowledge') }}</RouterLink>
          <button type="button" :disabled="loading" @click="refreshAll">
            {{ $t('common.refresh') }}
          </button>
        </nav>
      </header>

      <p v-if="errorMessage" class="error-message">
        {{ errorMessage }}
      </p>

      <section class="metric-grid">
        <article class="metric-card primary st-panel">
          <span>{{ $t('audit.metricTasks') }}</span>
          <strong>{{ overview.totalTasks }}</strong>
          <small>
            {{ $t('audit.metricTasksDetail', {
              running: overview.runningTasks,
              created: overview.createdTasks,
            }) }}
          </small>
        </article>
        <article class="metric-card st-panel">
          <span>{{ $t('audit.metricAnalyses') }}</span>
          <strong>{{ overview.totalAnalyses }}</strong>
          <small>{{ $t('audit.metricAnalysesDetail') }}</small>
        </article>
        <article class="metric-card st-panel">
          <span>{{ $t('audit.metricEvents') }}</span>
          <strong>{{ overview.totalAuditEvents }}</strong>
          <small>{{ $t('audit.metricEventsDetail') }}</small>
        </article>
        <article
          class="metric-card st-panel"
          :class="{ warning: overview.failedAuditEventsLast24Hours > 0 }"
        >
          <span>{{ $t('audit.metricFailures') }}</span>
          <strong>{{ overview.failedAuditEventsLast24Hours }}</strong>
          <small>{{ $t('audit.metricFailuresDetail') }}</small>
        </article>
      </section>

      <section class="audit-card st-panel">
        <div class="section-heading">
          <div>
            <h2>{{ $t('audit.logs') }}</h2>
            <p>{{ $t('audit.logsHint') }}</p>
          </div>
          <span class="total-mark">{{ $t('common.totalCount', { count: auditPage.totalElements }) }}</span>
        </div>

        <form class="filters" @submit.prevent="applyFilters">
          <label>
            <span>{{ $t('audit.actionType') }}</span>
            <select v-model="filters.action">
              <option value="">{{ $t('audit.allActions') }}</option>
              <option
                v-for="item in actionOptions"
                :key="item"
                :value="item"
              >
                {{ actionLabel(item) }}
              </option>
            </select>
          </label>
          <label>
            <span>{{ $t('audit.outcome') }}</span>
            <select v-model="filters.outcome">
              <option value="">{{ $t('audit.allOutcomes') }}</option>
              <option value="SUCCESS">{{ $t('audit.outcomeSuccess') }}</option>
              <option value="FAILURE">{{ $t('audit.outcomeFailure') }}</option>
            </select>
          </label>
          <label class="username-filter">
            <span>{{ $t('audit.username') }}</span>
            <input
              v-model.trim="filters.username"
              maxlength="128"
              :placeholder="$t('audit.usernamePlaceholder')"
            />
          </label>
          <button class="search-button" type="submit" :disabled="loading">
            {{ $t('common.query') }}
          </button>
          <button
            class="reset-button"
            type="button"
            :disabled="loading"
            @click="resetFilters"
          >
            {{ $t('common.reset') }}
          </button>
        </form>

        <div class="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>{{ $t('audit.colTimeAction') }}</th>
                <th>{{ $t('audit.colAccount') }}</th>
                <th>{{ $t('audit.colResource') }}</th>
                <th>{{ $t('audit.colResult') }}</th>
                <th>{{ $t('audit.colTrace') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="audit in auditPage.content" :key="audit.id">
                <td>
                  <strong>{{ actionLabel(audit.action) }}</strong>
                  <small>{{ formatTime(audit.createdAt) }}</small>
                </td>
                <td>
                  <strong>{{ audit.username }}</strong>
                  <small>{{ audit.roles || $t('common.noRoles') }}</small>
                </td>
                <td>
                  <span>{{ resourceLabel(audit.resourceType) }}</span>
                  <small>{{ audit.resourceId || audit.requestPath }}</small>
                </td>
                <td>
                  <span
                    class="outcome"
                    :class="audit.outcome.toLowerCase()"
                  >
                    {{ audit.outcome === 'SUCCESS' ? $t('audit.outcomeSuccess') : $t('audit.outcomeFailure') }}
                  </span>
                  <small>
                    HTTP {{ audit.statusCode }} · {{ audit.durationMs }} ms
                  </small>
                </td>
                <td>
                  <code :title="audit.requestId">
                    {{ shortRequestId(audit.requestId) }}
                  </code>
                  <small>{{ audit.clientIp }}</small>
                </td>
              </tr>
              <tr v-if="!loading && auditPage.content.length === 0">
                <td class="empty-cell" colspan="5">{{ $t('audit.emptyFiltered') }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <footer class="pagination">
          <span>
            {{ $t('common.pageOf', {
              current: auditPage.page + 1,
              total: Math.max(auditPage.totalPages, 1),
            }) }}
          </span>
          <div>
            <button
              type="button"
              :disabled="loading || auditPage.page === 0"
              @click="changePage(auditPage.page - 1)"
            >
              {{ $t('common.prevPage') }}
            </button>
            <button
              type="button"
              :disabled="
                loading
                  || auditPage.page + 1 >= auditPage.totalPages
              "
              @click="changePage(auditPage.page + 1)"
            >
              {{ $t('common.nextPage') }}
            </button>
          </div>
        </footer>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useTranslation } from 'i18next-vue'
import {
  getAdminOverview,
  getAuditLogs,
  type AdminOverview,
  type AuditLogPage,
} from '@/api/admin'
import i18n from '@/i18n'

const { t } = useTranslation()

const emptyOverview = (): AdminOverview => ({
  totalTasks: 0,
  createdTasks: 0,
  runningTasks: 0,
  completedTasks: 0,
  cancelledTasks: 0,
  totalAnalyses: 0,
  totalAuditEvents: 0,
  failedAuditEventsLast24Hours: 0,
})

const emptyPage = (): AuditLogPage => ({
  content: [],
  totalElements: 0,
  totalPages: 0,
  page: 0,
  size: 20,
})

const overview = ref(emptyOverview())
const auditPage = ref(emptyPage())
const loading = ref(false)
const errorMessage = ref('')
const filters = reactive({
  action: '',
  outcome: '' as '' | 'SUCCESS' | 'FAILURE',
  username: '',
})

const actionOptions = [
  'TASK_CREATE',
  'TASK_UPDATE',
  'WORKFLOW_START',
  'WORKFLOW_COMPLETE',
  'WORKFLOW_CANCEL',
  'AI_ANALYSIS',
  'AI_ANALYSIS_STREAM',
  'KNOWLEDGE_UPLOAD',
  'KNOWLEDGE_DELETE',
  'ALARM_CREATE',
]

async function refreshAll() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [summary, audits] = await Promise.all([
      getAdminOverview(),
      getAuditLogs({
        page: auditPage.value.page,
        size: auditPage.value.size,
        ...filters,
      }),
    ])
    overview.value = summary
    auditPage.value = audits
  } catch (error) {
    errorMessage.value = error instanceof Error
      ? error.message
      : t('audit.loadFailed')
  } finally {
    loading.value = false
  }
}

async function applyFilters() {
  auditPage.value.page = 0
  await refreshAll()
}

async function resetFilters() {
  filters.action = ''
  filters.outcome = ''
  filters.username = ''
  auditPage.value.page = 0
  await refreshAll()
}

async function changePage(page: number) {
  auditPage.value.page = page
  await refreshAll()
}

const actionLabel = (action: string) => {
  const key = `audit.actions.${action}`
  const translated = t(key)
  return translated === key ? action : translated
}

const resourceLabel = (type: string) => {
  const key = `audit.resources.${type}`
  const translated = t(key)
  return translated === key ? type : translated
}

const formatTime = (value: string) =>
  new Date(value).toLocaleString(
    i18n.language === 'en' ? 'en-US' : 'zh-CN',
    { hour12: false },
  )

const shortRequestId = (value: string) =>
  value.length > 18 ? `${value.slice(0, 8)}…${value.slice(-6)}` : value

onMounted(refreshAll)
</script>

<style scoped>
.admin-page {
  padding: 34px;
}

.admin-shell {
  width: min(1380px, 100%);
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 26px 28px;
}

.page-header h1 {
  margin: 3px 0 5px;
  font-size: 28px;
}

nav {
  display: flex;
  gap: 9px;
  padding-right: 220px;
}

button,
nav a,
select,
input {
  padding: 9px 12px;
  font: inherit;
  border: 1px solid var(--st-border);
  border-radius: 9px;
}

button,
nav a {
  color: var(--st-text);
  text-decoration: none;
  background: var(--st-bg-elevated);
  cursor: pointer;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.48;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin: 20px 0;
}

.metric-card {
  padding: 20px;
}

.metric-card span,
.metric-card small {
  display: block;
  color: var(--st-text-muted);
}

.metric-card strong {
  display: block;
  margin: 8px 0;
  font-size: 31px;
}

.metric-card.primary {
  color: #fff;
  background: linear-gradient(135deg, var(--st-color-primary), var(--st-color-accent));
  border-color: transparent;
}

.metric-card.primary span,
.metric-card.primary small {
  color: color-mix(in srgb, #fff 80%, transparent);
}

.metric-card.warning strong {
  color: var(--st-danger);
}

.audit-card {
  padding: 24px;
}

.section-heading,
.filters,
.pagination,
.pagination div {
  display: flex;
  align-items: center;
}

.section-heading,
.pagination {
  justify-content: space-between;
}

.section-heading h2,
.section-heading p {
  margin: 0;
}

.section-heading p {
  margin-top: 4px;
  color: var(--st-text-muted);
}

.total-mark {
  padding: 6px 10px;
  color: var(--st-color-accent);
  font-weight: 700;
  background: var(--st-color-primary-soft);
  border-radius: 999px;
}

.filters {
  gap: 12px;
  margin: 22px 0 18px;
  padding: 15px;
  background: var(--st-bg-elevated);
  border-radius: 12px;
}

.filters label {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--st-text-muted);
  font-size: 13px;
}

.username-filter {
  flex: 1;
}

.username-filter input,
select {
  color: var(--st-text);
  background: var(--st-input-bg);
}

.username-filter input {
  width: 100%;
  box-sizing: border-box;
}

.search-button {
  color: #fff;
  background: var(--st-color-primary);
  border-color: var(--st-color-primary);
}

.table-wrapper {
  overflow-x: auto;
}

table {
  width: 100%;
  min-width: 1040px;
  border-collapse: collapse;
}

th,
td {
  padding: 14px 12px;
  text-align: left;
  border-bottom: 1px solid var(--st-border);
}

th {
  color: var(--st-text-muted);
  font-size: 12px;
  background: var(--st-bg-elevated);
}

td strong,
td small {
  display: block;
}

td small {
  max-width: 300px;
  margin-top: 5px;
  overflow: hidden;
  color: var(--st-text-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}

code {
  color: var(--st-text);
  font-size: 12px;
}

.outcome {
  display: inline-block;
  padding: 4px 8px;
  font-size: 12px;
  font-weight: 800;
  border-radius: 999px;
}

.outcome.success {
  color: var(--st-success);
  background: color-mix(in srgb, var(--st-success) 18%, transparent);
}

.outcome.failure {
  color: var(--st-danger);
  background: color-mix(in srgb, var(--st-danger) 18%, transparent);
}

.pagination {
  margin-top: 18px;
  color: var(--st-text-muted);
}

.pagination div {
  gap: 8px;
}

.error-message {
  padding: 11px 14px;
  color: var(--st-danger);
  background: color-mix(in srgb, var(--st-danger) 14%, transparent);
  border: 1px solid color-mix(in srgb, var(--st-danger) 35%, transparent);
  border-radius: 10px;
}

.empty-cell {
  padding: 32px;
  color: var(--st-text-muted);
  text-align: center;
}

@media (max-width: 980px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .page-header,
  .filters {
    align-items: stretch;
    flex-direction: column;
  }

  nav {
    padding-right: 0;
  }
}
</style>
