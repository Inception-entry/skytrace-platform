import { createApp } from 'vue'
import App from './App.vue'
import { Button, ConfigProvider, Dropdown, Menu, Drawer, message, Tabs } from 'ant-design-vue'
import { store } from './store'
import router from './router'
import register from './components/st-global-register'
import i18next from 'i18next'
import I18NextVue from 'i18next-vue'
import { initializeAuthentication } from '@/auth/keycloak'
import { connectAlarmRealtime } from '@/realtime/socket'
import { useThemeStoreWithOut } from '@/store/modules/theme'
import { useLangStoreWithOut } from '@/store/modules/lang'

import '@/style/common.scss'
import '@/style/reset.scss'
import '@/style/theme-tokens.css'

import cesiumVue from '@/libs/cesium/cesium-vue'

async function bootstrap() {
  await initializeAuthentication()

  const themeStore = useThemeStoreWithOut()
  themeStore.hydrate()
  const langStore = useLangStoreWithOut()
  await langStore.hydrate()

  const app = createApp(App)

  app.use(store).use(router).use(I18NextVue, { i18next }).use(cesiumVue)

  app.use(Button)
  app.use(ConfigProvider)
  app.use(Dropdown)
  app.use(Menu)
  app.use(Drawer)
  app.use(Tabs)

  app.config.globalProperties.$message = message

  register(app)
  app.mount('#app')
  void connectAlarmRealtime().catch((error: unknown) => {
    console.error(i18next.t('auth.realtimeFailed'), error)
  })
}

void bootstrap().catch((error: unknown) => {
  console.error(error)
  const root = document.getElementById('app')
  if (root) {
    root.textContent = i18next.t('auth.bootFailed')
    root.classList.add('authentication-error')
  }
})
