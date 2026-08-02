<template>
  <main class="admin-page">
    <section class="admin-shell">
      <header class="page-header">
        <div>
          <p class="eyebrow">SECURITY &amp; GOVERNANCE</p>
          <h1>审计中心</h1>
          <p>查看运行概况、权限边界与关键操作审计记录</p>
        </div>
        <nav>
          <RouterLink to="/drone">巡检任务</RouterLink>
          <RouterLink to="/devices">设备管理</RouterLink>
          <RouterLink to="/knowledge">知识库</RouterLink>
          <button type="button" :disabled="loading" @click="refreshAll">
            刷新
          </button>
        </nav>
      </header>

      <p v-if="errorMessage" class="error-message">
        {{ errorMessage }}
      </p>

      <section class="metric-grid">
        <article class="metric-card primary">
          <span>巡检任务</span>
          <strong>{{ overview.totalTasks }}</strong>
          <small>
            执行中 {{ overview.runningTasks }} · 待启动
            {{ overview.createdTasks }}
          </small>
        </article>
        <article class="metric-card">
          <span>AI 分析记录</span>
          <strong>{{ overview.totalAnalyses }}</strong>
          <small>包含同步与流式分析的持久化记录</small>
        </article>
        <article class="metric-card">
          <span>审计事件</span>
          <strong>{{ overview.totalAuditEvents }}</strong>
          <small>关键写操作均保留责任人和结果</small>
        </article>
        <article
          class="metric-card"
          :class="{ warning: overview.failedAuditEventsLast24Hours > 0 }"
        >
          <span>24 小时失败</span>
          <strong>{{ overview.failedAuditEventsLast24Hours }}</strong>
          <small>包括校验失败和上游执行失败</small>
        </article>
      </section>

      <section class="audit-card">
        <div class="section-heading">
          <div>
            <h2>操作审计</h2>
            <p>不记录 Token、AI Prompt、请求正文或完整响应</p>
          </div>
          <span class="total-mark">共 {{ auditPage.totalElements }} 条</span>
        </div>

        <form class="filters" @submit.prevent="applyFilters">
          <label>
            <span>操作类型</span>
            <select v-model="filters.action">
              <option value="">全部操作</option>
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
            <span>执行结果</span>
            <select v-model="filters.outcome">
              <option value="">全部结果</option>
              <option value="SUCCESS">成功</option>
              <option value="FAILURE">失败</option>
            </select>
          </label>
          <label class="username-filter">
            <span>操作账号</span>
            <input
              v-model.trim="filters.username"
              maxlength="128"
              placeholder="输入用户名"
            />
          </label>
          <button class="search-button" type="submit" :disabled="loading">
            查询
          </button>
          <button
            class="reset-button"
            type="button"
            :disabled="loading"
            @click="resetFilters"
          >
            重置
          </button>
        </form>

        <div class="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>时间 / 操作</th>
                <th>账号</th>
                <th>资源</th>
                <th>结果</th>
                <th>请求追踪</th>
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
                  <small>{{ audit.roles || '无业务角色' }}</small>
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
                    {{ audit.outcome === 'SUCCESS' ? '成功' : '失败' }}
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
                <td class="empty-cell" colspan="5">没有符合条件的审计记录</td>
              </tr>
            </tbody>
          </table>
        </div>

        <footer class="pagination">
          <span>
            第 {{ auditPage.page + 1 }} /
            {{ Math.max(auditPage.totalPages, 1) }} 页
          </span>
          <div>
            <button
              type="button"
              :disabled="loading || auditPage.page === 0"
              @click="changePage(auditPage.page - 1)"
            >
              上一页
            </button>
            <button
              type="button"
              :disabled="
                loading
                  || auditPage.page + 1 >= auditPage.totalPages
              "
              @click="changePage(auditPage.page + 1)"
            >
              下一页
            </button>
          </div>
        </footer>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  getAdminOverview,
  getAuditLogs,
  type AdminOverview,
  type AuditLogPage,
} from '@/api/admin'

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

