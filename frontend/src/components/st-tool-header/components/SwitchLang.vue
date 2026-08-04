<template>
  <a-dropdown class="switch_lang" :trigger="['click']">
    <template #overlay>
      <a-menu @click="onSwitch">
        <a-menu-item
          v-for="key in langKeys"
          :key="key"
          :class="{ active_lang: currentLangKey === key }"
        >
          <GlobalOutlined />
          {{ $t(`lang.${key}`) }}
        </a-menu-item>
      </a-menu>
    </template>
    <a-button size="small">
      {{ $t('switchLang') }}
      <DownOutlined />
    </a-button>
  </a-dropdown>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { message } from 'ant-design-vue'
import { GlobalOutlined, DownOutlined } from '@ant-design/icons-vue'
import { useLangStore } from '@/store/modules/lang'
import { useTranslation } from 'i18next-vue'

const { t } = useTranslation()
const langStore = useLangStore()
const langKeys = ['zh', 'en'] as const
const currentLangKey = computed(() => langStore.getLang)

const onSwitch = async (info: { key: string | number }) => {
  await langStore.applyLang(String(info.key))
  message.success(t('switchLangSuccess'))
}
</script>

<style scoped>
.switch_lang {
  margin-left: 8px;
}
</style>
