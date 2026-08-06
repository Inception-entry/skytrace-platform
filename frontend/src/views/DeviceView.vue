<template>
  <main class="device-page st-page">
    <section class="device-panel">
      <header class="panel-header">
        <div>
          <p class="eyebrow">{{ $t('devices.eyebrow') }}</p>
          <h1>{{ $t('devices.title') }}</h1>
          <p class="subtitle">{{ $t('devices.subtitleDetail') }}</p>
        </div>

        <div class="header-actions">
          <button
            v-if="canOperate"
            class="secondary-button"
            type="button"
            :disabled="loading"
            @click="openCreateForm"
          >
            {{ $t('devices.create') }}
          </button>
          <RouterLink class="nav-link" to="/drone">{{ $t('nav.tasks') }}</RouterLink>
          <RouterLink class="nav-link" to="/knowledge">{{ $t('nav.knowledge') }}</RouterLink>
        </div>
      </header>

      <form
        v-if="formVisible"
        class="device-form st-panel"
        @submit.prevent="saveDevice"
      >
        <div class="form-title">
          <div>
            <h2>{{ editingDeviceCode ? $t('devices.editTitle') : $t('devices.createTitle') }}</h2>
            <p>{{ $t('devices.formHint') }}</p>
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
            <span>{{ $t('devices.deviceCode') }}</span>
            <input
              v-model.trim="form.deviceCode"
              maxlength="64"
              pattern="[A-Za-z0-9_-]+"
              :placeholder="$t('devices.deviceCodePlaceholder')"
              :disabled="loading || Boolean(editingDeviceCode)"
              required
            />
          </label>
          <label>
            <span>{{ $t('devices.deviceName') }}</span>
            <input
              v-model.trim="form.deviceName"
              maxlength="128"
              :placeholder="$t('devices.deviceNamePlaceholder')"
              :disabled="loading"
              required
            />
          </label>
          <label>
            <span>{{ $t('devices.deviceType') }}</span>
            <input
              v-model.trim="form.deviceType"
              maxlength="64"
              pattern="[A-Za-z0-9_-]+"
              :placeholder="$t('devices.deviceTypePlaceholder')"
              :disabled="loading"
              required
            />
          </label>
        </div>

        <div class="form-actions">
          <button class="primary-button" type="submit" :disabled="loading">
            {{ editingDeviceCode ? $t('devices.saveChanges') : $t('devices.createDevice') }}
          </button>
        </div>
      </form>

      <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
      <p v-if="successMessage" class="success-message">
        {{ successMessage }}
      </p>

      <div class="table-wrap st-panel">
        <table>
          <thead>
            <tr>
              <th>{{ $t('devices.code') }}</th>
              <th>{{ $t('devices.name') }}</th>
              <th>{{ $t('devices.type') }}</th>
              <th>{{ $t('common.status') }}</th>
              <th>{{ $t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!devices.length">
              <td colspan="5" class="empty">{{ $t('devices.empty') }}</td>
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
                  {{ device.status === 'ONLINE' ? $t('devices.online') : $t('devices.offline') }}
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
                  {{ $t('common.edit') }}
                </button>
                <button
                  v-if="canOperate"
                  type="button"
                  class="text-button"
                  :disabled="loading"
                  @click="sendHeartbeat(device.deviceCode)"
                >
                  {{ $t('devices.heartbeat') }}
                </button>
                <button
                  v-if="canOperate"
                  type="button"
                  class="text-button"
                  :disabled="loading"
                  @click="removeDevice(device.deviceCode)"
                >
                  {{ $t('common.delete') }}
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
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useTranslation } from 'i18next-vue'
import { authenticationState } from '@/auth/keycloak'
import {
  createDevice,
  getDevices,
  heartbeatDevice,
  updateDevice,
  type Device,
  deleteDevice,
} from '@/api/device'

const { t } = useTranslation()

const devices = ref<Device[]>([])
const loading = ref(false)
const formVisible = ref(false)
const editingDeviceCode = ref<string | null>(null)
const errorMessage = ref('')
const successMessage = ref('')

let pollingId: number | undefined

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

async function refresh(silent = false) {
  if (!silent) loading.value = true
  errorMessage.value = ''
  try {
    devices.value = await getDevices()
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : t('devices.loadFailed')
  } finally {
    if (!silent) loading.value = false
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
      successMessage.value = t('devices.updated')
    } else {
      await createDevice({
        deviceCode: form.deviceCode,
        deviceName: form.deviceName,
        deviceType: form.deviceType,
      })
      successMessage.value = t('devices.created')
    }
    closeForm()
    await refresh()
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : t('devices.saveFailed')
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
      error instanceof Error ? error.message : t('devices.heartbeatFailed')
  } finally {
    loading.value = false
  }
}

async function removeDevice(deviceCode: string) {
  if (!window.confirm(t('devices.confirmDelete', { code: deviceCode }))) {
    return
  }
  loading.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await deleteDevice(deviceCode)
    successMessage.value = t('devices.deleteSuccess')
    await refresh()
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : t('devices.deleteFailed')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void refresh()
  pollingId = window.setInterval(() => {
    if (!document.hidden && !loading.value) {
      void refresh(true)
    }
  }, 5_000)
})

onBeforeUnmount(() => {
  if (pollingId !== undefined) {
    window.clearInterval(pollingId)
  }
})
</script>

<style scoped>
.device-page {
  padding: 32px 24px 48px;
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

h1 {
  margin: 0 0 8px;
  font-size: 32px;
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
}

.primary-button {
  background: var(--st-color-primary);
  border-color: var(--st-color-primary);
  color: #fff;
}

.device-form,
.table-wrap {
  margin-bottom: 20px;
  padding: 18px;
}

.form-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.form-title p {
  color: var(--st-text-muted);
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
  color: var(--st-text-muted);
}

input {
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--st-border);
  background: var(--st-input-bg);
  color: var(--st-text);
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
  border-bottom: 1px solid var(--st-border);
  text-align: left;
}

.empty {
  color: var(--st-text-muted);
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
  background: color-mix(in srgb, var(--st-success) 20%, transparent);
  color: var(--st-success);
}

.status-pill.offline {
  background: var(--st-bg-elevated);
  color: var(--st-text-muted);
}

.error-message,
.success-message {
  margin: 0 0 16px;
  padding: 10px 12px;
  border-radius: 8px;
}

.error-message {
  background: color-mix(in srgb, var(--st-danger) 16%, transparent);
  color: var(--st-danger);
}

.success-message {
  background: color-mix(in srgb, var(--st-success) 16%, transparent);
  color: var(--st-success);
}
</style>
