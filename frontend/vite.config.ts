import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // The Spring Boot backend; start it with ./mvnw spring-boot:run in ../backend.
    proxy: { '/api': 'http://localhost:8080' },
  },
  build: {
    chunkSizeWarningLimit: 4000, // Monaco is large and loaded on demand
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
})
