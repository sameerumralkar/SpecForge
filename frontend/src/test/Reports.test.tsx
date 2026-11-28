import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { ReportsPage } from '../pages/ReportsPage'
import { fieldUnder } from '../components/help/FieldHelpLayer'
import { fieldHelp, screenHelp } from '../lib/help'
import { csvCell, toCsv } from '../lib/csv'
import * as diagrams from '../lib/diagrams'
import type { EndpointsReport, FieldsReport, ObjectsReport, Project, ProjectsReport } from '../api/types'
import { renderWithProviders } from './render'

const projects = [{ id: 'p1', name: 'Shop' }, { id: 'p2', name: 'Payments' }] as Project[]

const projectsReport: ProjectsReport = {
  totals: { projects: 2, endpoints: 5, restEndpoints: 4, asyncEndpoints: 1, objects: 3, structures: 4, fields: 20, fieldDefinitions: 2, errorCodes: 6, capabilities: 3, rules: 7 },
  projects: [
    { id: 'p2', name: 'Payments', description: 'Card payments', version: '2.0.0', basePackage: 'com.acme.pay', defaultImplementationStyle: 'PROCESSING_ROUTE',
      createdAt: '2026-11-01T10:00:00Z', updatedAt: '2026-11-20T10:00:00Z', endpoints: 2, restEndpoints: 2, asyncEndpoints: 0, objects: 1, structures: 2,
      fields: 8, fieldDefinitions: 0, errorCodes: 4, capabilities: 0, rules: 3, mappedEndpoints: 0 },
    { id: 'p1', name: 'Shop', description: null, version: '1.0.0', basePackage: 'com.acme.shop', defaultImplementationStyle: 'IMPLEMENTATION_CLASS',
      createdAt: '2026-10-01T10:00:00Z', updatedAt: '2026-11-25T10:00:00Z', endpoints: 3, restEndpoints: 2, asyncEndpoints: 1, objects: 2, structures: 2,
      fields: 12, fieldDefinitions: 2, errorCodes: 2, capabilities: 3, rules: 4, mappedEndpoints: 2 },
  ],
}

const endpointsReport: EndpointsReport = {
  total: 2,
  perProject: [{ projectId: 'p1', projectName: 'Shop', total: 2, rest: 1, async: 1, withRules: 1, mapped: 1 }],
  endpoints: [
    { projectId: 'p1', projectName: 'Shop', id: 'e1', name: 'createOrder', kind: 'REST', method: 'POST', path: '/orders', operation: null, channel: null, protocol: null,
      summary: 'Place an order', tags: ['orders'], implementationStyle: 'IMPLEMENTATION_CLASS', requestPayload: 'Order', responses: ['201 Order', '400 Problem'],
      messagePayload: null, parameters: 0, rules: 3, security: ['BEARER_JWT'], capabilities: ['L2 Ordering'] },
    { projectId: 'p1', projectName: 'Shop', id: 'e2', name: 'orderCreated', kind: 'ASYNC', method: null, path: null, operation: 'SEND', channel: 'orders.created', protocol: 'KAFKA',
      summary: null, tags: [], implementationStyle: 'IMPLEMENTATION_CLASS', requestPayload: null, responses: [], messagePayload: 'Order', parameters: 0, rules: 0,
      security: [], capabilities: [] },
  ],
}

const objectsReport: ObjectsReport = {
  total: 3, objects: 2, structures: 1,
  payloads: [
    { projectId: 'p1', projectName: 'Shop', id: 'm', name: 'Money', role: 'OBJECT', description: null, fields: 2, requiredFields: 2, objectFields: 0, linkedObjects: [], usedBy: 1, usages: ['Order (field total)'] },
    { projectId: 'p1', projectName: 'Shop', id: 'a', name: 'Address', role: 'OBJECT', description: null, fields: 4, requiredFields: 1, objectFields: 0, linkedObjects: [], usedBy: 0, usages: [] },
    { projectId: 'p1', projectName: 'Shop', id: 'o', name: 'Order', role: 'STRUCTURE', description: null, fields: 3, requiredFields: 1, objectFields: 1, linkedObjects: ['Money'], usedBy: 2, usages: ['createOrder (request body)', 'orderCreated (message on orders.created)'] },
  ],
}

