<template>
  <main class="task-page">
    <section class="task-panel">
      <header class="panel-header">
        <div>
          <p class="eyebrow">SKYTRACE INSPECTION</p>
          <h1>无人机巡检任务</h1>
          <p class="subtitle">先维护真实任务数据，再交给 Temporal 执行</p>
        </div>

        <div class="header-actions">
          <button
            v-if="canOperate"
            class="secondary-button"
            type="button"
            :disabled="loading"
            @click="openCreateForm"
          >
            新建任务
          </button>
          <RouterLink class="knowledge-link" to="/devices">
            设备管理
          </RouterLink>
          <RouterLink v-if="canOperate" class="chat-link" to="/chat">
            AI 智能分析
          </RouterLink>
          <RouterLink class="knowledge-link" to="/knowledge">
            知识库
          </RouterLink>
        </div>
      </header>

      <form
        v-if="formVisible"
        class="task-form"
        @submit.prevent="saveTask"
      >
        <div class="form-title">
          <div>
            <h2>{{ editingTaskCode ? '编辑巡检任务' : '新建巡检任务' }}</h2>
            <p>设备编号和计划时间会作为 AI 分析的真实上下文。</p>
          </div>
          <button
            class="text-button"
            type="button"
            :disabled="loading"
            @click="closeForm"
          >
            关闭
          </button>
        </div>

        <div class="form-grid">
          <label>
            <span>任务编号</span>
            <input
              v-model.trim="form.taskCode"
              maxlength="64"
              pattern="[A-Za-z0-9_-]+"
              placeholder="例如 TASK-006"
              :disabled="loading || Boolean(editingTaskCode)"
              required
            />
          </label>

          <label>
            <span>任务名称</span>
            <input
              v-model.trim="form.taskName"
              maxlength="128"
              placeholder="例如 东区输电线路巡检"
              :disabled="loading"
              required
            />
          </label>

          <label>
            <span>设备编号</span>
            <input
              v-model.trim="form.deviceCode"
              maxlength="64"
              placeholder="例如 UAV-001"
              :disabled="loading"
              required
            />
          </label>

          <label>
            <span>计划开始时间</span>
            <input
              v-model="form.planStartTime"
              type="datetime-local"
              :disabled="loading"
              required
            />
          </label>

          <label>
            <span>计划结束时间</span>
            <input
              v-model="form.planEndTime"
              type="datetime-local"
              :disabled="loading"
              required
            />
          </label>
        </div>

        <div class="form-actions">
          <button
            class="primary-button"
            type="submit"
            :disabled="loading"
          >
            {{ loading ? '保存中……' : '保存任务' }}
          </button>
          <button
            class="secondary-button"
            type="button"
            :disabled="loading"
            @click="closeForm"
          >
            取消
          </button>
        </div>
      </form>

      <div class="toolbar">
        <p>共 {{ tasks.length }} 条任务</p>
        <button
          class="secondary-button"
          type="button"
          :disabled="loading"
          @click="loadTasks"
        >
          刷新
        </button>
      </div>

      <p v-if="successMessage" class="success">
        {{ successMessage }}
      </p>
      <p v-if="errorMessage" class="error">
        {{ errorMessage }}
      </p>

      <p v-if="loading && !formVisible" class="loading-text">
        加载中……
      </p>

      <div v-else class="table-wrapper">
        <table>
          <thead>
            <tr>
              <th>任务信息</th>
              <th>设备</th>
              <th>计划时间</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>

          <tbody>
            <tr v-for="task in tasks" :key="task.taskCode">
              <td>
                <strong>{{ task.taskName }}</strong>
                <small>{{ task.taskCode }}</small>
              </td>
              <td>{{ task.deviceCode || '未设置' }}</td>
              <td>
                <span>{{ formatDateTime(task.planStartTime) }}</span>
                <small>至 {{ formatDateTime(task.planEndTime) }}</small>
              </td>
              <td>
                <span class="status-badge" :class="task.status.toLowerCase()">
                  {{ statusLabel(task.status) }}
                </span>
              </td>
              <td>
                <div v-if="canOperate" class="row-actions">
                  <button
                    type="button"
                    :disabled="loading || task.status !== 'CREATED'"
                    @click="handleStart(task.taskCode)"
                  >
                    启动
                  </button>
                  <button
                    type="button"
                    :disabled="loading || isTerminal(task.status)"
                    @click="openEditForm(task)"
                  >
                    编辑
                  </button>
                  <button
                    type="button"
                    :disabled="loading || task.status !== 'RUNNING'"
                    @click="handleComplete(task.taskCode)"
                  >
                    完成
                  </button>
                  <button
                    type="button"
                    :disabled="loading || task.status !== 'RUNNING'"
                    @click="handleCancel(task.taskCode)"
                  >
                    取消
                  </button>
                </div>
                <span v-else class="read-only-hint">只读权限</span>
              </td>
            </tr>

            <tr v-if="tasks.length === 0">
              <td colspan="5" class="empty-cell">暂无巡检任务</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { authenticationState } from '@/auth/keycloak'
