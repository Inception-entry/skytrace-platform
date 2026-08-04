import { defineStore } from 'pinia'
import { store } from '@/store'
import { getTheme, setTheme, removeTheme } from '@/utils/theme'
import {
  applyThemeToDocument,
  resolveThemeKey,
  themeRegistry,
  type ThemeKey,
} from '@/theme/registry'
import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context'

interface ThemeState {
  themeKey: ThemeKey
  antTheme: ThemeConfig
}

function buildState(key: ThemeKey): ThemeState {
  const def = themeRegistry[key]
  return {
    themeKey: key,
    antTheme: def.ant,
  }
}

export const useThemeStore = defineStore('theme', {
  state: (): ThemeState => {
    const key = resolveThemeKey(getTheme())
    return buildState(key)
  },
  getters: {
    getTheme(): ThemeKey {
      return this.themeKey
    },
    getThemeValue(): ThemeConfig {
      return this.antTheme
    },
  },
  actions: {
    hydrate() {
      this.applyTheme(resolveThemeKey(getTheme()))
    },
    applyTheme(rawKey: string) {
      const key = resolveThemeKey(rawKey)
      const def = themeRegistry[key]
      this.themeKey = key
      this.antTheme = def.ant
      setTheme(key)
      applyThemeToDocument(def)
    },
    setTheme(info: string) {
      this.applyTheme(info)
    },
    setThemeValue(_config: unknown) {
      // kept for backward compatibility with old SwitchTheme callers
      this.applyTheme(this.themeKey)
    },
    removeTheme() {
      removeTheme()
      this.applyTheme('classic')
    },
  },
})

export const useThemeStoreWithOut = () => useThemeStore(store)
