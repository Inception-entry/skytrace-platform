<template>
  <div
    v-if="authenticationState.authenticated"
    :class="['authentication-toolbar', `authentication-toolbar--${variant}`]"
  >
    <div class="toolbar-controls">
      <SwitchTheme />
      <SwitchLang />
    </div>
    <span class="toolbar-user">{{ authenticationState.username }}</span>
    <span class="authentication-role">{{ displayedRoles }}</span>
    <RouterLink
      v-if="isAdministrator"
      class="admin-link"
      to="/audit"
    >
      {{ $t('nav.audit') }}
    </RouterLink>
    <a-button size="small" @click="handleLogout">{{ $t('nav.logout') }}</a-button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { authenticationState, logout } from '@/auth/keycloak'
import SwitchTheme from '@/components/st-tool-header/components/SwitchTheme.vue'
import SwitchLang from '@/components/st-tool-header/components/SwitchLang.vue'

withDefaults(defineProps<{
  variant?: 'floating' | 'embedded'
}>(), {
  variant: 'floating',
})

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
.authentication-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  min-height: 38px;
  padding: 8px 10px;
  color: var(--st-text, #e8eef7);
  background: var(--st-toolbar-bg, rgb(0 21 41 / 78%));
  border: 1px solid var(--st-border, rgb(255 255 255 / 20%));
  border-radius: 6px;
  box-sizing: border-box;
  backdrop-filter: blur(6px);
  white-space: nowrap;
}

.toolbar-controls {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-right: 4px;
}

.toolbar-user {
  color: var(--st-text, #e8eef7);
}

.authentication-toolbar--floating {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 2000;
}

.authentication-toolbar--embedded {
  justify-self: end;
  max-width: 100%;
}

.authentication-role {
  color: var(--st-color-accent, #91caff);
  font-size: 12px;
}

.admin-link {
  color: var(--st-color-accent, #7eb6ff);
  text-decoration: none;
}
</style>
