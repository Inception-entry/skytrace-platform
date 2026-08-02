<template>
  <div class="layout">
    <div
      :class="[
        'layout__classic',
        'relative w-56px h-48px cursor-pointer bg-gray-300',
        {
          'is-acitve': currentLayoutKey === 'classic'
        }
      ]"
      @click="switchLayout('classic')"
    ></div>
    <div
      :class="[
        'layout__top-left',
        'relative w-56px h-48px cursor-pointer bg-gray-300',
        {
          'is-acitve': currentLayoutKey === 'topLeft'
        }
      ]"
      @click="switchLayout('topLeft')"
    ></div>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { useLayoutStore } from '@/store/modules/layout'
defineOptions({ name: 'SwitchLayout' })
const layoutStore = useLayoutStore();
// 当前默认的布局
const currentLayoutKey = computed(() => layoutStore.getLayout)
// 切换布局
const switchLayout = (info: any) => {
  layoutStore.setLayout(info)
}
</script>
<style lang="scss" scoped>
.layout {
  display: flex;
  flex-wrap: wrap;
  .layout__classic,
  .layout__top-left {
    position: relative;
    border: 2px solid #e5e7eb;
    border-radius: 4px;
    background-color: rgb(209 213 219 / 1);
    cursor: pointer;
    width: 56px;
    height: 48px;
    margin-left: 14px;
    &.is-acitve {
      border-color: #409eff;
    }
  }
  .layout__classic {
    margin-left: 0;
    &::before {
      position: absolute;
      top: 0;
      left: 0;
      z-index: 1;
      width: 33%;
      height: 100%;
      background-color: #273352;
      border-radius: 4px 0 0 4px;
      content: "";
    }
    &::after {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 25%;
      background-color: #fff;
      border-radius: 4px 4px 0;
      content: "";
    }
  }
  .layout__top-left {
    &::before {
      position: absolute;
      top: 0;
      left: 0;
      z-index: 1;
      width: 100%;
      height: 33%;
      background-color: #273352;
      border-radius: 4px 4px 0 0;
      content: "";
    }
    &::after {
      position: absolute;
      top: 0;
      left: 0;
      width: 33%;
      height: 100%;
      background-color: #fff;
      border-radius: 4px 0 0 4px;
      content: "";
    }
  }
}
</style>