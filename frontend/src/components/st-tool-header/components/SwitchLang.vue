<template>
  <a-dropdown class="switch_lang" :trigger="['click']">
    <template #overlay>
      <a-menu @click="switchLang">
        <a-menu-item v-for="key in langArray" :key="key" :class="[{'active_lang': currentLangKey === key }]">
          <BgColorsOutlined />
          {{ key }}
        </a-menu-item>
      </a-menu>
    </template>
    <a-button>
      {{ $t('switchLang') }}
      <DownOutlined />
    </a-button>
  </a-dropdown>
</template>
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { BgColorsOutlined, DownOutlined } from '@ant-design/icons-vue';
import { useLangStore } from '@/store/modules/lang'
import i18n from '@/i18n'
 
const langStore = useLangStore();

const langMap = reactive(new Map([
  ['zh', '中文'],
  ['en', '英文']
]))

// 浏览器中存储的语言；如果没有默认选择中文
const defaultLang = langStore.getLang || 'zh'
// 当前默认的语言
const currentLangKey = ref(defaultLang)

// 语言列表集合
const langArray = Array.from(langMap.keys());

// 切换语言
const switchLang = (info: any) => {
  currentLangKey.value = info.key
  langStore.setLang(info.key)
  i18n.changeLanguage(currentLangKey.value, (err, t) => {
    if (err) return console.log('something went wrong loading', err)
    console.log(t('switchLangSuccess'))
  })
}

</script>
<style lang="scss" scoped>
.switch_lang {
  margin-left: 20px;
}
</style>
