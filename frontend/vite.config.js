import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Dev server mirrors the production layout: the SPA on port 3000,
// with /api proxied to the backend on port 8000.
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 3000,
    proxy: {
      '/api': 'http://localhost:8000',
    },
  },
})
