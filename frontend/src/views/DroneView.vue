<template>
  <main class="task-page st-page">
    <section class="task-panel st-panel">
      <header class="panel-header">
        <div>
          <p class="eyebrow">{{ $t('tasks.eyebrow') }}</p>
          <h1>{{ $t('tasks.title') }}</h1>
          <p class="subtitle">{{ $t('tasks.subtitle') }}</p>
        </div>

        <div class="header-actions">
          <button
            v-if="canOperate"
            class="secondary-button"
            type="button"
            :disabled="loading"
            @click="openCreateForm"
          >
            {{ $t('tasks.create') }}
          </button>
          <RouterLink class="knowledge-link" to="/devices">
            {{ $t('nav.devices') }}
          </RouterLink>
          <RouterLink class="knowledge-link" to="/routes">
            {{ $t('nav.routes') }}
          </RouterLink>
          <RouterLink v-if="canOperate" class="chat-link" to="/chat">
            {{ $t('nav.chat') }}
          </RouterLink>
          <RouterLink class="knowledge-link" to="/knowledge">
            {{ $t('nav.knowledge') }}
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
            <h2>{{ editingTaskCode ? $t('tasks.editTitle') : $t('tasks.createTitle') }}</h2>
            <p>{{ $t('tasks.formHint') }}</p>
          </div>
          <button
            class="text-button"
            type="button"
            :disabled="loading"
            @click="closeForm"
          >
            {{ $t('common.close') }}
          </button>
        </div>

        <div class="form-grid">
          <label>
            <span>{{ $t('tasks.taskCode') }}</span>
            <input
              v-model.trim="form.taskCode"
              maxlength="64"
              pattern="[A-Za-z0-9_-]+"
              :placeholder="$t('tasks.taskCodePlaceholder')"
              :disabled="loading || Boolean(editingTaskCode)"
              required
            />
          </label>

          <label>
            <span>{{ $t('tasks.taskName') }}</span>
            <input
              v-model.trim="form.taskName"
              maxlength="128"
              :placeholder="$t('tasks.taskNamePlaceholder')"
              :disabled="loading"
              required
            />
          </label>

          <label>
            <span>{{ $t('tasks.device') }}</span>
            <select
              v-model="form.deviceCode"
              :disabled="loading || devices.length === 0"
              required
            >
              <option disabled value="">
                {{ devices.length ? $t('tasks.selectDevice') : $t('tasks.noDevice') }}
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
            <span>{{ $t('tasks.route') }}</span>
            <select
              v-model="form.routeCode"
              :disabled="loading"
            >
              <option value="">{{ $t('tasks.noRoute') }}</option>
              <option
                v-for="route in routes"
                :key="route.routeCode"
                :value="route.routeCode"
              >
                {{ route.routeName }}（{{ route.routeCode }}）
              </option>
            </select>
          </label>

          <label>
            <span>{{ $t('tasks.plannedStart') }}</span>
            <input
              v-model="form.planStartTime"
              type="datetime-local"
              :disabled="loading"
              required
            />
          </label>

          <label>
            <span>{{ $t('tasks.plannedEnd') }}</span>
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
            {{ loading ? $t('common.saving') : $t('tasks.save') }}
          </button>
          <button
            class="secondary-button"
            type="button"
            :disabled="loading"
            @click="closeForm"
          >
            {{ $t('common.cancel') }}
          </button>
        </div>
      </form>

      <div class="toolbar">
        <p>{{ $t('tasks.totalTasks', { count: tasks.length }) }}</p>
        <button
          class="secondary-button"
          type="button"
          :disabled="loading"
          @click="loadTasks"
        >
          {{ $t('common.refresh') }}
        </button>
      </div>

      <p v-if="successMessage" class="success">
        {{ successMessage }}
      </p>
      <p v-if="errorMessage" class="error">
        {{ errorMessage }}
      </p>

      <p v-if="loading && !formVisible" class="loading-text">
        {{ $t('common.loading') }}
      </p>

      <div v-else class="table-wrapper">
        <table>
          <thead>
            <tr>
              <th>{{ $t('tasks.taskInfo') }}</th>
              <th>{{ $t('tasks.device') }}</th>
              <th>{{ $t('nav.routes') }}</th>
              <th>{{ $t('tasks.plannedTime') }}</th>
              <th>{{ $t('common.status') }}</th>
              <th>{{ $t('common.actions') }}</th>
            </tr>
          </thead>

          <tbody>
            <tr v-for="task in tasks" :key="task.taskCode">
              <td>
                <strong>{{ task.taskName }}</strong>
                <small>{{ task.taskCode }}</small>
              </td>
              <td>
                <strong>{{ task.deviceName || task.deviceCode || $t('common.unset') }}</strong>
                <small v-if="task.deviceCode">
                  {{ task.deviceCode }}
                  <template v-if="task.deviceStatus">
                    · {{ task.deviceStatus }}
                  </template>
                </small>
              </td>
              <td>
                <strong>{{ task.routeName || task.routeCode || $t('tasks.unbound') }}</strong>
                <small v-if="task.routeCode">{{ task.routeCode }}</small>
              </td>
              <td>
                <span>{{ formatDateTime(task.planStartTime) }}</span>
                <small>{{ $t('tasks.timeTo', { time: formatDateTime(task.planEndTime) }) }}</small>
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
                    {{ $t('tasks.evidence') }}
                  </button>
                  <template v-if="canOperate">
                    <button
                      type="button"
                      :disabled="loading || task.status !== 'CREATED'"
                      @click="handleStart(task.taskCode)"
                    >
                      {{ $t('tasks.start') }}
                    </button>
                    <button
                      type="button"
                      :disabled="loading || isTerminal(task.status)"
                      @click="openEditForm(task)"
                    >
                      {{ $t('common.edit') }}
                    </button>
                    <button
                      type="button"
                      :disabled="loading || task.status !== 'RUNNING'"
                      @click="handleComplete(task.taskCode)"
                    >
                      {{ $t('tasks.complete') }}
                    </button>
                    <button
                      type="button"
                      :disabled="loading || task.status !== 'RUNNING'"
                      @click="handleCancel(task.taskCode)"
                    >
                      {{ $t('common.cancel') }}
                    </button>
                  </template>
                  <span v-else class="read-only-hint">{{ $t('common.readOnlyHint') }}</span>
                </div>
              </td>
            </tr>

            <tr v-if="tasks.length === 0">
              <td colspan="6" class="empty-cell">{{ $t('tasks.empty') }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <section v-if="selectedTaskCode" class="evidence-panel">
        <div class="evidence-header">
          <div>
            <h2>{{ $t('tasks.evidenceTitle', { code: selectedTaskCode }) }}</h2>
            <p>{{ $t('tasks.evidenceHint') }}</p>
          </div>
          <div class="evidence-actions">
            <label class="upload-button">
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp,video/mp4,video/webm"
                :disabled="loading || !canOperate"
                @change="handleEvidenceUpload"
              />
              {{ canOperate ? $t('tasks.uploadEvidence') : $t('common.readOnly') }}
            </label>
            <button
              class="secondary-button"
              type="button"
              :disabled="loading"
              @click="loadEvidence(selectedTaskCode)"
            >
              {{ $t('tasks.refreshEvidence') }}
            </button>
          </div>
        </div>

        <p v-if="evidenceLoading" class="loading-text">{{ $t('tasks.evidenceLoading') }}</p>
        <ul v-else-if="evidenceList.length" class="evidence-list">
          <li
            v-for="item in evidenceList"
            :key="item.evidenceCode || item.objectKey"
          >
            <div>
              <strong>{{ item.originalFilename || item.objectKey }}</strong>
              <small>
                <template v-if="item.evidenceCode">
                  {{ item.evidenceCode }} ·
                </template>
                {{ item.contentType }} · {{ formatBytes(item.sizeBytes) }}
                <template v-if="item.createdAt">
                  · {{ formatDateTime(item.createdAt) }}
                </template>
              </small>
            </div>
            <button
              class="evidence-link"
              type="button"
              :disabled="evidenceOpeningCode === (item.evidenceCode || item.objectKey)"
              @click="openEvidence(item)"
            >
              {{ $t('common.open') }}
            </button>
          </li>
        </ul>
        <p v-else class="empty-evidence">{{ $t('tasks.evidenceEmpty') }}</p>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useTranslation } from 'i18next-vue'
