import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { FieldInventoryPage } from '../pages/FieldInventoryPage'
import { PayloadPage } from '../pages/PayloadPage'
import { api } from '../api/client'
import type { ErrorCode, FieldDefinition, Payload } from '../api/types'
import { renderWithProviders } from './render'

const code: ErrorCode = {
  id: 'c1', projectId: 'p', code: 'CUR-001', message: 'Unknown currency', httpStatus: null, status: 400, category: 'VALIDATION', links: [],
}
const currency: FieldDefinition = {
  id: 'd1', projectId: 'p', usedIn: ['Money.currency'],
  field: { name: 'currency', type: 'STRING', required: true, minLength: 3, maxLength: 3, pattern: '[A-Z]{3}', enumValues: [], errorCodeIds: ['c1'] },
}
const amount: FieldDefinition = {
  id: 'd2', projectId: 'p', usedIn: [], field: { name: 'amount', type: 'DECIMAL', required: true, minimum: 0, enumValues: [], errorCodeIds: [] },
}
const money: Payload = {
  id: 'm', projectId: 'p', name: 'Money', role: 'OBJECT', fields: [{ name: 'currency', type: 'STRING', required: true, enumValues: [], errorCodeIds: [] }],
}

const seeds: [readonly unknown[], unknown][] = [
  [['projects', 'p', 'field-definitions'], [amount, currency]], [['projects', 'p', 'error-codes'], [code]],
  [['projects', 'p', 'payloads'], [money]], [['projects', 'p', 'payloads', 'm', 'usages'], []],
]

describe('Field inventory', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(api, 'fieldDefinitions').mockResolvedValue([amount, currency])
  })

  it('lists fields with their constraints, codes and uses, and creates one', async () => {
    const user = userEvent.setup()
    const created: FieldDefinition = { id: 'd3', projectId: 'p', usedIn: [], field: { name: 'email', type: 'STRING', required: true, format: 'email', enumValues: [], errorCodeIds: [] } }
    const create = vi.spyOn(api, 'createFieldDefinition').mockResolvedValue(created)
    renderWithProviders(<Routes><Route path="/projects/:projectId/fields" element={<FieldInventoryPage />} /></Routes>, seeds, '/projects/p/fields')

    const table = screen.getByRole('table', { name: 'Field inventory' })
    expect(within(table).getByText('string · 3..3 chars · /[A-Z]{3}/')).toBeInTheDocument()
    expect(within(table).getByText('CUR-001')).toBeInTheDocument()
    expect(within(table).getByText('Money.currency')).toBeInTheDocument()
    await user.type(screen.getByPlaceholderText('Find a field'), 'decimal')
    expect(within(table).queryByText('currency')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'New field' }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByRole('combobox', { name: 'Type' })).toBeInTheDocument()
    await user.click(within(dialog).getByRole('combobox', { name: 'Type' }))
    expect(screen.queryByRole('option', { name: 'OBJECT' })).not.toBeInTheDocument()
    await user.keyboard('{Escape}')
    await user.type(within(dialog).getByLabelText(/^Name/), 'email')
    await user.type(within(dialog).getByLabelText('Format'), 'email')
    await user.click(within(dialog).getByLabelText('Required'))
    await user.click(within(dialog).getByRole('button', { name: 'Add to inventory' }))
    await waitFor(() => expect(create).toHaveBeenCalledWith('p', expect.objectContaining({ name: 'email', type: 'STRING', format: 'email', required: true })))
  })

  it('offers the field inventory on an object page, copying a field in under a free name', async () => {
    const user = userEvent.setup()
    const save = vi.spyOn(api, 'updatePayload').mockImplementation(async (_p, _id, body) => ({ ...money, ...body, fields: body.fields }) as Payload)
    renderWithProviders(<Routes><Route path="/projects/:projectId/payloads/:payloadId" element={<PayloadPage />} /></Routes>, seeds, '/projects/p/payloads/m')

    const palette = screen.getByRole('complementary', { name: 'Field inventory' })
    expect(screen.getByRole('complementary', { name: 'Object inventory' })).toBeInTheDocument()
    await user.click(within(palette).getByRole('button', { name: 'Add field currency' }))
    await user.click(within(palette).getByRole('button', { name: 'Add field amount' }))
    expect(screen.getByLabelText('Field 2 name')).toHaveValue('currency2')
    expect(screen.getByLabelText('Field 3 name')).toHaveValue('amount')
    // The first field shares a name with an inventory entry; the new one can be saved as its own.
    expect(screen.getByRole('button', { name: 'Field 1 is in the field inventory' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Save field 2 to the field inventory' })).toBeEnabled()

    await user.click(screen.getByRole('button', { name: /^Save$/ }))
    await waitFor(() => expect(save).toHaveBeenCalled())
    const fields = save.mock.calls[0][2].fields
    expect(fields[1]).toMatchObject({ name: 'currency2', type: 'STRING', minLength: 3, maxLength: 3, pattern: '[A-Z]{3}', errorCodeIds: ['c1'] })
    expect(fields[2]).toMatchObject({ name: 'amount', type: 'DECIMAL', minimum: 0 })
  })

  it('saves a payload field to the field inventory', async () => {
    const user = userEvent.setup()
    const create = vi.spyOn(api, 'createFieldDefinition').mockResolvedValue(amount)
    const withNotes: Payload = { ...money, fields: [...money.fields, { name: 'notes', type: 'STRING', required: false, maxLength: 200, enumValues: [] }] }
    renderWithProviders(<Routes><Route path="/projects/:projectId/payloads/:payloadId" element={<PayloadPage />} /></Routes>,
      [...seeds, [['projects', 'p', 'payloads'], [withNotes]]], '/projects/p/payloads/m')

    await user.click(screen.getByRole('button', { name: 'Save field 2 to the field inventory' }))
    const dialog = screen.getByRole('dialog', { name: 'New inventory field' })
    expect(within(dialog).getByLabelText(/^Name/)).toHaveValue('notes')
    await user.click(within(dialog).getByRole('button', { name: 'Add to inventory' }))
    await waitFor(() => expect(create).toHaveBeenCalledWith('p', expect.objectContaining({ name: 'notes', maxLength: 200 })))
  })
})
