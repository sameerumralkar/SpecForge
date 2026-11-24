import { describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RuleDialog } from '../editors/rules/RuleDialog'
import type { RuleFormContext } from '../editors/rules/inputs'
import { ApiError } from '../api/client'
import type { Payload, RuleRequest } from '../api/types'
import { defaultConfig } from '../lib/rules'
import { META, renderWithProviders } from './render'

const order: Payload = {
  id: 'o', projectId: 'p', name: 'Order', role: 'STRUCTURE', fields: [
    { name: 'total', type: 'DECIMAL', required: true, enumValues: [] },
    { name: 'status', type: 'STRING', required: true, enumValues: [] },
  ],
}
const ctx: RuleFormContext = { payloads: [order], current: order, otherRules: ['Enrich customer'], parameters: [], meta: META }

async function pick(user: ReturnType<typeof userEvent.setup>, label: string, option: string) {
  await user.click(screen.getByRole('combobox', { name: label }))
  await user.click(within(screen.getByRole('listbox')).getByRole('option', { name: option }))
}

describe('RuleDialog', () => {
  it('builds a VALIDATE rule and sends a cleaned-up config', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn<(r: RuleRequest) => Promise<void>>().mockResolvedValue()
    renderWithProviders(<RuleDialog open title="Add rule" ctx={ctx} onSubmit={onSubmit} onClose={() => {}}
      initial={{ type: 'VALIDATE', name: '', condition: '', config: defaultConfig('VALIDATE') }} />)

    await user.type(screen.getByLabelText('Name'), 'Check total')
    await user.type(screen.getByRole('combobox', { name: 'Field' }), 'total')
    await pick(user, 'Check', 'MIN')
    await user.type(screen.getByLabelText('Value'), '0')
    await user.click(screen.getByRole('button', { name: 'Save rule' }))

    expect(onSubmit).toHaveBeenCalledWith({
      type: 'VALIDATE', name: 'Check total', condition: null,
      config: { checks: [{ field: 'total', kind: 'MIN', value: '0' }], failureStatus: 400 },
    })
  })

  it('suggests field paths of the payload the rule works on', async () => {
    const user = userEvent.setup()
    renderWithProviders(<RuleDialog open title="Add rule" ctx={ctx} onSubmit={vi.fn()} onClose={() => {}}
      initial={{ type: 'PERSIST', name: '', condition: '', config: defaultConfig('PERSIST') }} />)
    await user.click(screen.getByRole('combobox', { name: 'Id field' }))
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual(['total', 'status'])
  })

  it('shows server errors on the right inputs and keeps the dialog open', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockRejectedValue(new ApiError(400, 'invalid', [
      { field: 'condition', message: "at character 7: write '==' to compare" },
      { field: 'config.branches[0].name', message: 'use a lowerCamelCase name' },
    ]))
    renderWithProviders(<RuleDialog open title="Edit rule" ctx={ctx} onSubmit={onSubmit} onClose={() => {}}
      initial={{ type: 'CONDITIONAL_ROUTE', name: 'Route', condition: 'total = 5',
        config: { branches: [{ name: 'Big', condition: 'total > 5' }] } }} />)
    await user.click(screen.getByRole('button', { name: 'Save rule' }))

    expect(screen.getByLabelText('Only when (optional)')).toHaveAccessibleDescription("at character 7: write '==' to compare")
    expect(screen.getByLabelText('Branch name')).toHaveAccessibleDescription('use a lowerCamelCase name')
    expect(screen.getByText('Not saved: 2 problems')).toBeInTheDocument()
  })

  it('switches the form when the type changes, and toggles error-handling parts', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn<(r: RuleRequest) => Promise<void>>().mockResolvedValue()
    renderWithProviders(<RuleDialog open title="Add rule" ctx={ctx} onSubmit={onSubmit} onClose={() => {}}
      initial={{ type: 'VALIDATE', name: 'Resilience', condition: '', config: defaultConfig('VALIDATE') }} />)

    await pick(user, 'Type', 'Error handling')
    expect(screen.queryByLabelText('Only when (optional)')).not.toBeInTheDocument()
    await user.click(screen.getByRole('switch', { name: 'Retry' }))
    await user.click(screen.getByRole('switch', { name: 'Fallback' }))
    await pick(user, 'Applies to', 'Step "Enrich customer"')
    await user.click(screen.getByRole('button', { name: 'Save rule' }))

    expect(onSubmit.mock.calls[0][0]).toMatchObject({
      type: 'ERROR_HANDLING',
      config: { retry: null, fallback: { type: 'ERROR_RESPONSE', httpStatus: 503 }, targetRule: 'Enrich customer' },
    })
  })
})
