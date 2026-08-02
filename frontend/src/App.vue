<template>
  <a-config-provider
    :theme="currentTheme"
  >
    <router-view />
    <div
      v-if="authenticationState.authenticated"
      class="authentication-user"
    >
      <span>{{ authenticationState.username }}</span>
      <span class="authentication-role">{{ displayedRoles }}</span>
      <RouterLink
        v-if="isAdministrator"
        class="admin-link"
        to="/audit"
      >
        审计中心
      </RouterLink>
      <a-button size="small" @click="handleLogout">退出</a-button>
    </div>
  </a-config-provider>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { useThemeStore } from '@/store/modules/theme'
import { authenticationState, logout } from '@/auth/keycloak'

const themeStore = useThemeStore()
const currentTheme = computed(() => themeStore.getThemeValue);
const displayedRoles = computed(() =>
  authenticationState.roles
    .filter((role) => ['ADMIN', 'OPERATOR', 'VIEWER'].includes(role))
    .join(', '),
)
const isAdministrator = computed(() =>
  authenticationState.roles.includes('ADMIN'),
)

const handleLogout = () => {
  void logout()
}

</script>
<style scoped>
.authentication-user {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 2000;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  color: #fff;
  background: rgb(0 21 41 / 78%);
  border: 1px solid rgb(255 255 255 / 20%);
  border-radius: 6px;
  backdrop-filter: blur(6px);
}

.authentication-role {
  color: #91caff;
  font-size: 12px;
}

.admin-link {
  color: #fff;
  text-decoration: none;
}
</style>
