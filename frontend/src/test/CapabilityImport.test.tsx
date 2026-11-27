import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { CapabilityImportPage } from '../pages/CapabilityImportPage'
import { CapabilityImportDialog } from '../components/capabilities/CapabilityImportDialog'
import { fieldUnder } from '../components/help/FieldHelpLayer'
import { fieldHelp, screenHelp } from '../lib/help'
import { api } from '../api/client'
import type { Capability, CapabilityImport, ImportNodeView } from '../api/types'
import { renderWithProviders } from './render'

const cap = (id: string, parentId: string | null, level: number, name: string, path: string[]): Capability => ({
  id, projectId: 'p', parentId, level, levelName: 'x', position: 0, name, code: null, description: null, path, endpointIds: [], apiCount: 0, childCount: 0,
})
const existing = [cap('c1', null, 1, 'Payments', ['Payments']), cap('c2', 'c1', 2, 'Card payments', ['Payments', 'Card payments'])]
const node = (key: string, parentKey: string | null, level: number, name: string, extra: Partial<ImportNodeView> = {}): ImportNodeView => ({
  key, parentKey, level, path: [name], name, code: null, description: null, apis: [], resolution: 'CREATE', mergeTargetId: null,
  mergeTargetPath: null, sourceRow: 2, findings: [], ...extra,
})
const draft: CapabilityImport = {
  id: 'i1', projectId: 'p', fileName: 'map.xlsx', format: 'EXCEL', sheet: 'Capabilities', status: 'DRAFT', targetParentId: null, targetParentPath: null,
  warnings: ['Columns not used: Owner.'], createdAt: '2026-11-27T01:00:00Z', updatedAt: '2026-11-27T01:00:00Z', result: null,
  summary: { total: 3, create: 2, merge: 1, skip: 0, matches: 1, duplicates: 1, overlaps: 0, errors: 1 },
  nodes: [
    node('n1', null, 1, 'Payments', { resolution: 'MERGE', mergeTargetId: 'c1', mergeTargetPath: 'L1 Payments' }),
    node('n2', 'n1', 2, 'Cards payment', { findings: [{ type: 'SIMILAR_NAME', severity: 'DUPLICATE', message: 'Possible duplicate of L2 Payments › Card payments (92% alike)',
      existingId: 'c2', existingPath: 'Payments › Card payments', existingLevel: 2, otherKey: null, score: 92 }] }),
    node('n3', 'n1', 2, 'Wallets', { code: 'PAY.CARD', findings: [{ type: 'SAME_CODE', severity: 'ERROR', message: 'Code PAY.CARD is already used by L2 Card payments: merge into it, change the code or clear it',
      existingId: 'c2', existingPath: 'Payments › Card payments', existingLevel: 2, otherKey: null, score: 100 }] }),
  ],
}

const page = () => renderWithProviders(<Routes><Route path="/projects/:projectId/capabilities/imports/:importId" element={<CapabilityImportPage />} /></Routes>, [
  [['projects', 'p', 'capabilities', 'imports', 'i1'], draft], [['projects', 'p', 'capabilities'], existing], [['projects', 'p', 'endpoints'], []],
], '/projects/p/capabilities/imports/i1')

