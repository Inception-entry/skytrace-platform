<template>
  <label :class="['handle', open ? 'drop' : '']" @click="showDrawer">
    <MenuFoldOutlined v-show="open" :style="{ fontSize: '20px' }" />
    <MenuUnfoldOutlined v-show="!open" :style="{ fontSize: '20px' }" />
  </label>
  <a-drawer
    :width="300"
    :title="$t('nav.brand')"
    root-class-name="menu-aside"
    :content-wrapper-style="contentWrapperStyle"
    :force-render="true"
    :placement="placement"
    :open="open"
    :mask="false"
    :closable="false"
    :z-index="1000"
  >
    <nav class="aside-nav">
      <RouterLink
        v-for="item in visibleItems"
        :key="item.to"
        :to="item.to"
        class="aside-link"
        @click="open = false"
      >
        <span class="aside-label">{{ $t(item.labelKey) }}</span>
        <span class="aside-desc">{{ $t(item.descKey) }}</span>
      </RouterLink>
    </nav>
  </a-drawer>
</template>

<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { useLayoutStore } from '@/store/modules/layout'
import type { DrawerProps } from 'ant-design-vue'
import { MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons-vue'
import { authenticationState } from '@/auth/keycloak'

defineOptions({ name: 'st-menu-aside' })

const layoutStore = useLayoutStore()
const currentLayoutKey = computed(() => layoutStore.getLayout)
const headerStatus = computed(() => layoutStore.getHeaderStatus)

const placement = ref<DrawerProps['placement']>('left')
const open = ref(false)

watch(
  () => layoutStore.getAsideStatus,
  (status) => {
    open.value = status
  },
)

const navItems = [
  { to: '/drone', labelKey: 'nav.tasks', descKey: 'nav.tasksDesc', roles: [] as string[] },
  { to: '/devices', labelKey: 'nav.devices', descKey: 'nav.devicesDesc', roles: [] as string[] },
  { to: '/routes', labelKey: 'nav.routes', descKey: 'nav.routesDesc', roles: [] as string[] },
  { to: '/knowledge', labelKey: 'nav.knowledge', descKey: 'nav.knowledgeDesc', roles: [] as string[] },
  { to: '/chat', labelKey: 'nav.chat', descKey: 'nav.chatDesc', roles: ['ADMIN', 'OPERATOR'] },
  { to: '/audit', labelKey: 'nav.audit', descKey: 'nav.auditDesc', roles: ['ADMIN'] },
  { to: '/map', labelKey: 'nav.map', descKey: 'nav.map', roles: [] as string[] },
]

const visibleItems = computed(() =>
  navItems.filter((item) => {
    if (!item.roles.length) return true
    return item.roles.some((role) => authenticationState.roles.includes(role))
  }),
)

const contentWrapperStyle = computed(() => {
  if (currentLayoutKey.value === 'topLeft') {
    return { top: headerStatus.value ? '88px' : '0' }
  }
  return { top: '0' }
})

const showDrawer = () => {
  open.value = !open.value
  layoutStore.setAsideStatus(open.value)
}
</script>

<style lang="scss" scoped>
.handle {
  position: fixed;
  top: 108px;
  left: 0;
  z-index: 1100;
  display: inline-block;
  width: 40px;
  height: 40px;
  padding: 10px;
  color: var(--st-text, #e8eef7);
  background-color: var(--st-bg-elevated, rgba(255, 255, 255, 0.4));
  border-top-right-radius: 4px;
  border-bottom-right-radius: 4px;
  cursor: pointer;
  box-sizing: border-box;
  transition: all 0.3s;

  &.drop {
    left: 300px;
  }
}

.aside-nav {
  display: grid;
  gap: 10px;
  padding: 8px 4px;
}

.aside-link {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  border-radius: 10px;
  color: var(--st-text, #e8eef7);
  text-decoration: none;
  background: var(--st-bg-elevated, rgb(255 255 255 / 6%));
  border: 1px solid var(--st-border, rgb(255 255 255 / 10%));
  transition: background 0.2s ease;

  &:hover,
  &.router-link-active {
    background: var(--st-color-primary-soft, rgb(47 111 237 / 28%));
    border-color: var(--st-border-strong, rgb(126 182 255 / 40%));
  }
}

.aside-label {
  font-size: 15px;
  font-weight: 600;
}

.aside-desc {
  font-size: 12px;
  color: var(--st-text-muted, #9db0c7);
}

:global(.menu-aside) {
  outline: none;
}

:global(.menu-aside .ant-drawer-content) {
  background-color: var(--st-drawer-bg, rgba(0, 21, 41, 0.92)) !important;
  border-inline-end: 1px solid var(--st-border, rgba(255, 255, 255, 0.12));
}

:global(.menu-aside .ant-drawer-header) {
  background: transparent;
  border-bottom: 1px solid var(--st-border, rgba(255, 255, 255, 0.12));
}

:global(.menu-aside .ant-drawer-title) {
  color: var(--st-text, #e8eef7);
}
</style>
