import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { ReportsPage } from '../pages/ReportsPage'
import { MapIndex } from '../lib/capabilityMap'
import { fieldUnder } from '../components/help/FieldHelpLayer'
import { fieldHelp } from '../lib/help'
import type { CapabilityMap, Project } from '../api/types'
import { renderWithProviders } from './render'

const levels = ['Domain', 'Area', 'Capability', 'Sub-capability', 'Function', 'Process', 'Activity', 'Task']
// Commerce (L1) › Ordering (L2) › … › Checkout (L8), with createOrder linked at L8.
const chain = levels.map((_, i) => ({
  id: `c${i + 1}`, parentId: i ? `c${i}` : null, level: i + 1, name: ['Commerce', 'Ordering', 'Order capture', 'Cart', 'Basket', 'Submit', 'Confirm', 'Checkout'][i],
  code: null, description: null, endpointIds: i === 7 ? ['e1'] : [],
}))
const map: CapabilityMap = {
  projectId: 'p1', projectName: 'Shop', levelNames: levels, capabilities: chain,
  endpoints: [
    { id: 'e1', name: 'createOrder', kind: 'REST', method: 'POST', path: '/orders', operation: null, channel: null, protocol: null, summary: 'Place an order',
      uses: [{ payloadId: 'order', how: 'request body' }, { payloadId: 'order', how: 'response 201' }, { payloadId: 'receipt', how: "rule 'To receipt'" }],
      rules: [{ id: 'r1', position: 1, name: 'To receipt', type: 'TRANSFORM', typeLabel: 'Transform / map', condition: 'total.value > 0', payloadIds: ['receipt'], errorCodes: 1 }] },
    { id: 'e2', name: 'ping', kind: 'REST', method: 'GET', path: '/ping', operation: null, channel: null, protocol: null, summary: null, uses: [], rules: [] },
  ],
  payloads: [
    { id: 'money', name: 'Money', role: 'OBJECT', description: null, fields: [
      { name: 'value', type: 'DECIMAL', itemType: null, required: true, refPayloadId: null, constraints: 'value 0..', description: null },
      { name: 'currency', type: 'STRING', itemType: null, required: true, refPayloadId: null, constraints: '', description: null }] },
    { id: 'order', name: 'Order', role: 'STRUCTURE', description: null, fields: [
      { name: 'id', type: 'UUID', itemType: null, required: true, refPayloadId: null, constraints: '', description: null },
      { name: 'total', type: 'OBJECT', itemType: null, required: true, refPayloadId: 'money', constraints: '', description: null }] },
    { id: 'receipt', name: 'Receipt', role: 'STRUCTURE', description: null, fields: [
      { name: 'id', type: 'UUID', itemType: null, required: false, refPayloadId: null, constraints: '', description: null }] },
  ],
}

const page = (tab: 'map' | 'trace') => renderWithProviders(<Routes><Route path="/reports/:reportId?" element={<ReportsPage />} /></Routes>, [
  [['projects'], [{ id: 'p1', name: 'Shop' }] as Project[]],
  [['reports', 'map', 'p1'], map],
], `/reports/${tab}?project=p1`)

const column = (title: string) => screen.getByRole('region', { name: title })
const click = async (user: ReturnType<typeof userEvent.setup>, region: string, name: RegExp) =>
  user.click(within(column(region)).getByRole('button', { name }))

describe('Capability map', () => {
  it('walks paths, inventories and uses', () => {
    const index = new MapIndex(map)
    expect(index.pathOf('c3')).toEqual(['Commerce', 'Ordering', 'Order capture'])
    expect([...index.inventory('c1')]).toEqual(['e1'])
    expect(index.endpointsUsing('money').map((u) => u.how)).toEqual([['request body, through Order.total', 'response 201, through Order.total']])
    expect(index.endpointsUsing('receipt')[0]).toMatchObject({ direct: true, how: ["rule 'To receipt'"] })
    expect(index.fieldNames().find((f) => f.name === 'id')).toEqual({ name: 'id', payloads: 2, types: ['UUID'] })
  })

  it('goes down from L1 to L8, then to endpoints, objects, rules and fields', async () => {
    const user = userEvent.setup()
    page('map')
    const root = column('L1 · Domain')
    expect(within(root).getByRole('list', { name: 'Endpoints not mapped to a capability' })).toHaveTextContent('ping')
    await click(user, 'L1 · Domain', /Commerce/)
    for (let level = 1; level < 8; level++) {
      const c = chain[level - 1]
      expect(within(column(`L${level} ${c.name}`)).getByRole('list', { name: `L${level + 1} · ${levels[level]}` })).toBeInTheDocument()
      await click(user, `L${level} ${c.name}`, new RegExp(chain[level].name))
    }
    const l8 = column('L8 Checkout')
    expect(within(l8).queryByRole('list', { name: /^L9/ })).not.toBeInTheDocument()
    await click(user, 'L8 Checkout', /createOrder/)

    const endpoint = column('createOrder')
    expect(within(endpoint).getByRole('list', { name: 'Objects' })).toHaveTextContent('request body, response 201')
    expect(within(endpoint).getByRole('list', { name: 'Processing rules' })).toHaveTextContent('1. To receipt')
    expect(within(endpoint).getByRole('link', { name: 'Open createOrder in the editor' })).toHaveAttribute('href', '/projects/p1/endpoints/e1')

    await click(user, 'createOrder', /Order/)
    await click(user, 'Order', /total/)
    expect(within(column('Money')).getByRole('list', { name: 'Fields (2)' })).toHaveTextContent('currency')

    // Clicking a rule replaces everything to its right.
    await click(user, 'createOrder', /^1\. To receipt/)
    expect(screen.queryByRole('region', { name: 'Money' })).not.toBeInTheDocument()
    expect(within(column('1. To receipt')).getByText('when total.value > 0')).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Where you are' })).toHaveTextContent('Commerce')
  })

  it('goes up from a field to the objects, endpoints and capabilities, to L1', async () => {
    const user = userEvent.setup()
    page('trace')
    await user.type(screen.getByLabelText('Filter the first column'), 'curr')
    expect(within(column('Fields')).getAllByRole('button')).toHaveLength(1)
    await click(user, 'Fields', /currency/)
    await click(user, 'currency', /Money/)
    const money = column('Money')
    expect(within(money).getByRole('list', { name: 'Objects that hold it' })).toHaveTextContent('field total')
    expect(within(money).getByRole('list', { name: 'Endpoints using it' })).toHaveTextContent('request body, through Order.total')
    await click(user, 'Money', /createOrder/)
    await click(user, 'createOrder', /Checkout/)
    for (let level = 8; level > 1; level--) {
      await click(user, `L${level} ${chain[level - 1].name}`, new RegExp(chain[level - 2].name))
    }
    expect(within(column('L1 Commerce')).getByText(/the top of the map/)).toBeInTheDocument()
  })

  it('has a tip for every field', () => {
    page('map')
    const labels = new Set<string>()
    document.querySelectorAll('.MuiFormControl-root').forEach((c) => {
      const found = fieldUnder(c.querySelector('input, [role="combobox"]') ?? c)
      if (found) labels.add(found.label)
    })
    expect(labels).toEqual(new Set(['Projects in the report', 'Filter the first column']))
    expect([...labels].filter((l) => !fieldHelp(l))).toEqual([])
  })
})
