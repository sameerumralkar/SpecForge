import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CodePanel } from '../components/CodePanel'
import { api } from '../api/client'
import type { CodeView } from '../api/types'
import { codeFocusPattern, fileTree } from '../lib/fileTree'
import { renderWithProviders } from './render'

const base = 'src/main/java/com/example/orders'
const code: CodeView = {
  target: 'Spring Boot 4 + Apache Camel 4',
  ready: true,
  files: [
    { path: 'README.md', language: 'markdown', content: '# Orders' },
    { path: 'pom.xml', language: 'xml', content: '<project/>' },
    { path: `${base}/service/OrdersServiceImpl.java`, language: 'java', content: 'class OrdersServiceImpl {}' },
    { path: `${base}/route/PaidRoute.java`, language: 'java', content: 'class PaidRoute {}' },
  ],
  endpointFiles: { createOrder: `${base}/service/OrdersServiceImpl.java` },
  problems: [],
}

const setup = (view: CodeView, focusEndpoint?: string) =>
  renderWithProviders(<CodePanel projectId="p" focusEndpoint={focusEndpoint} />, [[['projects', 'p', 'code'], view]])

describe('CodePanel', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(api, 'code').mockResolvedValue(code)
  })

  it('opens the file of the endpoint being edited, and the README otherwise', async () => {
    setup(code, 'createOrder')
    expect(await screen.findByLabelText('Code editor')).toHaveValue('class OrdersServiceImpl {}')
    expect(screen.getByText(`${base}/service/OrdersServiceImpl.java`)).toBeInTheDocument()
  })

  it('shows a folder tree and opens files from it', async () => {
    const user = userEvent.setup()
    setup(code)
    expect(await screen.findByLabelText('Code editor')).toHaveValue('# Orders')
    const tree = screen.getByLabelText('Generated files')
    await user.click(within(tree).getByText('src/main/java/com/example/orders'))
    await user.click(within(tree).getByText('route'))
    await user.click(within(tree).getByText('PaidRoute.java'))
    expect(screen.getByLabelText('Code editor')).toHaveValue('class PaidRoute {}')
    expect(screen.getByRole('link', { name: 'Download .zip' })).toHaveAttribute('href', '/api/projects/p/generate/code.zip')
  })

  it('lists blocking problems and disables the download', () => {
    setup({ ...code, ready: false, problems: [{ severity: 'ERROR', source: 'rules', location: 'createOrder / Check', message: "'nope' is not a field" }] })
    expect(screen.getByLabelText('Generation problems')).toHaveTextContent("createOrder / Check: 'nope' is not a field")
    const download = screen.getByText('Download .zip').closest('a')
    expect(download).toHaveAttribute('aria-disabled', 'true')
    expect(download).not.toHaveAttribute('href')
  })

  it('builds a compact tree and focus patterns', () => {
    const tree = fileTree(code.files.map((f) => f.path))
    expect(tree.map((n) => n.name)).toEqual(['src/main/java/com/example/orders', 'pom.xml', 'README.md'])
    expect(tree[0].children.map((n) => n.name)).toEqual(['route', 'service'])
    expect(new RegExp(codeFocusPattern(`${base}/route/PaidRoute.java`, 'paid')).test('            .routeId("paid")')).toBe(true)
    expect(new RegExp(codeFocusPattern(`${base}/service/X.java`, 'createOrder'), 'm').test('    public Order createOrder(CreateOrderRequest r) {')).toBe(true)
  })
})
