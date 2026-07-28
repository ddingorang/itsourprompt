import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
  ],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:9090',
        changeOrigin: true,
        // /run 이 최대 5분까지 걸릴 수 있어 타임아웃 넉넉히
        proxyTimeout: 10 * 60 * 1000,
        timeout: 10 * 60 * 1000,
      },
    },
  },
})