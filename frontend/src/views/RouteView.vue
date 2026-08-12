<template>
  <main class="route-page st-page">
    <section class="route-panel">
      <header class="panel-header">
        <div>
          <p class="eyebrow">{{ $t('routes.eyebrow') }}</p>
          <h1>{{ $t('routes.title') }}</h1>
          <p class="subtitle">{{ $t('routes.subtitleDetail') }}</p>
        </div>
        <div class="header-actions">
          <button
            v-if="canOperate"
            class="secondary-button"
            type="button"
            :disabled="loading"
            @click="openCreateForm"
          >
            {{ $t('routes.create') }}
          </button>
          <RouterLink class="nav-link" to="/drone">{{ $t('nav.tasks') }}</RouterLink>
          <RouterLink class="nav-link" to="/map">{{ $t('nav.map') }}</RouterLink>
          <RouterLink class="nav-link" to="/devices">{{ $t('nav.devices') }}</RouterLink>
        </div>
      </header>

      <form v-if="formVisible" class="route-form st-panel" @submit.prevent="saveRoute">
        <div class="form-title">
          <h2>{{ editingRouteCode ? $t('routes.editTitle') : $t('routes.createTitle') }}</h2>
          <button class="text-button" type="button" @click="closeForm">{{ $t('common.close') }}</button>
        </div>
        <div class="form-grid">
          <label>
            <span>{{ $t('routes.routeCode') }}</span>
            <input
              v-model.trim="form.routeCode"
              maxlength="64"
              pattern="[A-Za-z0-9_-]+"
              :disabled="loading || Boolean(editingRouteCode)"
              required
            />
          </label>
          <label>
            <span>{{ $t('routes.routeName') }}</span>
            <input v-model.trim="form.routeName" maxlength="128" required />
          </label>
          <label class="full">
            <span>{{ $t('routes.description') }}</span>
            <input v-model.trim="form.description" maxlength="512" />
          </label>
          <div class="full map-field">
            <span class="field-label">{{ $t('routes.waypointsMap') }}</span>
            <RouteWaypointEditor
              v-model="form.waypointsJson"
              :hint="$t('routes.waypointsHint')"
              :undo-label="$t('routes.undoWaypoint')"
              :clear-label="$t('routes.clearWaypoints')"
              :count-template="$t('routes.waypointCount')"
            />
          </div>
          <label class="full">
            <span>{{ $t('routes.waypointsJsonAdvanced') }}</span>
            <textarea
              v-model.trim="form.waypointsJson"
              rows="3"
              :placeholder="$t('routes.waypointsPlaceholder')"
            />
          </label>
        </div>
        <button class="primary-button" type="submit" :disabled="loading">
          {{ $t('routes.save') }}
        </button>
      </form>

      <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
      <p v-if="successMessage" class="success-message">{{ successMessage }}</p>

      <div class="table-wrap st-panel">
        <table>
          <thead>
            <tr>
              <th>{{ $t('routes.preview') }}</th>
              <th>{{ $t('routes.code') }}</th>
              <th>{{ $t('routes.name') }}</th>
              <th>{{ $t('routes.description') }}</th>
              <th>{{ $t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="route in routes" :key="route.routeCode">
              <td>
                <RouteThumbnail
                  :waypoints-json="route.waypointsJson"
                  :title="route.routeName"
                  :empty-label="$t('routes.noWaypoints')"
                />
              </td>
              <td>{{ route.routeCode }}</td>
              <td>{{ route.routeName }}</td>
              <td>{{ route.description || '—' }}</td>
              <td>
                <button
                  v-if="canOperate"
                  class="text-button"
                  type="button"
                  @click="openEditForm(route)"
                >
                  {{ $t('common.edit') }}
                </button>
              </td>
            </tr>
            <tr v-if="!routes.length">
              <td colspan="5" class="empty">{{ $t('routes.empty') }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useTranslation } from 'i18next-vue'
import { authenticationState } from '@/auth/keycloak'
import {
  createRoute,
  getRoutes,
  updateRoute,
  type Route,
} from '@/api/route'
import { parseWaypointsJson } from '@/libs/route/waypoints'
import RouteThumbnail from '@/components/route-thumbnail/index.vue'
import RouteWaypointEditor from '@/components/route-waypoint-editor/index.vue'

const { t } = useTranslation()

const routes = ref<Route[]>([])
const loading = ref(false)
const formVisible = ref(false)
const editingRouteCode = ref<string | null>(null)
const errorMessage = ref('')
const successMessage = ref('')
const form = reactive({
  routeCode: '',
  routeName: '',
  description: '',
  waypointsJson: '',
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
    routes.value = await getRoutes()
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : t('routes.loadFailed')
  } finally {
    loading.value = false
  }
}

function openCreateForm() {
  editingRouteCode.value = null
  form.routeCode = ''
  form.routeName = ''
  form.description = ''
  form.waypointsJson = ''
  formVisible.value = true
}

function openEditForm(route: Route) {
  editingRouteCode.value = route.routeCode
  form.routeCode = route.routeCode
  form.routeName = route.routeName
  form.description = route.description || ''
  form.waypointsJson = route.waypointsJson || ''
  formVisible.value = true
}

function closeForm() {
  formVisible.value = false
  editingRouteCode.value = null
}

async function saveRoute() {
  loading.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    if (form.waypointsJson.trim()) {
      try {
        JSON.parse(form.waypointsJson)
      } catch {
        throw new Error(t('routes.invalidWaypointsJson'))
      }
      if (!parseWaypointsJson(form.waypointsJson).length) {
        throw new Error(t('routes.invalidWaypointsJson'))
      }
    }
    if (editingRouteCode.value) {
      await updateRoute(editingRouteCode.value, {
        routeName: form.routeName,
        description: form.description || undefined,
        waypointsJson: form.waypointsJson || undefined,
      })
      successMessage.value = t('routes.updated')
    } else {
      await createRoute({
        routeCode: form.routeCode,
        routeName: form.routeName,
        description: form.description || undefined,
        waypointsJson: form.waypointsJson || undefined,
      })
      successMessage.value = t('routes.created')
    }
    closeForm()
    await refresh()
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : t('routes.saveFailed')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void refresh()
})
</script>

<style scoped>
.route-page {
  padding: 32px 24px;
}

.route-panel {
  max-width: 1080px;
  margin: 0 auto;
}

.panel-header,
.form-title,
.header-actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
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

.route-form,
.table-wrap {
  margin-top: 18px;
  padding: 16px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 14px 0;
}

.form-grid .full {
  grid-column: 1 / -1;
}

.map-field {
  display: grid;
  gap: 8px;
}

.field-label {
  color: var(--st-text-muted);
}

label {
  display: grid;
  gap: 6px;
  color: var(--st-text-muted);
}

input,
textarea {
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--st-border);
  background: var(--st-input-bg);
  color: var(--st-text);
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: 10px;
  border-bottom: 1px solid var(--st-border);
  text-align: left;
  vertical-align: middle;
}

.empty {
  text-align: center;
  color: var(--st-text-muted);
}

.error-message,
.success-message {
  margin-top: 12px;
  padding: 10px;
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
