<template>
  <main class="device-page">
    <section class="device-panel">
      <header class="panel-header">
        <div>
          <p class="eyebrow">SKYTRACE DEVICES</p>
          <h1>设备管理</h1>
          <p class="subtitle">
            设备主数据来自数据库，在线状态由 heartbeat 写入 Redis
          </p>
        </div>

        <div class="header-actions">
          <button
            v-if="canOperate"
            class="secondary-button"
            type="button"
            :disabled="loading"
            @click="openCreateForm"
          >
            新建设备
          </button>
          <RouterLink class="nav-link" to="/drone">巡检任务</RouterLink>
          <RouterLink class="nav-link" to="/knowledge">知识库</RouterLink>
        </div>
      </header>

      <form
        v-if="formVisible"
        class="device-form"
        @submit.prevent="saveDevice"
      >
        <div class="form-title">
          <div>
            <h2>{{ editingDeviceCode ? '编辑设备' : '新建设备' }}</h2>
            <p>编号创建后不可修改；类型建议使用 UAV / CAMERA。</p>
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
            <span>设备编号</span>
            <input
              v-model.trim="form.deviceCode"
              maxlength="64"
              pattern="[A-Za-z0-9_-]+"
              placeholder="例如 UAV-003"
              :disabled="loading || Boolean(editingDeviceCode)"
              required
            />
          </label>
          <label>
            <span>设备名称</span>
            <input
              v-model.trim="form.deviceName"
              maxlength="128"
              placeholder="例如 东区巡检无人机"
              :disabled="loading"
              required
            />
          </label>
          <label>
            <span>设备类型</span>
            <input
              v-model.trim="form.deviceType"
              maxlength="64"
              pattern="[A-Za-z0-9_-]+"
              placeholder="例如 UAV"
              :disabled="loading"
              required
            />
          </label>
        </div>

        <div class="form-actions">
          <button class="primary-button" type="submit" :disabled="loading">
            {{ editingDeviceCode ? '保存修改' : '创建设备' }}
          </button>
        </div>
      </form>

      <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
      <p v-if="successMessage" class="success-message">
        {{ successMessage }}
      </p>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>编号</th>
              <th>名称</th>
              <th>类型</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!devices.length">
              <td colspan="5" class="empty">暂无设备</td>
            </tr>
            <tr v-for="device in devices" :key="device.deviceCode">
              <td>{{ device.deviceCode }}</td>
              <td>{{ device.deviceName }}</td>
              <td>{{ device.deviceType }}</td>
              <td>
                <span
                  class="status-pill"
                  :class="device.status === 'ONLINE' ? 'online' : 'offline'"
                >
                  {{ device.status }}
                </span>
              </td>
              <td class="actions">
                <button
                  v-if="canOperate"
                  type="button"
                  class="text-button"
                  :disabled="loading"
                  @click="openEditForm(device)"
                >
                  编辑
                </button>
                <button
                  v-if="canOperate"
                  type="button"
                  class="text-button"
                  :disabled="loading"
                  @click="sendHeartbeat(device.deviceCode)"
                >
                  Heartbeat
                </button>
              </td>
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
  createDevice,
  getDevices,
  heartbeatDevice,
  updateDevice,
  type Device,
} from '@/api/device'

const devices = ref<Device[]>([])
const loading = ref(false)
const formVisible = ref(false)
const editingDeviceCode = ref<string | null>(null)
const errorMessage = ref('')
const successMessage = ref('')

const form = reactive({
  deviceCode: '',
  deviceName: '',
  deviceType: 'UAV',
})

const canOperate = computed(() =>
  authenticationState.roles.some((role) =>
    ['ADMIN', 'OPERATOR'].includes(role),
  ),
)

async function refresh() {
  loading.value = true
  errorMessage.value = ''
  try {
    devices.value = await getDevices()
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '加载设备失败'
  } finally {
    loading.value = false
  }
}

function openCreateForm() {
  editingDeviceCode.value = null
  form.deviceCode = ''
  form.deviceName = ''
  form.deviceType = 'UAV'
  formVisible.value = true
  successMessage.value = ''
}

function openEditForm(device: Device) {
  editingDeviceCode.value = device.deviceCode
  form.deviceCode = device.deviceCode
  form.deviceName = device.deviceName
  form.deviceType = device.deviceType
  formVisible.value = true
  successMessage.value = ''
}

function closeForm() {
  formVisible.value = false
  editingDeviceCode.value = null
}

async function saveDevice() {
  loading.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    if (editingDeviceCode.value) {
      await updateDevice(editingDeviceCode.value, {
        deviceName: form.deviceName,
        deviceType: form.deviceType,
      })
      successMessage.value = '设备已更新'
    } else {
      await createDevice({
        deviceCode: form.deviceCode,
        deviceName: form.deviceName,
        deviceType: form.deviceType,
      })
      successMessage.value = '设备已创建'
    }
    closeForm()
    await refresh()
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '保存设备失败'
  } finally {
    loading.value = false
  }
}

async function sendHeartbeat(deviceCode: string) {
  loading.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const result = await heartbeatDevice(deviceCode)
    successMessage.value = `${result.deviceCode} → ${result.status}`
    await refresh()
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : 'Heartbeat 失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void refresh()
})
</script>

<style scoped>
.device-page {
  min-height: 100vh;
  padding: 32px 24px 48px;
  color: #e8eef7;
  background:
    radial-gradient(circle at top left, #1d4f7a 0%, transparent 40%),
    linear-gradient(160deg, #0b1624 0%, #132033 45%, #0a121c 100%);
}

.device-panel {
  max-width: 1080px;
  margin: 0 auto;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
  margin-bottom: 24px;
}

.eyebrow {
  margin: 0 0 8px;
  letter-spacing: 0.14em;
  font-size: 12px;
  color: #7eb6ff;
}

h1 {
  margin: 0 0 8px;
  font-size: 32px;
}

.subtitle {
  margin: 0;
  color: #9db0c7;
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
  border: 1px solid rgb(255 255 255 / 18%);
  border-radius: 8px;
  background: rgb(255 255 255 / 6%);
  color: inherit;
  text-decoration: none;
  padding: 8px 12px;
  cursor: pointer;
}

.primary-button {
  background: #2f6fed;
  border-color: #2f6fed;
}

.device-form,
.table-wrap {
  margin-bottom: 20px;
  padding: 18px;
  border: 1px solid rgb(255 255 255 / 12%);
  border-radius: 14px;
  background: rgb(8 16 28 / 72%);
}

.form-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
}

label {
  display: grid;
  gap: 6px;
}

label span {
  font-size: 13px;
  color: #9db0c7;
}

input {
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid rgb(255 255 255 / 16%);
  background: rgb(0 0 0 / 25%);
  color: inherit;
}

.form-actions {
  margin-top: 16px;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: 12px 10px;
  border-bottom: 1px solid rgb(255 255 255 / 10%);
  text-align: left;
}

.empty {
  color: #9db0c7;
  text-align: center;
}

.actions {
  display: flex;
  gap: 8px;
}

.status-pill {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
}

.status-pill.online {
  background: rgb(34 197 94 / 20%);
  color: #86efac;
}

.status-pill.offline {
  background: rgb(148 163 184 / 18%);
  color: #cbd5e1;
}

.error-message,
.success-message {
  margin: 0 0 16px;
  padding: 10px 12px;
  border-radius: 8px;
}

.error-message {
  background: rgb(239 68 68 / 16%);
  color: #fecaca;
}

.success-message {
  background: rgb(34 197 94 / 16%);
  color: #bbf7d0;
}
</style>
