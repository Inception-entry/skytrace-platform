<template>
  <label :class="['handle', open ? 'drop' : '']" @click="showDrawer"><i class="arrow" /></label>
  <a-drawer
    :height="130"
    title=""
    root-class-name="tool-header"
    :content-wrapper-style="contentWrapperStyle"
    :force-render="true"
    :placement="placement"
    :open="open"
    :mask="false"
    :closable="false"
    :z-index="1001">
    <div class="left-control">
      {{ currentLayoutKey  }}
    </div>
    <div class="center-control">
      <a-tabs v-model:activeKey="activeKey" type="card">
        <a-tab-pane key="1" tab="Tab 1">
          <switch-layout />
        </a-tab-pane>
        <a-tab-pane key="2" tab="Tab 2" force-render>Content of Tab Pane 2</a-tab-pane>
        <a-tab-pane key="3" tab="Tab 3">Content of Tab Pane 3</a-tab-pane>
      </a-tabs>
    </div>
    <div class="right-control">
      <switch-theme />
      <switch-lang />
    </div>
  </a-drawer>
</template>
<script lang="ts" setup>
import { computed, ref } from 'vue'
import { useLayoutStore } from '@/store/modules/layout'

import type { DrawerProps } from 'ant-design-vue';
import SwitchLayout from '@/components/st-tool-header/components/SwitchLayout.vue'
import SwitchTheme from '@/components/st-tool-header/components/SwitchTheme.vue'
import SwitchLang from '@/components/st-tool-header/components/SwitchLang.vue'

defineOptions({ name: 'st-tool-header' })

const layoutStore = useLayoutStore();
const currentLayoutKey = computed(() => layoutStore.getLayout)
const asideStatus = computed(() => layoutStore.getAsideStatus)


const placement = ref<DrawerProps['placement']>('top');
const open = ref<boolean>(false);

const contentWrapperStyle = computed(() => {
  if (currentLayoutKey.value === 'classic') {
    if (open.value && asideStatus.value) {
      return {left: '300px'}
    } else if (open.value && !asideStatus.value) {
      return {left: '0'}
    } else if (!open.value && asideStatus.value) {
      return {left: '300px'}
    } else {
      return {left: '0'}
    }
  } else if (currentLayoutKey.value === 'topLeft') {
    if (open.value && asideStatus.value) {
      return {left: '0'}
    } else if (open.value && !asideStatus.value) {
      return {left: '0'}
    } else if (!open.value && asideStatus.value) {
      return {left: '0'}
    } else {
      return {left: '0'}
    } 
  }
  return {left: '0'}
})

const showDrawer = () => {
  open.value = !open.value;
  layoutStore.setHeaderStatus(open.value)
};

const activeKey = ref('1');

</script>
<style lang="scss" scoped>
.handle {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  display: inline-block;
  width: 40px;
  height: 20px;
  background-color: rgba(255, 255, 255, 0.4);
  border-bottom-left-radius: 4px;
  border-bottom-right-radius: 4px;
  margin: auto;
  cursor: pointer;
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
    top: 130px;
    i.arrow {
      transform: rotate(180deg) translateX(1px);
      transition: transform 0.3s cubic-bezier(0.645, 0.045, 0.355, 1), opacity 0.3s;
    }
  }
}

.left-control,
.center-control,
.right-contrl {
  color: #fff;
  font-size: 20px;
  flex: 0 1 auto;
}

.center-control {
  background-color: #e5e7eb;
  .ant-tabs {
    color: #fff;
    :deep(.ant-tabs-tab) {
      border-radius: 0;
      border: none;
      background-color: rgb(107 114 128 / 1);
      &.ant-tabs-tab-active {
        border: none;
      }
    }
  }
}

// 覆盖 drawer 的样式
:global(.tool-header .ant-drawer-content) {
  background-color: rgba(0, 21, 41, 0.4);
}
:global(.tool-header .ant-drawer-body) {
  display: flex;
  justify-content: space-between;
  padding: 0;
}
</style>
