import { theme as antdTheme } from 'antd'
import type { ThemeConfig } from 'antd'
import type { AdminThemeMode } from '../store/theme'

const sharedToken = {
  colorPrimary: '#1677ff',
  colorInfo: '#1677ff',
  colorSuccess: '#16a34a',
  colorWarning: '#f59e0b',
  colorError: '#ef4444',
  borderRadius: 8,
  fontFamily:
    '"Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif',
  controlHeight: 36,
}

const lightTheme: ThemeConfig = {
  token: {
    ...sharedToken,
    colorTextBase: '#1f2937',
    colorBgBase: '#f4f7fb',
    colorBorder: '#e5e7eb',
  },
  components: {
    Layout: {
      bodyBg: '#f4f7fb',
      headerBg: '#ffffff',
      siderBg: '#ffffff',
    },
    Menu: {
      itemSelectedBg: '#e8f1ff',
      itemSelectedColor: '#1677ff',
      itemHoverBg: '#f3f7ff',
      itemHoverColor: '#1677ff',
      itemBorderRadius: 8,
    },
    Card: {
      borderRadiusLG: 8,
      boxShadowTertiary: '0 8px 24px rgba(31, 41, 55, 0.05)',
    },
    Button: {
      primaryShadow: '0 6px 16px rgba(22, 119, 255, 0.24)',
    },
    Table: {
      headerBg: '#f8fafc',
      headerColor: '#475569',
      rowHoverBg: '#f5f9ff',
    },
  },
}

const darkTheme: ThemeConfig = {
  algorithm: antdTheme.darkAlgorithm,
  token: {
    ...sharedToken,
    colorTextBase: '#e5edf8',
    colorBgBase: '#0b1220',
    colorBorder: '#1e293b',
  },
  components: {
    Layout: {
      bodyBg: '#0b1220',
      headerBg: '#111827',
      siderBg: '#0f172a',
    },
    Menu: {
      darkItemBg: '#0f172a',
      darkSubMenuItemBg: '#111827',
      darkItemSelectedBg: 'rgba(22, 119, 255, 0.22)',
      darkItemSelectedColor: '#f8fbff',
      darkItemHoverBg: 'rgba(59, 130, 246, 0.16)',
      darkItemColor: 'rgba(226, 232, 240, 0.76)',
      itemBorderRadius: 8,
    },
    Card: {
      borderRadiusLG: 8,
      boxShadowTertiary: '0 12px 30px rgba(2, 6, 23, 0.35)',
    },
    Button: {
      primaryShadow: '0 8px 18px rgba(22, 119, 255, 0.3)',
    },
    Table: {
      headerBg: '#111827',
      headerColor: '#cbd5e1',
      rowHoverBg: '#111c30',
    },
  },
}

export function getAdminTheme(mode: AdminThemeMode): ThemeConfig {
  return mode === 'dark' ? darkTheme : lightTheme
}