import {
  cancelInspectionTask,
  completeInspectionTask,
  createInspectionTask,
  getInspectionTasks,
  startInspectionTask,
  updateInspectionTask,
  type InspectionTask,
} from '@/api/inspection-task'

interface TaskForm {
  taskCode: string
  taskName: string
  deviceCode: string
  planStartTime: string
  planEndTime: string
}

const tasks = ref<InspectionTask[]>([])
const loading = ref(false)
const formVisible = ref(false)
const editingTaskCode = ref('')
const errorMessage = ref('')
const successMessage = ref('')
const canOperate = computed(() =>
  authenticationState.roles.some((role) =>
    role === 'ADMIN' || role === 'OPERATOR',
  ),
)
const form = reactive<TaskForm>(createEmptyForm())

function createEmptyForm(): TaskForm {
  return {
    taskCode: '',
    taskName: '',
    deviceCode: '',
    planStartTime: '',
    planEndTime: '',
  }
}

const delay = (milliseconds: number) =>
  new Promise(resolve => setTimeout(resolve, milliseconds))

const resetMessages = () => {
  errorMessage.value = ''
  successMessage.value = ''
}

const resetForm = () => {
  Object.assign(form, createEmptyForm())
  editingTaskCode.value = ''
}

const openCreateForm = () => {
  resetMessages()
  resetForm()
  formVisible.value = true
}

const openEditForm = (task: InspectionTask) => {
  resetMessages()
  editingTaskCode.value = task.taskCode
  Object.assign(form, {
    taskCode: task.taskCode,
    taskName: task.taskName,
    deviceCode: task.deviceCode ?? '',
    planStartTime: toDateTimeInput(task.planStartTime),
    planEndTime: toDateTimeInput(task.planEndTime),
  })
  formVisible.value = true
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const closeForm = () => {
  formVisible.value = false
  resetForm()
}

const loadTasks = async () => {
  loading.value = true
  errorMessage.value = ''

  try {
    tasks.value = await getInspectionTasks()
  } catch (error) {
    errorMessage.value = errorText(error, '加载失败')
  } finally {
    loading.value = false
  }
}

const saveTask = async () => {
  resetMessages()
  if (form.planEndTime <= form.planStartTime) {
    errorMessage.value = '计划结束时间必须晚于计划开始时间'
    return
  }

  loading.value = true
  try {
    const details = {
      taskName: form.taskName,
      deviceCode: form.deviceCode,
      planStartTime: form.planStartTime,
      planEndTime: form.planEndTime,
    }

    if (editingTaskCode.value) {
      await updateInspectionTask(editingTaskCode.value, details)
      successMessage.value = `任务 ${editingTaskCode.value} 已更新`
    } else {
      await createInspectionTask({
        taskCode: form.taskCode,
        ...details,
      })
      successMessage.value = `任务 ${form.taskCode} 已创建，可以启动执行`
    }

    formVisible.value = false
    resetForm()
    await loadTasks()
  } catch (error) {
    errorMessage.value = errorText(error, '保存失败')
    loading.value = false
  }
}

const handleStart = async (taskCode: string) => {
  await runTaskAction(
    () => startInspectionTask(taskCode),
    taskCode,
    '已启动',
    '启动失败',
  )
}

const handleComplete = async (taskCode: string) => {
  await runTaskAction(
    () => completeInspectionTask(taskCode),
    taskCode,
    '已完成',
    '完成失败',
  )
}

const handleCancel = async (taskCode: string) => {
  await runTaskAction(
    () => cancelInspectionTask(taskCode),
    taskCode,
    '已取消',
    '取消失败',
  )
}

const runTaskAction = async (
  action: () => Promise<unknown>,
  taskCode: string,
  successText: string,
  failureText: string,
) => {
  loading.value = true
  resetMessages()

  try {
    await action()
    await delay(500)
    successMessage.value = `任务 ${taskCode} ${successText}`
    await loadTasks()
  } catch (error) {
    errorMessage.value = errorText(error, failureText)
    loading.value = false
  }
}

const isTerminal = (status: string) =>
  status === 'COMPLETED' || status === 'CANCELLED'

const statusLabel = (status: string) =>
  ({
    CREATED: '待启动',
    RUNNING: '执行中',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
  })[status] ?? status

const toDateTimeInput = (value: string | null) =>
  value ? value.slice(0, 16) : ''

const formatDateTime = (value: string | null) =>
  value ? value.replace('T', ' ').slice(0, 16) : '未设置'

const errorText = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message : fallback

onMounted(loadTasks)
</script>

<style scoped>
.task-page {
  min-height: 100vh;
  padding: 36px;
  box-sizing: border-box;
  color: #172033;
  background: #f4f7fb;
}

.task-panel {
  max-width: 1240px;
  margin: 0 auto;
  padding: 28px;
  background: white;
  border: 1px solid #e4eaf2;
  border-radius: 16px;
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.07);
}

