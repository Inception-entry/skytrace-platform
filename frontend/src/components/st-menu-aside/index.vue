<template>
  <label :class="['handle', open ? 'drop' : '']" @click="showDrawer">
    <MenuFoldOutlined v-show="open" :style="{fontSize: '20px'}"/>
    <MenuUnfoldOutlined v-show="!open" :style="{fontSize: '20px'}"/>
  </label>
  <a-drawer
    :width="300"
    title=""
    root-class-name="menu-aside"
    :content-wrapper-style="contentWrapperStyle"
    :force-render="true"
    :placement="placement"
    :open="open"
    :mask="false"
    :closable="false"
    :z-index="1000">
    
  </a-drawer>
</template>
<script lang="ts" setup>
import { computed, ref } from 'vue';
import { useLayoutStore } from '@/store/modules/layout'
import type { DrawerProps } from 'ant-design-vue';
import { MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons-vue';

defineOptions({ name: 'st-menu-aside' })

const layoutStore = useLayoutStore();
const currentLayoutKey = computed(() => layoutStore.getLayout)
const headerStatus = computed(() => layoutStore.getHeaderStatus)

const placement = ref<DrawerProps['placement']>('left');
const open = ref<boolean>(false);

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

:global(.menu-aside) {
  outline: none;
}

// 覆盖 drawer 的样式
:global(.menu-aside .ant-drawer-content) {
  background-color: rgba(0, 21, 41, 0.4) !important;
}

</style>