import { authenticationState } from '@/auth/keycloak'
import {
  getEvidence,
  uploadEvidence,
  type EvidenceAsset,
} from '@/api/alarm-evidence'
import { getDevices, type Device } from '@/api/device'
import { getRoutes, type Route } from '@/api/route'
import {
  cancelInspectionTask,
  completeInspectionTask,
  createInspectionTask,
  getInspectionTasks,
  startInspectionTask,
  updateInspectionTask,
  type InspectionTask,
} from '@/api/inspection-task'
import { createEvidencePreviewUrl } from '@/api/evidence'

interface TaskForm {
  taskCode: string
  taskName: string
  deviceCode: string
  routeCode: string
  planStartTime: string
  planEndTime: string
}

const { t } = useTranslation()

const tasks = ref<InspectionTask[]>([])
const devices = ref<Device[]>([])
const routes = ref<Route[]>([])
const evidenceList = ref<EvidenceAsset[]>([])
const selectedTaskCode = ref('')
const loading = ref(false)
const evidenceLoading = ref(false)
const evidenceOpeningCode = ref('')
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
    routeCode: '',
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
  await Promise.all([loadDevices(), loadRoutes()])
  if (devices.value.length === 1) {
    form.deviceCode = devices.value[0].deviceCode
  }
  formVisible.value = true
}

