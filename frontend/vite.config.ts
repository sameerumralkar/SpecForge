import react from '@vitejs/plugin-react'
import type { Plugin } from 'vite'
import { defineConfig } from 'vitest/config'

/** Languages the app shows in Monaco; everything else is left out of the bundle. */
const MONACO_LANGUAGES = ['json', 'yaml', 'java', 'xml', 'markdown', 'shell']

/**
 * Monaco's entry point (esm/vs/index.js, and editor.main) imports every language and the TypeScript/CSS/HTML language services. This
 * removes the imports (and their re-exports) the app doesn't use, keeping the editor itself whole.
 */
function monacoLanguages(): Plugin {
  const keep = new Set(MONACO_LANGUAGES)
  return {
    name: 'monaco-languages',
    enforce: 'pre',
    transform(code, id) {
      const file = id.replace(/\\/g, '/').split('?')[0]
      if (!file.endsWith('monaco-editor/esm/vs/index.js') && !file.endsWith('monaco-editor/esm/vs/editor/editor.main.js')) return null
      const dropped = new Set<string>()
      const lines = code.split('\n').filter((line) => {
        const language = line.match(/languages\/definitions\/([a-z0-9-]+)\/register\.js/)
        if (language && !keep.has(language[1])) return false
        const feature = line.match(/import \* as (\w+) from '(?:\.\.?\/)+languages\/features\/(\w+)\/register\.js'/)
        if (feature && !keep.has(feature[2])) {
          dropped.add(feature[1])
          return false
        }
        return true
      })
      return lines.filter((line) => ![...dropped].some((name) => line.includes(`export { ${name} as`))).join('\n')
    },
  }
}

export default defineConfig({
  plugins: [react(), monacoLanguages()],
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
