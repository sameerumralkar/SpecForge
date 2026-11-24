import { useState } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { ErrorCodesPage } from '../pages/ErrorCodesPage'
import { PayloadEditor } from '../editors/PayloadEditor'
import { api } from '../api/client'
import type { Endpoint, ErrorCode, Payload, PayloadRequest, RuleChain } from '../api/types'
import { renderWithProviders } from './render'

const money: Payload = {
  id: 'm', projectId: 'p', name: 'Money', role: 'OBJECT', fields: [
    { name: 'value', type: 'DECIMAL', required: true, enumValues: [], errorCodeIds: ['c1'] },
    { name: 'currency', type: 'STRING', required: true, enumValues: [] },
  ],
}
const amount: ErrorCode = {
  id: 'c1', projectId: 'p', code: 'PAY-001', message: 'The amount must be positive', httpStatus: null, status: 400,
  category: 'VALIDATION', links: [{ kind: 'FIELD', payloadId: 'm', payloadName: 'Money', field: 'value', detail: 'Money.value' }],
}
const funds: ErrorCode = {
  id: 'c2', projectId: 'p', code: 'PAY-010', message: 'Insufficient funds', httpStatus: null, status: 422, category: 'BUSINESS',
  links: [{ kind: 'CHECK', endpointId: 'e', endpointName: 'pay', ruleId: 'r1', ruleName: 'Balance', detail: 'pay › Balance › check 1: balance CUSTOM' }],
}
const endpoint = { id: 'e', projectId: 'p', kind: 'REST', name: 'pay', tags: [], parameters: [], responses: [], security: [] } as unknown as Endpoint
const chain: RuleChain = {
  endpointId: 'e', endpointName: 'pay', effectiveImplementationStyle: 'IMPLEMENTATION_CLASS', rules: [
    { id: 'r1', endpointId: 'e', position: 0, type: 'VALIDATE', typeLabel: 'Validate', name: 'Balance', config: {}, summary: '', errorCodeIds: [] },
    { id: 'r2', endpointId: 'e', position: 1, type: 'ENRICH', typeLabel: 'Enrich', name: 'Call bank', config: {}, summary: '', errorCodeIds: [] },
  ], summary: [], warnings: [],
}

const page = () => renderWithProviders(<Routes><Route path="/projects/:projectId/errors" element={<ErrorCodesPage />} /></Routes>, [
  [['projects', 'p', 'error-codes'], [amount, funds]], [['projects', 'p', 'payloads'], [money]],
  [['projects', 'p', 'endpoints'], [endpoint]], [['projects', 'p', 'endpoints', 'e', 'rules'], chain],
], '/projects/p/errors')

describe('Error codes', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(api, 'errorCodes').mockResolvedValue([amount, funds])
  })

  it('lists codes with their status, category and links, and filters them', async () => {
    const user = userEvent.setup()
    page()
    const table = screen.getByRole('table', { name: 'Error codes' })
    expect(within(table).getByText('PAY-001')).toBeInTheDocument()
    expect(within(table).getByText('Money.value')).toBeInTheDocument()
    expect(within(table).getByText(/pay › Balance › check 1/)).toBeInTheDocument()

    await user.click(within(screen.getByLabelText('Filter by category')).getByText('BUSINESS (1)'))
    expect(within(table).queryByText('PAY-001')).not.toBeInTheDocument()
    expect(within(table).getByText('PAY-010')).toBeInTheDocument()
    await user.type(screen.getByPlaceholderText('Find a code, message or link'), 'nothing like this')
    expect(within(table).getByText('Nothing matches.')).toBeInTheDocument()
  })

  it('creates a code, then links it to a field and a processing rule', async () => {
    const user = userEvent.setup()
    const created: ErrorCode = { id: 'c3', projectId: 'p', code: 'PAY-020', message: 'Bank unavailable', httpStatus: null, status: 503, category: 'DOWNSTREAM', links: [] }
    const create = vi.spyOn(api, 'createErrorCode').mockResolvedValue(created)
    const link = vi.spyOn(api, 'setErrorCodeLinks').mockResolvedValue(created)
    page()

    await user.click(screen.getByRole('button', { name: 'New error code' }))
    await user.type(screen.getByLabelText(/^Code/), 'pay-020')
    await user.click(screen.getByRole('combobox', { name: /Category/ }))
    await user.click(screen.getByRole('option', { name: 'DOWNSTREAM (503)' }))
    await user.type(screen.getByLabelText(/^Message/), 'Bank unavailable')
    await user.click(screen.getByRole('button', { name: 'Create' }))
    await waitFor(() => expect(create).toHaveBeenCalledWith('p', expect.objectContaining({ code: 'PAY-020', category: 'DOWNSTREAM', message: 'Bank unavailable' })))

    // Linking opens straight after creating.
    const dialog = await screen.findByRole('dialog', { name: /Link PAY-020/ })
    await user.click(within(dialog).getByRole('combobox', { name: 'Payload' }))
    await user.click(screen.getByRole('option', { name: 'Money' }))
    await user.click(within(dialog).getByRole('combobox', { name: 'Field' }))
    await user.click(screen.getByRole('option', { name: 'currency' }))
    await user.click(within(dialog).getByRole('button', { name: 'Link field' }))
    await user.click(within(dialog).getByRole('combobox', { name: 'Endpoint' }))
    await user.click(screen.getByRole('option', { name: 'pay' }))
    await user.click(within(dialog).getByRole('combobox', { name: 'Processing rule' }))
    await user.click(screen.getByRole('option', { name: '2. Call bank (Enrich)' }))
    await user.click(within(dialog).getByRole('button', { name: 'Link rule' }))
    expect(within(within(dialog).getByRole('list', { name: 'Links' })).getByText('Money.currency')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Save links' }))

    await waitFor(() => expect(link).toHaveBeenCalledWith('p', 'c3', [
      { kind: 'FIELD', payloadId: 'm', field: 'currency', ruleId: undefined },
      { kind: 'RULE', payloadId: undefined, field: undefined, ruleId: 'r2' },
    ]))
  })

  it('shows a field\'s codes in the payload editor and picks more there', async () => {
    const user = userEvent.setup()
    let latest: PayloadRequest | undefined
    function Harness() {
      const [value, setValue] = useState<PayloadRequest>({ name: 'Money', fields: money.fields })
      return <PayloadEditor value={value} onChange={(v) => { setValue(v); latest = v }} payloads={[money]} selfId="m" error={null} />
    }
    renderWithProviders(<Routes><Route path="/projects/:projectId/payloads/:payloadId" element={<Harness />} /></Routes>,
      [[['projects', 'p', 'error-codes'], [amount, funds]]], '/projects/p/payloads/m')

    expect(screen.getAllByLabelText('Error codes')[0]).toHaveTextContent('PAY-001')
    await user.click(screen.getByRole('button', { name: 'Constraints of field 2' }))
    await user.click(screen.getByRole('combobox', { name: 'Error codes' }))
    await user.click(screen.getByRole('option', { name: /PAY-010/ }))
    expect(latest?.fields[1].errorCodeIds).toEqual(['c2'])
    expect(latest?.fields[0].errorCodeIds).toEqual(['c1'])
  })
})
