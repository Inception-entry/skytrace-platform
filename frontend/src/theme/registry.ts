import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context'
import { theme as antTheme } from 'ant-design-vue'

export type ThemeKey = 'classic' | 'aurora' | 'emerald' | 'violet'

export interface AppThemeDefinition {
  key: ThemeKey
  labelKey: string
  mode: 'dark' | 'light'
  ant: ThemeConfig
  cssVars: Record<string, string>
}

const classic: AppThemeDefinition = {
  key: 'classic',
  labelKey: 'theme.classic',
  mode: 'dark',
  ant: {
    algorithm: antTheme.darkAlgorithm,
    token: {
      colorPrimary: '#2f6fed',
      colorInfo: '#2f6fed',
      colorSuccess: '#22c55e',
      colorWarning: '#f59e0b',
      colorError: '#ef4444',
      colorBgBase: '#0b1220',
      colorTextBase: '#e8eef7',
      borderRadius: 8,
    },
  },
  cssVars: {
    '--st-color-primary': '#2f6fed',
    '--st-color-primary-soft': 'rgba(47, 111, 237, 0.28)',
    '--st-color-accent': '#7eb6ff',
    '--st-bg-page': '#07111f',
    '--st-bg-page-gradient': 'radial-gradient(ellipse 120% 80% at 20% 0%, #132#if 0%, #07111f 55%, #050a14 100%)',
    '--st-bg-panel': 'rgba(12, 22, 40, 0.86)',
    '--st-bg-panel-solid': '#0f1a2e',
    '--st-bg-elevated': 'rgba(255, 255, 255, 0.06)',
    '--st-border': 'rgba(255, 255, 255, 0.12)',
    '--st-border-strong': 'rgba(126, 182, 255, 0.35)',
    '--st-text': '#e8eef7',
    '--st-text-muted': '#9db0c7',
    '--st-text-inverse': '#0b1220',
    '--st-danger': '#ef4444',
    '--st-success': '#22c55e',
    '--st-toolbar-bg': 'rgba(0, 21, 41, 0.78)',
    '--st-drawer-bg': 'rgba(0, 21, 41, 0.92)',
    '--st-input-bg': 'rgba(255, 255, 255, 0.06)',
    '--st-shadow': '0 16px 40px rgba(0, 0, 0, 0.35)',
  },
}

const aurora: AppThemeDefinition = {
  key: 'aurora',
  labelKey: 'theme.aurora',
  mode: 'light',
  ant: {
    algorithm: antTheme.defaultAlgorithm,
    token: {
      colorPrimary: '#2563eb',
      colorInfo: '#2563eb',
      colorSuccess: '#16a34a',
      colorWarning: '#d97706',
      colorError: '#dc2626',
      colorBgBase: '#f5f7fb',
      colorTextBase: '#0f172a',
      borderRadius: 8,
    },
  },
  cssVars: {
    '--st-color-primary': '#2563eb',
    '--st-color-primary-soft': 'rgba(37, 99, 235, 0.14)',
    '--st-color-accent': '#3b82f6',
    '--st-bg-page': '#eef2f8',
    '--st-bg-page-gradient': 'linear-gradient(160deg, #eef2f8 0%, #e2e8f4 45%, #f8fafc 100%)',
    '--st-bg-panel': 'rgba(255, 255, 255, 0.92)',
    '--st-bg-panel-solid': '#ffffff',
    '--st-bg-elevated': '#f8fafc',
    '--st-border': '#dbe3f0',
    '--st-border-strong': 'rgba(37, 99, 235, 0.35)',
    '--st-text': '#0f172a',
    '--st-text-muted': '#64748b',
    '--st-text-inverse': '#ffffff',
    '--st-danger': '#dc2626',
    '--st-success': '#16a34a',
    '--st-toolbar-bg': 'rgba(255, 255, 255, 0.92)',
    '--st-drawer-bg': 'rgba(248, 250, 252, 0.98)',
    '--st-input-bg': '#ffffff',
    '--st-shadow': '0 12px 32px rgba(15, 23, 42, 0.08)',
  },
}

