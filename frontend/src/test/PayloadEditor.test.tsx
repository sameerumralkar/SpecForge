import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { PayloadEditor } from '../editors/PayloadEditor'
import { ApiError } from '../api/client'
import type { Payload, PayloadRequest } from '../api/types'
import { renderWithProviders } from './render'

const address: Payload = { id: 'a', projectId: 'p', name: 'Address', fields: [] }

function Harness({ initial, error, onValue }: { initial: PayloadRequest; error?: unknown; onValue?: (v: PayloadRequest) => void }) {
  const [value, setValue] = useState(initial)
  return <PayloadEditor value={value} onChange={(v) => { setValue(v); onValue?.(v) }} payloads={[address]} selfId="self" error={error} />
}

/** Opens an MUI select by its accessible name and picks an option. */
async function choose(user: ReturnType<typeof userEvent.setup>, label: string, option: string) {
  await user.click(screen.getByRole('combobox', { name: label }))
  await user.click(within(screen.getByRole('listbox')).getByRole('option', { name: option }))
}

describe('PayloadEditor', () => {
  it('adds a field and asks for the payload when the type is OBJECT', async () => {
    const user = userEvent.setup()
    let latest: PayloadRequest | undefined
    renderWithProviders(<Harness initial={{ name: 'Customer', fields: [] }} onValue={(v) => (latest = v)} />)

    expect(screen.getByText('No fields yet.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Add field' }))
    await user.type(screen.getByLabelText('Field 1 name'), 'home')
    expect(screen.queryByLabelText('Payload')).not.toBeInTheDocument()

    await choose(user, 'Field 1 type', 'OBJECT')
    await user.click(screen.getByRole('combobox', { name: /Payload/ }))
    await user.click(screen.getByRole('option', { name: 'Address' }))
    await user.click(screen.getByLabelText('Field 1 required'))

    expect(latest?.fields[0]).toMatchObject({ name: 'home', type: 'OBJECT', refPayloadId: 'a', required: true })
  })

  it('shows constraints that fit the type and clears the payload when the type changes', async () => {
    const user = userEvent.setup()
    let latest: PayloadRequest | undefined
    renderWithProviders(<Harness onValue={(v) => (latest = v)} initial={{
      name: 'Item', fields: [{ name: 'home', type: 'OBJECT', refPayloadId: 'a', required: false, enumValues: [] }],
    }} />)

    await choose(user, 'Field 1 type', 'INTEGER')
    expect(latest?.fields[0].refPayloadId).toBeNull()
    await user.click(screen.getByRole('button', { name: 'Constraints of field 1' }))
    expect(screen.getByLabelText('Minimum')).toBeInTheDocument()
    expect(screen.queryByLabelText('Pattern (regex)')).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('Minimum'), '5')
    expect(latest?.fields[0].minimum).toBe(5)
  })

  it('reorders and removes fields', async () => {
    const user = userEvent.setup()
    let latest: PayloadRequest | undefined
    const field = (name: string) => ({ name, type: 'STRING' as const, required: false, enumValues: [] })
    renderWithProviders(<Harness onValue={(v) => (latest = v)} initial={{ name: 'X', fields: [field('a'), field('b'), field('c')] }} />)
    await user.click(screen.getByRole('button', { name: 'Move field 3 up' }))
    expect(latest?.fields.map((f) => f.name)).toEqual(['a', 'c', 'b'])
    await user.click(screen.getByRole('button', { name: 'Remove field 1' }))
    expect(latest?.fields.map((f) => f.name)).toEqual(['c', 'b'])
  })

  it('shows server errors next to the field, opening its constraints when needed', () => {
    const error = new ApiError(400, 'invalid', [
      { field: 'name', message: 'must be UpperCamelCase' },
      { field: 'fields[0].pattern', message: 'not a valid regular expression' },
    ])
    renderWithProviders(<Harness error={error} initial={{
      name: 'bad', fields: [{ name: 'code', type: 'STRING', required: false, pattern: '[a-', enumValues: [] }],
    }} />)
    expect(screen.getByText('must be UpperCamelCase')).toBeInTheDocument()
    expect(screen.getByLabelText('Pattern (regex)')).toHaveAccessibleDescription('not a valid regular expression')
  })
})