const openEditForm = async (task: InspectionTask) => {
  resetMessages()
  editingTaskCode.value = task.taskCode
  await Promise.all([loadDevices(), loadRoutes()])
  Object.assign(form, {
    taskCode: task.taskCode,
    taskName: task.taskName,
    deviceCode: task.deviceCode ?? '',
    routeCode: task.routeCode ?? '',
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
    errorMessage.value = errorText(error, t('tasks.loadDevicesFailed'))
  }
}

const loadRoutes = async () => {
  try {
    routes.value = await getRoutes()
  } catch (error) {
    errorMessage.value = errorText(error, t('tasks.loadRoutesFailed'))
  }
}

const loadTasks = async () => {
  loading.value = true
  errorMessage.value = ''

  try {
    tasks.value = await getInspectionTasks()
  } catch (error) {
    errorMessage.value = errorText(error, t('tasks.loadFailed'))
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
    errorMessage.value = errorText(error, t('tasks.loadEvidenceFailed'))
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
    successMessage.value = t('tasks.evidenceUploaded', { code: selectedTaskCode.value })
    await loadEvidence(selectedTaskCode.value)
  } catch (error) {
    errorMessage.value = errorText(error, t('tasks.uploadEvidenceFailed'))
  } finally {
    loading.value = false
    input.value = ''
  }
}

const saveTask = async () => {
  resetMessages()
  if (!form.deviceCode) {
    errorMessage.value = t('tasks.selectDeviceRequired')
    return
  }
  if (form.planEndTime <= form.planStartTime) {
    errorMessage.value = t('tasks.invalidPlanRange')
    return
  }

  loading.value = true
  try {
    const details = {
      taskName: form.taskName,
      deviceCode: form.deviceCode,
      routeCode: form.routeCode || undefined,
      planStartTime: form.planStartTime,
      planEndTime: form.planEndTime,
    }

    if (editingTaskCode.value) {
      await updateInspectionTask(editingTaskCode.value, details)
      successMessage.value = t('tasks.updated', { code: editingTaskCode.value })
    } else {
      await createInspectionTask({
        taskCode: form.taskCode,
        ...details,
      })
      successMessage.value = t('tasks.created', { code: form.taskCode })
    }

    formVisible.value = false
    resetForm()
    await loadTasks()
  } catch (error) {
    errorMessage.value = errorText(error, t('tasks.saveFailed'))
    loading.value = false
  }
}

async function openEvidence(item: EvidenceAsset) {
  const openingKey = item.evidenceCode || item.objectKey
  evidenceOpeningCode.value = openingKey
  errorMessage.value = ''
  try {
    if (!item.evidenceCode) {
      // 兼容历史数据：仍可临时走 publicPath
      window.open(item.publicPath, '_blank', 'noopener')
      return
    }
    const access = await createEvidencePreviewUrl(item.evidenceCode)
    window.open(access.url, '_blank', 'noopener')
  } catch (error) {
    errorMessage.value = errorText(error, t('tasks.openEvidenceFailed'))
  } finally {
    evidenceOpeningCode.value = ''
  }
}

const handleStart = async (taskCode: string) => {
  await runTaskAction(
    () => startInspectionTask(taskCode),
    taskCode,
    t('tasks.started'),
    t('tasks.startFailed'),
  )
}

const handleComplete = async (taskCode: string) => {
  await runTaskAction(
    () => completeInspectionTask(taskCode),
    taskCode,
    t('tasks.completed'),
    t('tasks.completeFailed'),
  )
}

const handleCancel = async (taskCode: string) => {
  await runTaskAction(
    () => cancelInspectionTask(taskCode),
    taskCode,
    t('tasks.cancelled'),
    t('tasks.cancelFailed'),
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
    successMessage.value = t('tasks.actionDone', { code: taskCode, action: successText })
    await loadTasks()
  } catch (error) {
    errorMessage.value = errorText(error, failureText)
    loading.value = false
  }
}

const isTerminal = (status: string) =>
  status === 'COMPLETED' || status === 'CANCELLED'

const statusLabel = (status: string) => {
  const key = `tasks.status.${status}`
  const translated = t(key)
  return translated === key ? status : translated
}

const toDateTimeInput = (value: string | null) =>
  value ? value.slice(0, 16) : ''

const formatDateTime = (value: string | null) =>
  value ? value.replace('T', ' ').slice(0, 16) : t('common.unset')

const formatBytes = (size: number) => {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

const errorText = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message : fallback

onMounted(async () => {
  await Promise.all([loadTasks(), loadDevices(), loadRoutes()])
})
</script>

<style scoped>
.task-page {
  padding: 36px;
}

.task-panel {
  max-width: 1240px;
  margin: 0 auto;
  padding: 28px;
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

.form-title p,
small,
.toolbar p {
  color: var(--st-text-muted);
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
  border: 1px solid var(--st-border);
  border-radius: 8px;
  font: inherit;
  cursor: pointer;
  background: var(--st-bg-elevated);
  color: var(--st-text);
}

.chat-link,
.primary-button {
  color: #fff;
  text-decoration: none;
  background: var(--st-color-primary);
  border-color: var(--st-color-primary);
}

.knowledge-link {
  color: var(--st-color-accent);
  text-decoration: none;
  background: var(--st-bg-elevated);
  border-color: var(--st-border);
}

.secondary-button {
  color: var(--st-text);
  background: var(--st-bg-elevated);
}

.text-button {
  color: var(--st-text-muted);
  border: 0;
  background: transparent;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.task-form {
  margin: 26px 0;
  padding: 22px;
  background: var(--st-bg-elevated);
  border: 1px solid var(--st-border);
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
  color: var(--st-text-muted);
  font-size: 13px;
  font-weight: 650;
}

input,
select {
  width: 100%;
  padding: 10px 12px;
  box-sizing: border-box;
  color: var(--st-text);
  background: var(--st-input-bg);
  border: 1px solid var(--st-border);
  border-radius: 8px;
  outline: none;
}

input:focus,
select:focus {
  border-color: var(--st-color-primary);
  box-shadow: 0 0 0 3px var(--st-color-primary-soft);
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
  color: var(--st-danger);
  background: color-mix(in srgb, var(--st-danger) 14%, transparent);
}

.success {
  color: var(--st-success);
  background: color-mix(in srgb, var(--st-success) 14%, transparent);
}

.loading-text,
.empty-cell {
  color: var(--st-text-muted);
  text-align: center;
}

.read-only-hint {
  color: var(--st-text-muted);
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
  border-bottom: 1px solid var(--st-border);
}

th {
  color: var(--st-text-muted);
  font-size: 12px;
  letter-spacing: 0.04em;
  background: var(--st-bg-elevated);
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
  color: var(--st-text-muted);
  font-size: 12px;
  font-weight: 700;
  background: var(--st-bg-elevated);
  border-radius: 999px;
}

.status-badge.running {
  color: var(--st-color-accent);
  background: var(--st-color-primary-soft);
}

.status-badge.completed {
  color: var(--st-success);
  background: color-mix(in srgb, var(--st-success) 18%, transparent);
}

.status-badge.cancelled {
  color: var(--st-danger);
  background: color-mix(in srgb, var(--st-danger) 18%, transparent);
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
  background: var(--st-bg-elevated);
  border: 1px solid var(--st-border);
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
  color: var(--st-text-muted);
}

.evidence-actions {
  justify-content: flex-end;
}

.upload-button {
  display: inline-flex;
  align-items: center;
  padding: 9px 13px;
  color: #fff;
  background: var(--st-color-primary);
  border: 1px solid var(--st-color-primary);
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
  border-bottom: 1px solid var(--st-border);
}

.evidence-link {
  color: var(--st-color-accent);
  text-decoration: none;
  white-space: nowrap;
  background: transparent;
  border: 0;
  padding: 0;
  cursor: pointer;
  font: inherit;
}

.evidence-link:disabled {
  opacity: 0.6;
  cursor: wait;
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
