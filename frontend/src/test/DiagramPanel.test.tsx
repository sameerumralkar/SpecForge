import { useState } from 'react'
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
    { id: 'classes-endpoint-createOrder', kind: 'CLASS', title: 'Class diagram: createOrder', endpointName: 'createOrder',
      mermaid: 'classDiagram\n  class OrdersController', plantuml: '@startuml\nclass OrdersController\n@enduml' },
    { id: 'sequence-getOrder', kind: 'SEQUENCE', title: 'getOrder (GET /orders/{id})', endpointName: 'getOrder',
      mermaid: 'sequenceDiagram\n  Client->>Svc: GET', plantuml: '@startuml\nClient -> Svc : GET\n@enduml' },
    { id: 'classes-endpoint-getOrder', kind: 'CLASS', title: 'Class diagram: getOrder', endpointName: 'getOrder',
      mermaid: 'classDiagram\n  class OrderEntityStore', plantuml: '@startuml\nclass OrderEntityStore\n@enduml' },
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

  it("offers the endpoint's sequence and class diagrams, and keeps the choice when another endpoint opens", async () => {
    const user = userEvent.setup()
    function Harness() {
      const [endpoint, setEndpoint] = useState('createOrder')
      return <><button onClick={() => setEndpoint('getOrder')}>Open getOrder</button><DiagramPanel projectId="p" focusEndpoint={endpoint} /></>
    }
    renderWithProviders(<Harness />, [[['projects', 'p', 'diagrams'], view]])
    await user.click(await screen.findByRole('button', { name: 'Classes' }))
    expect(await screen.findByLabelText('Mermaid diagram')).toHaveTextContent('class OrdersController')

    await user.click(screen.getByRole('combobox', { name: 'Diagram' }))
    const groups = screen.getAllByRole('listbox')[0].querySelectorAll('li.MuiListSubheader-root')
    expect([...groups].map((g) => g.textContent)).toEqual(['This endpoint: createOrder', 'Project', 'Sequence diagrams', 'Endpoint class diagrams'])
    await user.keyboard('{Escape}')

    await user.click(screen.getByRole('button', { name: 'Open getOrder' }))
    expect(await screen.findByLabelText('Mermaid diagram')).toHaveTextContent('class OrderEntityStore')
    expect(screen.getByRole('button', { name: 'Classes' })).toHaveAttribute('aria-pressed', 'true')
    await user.click(screen.getByRole('button', { name: 'Sequence' }))
    expect(screen.getByLabelText('Mermaid diagram')).toHaveTextContent('Client->>Svc: GET')

    // Picking another endpoint's diagram from the list shows that one.
    await user.click(screen.getByRole('combobox', { name: 'Diagram' }))
    await user.click(within(screen.getAllByRole('listbox')[0]).getByRole('option', { name: 'Class diagram: createOrder' }))
    expect(screen.getByLabelText('Mermaid diagram')).toHaveTextContent('class OrdersController')
    expect(screen.getByRole('button', { name: 'Classes' })).toHaveAttribute('aria-pressed', 'false')
  })

  it("on an endpoint's page, offers only that endpoint's diagrams", async () => {
    const user = userEvent.setup()
    renderWithProviders(<DiagramPanel projectId="p" focusEndpoint="getOrder" endpointOnly />, [[['projects', 'p', 'diagrams'], view]])
    expect(await screen.findByLabelText('Mermaid diagram')).toHaveTextContent('Client->>Svc: GET')
    expect(screen.queryByRole('combobox', { name: 'Diagram' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Classes' }))
    expect(screen.getByLabelText('Mermaid diagram')).toHaveTextContent('class OrderEntityStore')
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
