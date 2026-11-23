import { useState } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ImportDialog } from '../components/ImportDialog'
import { CodePanel } from '../components/CodePanel'
import { EndpointDefinition } from '../editors/EndpointDefinition'
import { api } from '../api/client'
import type { CodeView, EndpointRequest, GenerationDiff, ImportResult, Project } from '../api/types'
import { renderWithProviders } from './render'

const project = { id: 'new', name: 'Imported', endpointCount: 2, payloadCount: 3 } as Project
const exportFile = JSON.stringify({ format: 'spec-builder/project', version: 1, project: { name: 'Shop' }, payloads: [], endpoints: [] })

function upload(files: File[]) {
  fireEvent.change(screen.getByLabelText('Files to import'), { target: { files } })
}

describe('ImportDialog', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('imports a project export and opens it', async () => {
    const user = userEvent.setup()
    const importProject = vi.spyOn(api, 'importProject').mockResolvedValue({ project, warnings: [] })
    const onImported = vi.fn()
    renderWithProviders(<ImportDialog open onClose={() => {}} onImported={onImported} />)

    upload([new File([exportFile], 'shop.specbuilder.json', { type: 'application/json' })])
    expect(await screen.findByText('shop.specbuilder.json · project export')).toBeInTheDocument()
    expect(screen.queryByLabelText('Base package (optional)')).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('Project name (optional)'), 'Shop 2')
    await user.click(screen.getByRole('button', { name: 'Import' }))

    expect(importProject).toHaveBeenCalledWith(JSON.parse(exportFile), 'Shop 2')
    await waitFor(() => expect(onImported).toHaveBeenCalledWith('new'))
  })

  it('imports API specs together and shows what could not be imported exactly', async () => {
    const user = userEvent.setup()
    const result: ImportResult = { project, warnings: ['cookie parameter skipped', 'oneOf imported as its first option'] }
    const importSpec = vi.spyOn(api, 'importSpec').mockResolvedValue(result)
    const onImported = vi.fn()
    renderWithProviders(<ImportDialog open onClose={() => {}} onImported={onImported} />)

    upload([new File(['openapi: 3.1.0'], 'api.yaml'), new File(['asyncapi: 3.0.0'], 'events.yaml')])
    expect(await screen.findByText('events.yaml · API spec')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Base package (optional)'), 'org.shop')
    await user.click(screen.getByRole('button', { name: 'Import' }))

    expect(importSpec).toHaveBeenCalledWith(['openapi: 3.1.0', 'asyncapi: 3.0.0'], undefined, 'org.shop')
    const warnings = await screen.findByLabelText('Import warnings')
    expect(within(warnings).getAllByRole('listitem').map((li) => li.textContent)).toEqual(result.warnings)
    await user.click(screen.getByRole('button', { name: 'Open project' }))
    expect(onImported).toHaveBeenCalledWith('new')
  })

  it('refuses to mix a project export with other files', async () => {
    renderWithProviders(<ImportDialog open onClose={() => {}} onImported={() => {}} />)
    upload([new File([exportFile], 'a.json'), new File(['openapi: 3.1.0'], 'b.yaml')])
    expect(await screen.findByText('A project export has to be imported on its own.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Import' })).toBeDisabled()
  })
})

describe('comparing generated code with a snapshot', () => {
  const code: CodeView = {
    target: 'Spring Boot 4', ready: true, endpointFiles: {}, problems: [],
    files: [
      { path: 'README.md', language: 'markdown', content: '# Shop' },
      { path: 'src/A.java', language: 'java', content: 'class A { int x; }' },
      { path: 'src/New.java', language: 'java', content: 'class New {}' },
    ],
  }
  const diff: GenerationDiff = {
    baseline: { id: 's1', kind: 'CODE', reason: 'DOWNLOAD', createdAt: '2026-11-22T10:00:00Z', fileCount: 3 },
    files: [
      { path: 'README.md', status: 'UNCHANGED' },
      { path: 'src/A.java', status: 'CHANGED', before: 'class A { }', after: 'class A { int x; }' },
      { path: 'src/New.java', status: 'ADDED', after: 'class New {}' },
      { path: 'src/Old.java', status: 'REMOVED', before: 'class Old {}' },
    ],
  }

  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(api, 'code').mockResolvedValue(code)
    vi.spyOn(api, 'snapshots').mockResolvedValue([diff.baseline])
    vi.spyOn(api, 'diff').mockResolvedValue(diff)
  })

  it('lists only the changed files, marks them, and shows a diff', async () => {
    const user = userEvent.setup()
    renderWithProviders(<CodePanel projectId="p" />, [[['projects', 'p', 'code'], code]])
    await user.click(await screen.findByRole('combobox', { name: 'Compare with' }))
    await user.click(await screen.findByRole('option', { name: /Compare with Downloaded/ }))

    expect(await screen.findByText('1 changed, 1 added, 1 removed')).toBeInTheDocument()
    const tree = screen.getByLabelText('Generated files')
    expect(within(tree).queryByText('README.md')).not.toBeInTheDocument()
    expect(within(tree).getByLabelText('changed')).toHaveTextContent('M')
    expect(within(tree).getByLabelText('removed')).toHaveTextContent('D')

    expect(await screen.findByLabelText('Diff')).toHaveTextContent('class A { }class A { int x; }')
    await user.click(within(tree).getByText('Old.java'))
    expect(await screen.findByLabelText('Code editor')).toHaveValue('class Old {}')
    expect(screen.getByText('src/Old.java (removed)')).toBeInTheDocument()

    await user.click(screen.getByRole('checkbox', { name: 'Changed files only' }))
    expect(within(screen.getByLabelText('Generated files')).getByText('README.md')).toBeInTheDocument()
  })

  it('saves a snapshot on request', async () => {
    const user = userEvent.setup()
    const take = vi.spyOn(api, 'takeSnapshot').mockResolvedValue(diff.baseline)
    renderWithProviders(<CodePanel projectId="p" />, [[['projects', 'p', 'code'], code]])
    await user.click(await screen.findByRole('button', { name: 'Save snapshot' }))
    expect(take).toHaveBeenCalledWith('p', 'CODE')
  })
})

describe('switching an endpoint between REST and async', () => {
  it('turns the body into the message and keeps only headers', async () => {
    const user = userEvent.setup()
    let latest: EndpointRequest | undefined
    const rest: EndpointRequest = {
      kind: 'REST', name: 'orderPlaced', tags: ['Orders'], httpMethod: 'POST', path: '/orders/{id}', requestPayloadId: 'order',
      parameters: [{ name: 'id', location: 'PATH', type: 'UUID', required: true }, { name: 'X-Trace', location: 'HEADER', type: 'STRING', required: false }],
      responses: [{ statusCode: '201' }], security: [],
    }
    function Harness() {
      const [value, setValue] = useState(rest)
      return <EndpointDefinition value={value} onChange={(v) => { setValue(v); latest = v }} allTags={[]} error={null} />
    }
    renderWithProviders(<Harness />)
    await user.click(screen.getByRole('combobox', { name: 'Kind' }))
    await user.click(screen.getByRole('option', { name: 'Async (AsyncAPI)' }))

    expect(latest).toMatchObject({ kind: 'ASYNC', operation: 'RECEIVE', protocol: 'KAFKA', channel: 'order.placed', messagePayloadId: 'order', tags: ['Orders'] })
    expect(latest!.parameters.map((p) => p.name)).toEqual(['X-Trace'])
    expect(screen.getByLabelText('Channel / topic')).toHaveValue('order.placed')
  })
})
