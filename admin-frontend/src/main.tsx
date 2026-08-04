import { StrictMode, useEffect } from 'react'
import { createRoot } from 'react-dom/client'
import { ConfigProvider, App as AntdApp } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import App from './App'
import { useThemeStore } from './store/theme'
import { getAdminTheme } from './theme/tokens'
import './styles/global.css'

function RootApp() {
  const mode = useThemeStore(state => state.mode)

  useEffect(() => {
    document.documentElement.dataset.theme = mode
    document.body.dataset.theme = mode
  }, [mode])

  return (
    <ConfigProvider locale={zhCN} theme={getAdminTheme(mode)}>
      <AntdApp>
        <App />
      </AntdApp>
    </ConfigProvider>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <RootApp />
  </StrictMode>,
)
