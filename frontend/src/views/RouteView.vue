<template>
  <main class="route-page">
    <section class="route-panel">
      <header class="panel-header">
        <div>
          <p class="eyebrow">SKYTRACE ROUTES</p>
          <h1>航线管理</h1>
          <p class="subtitle">维护巡检航线，可在任务中选择绑定</p>
        </div>
        <div class="header-actions">
          <button
            v-if="canOperate"
            class="secondary-button"
            type="button"
            :disabled="loading"
            @click="openCreateForm"
          >
            新建航线
          </button>
          <RouterLink class="nav-link" to="/drone">巡检任务</RouterLink>
          <RouterLink class="nav-link" to="/devices">设备管理</RouterLink>
        </div>
      </header>

      <form v-if="formVisible" class="route-form" @submit.prevent="saveRoute">
        <div class="form-title">
          <h2>{{ editingRouteCode ? '编辑航线' : '新建航线' }}</h2>
          <button class="text-button" type="button" @click="closeForm">关闭</button>
        </div>
        <div class="form-grid">
          <label>
            <span>航线编号</span>
            <input
              v-model.trim="form.routeCode"
              maxlength="64"
              pattern="[A-Za-z0-9_-]+"
              :disabled="loading || Boolean(editingRouteCode)"
              required
            />
          </label>
          <label>
            <span>航线名称</span>
            <input v-model.trim="form.routeName" maxlength="128" required />
          </label>
          <label class="full">
            <span>描述</span>
            <input v-model.trim="form.description" maxlength="512" />
          </label>
          <label class="full">
            <span>航点 JSON</span>
            <textarea
              v-model.trim="form.waypointsJson"
              rows="4"
              placeholder='[{"lat":31.23,"lng":121.47,"alt":80}]'
            />
          </label>
        </div>
        <button class="primary-button" type="submit" :disabled="loading">
          保存航线
        </button>
      </form>

      <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
      <p v-if="successMessage" class="success-message">{{ successMessage }}</p>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>编号</th>
              <th>名称</th>
              <th>描述</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="route in routes" :key="route.routeCode">
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
                  编辑
                </button>
              </td>
            </tr>
            <tr v-if="!routes.length">
              <td colspan="4" class="empty">暂无航线</td>
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
  createRoute,
  getRoutes,
  updateRoute,
  type Route,
} from '@/api/route'

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
      error instanceof Error ? error.message : '加载航线失败'
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
    if (editingRouteCode.value) {
      await updateRoute(editingRouteCode.value, {
        routeName: form.routeName,
        description: form.description || undefined,
        waypointsJson: form.waypointsJson || undefined,
      })
      successMessage.value = '航线已更新'
    } else {
      await createRoute({
        routeCode: form.routeCode,
        routeName: form.routeName,
        description: form.description || undefined,
        waypointsJson: form.waypointsJson || undefined,
      })
      successMessage.value = '航线已创建'
    }
    closeForm()
    await refresh()
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '保存航线失败'
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
  min-height: 100vh;
  padding: 32px 24px;
  color: #e8eef7;
  background: linear-gradient(160deg, #0b1624, #132033 50%, #0a121c);
}
.route-panel { max-width: 980px; margin: 0 auto; }
.panel-header, .form-title, .header-actions {
  display: flex; justify-content: space-between; gap: 12px; align-items: flex-start;
}
.eyebrow { color: #7eb6ff; letter-spacing: 0.12em; font-size: 12px; }
.subtitle { color: #9db0c7; }
.nav-link, .secondary-button, .primary-button, .text-button {
  border: 1px solid rgb(255 255 255 / 18%);
  border-radius: 8px;
  background: rgb(255 255 255 / 6%);
  color: inherit;
  text-decoration: none;
  padding: 8px 12px;
  cursor: pointer;
}
.primary-button { background: #2f6fed; border-color: #2f6fed; }
.route-form, .table-wrap {
  margin-top: 18px;
  padding: 16px;
  border-radius: 14px;
  border: 1px solid rgb(255 255 255 / 12%);
  background: rgb(8 16 28 / 72%);
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 14px 0;
}
.form-grid .full { grid-column: 1 / -1; }
label { display: grid; gap: 6px; }
input, textarea {
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid rgb(255 255 255 / 16%);
  background: rgb(0 0 0 / 25%);
  color: inherit;
}
table { width: 100%; border-collapse: collapse; }
th, td { padding: 10px; border-bottom: 1px solid rgb(255 255 255 / 10%); text-align: left; }
.empty { text-align: center; color: #9db0c7; }
.error-message, .success-message { margin-top: 12px; padding: 10px; border-radius: 8px; }
.error-message { background: rgb(239 68 68 / 16%); color: #fecaca; }
.success-message { background: rgb(34 197 94 / 16%); color: #bbf7d0; }
</style>
