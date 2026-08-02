<template>
  <a-dropdown :trigger="['click']">
    <template #overlay>
      <a-menu @click="switchTheme">
        <a-menu-item v-for="key in themeArray" :key="key" :class="[{'active_theme': currentThemeKey === key }]">
          <BgColorsOutlined />
          {{ key }}
        </a-menu-item>
      </a-menu>
    </template>
    <a-button>
      {{ $t('switchTheme') }}
      <DownOutlined />
    </a-button>
  </a-dropdown>
</template>
<script setup lang="ts">
import greenTheme from '@/theme/greenTheme.json'
import purpleTheme from '@/theme/purpleTheme.json'
import { reactive, ref } from 'vue'
import { BgColorsOutlined, DownOutlined } from '@ant-design/icons-vue';
import { useThemeStore } from '@/store/modules/theme'

const themeStore = useThemeStore();

const themeMap = reactive(new Map([
  ['greenTheme', greenTheme],
  ['purpleTheme', purpleTheme]
]))

// 浏览器中存储的主题key；如果没有默认选择一个主题key
const defaultTheme = themeStore.getTheme || 'greenTheme'
// 当前默认的主题key
const currentThemeKey = ref(defaultTheme)
// 当前默认的主题配置
const currentTheme = ref(themeMap.get(defaultTheme))
// 设置默认主题色
themeStore.setTheme(defaultTheme)


// 主题色集合
const themeArray = Array.from(themeMap.keys()); // 获取所有键

// 切换主题色
const switchTheme = (info: any) => {
  currentThemeKey.value = info.key
  currentTheme.value = themeMap.get(info.key)
  themeStore.setTheme(info.key)
  themeStore.setThemeValue(currentTheme.value)
}
</script>
<style lang="scss" scoped>
.switch_theme {

}
</style>