.panel-header,
.form-title,
.toolbar,
.header-actions,
.form-actions,
.row-actions {
  display: flex;
  align-items: center;
}

.panel-header,
.form-title,
.toolbar {
  justify-content: space-between;
  gap: 20px;
}

.panel-header h1,
.form-title h2 {
  margin: 3px 0 5px;
}

.eyebrow,
.subtitle,
.form-title p,
.toolbar p {
  margin: 0;
}

.eyebrow {
  color: #2563eb;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.subtitle,
.form-title p,
small,
.toolbar p {
  color: #718096;
}

.header-actions,
.form-actions,
.row-actions {
  gap: 9px;
}

.chat-link,
.knowledge-link,
button {
  padding: 9px 13px;
  border: 1px solid #d7dfeb;
  border-radius: 8px;
  font: inherit;
  cursor: pointer;
  background: white;
}

.chat-link,
.primary-button {
  color: white;
  text-decoration: none;
  background: #2563eb;
  border-color: #2563eb;
}

.knowledge-link {
  color: #1d4ed8;
  text-decoration: none;
  background: #eff6ff;
  border-color: #bfdbfe;
}

.secondary-button {
  color: #334155;
  background: #f8fafc;
}

.text-button {
  color: #64748b;
  border: 0;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.task-form {
  margin: 26px 0;
  padding: 22px;
  background: #f8faff;
  border: 1px solid #dce7f8;
  border-radius: 13px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin: 20px 0;
}

.form-grid label {
  display: flex;
  flex-direction: column;
  gap: 7px;
  color: #475569;
  font-size: 13px;
  font-weight: 650;
}

input {
  width: 100%;
  padding: 10px 12px;
  box-sizing: border-box;
  color: #172033;
  background: white;
  border: 1px solid #ccd6e4;
  border-radius: 8px;
  outline: none;
}

input:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12);
}

.toolbar {
  margin: 24px 0 14px;
}

.error,
.success {
  padding: 11px 14px;
  border-radius: 8px;
}

.error {
  color: #b91c1c;
  background: #fef2f2;
}

.success {
  color: #166534;
  background: #f0fdf4;
}

.loading-text,
.empty-cell {
  color: #64748b;
  text-align: center;
}

.read-only-hint {
  color: #64748b;
  font-size: 12px;
}

.table-wrapper {
  overflow-x: auto;
}

table {
  width: 100%;
  min-width: 980px;
  border-collapse: collapse;
}

th,
td {
  padding: 14px 12px;
  text-align: left;
  border-bottom: 1px solid #e5eaf1;
}

th {
  color: #64748b;
  font-size: 12px;
  letter-spacing: 0.04em;
  background: #f8fafc;
}

td strong,
td small,
td span {
  display: block;
}

td small {
  margin-top: 4px;
}

.status-badge {
  width: fit-content;
  padding: 5px 9px;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
  background: #f1f5f9;
  border-radius: 999px;
}

.status-badge.running {
  color: #1d4ed8;
  background: #dbeafe;
}

.status-badge.completed {
  color: #15803d;
  background: #dcfce7;
}

.status-badge.cancelled {
  color: #b91c1c;
  background: #fee2e2;
}

.row-actions {
  flex-wrap: wrap;
}

.row-actions button {
  padding: 6px 9px;
  font-size: 12px;
}

@media (max-width: 760px) {
  .task-page {
    padding: 14px;
  }

  .task-panel {
    padding: 18px;
  }

  .panel-header,
  .form-title {
    align-items: flex-start;
    flex-direction: column;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
