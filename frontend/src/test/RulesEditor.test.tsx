import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RulesEditor } from '../editors/RulesEditor'
import { api } from '../api/client'
import type { Rule, RuleChain } from '../api/types'
import { renderWithProviders } from './render'

const rule = (id: string, name: string, type: Rule['type'], summary: string): Rule =>
  ({ id, endpointId: 'e', position: 0, type, typeLabel: type, name, condition: null, config: {}, summary, errorCodeIds: [] })

const chain: RuleChain = {
  endpointId: 'e', endpointName: 'createOrder', effectiveImplementationStyle: 'IMPLEMENTATION_CLASS',
  inputPayloadId: 'req', inputPayloadName: 'CreateOrderRequest',
  rules: [rule('r1', 'Validate order', 'VALIDATE', 'Validate the request.'), rule('r2', 'Save order', 'PERSIST', 'Save the Order.')],
  summary: ['1. Validate the request.', '2. Save the Order.'],
  warnings: [{ ruleId: 'r2', ruleName: 'Save order', message: "'total' is not a field of Order" }],
}

const setup = () => renderWithProviders(
  <RulesEditor projectId="p" endpointId="e" payloads={[]} parameters={[]} endpointDirty={false} />,
  [[['projects', 'p', 'endpoints', 'e', 'rules'], chain]],
)

describe('RulesEditor', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(api, 'ruleChain').mockResolvedValue(chain)
  })

  it('shows the rules in order, the plain-English summary and warnings', () => {
    setup()
    expect(screen.getAllByTestId('rule-card').map((c) => c.textContent)).toEqual([
      expect.stringContaining('Validate order'), expect.stringContaining('Save order'),
    ])
    expect(screen.getByRole('list', { name: 'Rule chain summary' })).toHaveTextContent('Validate the request.Save the Order.')
    expect(screen.getByText("⚠ 'total' is not a field of Order")).toBeInTheDocument()
    expect(screen.getByText(/1 warning/)).toBeInTheDocument()
    expect(screen.getByText('CreateOrderRequest')).toBeInTheDocument()
  })

  it('moves a rule with the arrow buttons and saves the new order', async () => {
    const user = userEvent.setup()
    const reorder = vi.spyOn(api, 'reorderRules').mockResolvedValue({ ...chain, rules: [chain.rules[1], chain.rules[0]] })
    setup()
    expect(screen.getByRole('button', { name: 'Move Validate order up' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Move Save order up' }))
    expect(reorder).toHaveBeenCalledWith('p', 'e', ['r2', 'r1'])
    await waitFor(() => expect(screen.getAllByTestId('rule-card')[0]).toHaveTextContent('Save order'))
  })

  it('puts the order back if the server refuses it', async () => {
    const user = userEvent.setup()
    vi.spyOn(api, 'reorderRules').mockRejectedValue(new Error('Server is down'))
    setup()
    await user.click(screen.getByRole('button', { name: 'Move Validate order down' }))
    expect(await screen.findByText('Server is down')).toBeInTheDocument()
    expect(screen.getAllByTestId('rule-card')[0]).toHaveTextContent('Validate order')
  })

  it('asks before deleting a rule', async () => {
    const user = userEvent.setup()
    const remove = vi.spyOn(api, 'deleteRule').mockResolvedValue()
    setup()
    await user.click(screen.getByRole('button', { name: 'Delete Save order' }))
    expect(screen.getByText('Delete rule "Save order"?')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Delete' }))
    expect(remove).toHaveBeenCalledWith('p', 'e', 'r2')
  })

  it('opens the dialog for the chosen rule type', async () => {
    const user = userEvent.setup()
    setup()
    await user.click(screen.getByRole('button', { name: 'Add rule' }))
    await user.click(screen.getByRole('menuitem', { name: 'Publish event' }))
    expect(screen.getByRole('dialog', { name: 'Add rule' })).toBeInTheDocument()
    expect(screen.getByLabelText('Topic / destination')).toBeInTheDocument()
  })
})
