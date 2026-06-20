import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // Vite forwards /api/* to Spring Boot in development.
      // This means the browser only ever talks to localhost:5173,
      // so CORS never fires during development.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      }
    }
  }
})
