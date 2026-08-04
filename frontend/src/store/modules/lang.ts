import { defineStore } from 'pinia'
import { store } from '@/store'
import { getLang, setLang, removeLang, type LangKey } from '@/utils/lang'
import i18n from '@/i18n'

interface LangState {
  langKey: LangKey
}

export const useLangStore = defineStore('lang', {
  state: (): LangState => ({
    langKey: getLang(),
  }),
  getters: {
    getLang(): LangKey {
      return this.langKey
    },
  },
  actions: {
    async hydrate() {
      await this.applyLang(getLang())
    },
    async applyLang(raw: string) {
      const lang = (raw === 'en' ? 'en' : 'zh') as LangKey
      this.langKey = lang
      setLang(lang)
      document.documentElement.lang = lang === 'en' ? 'en' : 'zh-CN'
      await i18n.changeLanguage(lang)
    },
    async setLang(info: string) {
      await this.applyLang(info)
    },
    removeLang() {
      removeLang()
      void this.applyLang('zh')
    },
  },
})

export const useLangStoreWithOut = () => useLangStore(store)
