import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { InventoryPage } from '../pages/InventoryPage'
import { Sidebar } from '../components/Sidebar'
import { api } from '../api/client'
import type { Payload, Project } from '../api/types'
import { fieldNameFor, objectField } from '../lib/inventory'
import { renderWithProviders } from './render'

const money: Payload = {
  id: 'm', projectId: 'p', name: 'Money', role: 'OBJECT', description: 'An amount in one currency', fields: [
    { name: 'value', type: 'DECIMAL', required: true, enumValues: [] },
    { name: 'currency', type: 'STRING', required: true, enumValues: [] },
  ],
}
const request: Payload = {
  id: 'r', projectId: 'p', name: 'PaymentRequest', role: 'STRUCTURE', fields: [
    { name: 'amount', type: 'OBJECT', refPayloadId: 'm', required: false, enumValues: [] },
  ],
}
const payer: Payload = { id: 'y', projectId: 'p', name: 'Payer', role: 'STRUCTURE', fields: [{ name: 'accountId', type: 'STRING', required: true, enumValues: [] }] }

describe('Object inventory', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(api, 'payloadUsages').mockImplementation(async (_p, id) => (id === 'm'
      ? [{ kind: 'PAYLOAD_FIELD', ownerId: 'r', ownerName: 'PaymentRequest', detail: 'field amount' }] : []))
  })

  it('lists objects and structures separately, with their fields and uses', async () => {
    renderWithProviders(<Routes><Route path="/projects/:projectId/inventory" element={<InventoryPage />} /></Routes>,
      [[['projects', 'p', 'payloads'], [money, request, payer]]], '/projects/p/inventory')
    const objects = screen.getByRole('table', { name: 'Inventory objects' })
    expect(within(objects).getByText('Money')).toBeInTheDocument()
    expect(within(objects).getByText('value: decimal')).toBeInTheDocument()
    expect(await within(objects).findByText('1 place: PaymentRequest')).toBeInTheDocument()
    const structures = screen.getByRole('table', { name: 'Structures' })
    expect(within(structures).getByText('amount?: Money')).toBeInTheDocument()
    expect(within(structures).getByText('Payer')).toBeInTheDocument()
  })

  it('moves a structure into the inventory, and refuses to delete an object in use', async () => {
    const user = userEvent.setup()
    const update = vi.spyOn(api, 'updatePayload').mockResolvedValue({ ...payer, role: 'OBJECT' })
    vi.spyOn(api, 'payloads').mockResolvedValue([money, request, { ...payer, role: 'OBJECT' }])
    vi.spyOn(api, 'projects').mockResolvedValue([])
    vi.spyOn(api, 'project').mockResolvedValue({ id: 'p' } as never)
    const del = vi.spyOn(api, 'deletePayload')
    renderWithProviders(<Routes><Route path="/projects/:projectId/inventory" element={<InventoryPage />} /></Routes>,
      [[['projects', 'p', 'payloads'], [money, request, payer]]], '/projects/p/inventory')

    await user.click(screen.getByRole('button', { name: 'Move Payer into the inventory' }))
    await waitFor(() => expect(update).toHaveBeenCalledWith('p', 'y', expect.objectContaining({ name: 'Payer', role: 'OBJECT' })))

    expect(await within(await screen.findByRole('table', { name: 'Inventory objects' })).findByText('Payer')).toBeInTheDocument()
    await screen.findByText('1 place: PaymentRequest')
    await user.click(screen.getByRole('button', { name: 'Delete Money' }))
    expect(await screen.findByText(/field amount/)).toBeInTheDocument()
    expect(del).not.toHaveBeenCalled()
  })

  it('groups the sidebar payloads into objects and structures', () => {
    const project = { id: 'p', name: 'Pay', basePackage: 'com.example', version: '1', defaultImplementationStyle: 'IMPLEMENTATION_CLASS' } as Project
    renderWithProviders(<Sidebar projectId="p" />, [[['projects', 'p'], project], [['projects', 'p', 'endpoints'], []], [['projects', 'p', 'payloads'], [money, request]]])
    const headings = screen.getAllByText(/^(Inventory objects|Structures)$/).map((e) => e.textContent)
    expect(headings).toEqual(['Inventory objects', 'Structures'])
  })

  it('names a dropped object field after the object, without clashing', () => {
    expect(fieldNameFor('Money', ['amount'])).toBe('money')
    expect(fieldNameFor('Money', ['money', 'Money2'])).toBe('money3')
    expect(objectField(money, request.fields)).toEqual({ name: 'money', type: 'OBJECT', refPayloadId: 'm', required: false, enumValues: [] })
  })
})

