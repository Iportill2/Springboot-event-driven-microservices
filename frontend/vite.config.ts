import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // En desarrollo, las llamadas a /api se reenvian al API Gateway.
      // En produccion, Nginx servira el build y enrutara /api al Gateway
      // (el navegador usa siempre la misma origen, sin CORS).
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})