const actionLabels: Record<string, string> = {
  TASK_CREATE: '创建巡检任务',
  TASK_UPDATE: '修改巡检任务',
  WORKFLOW_START: '启动巡检流程',
  WORKFLOW_COMPLETE: '完成巡检流程',
  WORKFLOW_CANCEL: '取消巡检流程',
  AI_ANALYSIS: '执行 AI 分析',
  AI_ANALYSIS_STREAM: '执行流式 AI 分析',
  KNOWLEDGE_UPLOAD: '上传知识文档',
  KNOWLEDGE_DELETE: '删除知识文档',
  ALARM_CREATE: '创建告警',
  API_MUTATION: '其他数据变更',
}

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
      : '管理数据加载失败'
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

const actionLabel = (action: string) =>
  actionLabels[action] ?? action

const resourceLabel = (type: string) =>
  ({
    INSPECTION_TASK: '巡检任务',
    KNOWLEDGE_DOCUMENT: '知识文档',
    ALARM: '告警',
    API: '接口资源',
  })[type] ?? type

const formatTime = (value: string) =>
  new Date(value).toLocaleString('zh-CN', { hour12: false })

const shortRequestId = (value: string) =>
  value.length > 18 ? `${value.slice(0, 8)}…${value.slice(-6)}` : value

onMounted(refreshAll)
</script>

<style scoped>
.admin-page {
  min-height: 100vh;
  padding: 34px;
  box-sizing: border-box;
  color: #172033;
  background:
    radial-gradient(circle at 12% 0, #dbeafe 0, transparent 27%),
    radial-gradient(circle at 88% 96%, #e0e7ff 0, transparent 25%),
    #f4f7fb;
}

.admin-shell {
  width: min(1380px, 100%);
  margin: 0 auto;
}

.page-header,
.audit-card,
.metric-card {
  background: rgb(255 255 255 / 94%);
  border: 1px solid #e1e7f0;
  box-shadow: 0 18px 50px rgb(15 23 42 / 7%);
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 26px 28px;
  border-radius: 18px;
}

.page-header h1 {
  margin: 3px 0 5px;
  font-size: 28px;
}

.page-header p,
.eyebrow {
  margin: 0;
}

.page-header p {
  color: #6b7280;
}

.eyebrow {
  color: #2563eb !important;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.16em;
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
  border: 1px solid #d6deea;
  border-radius: 9px;
}

button,
nav a {
  color: #334155;
  text-decoration: none;
  background: #f8fafc;
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
  border-radius: 15px;
}

.metric-card span,
.metric-card small {
  display: block;
  color: #64748b;
}

.metric-card strong {
  display: block;
  margin: 8px 0;
  font-size: 31px;
}

.metric-card.primary {
  color: white;
  background: linear-gradient(135deg, #1d4ed8, #2563eb);
  border-color: transparent;
}

.metric-card.primary span,
.metric-card.primary small {
  color: #dbeafe;
}

.metric-card.warning strong {
  color: #dc2626;
}

.audit-card {
  padding: 24px;
  border-radius: 18px;
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
  color: #64748b;
}

.total-mark {
  padding: 6px 10px;
  color: #1d4ed8;
  font-weight: 700;
  background: #eff6ff;
  border-radius: 999px;
}

.filters {
  gap: 12px;
  margin: 22px 0 18px;
  padding: 15px;
  background: #f8fafc;
  border-radius: 12px;
}

.filters label {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 13px;
}

.username-filter {
  flex: 1;
}

.username-filter input {
  width: 100%;
  box-sizing: border-box;
  background: white;
}

.search-button {
  color: white;
  background: #2563eb;
  border-color: #2563eb;
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
  border-bottom: 1px solid #e7ebf1;
}

th {
  color: #64748b;
  font-size: 12px;
  background: #f8fafc;
}

td strong,
td small {
  display: block;
}

td small {
  max-width: 300px;
  margin-top: 5px;
  overflow: hidden;
  color: #718096;
  text-overflow: ellipsis;
  white-space: nowrap;
}

code {
  color: #334155;
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
  color: #15803d;
  background: #dcfce7;
}

.outcome.failure {
  color: #b91c1c;
  background: #fee2e2;
}

.pagination {
  margin-top: 18px;
  color: #64748b;
}

.pagination div {
  gap: 8px;
}

.error-message {
  padding: 11px 14px;
  color: #b91c1c;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 10px;
}

.empty-cell {
  padding: 32px;
  color: #64748b;
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
