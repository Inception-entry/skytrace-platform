<template>
  <main class="authorization-page st-page">
    <section class="authorization-card st-panel">
      <div class="status-code">{{ status }}</div>
      <p class="eyebrow">{{ $t('auth.accessControl') }}</p>
      <h1>{{ title }}</h1>
      <p class="description">{{ description }}</p>

      <div class="identity">
        <span>{{ $t('auth.currentUser') }}</span>
        <strong>{{ authenticationState.username || $t('common.unknownUser') }}</strong>
        <span>{{ $t('auth.currentRoles') }}</span>
        <strong>{{ displayedRoles || $t('common.noRoles') }}</strong>
      </div>

      <div class="actions">
        <a-button type="primary" @click="handlePrimaryAction">
          {{ status === 401 ? $t('auth.reLogin') : $t('auth.backToTasks') }}
        </a-button>
        <a-button @click="goHome">{{ $t('common.backHome') }}</a-button>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useTranslation } from 'i18next-vue'
import { useRoute, useRouter } from 'vue-router'
import {
  authenticationState,
  reauthenticate,
} from '@/auth/keycloak'
import { resolveSafeRedirect } from '@/auth/authorization-navigation'

const props = defineProps<{
  status: 401 | 403
}>()
const { t } = useTranslation()
const route = useRoute()
const router = useRouter()

const title = computed(() => props.status === 401
  ? t('auth.expiredTitle')
  : t('auth.forbiddenTitle'))
const description = computed(() => props.status === 401
  ? t('auth.expiredBody')
  : t('auth.forbiddenBody'))
const displayedRoles = computed(() => authenticationState.roles
  .filter((role) => ['ADMIN', 'OPERATOR', 'VIEWER'].includes(role))
  .join(', '))
const redirectPath = computed(() => resolveSafeRedirect(
  route.query.redirect,
  '/drone',
))

const handlePrimaryAction = () => {
  if (props.status === 401) {
    void reauthenticate(redirectPath.value)
    return
  }
  void router.push(redirectPath.value)
}

const goHome = () => {
  void router.push('/')
}
</script>

<style scoped>
.authorization-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 24px;
}

.authorization-card {
  width: min(560px, 100%);
  padding: 44px;
}

.status-code {
  color: var(--st-color-primary);
  font-size: clamp(72px, 18vw, 132px);
  font-weight: 800;
  line-height: 0.85;
  letter-spacing: -8px;
}

.eyebrow {
  margin: 30px 0 8px;
}

h1 {
  margin: 0;
  font-size: 30px;
}

.description {
  margin: 14px 0 24px;
  color: var(--st-text-muted);
  line-height: 1.7;
}

.identity {
  display: grid;
  grid-template-columns: 90px 1fr;
  gap: 10px 16px;
  padding: 18px;
  background: var(--st-bg-elevated);
  border-radius: 10px;
}

.identity span {
  color: var(--st-text-muted);
}

.actions {
  display: flex;
  gap: 12px;
  margin-top: 28px;
}

@media (max-width: 560px) {
  .authorization-card {
    padding: 30px 24px;
  }

  .identity {
    grid-template-columns: 1fr;
  }
}
</style>
