import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

// Monaco does not run in jsdom; a textarea stands in for it.
vi.mock('../components/CodeEditor', async () => {
  const { createElement } = await import('react')
  return {
    default: ({ value, onChange }: { value: string; onChange?: (v: string) => void }) =>
      createElement('textarea', { 'aria-label': 'Code editor', value, onChange: (e: { target: { value: string } }) => onChange?.(e.target.value) }),
  }
})

afterEach(() => cleanup())
