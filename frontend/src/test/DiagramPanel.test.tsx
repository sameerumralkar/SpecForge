import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DiagramPanel } from '../components/DiagramPanel'
import { api } from '../api/client'
import type { DiagramsView } from '../api/types'
import { defaultDiagram, hash } from '../lib/diagrams'
import { renderWithProviders } from './render'

// Mermaid needs a real browser; show its source instead.
vi.mock('../components/MermaidView', async () => {
  const { createElement } = await import('react')
  return { default: ({ source }: { source: string }) => createElement('pre', { 'aria-label': 'Mermaid diagram' }, source) }
})

const view: DiagramsView = {
  problems: [],
  diagrams: [
    { id: 'overview', kind: 'OVERVIEW', title: 'Overview', mermaid: 'flowchart LR\n  svc["Shop"]', plantuml: '@startuml\ncomponent Shop\n@enduml' },
    { id: 'classes', kind: 'CLASS', title: 'Class diagram: everything', mermaid: 'classDiagram\n  class Order', plantuml: '@startuml\nclass Order\n@enduml' },
    { id: 'sequence-createOrder', kind: 'SEQUENCE', title: 'createOrder (POST /orders)', endpointName: 'createOrder',
      mermaid: 'sequenceDiagram\n  Client->>Svc: POST', plantuml: '@startuml\nClient -> Svc : POST\n@enduml' },
  ],
}

const setup = (focusEndpoint?: string) =>
  renderWithProviders(<DiagramPanel projectId="p" focusEndpoint={focusEndpoint} />, [[['projects', 'p', 'diagrams'], view]])

describe('DiagramPanel', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
    vi.spyOn(api, 'diagrams').mockResolvedValue(view)
  })

  it('opens the sequence diagram of the endpoint being edited, drawn with Mermaid', async () => {
    setup('createOrder')
    expect(await screen.findByLabelText('Mermaid diagram')).toHaveTextContent('sequenceDiagram Client->>Svc: POST')
    expect(screen.getByRole('combobox', { name: 'Diagram' })).toHaveTextContent('createOrder (POST /orders)')
  })

  it('switches to PlantUML, rendered by the server, and remembers the choice', async () => {
    const user = userEvent.setup()
    setup()
    await user.click(screen.getByRole('button', { name: 'PlantUML' }))
    const image = screen.getByRole('img', { name: 'Overview (PlantUML)' })
    expect(image).toHaveAttribute('src', `/api/projects/p/diagrams/overview.svg?download=false&v=${hash(view.diagrams[0].plantuml)}`)
    expect(localStorage.getItem('diagram-notation')).toBe('plantuml')
  })

  it('shows the source and offers exports in the chosen notation', async () => {
    const user = userEvent.setup()
    setup()
    await user.click(screen.getByRole('combobox', { name: 'Diagram' }))
    await user.click(within(screen.getByRole('listbox')).getByRole('option', { name: 'Class diagram: everything' }))
    await user.click(screen.getByRole('button', { name: 'Source' }))
    expect(await screen.findByLabelText('Code editor')).toHaveValue('classDiagram\n  class Order')
    await user.click(screen.getByRole('button', { name: 'Export diagram' }))
    expect(screen.getAllByRole('menuitem').map((m) => m.textContent)).toEqual(['.mmd (Mermaid source)', '.svg', '.png'])
  })

  it('zooms in and back to fit', async () => {
    const user = userEvent.setup()
    setup()
    expect(screen.getByText('Fit')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Zoom in' }))
    expect(screen.getByText('125%')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Fit to width' }))
    expect(screen.getByText('Fit')).toBeInTheDocument()
  })

  it('picks a sensible first diagram', () => {
    expect(defaultDiagram(view.diagrams, 'createOrder')?.id).toBe('sequence-createOrder')
    expect(defaultDiagram(view.diagrams, 'unknown')?.id).toBe('overview')
    expect(hash('a')).not.toBe(hash('b'))
  })
})
