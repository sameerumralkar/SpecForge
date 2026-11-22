import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SpecPanel } from '../components/SpecPanel'
import { api } from '../api/client'
import type { SpecsView } from '../api/types'
import { focusPattern } from '../lib/specs'
import { renderWithProviders } from './render'

const specs: SpecsView = {
  openapi: {
    id: 'openapi', present: true, valid: false, yaml: 'openapi: 3.1.0\n', json: '{"openapi": "3.1.0"}\n',
    problems: [
      { severity: 'ERROR', source: 'openapi-schema', location: '$.info', message: 'required property "version" not found' },
      { severity: 'WARNING', source: 'openapi', location: '/components/schemas/Item/properties/price/examples', message: 'example is text' },
    ],
  },
  asyncapi: {
    id: 'asyncapi', present: false, valid: true, yaml: null, json: null,
    problems: [{ severity: 'INFO', source: 'asyncapi', location: '', message: 'The project has no async endpoints, so there is no AsyncAPI document.' }],
  },
}

const setup = (focus?: { name: string; kind: 'REST' | 'ASYNC' }) =>
  renderWithProviders(<SpecPanel projectId="p" focus={focus} onClose={() => {}} />, [[['projects', 'p', 'specs'], specs]])

describe('SpecPanel', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
    vi.spyOn(api, 'specs').mockResolvedValue(specs)
  })

  it('shows the YAML, the validation problems and a download link', async () => {
    setup()
    expect(await screen.findByLabelText('Code editor')).toHaveValue('openapi: 3.1.0\n')
    const results = screen.getByLabelText('Validation results')
    expect(results).toHaveTextContent('1 error, 1 warning')
    expect(within(results).getByText(/required property "version" not found/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Download openapi.yaml' })).toHaveAttribute('href', '/api/projects/p/specs/openapi.yaml')
  })

  it('switches to JSON and remembers the choice', async () => {
    const user = userEvent.setup()
    setup()
    await user.click(screen.getByRole('button', { name: 'JSON' }))
    expect(await screen.findByLabelText('Code editor')).toHaveValue('{"openapi": "3.1.0"}\n')
    expect(localStorage.getItem('spec-format')).toBe('json')
    expect(screen.getByRole('link', { name: 'Download openapi.json' })).toBeInTheDocument()
  })

  it('opens the AsyncAPI tab for async endpoints and explains when there is nothing to show', () => {
    setup({ name: 'onPaid', kind: 'ASYNC' })
    expect(screen.getByRole('tab', { name: /AsyncAPI/ })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByText('The project has no async endpoints, so there is no AsyncAPI document.')).toBeInTheDocument()
    expect(screen.queryByLabelText('Code editor')).not.toBeInTheDocument()
  })

  it('builds patterns that find where an endpoint starts', () => {
    const yaml = 'paths:\n  /x:\n    get:\n      operationId: getX\n'
    expect(new RegExp(focusPattern('openapi', 'yaml', 'getX'), 'm').test(yaml)).toBe(true)
    expect(new RegExp(focusPattern('openapi', 'json', 'getX'), 'm').test('{ "operationId": "getX" }')).toBe(true)
    expect(new RegExp(focusPattern('asyncapi', 'yaml', 'onPaid'), 'm').test('operations:\n  onPaid:\n    action: receive')).toBe(true)
    expect(new RegExp(focusPattern('asyncapi', 'yaml', 'onPaid'), 'm').test('components:\n  messages:\n    onPaid:')).toBe(false)
    expect(new RegExp(focusPattern('asyncapi', 'json', 'onPaid'), 'm').test('{\n  "operations": {\n    "onPaid": {')).toBe(true)
  })
})