describe('Capability import', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('reads the chosen file into a draft and opens it', async () => {
    const upload = vi.spyOn(api, 'uploadCapabilityImport').mockResolvedValue(draft)
    vi.spyOn(api, 'capabilityImport').mockResolvedValue(draft)
    renderWithProviders(<Routes>
      <Route path="/projects/:projectId/capabilities" element={<CapabilityImportDialog projectId="p" open onClose={() => {}} />} />
      <Route path="/projects/:projectId/capabilities/imports/:importId" element={<p>review page</p>} />
    </Routes>, [], '/projects/p/capabilities')
    expect(screen.getByRole('table', { name: 'Layouts' })).toHaveTextContent('L1, L2 … L8')
    const file = new File(['L1,L2\nPayments,Cards\n'], 'map.csv', { type: 'text/csv' })
    fireEvent.change(screen.getByLabelText('Capability file'), { target: { files: [file] } })
    await waitFor(() => expect(upload).toHaveBeenCalledWith('p', 'map.csv', btoa('L1,L2\nPayments,Cards\n')))
    expect(await screen.findByText('review page')).toBeInTheDocument()
  })

  it('shows the draft beside the map, with duplicates and blocking problems', async () => {
    const user = userEvent.setup()
    page()
    expect(screen.getByLabelText('Import summary')).toHaveTextContent('3 capabilities')
    expect(screen.getByLabelText('Import summary')).toHaveTextContent('1 possible duplicates')
    expect(screen.getByRole('button', { name: 'Apply import' })).toBeDisabled()
    const table = screen.getByRole('table', { name: 'Imported capabilities' })
    expect(within(table).getByText(/Possible duplicate of L2 Payments › Card payments/)).toBeInTheDocument()
    expect(within(table).getAllByText('blocks applying')).toHaveLength(1)

    // Selecting a row highlights what it was compared with.
    await user.click(screen.getByRole('row', { name: 'Imported Cards payment' }))
    const map = screen.getByRole('list', { name: 'Existing capabilities' })
    expect(within(map).getByText('Card payments').closest('li')).toHaveAttribute('aria-current', 'true')

    await user.click(screen.getByRole('button', { name: 'Duplicates' }))
    expect(within(table).queryByRole('row', { name: 'Imported Wallets' })).not.toBeInTheDocument()
    expect(within(table).getByRole('row', { name: 'Imported Cards payment' })).toBeInTheDocument()
  })

  it('merges, renames and skips, saving the draft each time', async () => {
    const user = userEvent.setup()
    const update = vi.spyOn(api, 'updateCapabilityImport').mockResolvedValue(draft)
    page()
    await user.click(within(screen.getByRole('row', { name: 'Imported Cards payment' })).getByRole('button', { name: 'Merge' }))
    await waitFor(() => expect(update).toHaveBeenLastCalledWith('p', 'i1', null, expect.arrayContaining([
      expect.objectContaining({ key: 'n2', resolution: 'MERGE', mergeTargetId: 'c2' })])))

    const name = screen.getByLabelText('Imported name 3')
    await user.clear(name)
    await user.type(name, 'Digital wallets{Enter}')
    await waitFor(() => expect(update).toHaveBeenLastCalledWith('p', 'i1', null, expect.arrayContaining([
      expect.objectContaining({ key: 'n3', name: 'Digital wallets' })])))

    await user.click(screen.getByRole('combobox', { name: 'Resolution 3' }))
    await user.click(screen.getByRole('option', { name: 'Skip (and what is below)' }))
    await waitFor(() => expect(update).toHaveBeenLastCalledWith('p', 'i1', null, expect.arrayContaining([
      expect.objectContaining({ key: 'n3', resolution: 'SKIP', mergeTargetId: null })])))
  })

  it('applies a draft without blocking problems and reports what it did', async () => {
    const user = userEvent.setup()
    const clean = { ...draft, summary: { ...draft.summary, errors: 0 }, nodes: draft.nodes.slice(0, 2) }
    const applied: CapabilityImport = { ...clean, status: 'APPLIED', result: { created: 1, merged: 1, skipped: 0, apisLinked: 0 } }
    const apply = vi.spyOn(api, 'applyCapabilityImport').mockResolvedValue(applied)
    renderWithProviders(<Routes><Route path="/projects/:projectId/capabilities/imports/:importId" element={<CapabilityImportPage />} /></Routes>, [
      [['projects', 'p', 'capabilities', 'imports', 'i1'], clean], [['projects', 'p', 'capabilities'], existing], [['projects', 'p', 'endpoints'], []],
    ], '/projects/p/capabilities/imports/i1')
    await user.click(screen.getByRole('button', { name: 'Apply import' }))
    await user.click(screen.getByRole('button', { name: 'Apply' }))
    await waitFor(() => expect(apply).toHaveBeenCalledWith('p', 'i1'))
    expect(await screen.findByText(/Created 1, merged 1, skipped 0/)).toBeInTheDocument()
  })

  it('explains the import screen and every field on it', () => {
    expect(screenHelp('capability-import')?.summary).toMatch(/duplicates and overlaps/)
    page()
    const labels = new Set<string>()
    document.querySelectorAll('.MuiFormControl-root, .MuiToggleButtonGroup-root[aria-label]').forEach((c) => {
      const found = fieldUnder(c.querySelector('input, textarea, [role="combobox"], button') ?? c)
      if (found) labels.add(found.label)
    })
    expect(labels.size).toBeGreaterThan(8)
    expect([...labels].filter((l) => !fieldHelp(l))).toEqual([])
  })
})
