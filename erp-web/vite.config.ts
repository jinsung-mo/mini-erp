import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      // 브라우저는 같은 출처(5173)의 /api로 요청하고, Vite 개발 서버가 API 서버로 넘겨준다.
      // 브라우저 입장에서는 출처가 하나라서 CORS 설정이 필요 없다.
      // localhost 대신 127.0.0.1: Node는 localhost를 IPv6(::1)로 먼저 풀 수 있는데 API는 IPv4에서만 받는다.
      '/api': 'http://127.0.0.1:8082',
    },
  },
})
