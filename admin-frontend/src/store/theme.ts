import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type AdminThemeMode = 'light' | 'dark'

interface ThemeState {
  mode: AdminThemeMode
  // eslint-disable-next-line no-unused-vars
  setMode: (mode: AdminThemeMode) => void
  toggleMode: () => void
}

export const useThemeStore = create<ThemeState>()(
  persist(
    set => ({
      mode: 'light',
      setMode: mode => set({ mode }),
      toggleMode: () =>
        set(state => ({
          mode: state.mode === 'light' ? 'dark' : 'light',
        })),
    }),
    {
      name: 'skytrace-admin-theme',
    },
  ),
)
