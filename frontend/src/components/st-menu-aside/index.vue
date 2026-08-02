<template>
  <label :class="['handle', open ? 'drop' : '']" @click="showDrawer">
    <MenuFoldOutlined v-show="open" :style="{fontSize: '20px'}"/>
    <MenuUnfoldOutlined v-show="!open" :style="{fontSize: '20px'}"/>
  </label>
  <a-drawer
    :width="300"
    title="SkyTrace"
    root-class-name="menu-aside"
    :content-wrapper-style="contentWrapperStyle"
    :force-render="true"
    :placement="placement"
    :open="open"
    :mask="false"
    :closable="false"
    :z-index="1000">
    <nav class="aside-nav">
      <RouterLink
        v-for="item in visibleItems"
        :key="item.to"
        :to="item.to"
        class="aside-link"
        @click="open = false"
      >
        <span class="aside-label">{{ item.label }}</span>
        <span class="aside-desc">{{ item.desc }}</span>
      </RouterLink>
    </nav>
  </a-drawer>
</template>
<script lang="ts" setup>
import { computed, ref } from 'vue';
import { useLayoutStore } from '@/store/modules/layout'
import type { DrawerProps } from 'ant-design-vue';
import { MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons-vue';
import { authenticationState } from '@/auth/keycloak'

defineOptions({ name: 'st-menu-aside' })

const layoutStore = useLayoutStore();
const currentLayoutKey = computed(() => layoutStore.getLayout)
const headerStatus = computed(() => layoutStore.getHeaderStatus)

const placement = ref<DrawerProps['placement']>('left');
const open = ref<boolean>(false);

const navItems = [
  { to: '/drone', label: '巡检任务', desc: '任务列表与工作流', roles: [] as string[] },
  { to: '/devices', label: '设备管理', desc: '设备主数据与在线状态', roles: [] },
  { to: '/knowledge', label: '知识库', desc: '文档入库与检索', roles: [] },
  { to: '/chat', label: 'AI 分析', desc: '流式问答与巡检分析', roles: ['ADMIN', 'OPERATOR'] },
  { to: '/audit', label: '审计中心', desc: '运行概况与操作审计', roles: ['ADMIN'] },
]

const visibleItems = computed(() =>
  navItems.filter((item) => {
    if (!item.roles.length) return true
    return item.roles.some((role) =>
      authenticationState.roles.includes(role),
    )
  }),
)

const contentWrapperStyle = computed(() => {
  if (currentLayoutKey.value ==='classic') {
    if (headerStatus.value && open.value) {
      return {top: '0'}
    } else if (!headerStatus.value && open.value) {
      return {top: '0'}
    } else if (headerStatus.value && !open.value) {
      return {top: '0'}
    } else {
      return {top: '0'}
    }
  } else if (currentLayoutKey.value === 'topLeft') {
    if (headerStatus.value && open.value) {
      return {top: '130px'}
    } else if (!headerStatus.value && open.value) {
      return {top: '0'}
    } else if (headerStatus.value && !open.value) {
      return {top: '130px'}
    } else {
      return {top: '0'}
    } 
  }
  return {top: '0'}
})

const showDrawer = () => {
  open.value = !open.value;
  layoutStore.setAsideStatus(open.value)
};

</script>
<style lang="scss" scoped>
.handle {
  position: absolute;
  top: 150px;
  left: 0;
  display: inline-block;
  width: 40px;
  height: 40px;
  padding: 10px;
  background-color: rgba(255, 255, 255, 0.4);
  border-top-right-radius: 4px;
  border-bottom-right-radius: 4px;
  margin: auto;
  cursor: pointer;
  box-sizing: border-box;
  transition: all 0.3s;
  i.arrow {
    position: absolute;
    top: 50%;
    inset-inline-end: 16px;
    width: 10px;
    color: currentcolor;
    transform: translateY(-50%) translateX(3px);
    transition: transform 0.3s cubic-bezier(0.645, 0.045, 0.355, 1), opacity 0.3s;
    &::before, &::after {
      position: absolute;
      width: 6px;
      height: 1.5px;
      background-color: currentcolor;
      border-radius: 6px;
      transition: 
        background 0.3s cubic-bezier(0.645, 0.045, 0.355, 1), 
        transform 0.3s cubic-bezier(0.645, 0.045, 0.355, 1), 
        top 0.3s cubic-bezier(0.645, 0.045, 0.355, 1), 
        color 0.3s cubic-bezier(0.645, 0.045, 0.355, 1);
      content: "";
    }
    &::before {
      transform: rotate(-45deg) translateX(2.5px);
    }
    &::after {
      transform: rotate(45deg) translateX(-2.5px);
    }
  }
  &.drop {
    left: 300px;
    i.arrow {
      transform: rotate(180deg) translateX(1px);
      transition: transform 0.3s cubic-bezier(0.645, 0.045, 0.355, 1), opacity 0.3s;
    }
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
  color: #e8eef7;
  text-decoration: none;
  background: rgb(255 255 255 / 6%);
  border: 1px solid rgb(255 255 255 / 10%);
  transition: background 0.2s ease;

  &:hover,
  &.router-link-active {
    background: rgb(47 111 237 / 28%);
    border-color: rgb(126 182 255 / 40%);
  }
}

.aside-label {
  font-size: 15px;
  font-weight: 600;
}

.aside-desc {
  font-size: 12px;
  color: #9db0c7;
}

:global(.menu-aside) {
  outline: none;
}

:global(.menu-aside .ant-drawer-content) {
  background-color: rgba(0, 21, 41, 0.4) !important;
}

:global(.menu-aside .ant-drawer-title) {
  color: #e8eef7;
}

</style>
