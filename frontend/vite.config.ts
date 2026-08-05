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
        // 턴 추가(/api/attempts/{id}/turns)와 제출(/submit)이 AI 호출 때문에
        // 수 분까지 걸릴 수 있어 타임아웃을 넉넉히 잡는다.
        proxyTimeout: 10 * 60 * 1000,
        timeout: 10 * 60 * 1000,
      },
      // 릴레이 게임의 WebSocket(/ws/relay/{roomId}). ws: true가 없으면 업그레이드
      // 요청이 프록시를 통과하지 못한다. 게임 한 판이 수십 분까지 이어질 수 있어
      // 타임아웃을 /api와 같게 잡는다.
      '/ws': {
        target: 'http://localhost:9090',
        changeOrigin: true,
        ws: true,
        proxyTimeout: 10 * 60 * 1000,
        timeout: 10 * 60 * 1000,
      },
    },
  },
})