const fieldsReport: FieldsReport = {
  total: 3, linked: 1, linkedObjects: 1,
  perObject: [{ projectId: 'p1', projectName: 'Shop', objectId: 'm', objectName: 'Money', objectRole: 'OBJECT', fields: 1, heldBy: ['Order.total'] }],
  fields: [
    { projectId: 'p1', projectName: 'Shop', payloadId: 'o', payloadName: 'Order', payloadRole: 'STRUCTURE', position: 1, name: 'id', type: 'UUID', itemType: null, required: true,
      linkedObjectId: null, linkedObject: null, linkedObjectRole: null, constraints: '', description: null, inFieldInventory: true },
    { projectId: 'p1', projectName: 'Shop', payloadId: 'o', payloadName: 'Order', payloadRole: 'STRUCTURE', position: 2, name: 'total', type: 'OBJECT', itemType: null, required: true,
      linkedObjectId: 'm', linkedObject: 'Money', linkedObjectRole: 'OBJECT', constraints: '', description: null, inFieldInventory: false },
    { projectId: 'p1', projectName: 'Shop', payloadId: 'm', payloadName: 'Money', payloadRole: 'OBJECT', position: 2, name: 'currency', type: 'STRING', itemType: null, required: true,
      linkedObjectId: null, linkedObject: null, linkedObjectRole: null, constraints: 'length 3..3', description: null, inFieldInventory: true },
  ],
}

const page = (path = '/reports') => renderWithProviders(<Routes><Route path="/reports/:reportId?" element={<ReportsPage />} /></Routes>, [
  [['projects'], projects],
  [['reports', 'projects', 'all'], projectsReport],
  [['reports', 'endpoints', 'all'], endpointsReport],
  [['reports', 'objects', 'all'], objectsReport],
  [['reports', 'fields', 'all'], fieldsReport],
], path)

const names = (table: HTMLElement, column: number) =>
  within(table).getAllByRole('row').slice(1).map((r) => within(r).getAllByRole('cell')[column].textContent)