const emerald: AppThemeDefinition = {
  key: 'emerald',
  labelKey: 'theme.emerald',
  mode: 'dark',
  ant: {
    algorithm: antTheme.darkAlgorithm,
    token: {
      colorPrimary: '#00b96b',
      colorInfo: '#00b96b',
      colorSuccess: '#22c55e',
      colorWarning: '#f59e0b',
      colorError: '#ef4444',
      colorBgBase: '#071a14',
      colorTextBase: '#e8f7f0',
      borderRadius: 8,
    },
  },
  cssVars: {
    '--st-color-primary': '#00b96b',
    '--st-color-primary-soft': 'rgba(0, 185, 107, 0.24)',
    '--st-color-accent': '#5eead4',
    '--st-bg-page': '#061510',
    '--st-bg-page-gradient': 'radial-gradient(ellipse 120% 80% at 10% 0%, #0d2a1f 0%, #061510 55%, #040e0b 100%)',
    '--st-bg-panel': 'rgba(8, 28, 22, 0.9)',
    '--st-bg-panel-solid': '#0b1f18',
    '--st-bg-elevated': 'rgba(255, 255, 255, 0.05)',
    '--st-border': 'rgba(94, 234, 212, 0.16)',
    '--st-border-strong': 'rgba(0, 185, 107, 0.4)',
    '--st-text': '#e8f7f0',
    '--st-text-muted': '#8fb9a8',
    '--st-text-inverse': '#042f1a',
    '--st-danger': '#ef4444',
    '--st-success': '#22c55e',
    '--st-toolbar-bg': 'rgba(4, 32, 24, 0.88)',
    '--st-drawer-bg': 'rgba(4, 32, 24, 0.94)',
    '--st-input-bg': 'rgba(255, 255, 255, 0.05)',
    '--st-shadow': '0 16px 40px rgba(0, 0, 0, 0.35)',
  },
}

const violet: AppThemeDefinition = {
  key: 'violet',
  labelKey: 'theme.violet',
  mode: 'dark',
  ant: {
    algorithm: antTheme.darkAlgorithm,
    token: {
      colorPrimary: '#8b5cf6',
      colorInfo: '#8b5cf6',
      colorSuccess: '#22c55e',
      colorWarning: '#f59e0b',
      colorError: '#ef4444',
      colorBgBase: '#120b1f',
      colorTextBase: '#f3e8ff',
      borderRadius: 8,
    },
  },
  cssVars: {
    '--st-color-primary': '#8b5cf6',
    '--st-color-primary-soft': 'rgba(139, 92, 246, 0.28)',
    '--st-color-accent': '#c4b5fd',
    '--st-bg-page': '#0c0716',
    '--st-bg-page-gradient': 'radial-gradient(ellipse 120% 80% at 80% 0%, #24103f 0%, #0c0716 55%, #080410 100%)',
    '--st-bg-panel': 'rgba(22, 12, 40, 0.9)',
    '--st-bg-panel-solid': '#160c28',
    '--st-bg-elevated': 'rgba(255, 255, 255, 0.06)',
    '--st-border': 'rgba(196, 181, 253, 0.16)',
    '--st-border-strong': 'rgba(139, 92, 246, 0.4)',
    '--st-text': '#f3e8ff',
    '--st-text-muted': '#b6a4d8',
    '--st-text-inverse': '#2e1065',
    '--st-danger': '#ef4444',
    '--st-success': '#22c55e',
    '--st-toolbar-bg': 'rgba(24, 10, 48, 0.88)',
    '--st-drawer-bg': 'rgba(24, 10, 48, 0.94)',
    '--st-input-bg': 'rgba(255, 255, 255, 0.05)',
    '--st-shadow': '0 16px 40px rgba(0, 0, 0, 0.35)',
  },
}

// fix typo in classic gradient
classic.cssVars['--st-bg-page-gradient'] =
  'radial-gradient(ellipse 120% 80% at 20% 0%, #13233f 0%, #07111f 55%, #050a14 100%)'

export const themeRegistry: Record<ThemeKey, AppThemeDefinition> = {
  classic,
  aurora,
  emerald,
  violet,
}

export const themeKeys = Object.keys(themeRegistry) as ThemeKey[]

export function resolveThemeKey(raw: string | null | undefined): ThemeKey {
  if (raw === 'greenTheme' || raw === 'green') return 'emerald'
  if (raw === 'purpleTheme' || raw === 'purple') return 'violet'
  if (raw && raw in themeRegistry) return raw as ThemeKey
  return 'classic'
}

export function applyThemeToDocument(def: AppThemeDefinition) {
  const root = document.documentElement
  root.dataset.theme = def.key
  root.dataset.themeMode = def.mode
  Object.entries(def.cssVars).forEach(([key, value]) => {
    root.style.setProperty(key, value)
  })
}
