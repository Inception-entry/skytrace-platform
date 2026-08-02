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
            <span>关联设备</span>
            <select
              v-model="form.deviceCode"
              :disabled="loading || devices.length === 0"
              required
            >
              <option disabled value="">
                {{ devices.length ? '请选择设备' : '暂无可用设备' }}
              </option>
              <option
                v-for="device in devices"
                :key="device.deviceCode"
                :value="device.deviceCode"
              >
                {{ device.deviceName }}（{{ device.deviceCode }} ·
                {{ device.status }}）
              </option>
            </select>
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
              <td>
                <strong>{{ task.deviceName || task.deviceCode || '未设置' }}</strong>
                <small v-if="task.deviceCode">
                  {{ task.deviceCode }}
                  <template v-if="task.deviceStatus">
                    · {{ task.deviceStatus }}
                  </template>
                </small>
              </td>
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
                <div class="row-actions">
                  <button
                    type="button"
                    :disabled="loading"
                    @click="selectTask(task.taskCode)"
                  >
                    证据
                  </button>
                  <template v-if="canOperate">
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
                  </template>
                  <span v-else class="read-only-hint">只读权限</span>
                </div>
              </td>
            </tr>

            <tr v-if="tasks.length === 0">
              <td colspan="5" class="empty-cell">暂无巡检任务</td>
            </tr>
          </tbody>
        </table>
      </div>

      <section v-if="selectedTaskCode" class="evidence-panel">
        <div class="evidence-header">
          <div>
            <h2>任务证据 · {{ selectedTaskCode }}</h2>
            <p>按任务编号查询已上传的截图/视频元数据</p>
          </div>
          <div class="evidence-actions">
            <label class="upload-button">
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp,video/mp4,video/webm"
                :disabled="loading || !canOperate"
                @change="handleEvidenceUpload"
              />
              {{ canOperate ? '上传证据' : '只读' }}
            </label>
            <button
              class="secondary-button"
              type="button"
              :disabled="loading"
              @click="loadEvidence(selectedTaskCode)"
            >
              刷新证据
            </button>
          </div>
        </div>

        <p v-if="evidenceLoading" class="loading-text">证据加载中……</p>
        <ul v-else-if="evidenceList.length" class="evidence-list">
          <li v-for="item in evidenceList" :key="item.objectKey">
            <div>
              <strong>{{ item.originalFilename || item.objectKey }}</strong>
              <small>
                {{ item.contentType }} · {{ formatBytes(item.sizeBytes) }}
                <template v-if="item.createdAt">
                  · {{ formatDateTime(item.createdAt) }}
                </template>
              </small>
            </div>
            <a
              class="evidence-link"
              :href="item.publicPath"
              target="_blank"
              rel="noopener noreferrer"
            >
              打开
            </a>
          </li>
        </ul>
        <p v-else class="empty-evidence">该任务暂无证据</p>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { authenticationState } from '@/auth/keycloak'
import {
  getEvidence,
  uploadEvidence,
  type EvidenceAsset,
} from '@/api/alarm-evidence'
import { getDevices, type Device } from '@/api/device'
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
const devices = ref<Device[]>([])
const evidenceList = ref<EvidenceAsset[]>([])
const selectedTaskCode = ref('')
const loading = ref(false)
const evidenceLoading = ref(false)
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

const openCreateForm = async () => {
  resetMessages()
  resetForm()
  await loadDevices()
  if (devices.value.length === 1) {
    form.deviceCode = devices.value[0].deviceCode
  }
  formVisible.value = true
}

const openEditForm = async (task: InspectionTask) => {
  resetMessages()
  editingTaskCode.value = task.taskCode
  await loadDevices()
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

const loadDevices = async () => {
  try {
    devices.value = await getDevices()
  } catch (error) {
    errorMessage.value = errorText(error, '加载设备失败')
  }
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

const selectTask = async (taskCode: string) => {
  selectedTaskCode.value = taskCode
  await loadEvidence(taskCode)
}

const loadEvidence = async (taskCode: string) => {
  evidenceLoading.value = true
  try {
    evidenceList.value = await getEvidence({ taskCode })
  } catch (error) {
    evidenceList.value = []
    errorMessage.value = errorText(error, '加载证据失败')
  } finally {
    evidenceLoading.value = false
  }
}

const handleEvidenceUpload = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !selectedTaskCode.value) {
    return
  }
  loading.value = true
  resetMessages()
  try {
    await uploadEvidence(file, selectedTaskCode.value)
    successMessage.value = `证据已上传到 ${selectedTaskCode.value}`
    await loadEvidence(selectedTaskCode.value)
  } catch (error) {
    errorMessage.value = errorText(error, '证据上传失败')
  } finally {
    loading.value = false
    input.value = ''
  }
}

const saveTask = async () => {
  resetMessages()
  if (!form.deviceCode) {
    errorMessage.value = '请选择关联设备'
    return
  }
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

const formatBytes = (size: number) => {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

const errorText = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message : fallback

onMounted(async () => {
  await Promise.all([loadTasks(), loadDevices()])
})
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

select {
  width: 100%;
  padding: 10px 12px;
  box-sizing: border-box;
  color: #172033;
  background: white;
  border: 1px solid #ccd6e4;
  border-radius: 8px;
  outline: none;
}

input:focus,
select:focus {
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

.evidence-panel {
  margin-top: 24px;
  padding: 18px;
  background: #f8faff;
  border: 1px solid #dce7f8;
  border-radius: 13px;
}

.evidence-header,
.evidence-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.evidence-header h2 {
  margin: 0 0 4px;
}

.evidence-header p,
.empty-evidence {
  margin: 0;
  color: #718096;
}

.evidence-actions {
  justify-content: flex-end;
}

.upload-button {
  display: inline-flex;
  align-items: center;
  padding: 9px 13px;
  color: white;
  background: #2563eb;
  border: 1px solid #2563eb;
  border-radius: 8px;
  cursor: pointer;
}

.upload-button input {
  display: none;
}

.evidence-list {
  margin: 16px 0 0;
  padding: 0;
  list-style: none;
}

.evidence-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #e5eaf1;
}

.evidence-link {
  color: #1d4ed8;
  text-decoration: none;
  white-space: nowrap;
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
