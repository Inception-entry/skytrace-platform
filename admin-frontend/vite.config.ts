import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/admin-api': {
        target: 'http://localhost:3100',
        changeOrigin: true,
      },
    },
  },
})
