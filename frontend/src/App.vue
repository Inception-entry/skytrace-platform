<template>
  <a-config-provider :theme="currentTheme" :locale="antdLocale">
    <st-menu-aside v-if="showChrome" />
    <router-view />
    <st-auth-toolbar v-if="showChrome && route.path !== '/map'" />
  </a-config-provider>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import enUS from 'ant-design-vue/es/locale/en_US'
import { useThemeStore } from '@/store/modules/theme'
import { useLangStore } from '@/store/modules/lang'
import StAuthToolbar from '@/components/st-auth-toolbar/index.vue'
import StMenuAside from '@/components/st-menu-aside/index.vue'

const themeStore = useThemeStore()
const langStore = useLangStore()
const route = useRoute()

const currentTheme = computed(() => themeStore.getThemeValue)
const antdLocale = computed(() => (langStore.getLang === 'en' ? enUS : zhCN))
const showChrome = computed(() => route.path !== '/401' && route.path !== '/403')
</script>
