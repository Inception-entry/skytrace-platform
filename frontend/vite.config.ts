import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import cesium from 'vite-plugin-cesium'
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    cesium(),
  ],
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: `@use "@/style/variables.scss" as variables;`
      }
    }
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 8888,
    host: true,
    open: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      '/socket.io': {
        target: 'ws://localhost:8082',
        ws: true,
        changeOrigin: true,
      },
    },
  }
})
