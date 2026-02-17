import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:5200',
        changeOrigin: true,
        secure: false
      }
    }
  },
  build: {
    outDir: '../public',
    emptyOutDir: true,
    assetsDir: 'assets',
    sourcemap: false
  }
})