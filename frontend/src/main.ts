import { createApp } from 'vue'
import App from './App.vue'
import { Button, ConfigProvider, Dropdown, Menu, Drawer, message, Tabs } from 'ant-design-vue';
import { store } from './store';
import router from './router'
import register from './components/st-global-register'
import i18next from "i18next"
import I18NextVue from "i18next-vue"
import { initializeAuthentication } from '@/auth/keycloak'
import { connectAlarmRealtime } from '@/realtime/socket'

// global css
import '@/style/common.scss'
import '@/style/reset.scss'

// cesium vue
import cesiumVue from '@/libs/cesium/cesium-vue'

async function bootstrap() {
  await initializeAuthentication()

  const app = createApp(App)

  app.use(store).use(router).use(I18NextVue, { i18next }).use(cesiumVue)

  // 引入antd的组件
  app.use(Button)
  app.use(ConfigProvider)
  app.use(Dropdown)
  app.use(Menu)
  app.use(Drawer)
  app.use(Tabs)

  app.config.globalProperties.$message = message;

  register(app)
  app.mount('#app')
  void connectAlarmRealtime().catch((error: unknown) => {
    console.error('实时告警连接初始化失败', error)
  })
}

void bootstrap().catch((error: unknown) => {
  console.error('Keycloak 初始化失败', error)
  const root = document.getElementById('app')
  if (root) {
    root.textContent = '登录服务暂时不可用，请确认 Keycloak 已启动后刷新页面。'
    root.classList.add('authentication-error')
  }
})
