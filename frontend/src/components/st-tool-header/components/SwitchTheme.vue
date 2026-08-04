<template>
  <a-dropdown :trigger="['click']">
    <template #overlay>
      <a-menu @click="onSwitch">
        <a-menu-item
          v-for="key in themeKeys"
          :key="key"
          :class="{ active_theme: currentThemeKey === key }"
        >
          <BgColorsOutlined />
          {{ $t(`theme.${key}`) }}
        </a-menu-item>
      </a-menu>
    </template>
    <a-button size="small">
      {{ $t('switchTheme') }}
      <DownOutlined />
    </a-button>
  </a-dropdown>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { BgColorsOutlined, DownOutlined } from '@ant-design/icons-vue'
import { useThemeStore } from '@/store/modules/theme'
import { themeKeys } from '@/theme/registry'

const themeStore = useThemeStore()
const currentThemeKey = computed(() => themeStore.getTheme)

const onSwitch = (info: { key: string | number }) => {
  themeStore.applyTheme(String(info.key))
}
</script>
