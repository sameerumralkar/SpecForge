import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { CapabilitiesPage } from '../pages/CapabilitiesPage'
import { EndpointCapabilities } from '../components/capabilities/EndpointCapabilities'
import { api } from '../api/client'
import type { Capability, CapabilityApi, Endpoint } from '../api/types'
import { renderWithProviders } from './render'

const LEVELS = ['Business domain', 'Capability area', 'Capability', 'Sub-capability', 'Function', 'Process', 'Activity', 'Task']
const cap = (id: string, parentId: string | null, level: number, name: string, endpointIds: string[], apiCount: number, childCount: number, position = 0): Capability => ({
  id, projectId: 'p', parentId, level, levelName: LEVELS[level - 1], position, name, code: null, description: null,
  path: [name], endpointIds, apiCount, childCount,
})
const payments = cap('c1', null, 1, 'Payments', ['e3'], 3, 1)
const cards = cap('c2', 'c1', 2, 'Card payments', [], 2, 2)
const capture = cap('c3', 'c2', 3, 'Capture', ['e1'], 1, 0)
const refunds = cap('c4', 'c2', 3, 'Refunds', ['e2'], 1, 0, 1)
const all = [payments, cards, capture, refunds]
const endpoint = (id: string, name: string, path: string): Endpoint =>
  ({ id, projectId: 'p', kind: 'REST', name, httpMethod: 'POST', path, tags: [], parameters: [], responses: [], security: [] }) as unknown as Endpoint
const endpoints = [endpoint('e1', 'pay', '/pay'), endpoint('e2', 'refund', '/refunds'), endpoint('e3', 'status', '/status'), endpoint('e4', 'ping', '/ping')]
const inventory: CapabilityApi[] = [
  { endpointId: 'e1', name: 'pay', kind: 'REST', httpMethod: 'POST', path: '/pay', channel: null, operation: null, summary: null, direct: false,
    via: [{ capabilityId: 'c3', name: 'Capture', level: 3 }] },
  { endpointId: 'e3', name: 'status', kind: 'REST', httpMethod: 'POST', path: '/status', channel: null, operation: null, summary: null, direct: true, via: [] },
]

const page = (path = '/projects/p/capabilities') => renderWithProviders(
  <Routes><Route path="/projects/:projectId/capabilities" element={<CapabilitiesPage />} /></Routes>, [
    [['projects', 'p', 'capabilities'], all], [['projects', 'p', 'capabilities', 'levels'], { levels: LEVELS }],
    [['projects', 'p', 'endpoints'], endpoints], [['projects', 'p', 'capabilities', 'c1', 'apis'], inventory],
  ], path)

describe('Capabilities', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(api, 'capabilities').mockResolvedValue(all)
  })

  it('shows the map and an overview of levels and APIs not mapped yet', () => {
    page()
    const map = screen.getByRole('list', { name: 'Capability map' })
    expect(within(map).getByRole('button', { name: 'Refunds, L3' })).toBeInTheDocument()
    expect(screen.getByText('4 capabilities · 3 of 4 APIs mapped')).toBeInTheDocument()
    expect(within(screen.getByRole('table', { name: 'Levels' })).getByText('Sub-capability')).toBeInTheDocument()
    expect(within(screen.getByLabelText('APIs not mapped')).getByText('ping')).toBeInTheDocument()
  })

  it('shows the API inventory at a level, rolled up from below', async () => {
    const user = userEvent.setup()
    page('/projects/p/capabilities?c=c1')
    const table = screen.getByRole('table', { name: 'API inventory' })
    expect(screen.getByText('API inventory at L1 (2)')).toBeInTheDocument()
    expect(within(table).getByText('this capability')).toBeInTheDocument()
    expect(within(table).getByText('L3 Capture')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Add L2 capability' })).toBeInTheDocument()

    // Collapsing hides what is below.
    await user.click(screen.getByRole('button', { name: 'Collapse Payments' }))
    expect(screen.queryByRole('button', { name: 'Refunds, L3' })).not.toBeInTheDocument()
  })

  it('creates a capability below the selected one, at the next level', async () => {
    const user = userEvent.setup()
    const create = vi.spyOn(api, 'createCapability').mockResolvedValue(cap('c5', 'c3', 4, 'Authorise', [], 0, 0))
    page('/projects/p/capabilities?c=c3')
    await user.click(screen.getByRole('button', { name: 'Add L4 capability' }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText('It will be an L4 capability')).toBeInTheDocument()
    await user.type(within(dialog).getByLabelText(/^Name/), 'Authorise')
    await user.type(within(dialog).getByLabelText('Code (optional)'), 'PAY.01.01.01')
    await user.click(within(dialog).getByRole('button', { name: 'Create' }))
    await waitFor(() => expect(create).toHaveBeenCalledWith('p', { name: 'Authorise', code: 'PAY.01.01.01', description: null, parentId: 'c3' }))
  })

  it('links APIs to the selected capability', async () => {
    const user = userEvent.setup()
    const link = vi.spyOn(api, 'setCapabilityApis').mockResolvedValue(capture)
    vi.spyOn(api, 'capabilityApis').mockResolvedValue([])
    page('/projects/p/capabilities?c=c3')
    await user.click(screen.getByRole('button', { name: 'Link APIs…' }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByRole('checkbox', { name: 'pay' })).toBeChecked()
    await user.click(within(dialog).getByRole('checkbox', { name: 'ping' }))
    await user.click(within(dialog).getByRole('button', { name: 'Save links' }))
    await waitFor(() => expect(link).toHaveBeenCalledWith('p', 'c3', ['e1', 'e4']))
  })

  it('links an API to capabilities from its Definition tab', async () => {
    const user = userEvent.setup()
    const set = vi.spyOn(api, 'setEndpointCapabilities').mockResolvedValue([capture])
    renderWithProviders(<Routes><Route path="/projects/:projectId" element={<EndpointCapabilities projectId="p" endpointId="e1" />} /></Routes>,
      [[['projects', 'p', 'capabilities'], all]], '/projects/p')
    const field = screen.getByRole('combobox', { name: 'Linked capabilities' })
    expect(screen.getByText('L3 Capture')).toBeInTheDocument()
    await user.click(field)
    await user.click(screen.getByRole('option', { name: /Refunds/ }))
    await waitFor(() => expect(set).toHaveBeenCalledWith('p', 'e1', ['c3', 'c4']))
    expect(await screen.findByText('Saved.')).toBeInTheDocument()
  })
})