describe('Reports', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('1. counts the projects and sorts them by any column', async () => {
    const user = userEvent.setup()
    page()
    expect(screen.getByRole('list', { name: 'Project totals' })).toHaveTextContent('2Projects')
    expect(screen.getByRole('listitem', { name: 'Endpoints: 5' })).toBeInTheDocument()
    const table = screen.getByRole('table', { name: 'Projects' })
    expect(names(table, 0)).toEqual(['PaymentsCard payments', 'Shop'])
    await user.click(within(table).getByRole('button', { name: 'Endpoints' }))
    await user.click(within(table).getByRole('button', { name: 'Endpoints' }))
    expect(names(table, 0)[0]).toBe('Shop')
    expect(within(table).getByRole('link', { name: 'Shop' })).toHaveAttribute('href', '/projects/p1')
    expect(within(table).getByText('2 of 3')).toBeInTheDocument()
  })

  it('2. counts endpoints per project and filters the details by kind and words', async () => {
    const user = userEvent.setup()
    page('/reports/endpoints')
    expect(within(screen.getByRole('table', { name: 'Endpoints per project' })).getAllByRole('row')[1]).toHaveTextContent('Shop21111')
    const details = screen.getByRole('table', { name: 'Endpoint details' })
    expect(within(details).getByRole('link', { name: 'createOrder' })).toHaveAttribute('href', '/projects/p1/endpoints/e1')
    expect(within(details).getByText('not mapped')).toBeInTheDocument()
    await user.click(within(screen.getByRole('group', { name: 'Endpoint kind' })).getByRole('button', { name: 'Async' }))
    expect(names(details, 1)).toEqual(['orderCreated'])
    await user.click(within(screen.getByRole('group', { name: 'Endpoint kind' })).getByRole('button', { name: 'All' }))
    await user.type(screen.getByLabelText('Find in endpoint details'), 'orders.created')
    expect(names(details, 1)).toEqual(['orderCreated'])
    expect(screen.getByText('(1 of 2)')).toBeInTheDocument()
  })

  it('3. shows objects by default, structures on request, and what uses each', async () => {
    const user = userEvent.setup()
    page('/reports/objects')
    expect(screen.getByRole('listitem', { name: 'Objects not used: 1' })).toBeInTheDocument()
    const table = screen.getByRole('table', { name: 'Object details' })
    expect(names(table, 1)).toEqual(['Money', 'Address'])
    expect(within(table).getByText('not used')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Structures' }))
    expect(names(table, 1)).toEqual(['Order'])
    expect(within(table).getByText(/createOrder \(request body\)/)).toBeInTheDocument()
  })

  it('4. lists fields with their linked objects, and narrows to linked ones', async () => {
    const user = userEvent.setup()
    page('/reports/fields')
    expect(screen.getByRole('listitem', { name: 'Fields with a linked object: 1' })).toBeInTheDocument()
    expect(within(screen.getByRole('table', { name: 'Fields per linked object' })).getByText('Order.total')).toBeInTheDocument()
    const details = screen.getByRole('table', { name: 'Field details' })
    expect(within(details).getAllByRole('row')).toHaveLength(4)
    await user.click(screen.getByLabelText('Only fields with a linked object'))
    expect(within(details).getAllByRole('row')).toHaveLength(2)
    expect(within(details).getByRole('link', { name: 'Money' })).toHaveAttribute('href', '/projects/p1/payloads/m')
  })

  it('downloads what is shown as CSV', async () => {
    const user = userEvent.setup()
    const save = vi.spyOn(diagrams, 'saveBlob').mockImplementation(() => {})
    page('/reports/objects')
    const card = screen.getByRole('table', { name: 'Object details' }).closest('.MuiCard-root') as HTMLElement
    await user.click(within(card).getByRole('button', { name: 'CSV' }))
    const [blob, name] = save.mock.calls[0]
    expect(name).toBe('objects-all-projects.csv')
    const text = await (blob as Blob).text()
    expect(new Uint8Array(await (blob as Blob).arrayBuffer()).slice(0, 3)).toEqual(new Uint8Array([0xef, 0xbb, 0xbf]))
    expect(text.replace(/^\uFEFF/, '').split('\r\n')[0]).toBe('Project,Name,Role,Fields,Required,Fields holding objects,Objects it holds,Uses,Used by')
    expect(text).toContain('Shop,Money,object,2,2,0,,1,Order (field total)')
    expect(text).not.toContain('Order,structure')
  })

  it('quotes and defuses CSV cells', () => {
    expect(csvCell('a,b')).toBe('"a,b"')
    expect(csvCell('say "hi"')).toBe('"say ""hi"""')
    expect(csvCell('=SUM(A1)')).toBe("'=SUM(A1)")
    expect(csvCell(-3)).toBe('-3')
    expect(csvCell(['x', 'y'])).toBe('x; y')
    expect(toCsv(['a'], [[1], [null]])).toBe('a\r\n1\r\n\r\n')
  })

  it('explains the screen and every field on it', async () => {
    expect(screenHelp('reports')?.summary).toMatch(/endpoints of each project/)
    for (const report of ['', '/endpoints', '/objects', '/fields']) {
      const { unmount } = page(`/reports${report}`)
      const labels = new Set<string>()
      document.querySelectorAll('.MuiFormControl-root, .MuiToggleButtonGroup-root[aria-label], .MuiFormControlLabel-root').forEach((c) => {
        const found = fieldUnder(c.querySelector('input, [role="combobox"], button') ?? c)
        if (found) labels.add(found.label)
      })
      expect(labels.size).toBeGreaterThan(1)
      expect([...labels].filter((l) => !fieldHelp(l))).toEqual([])
      unmount()
    }
  })
})
