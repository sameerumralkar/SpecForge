import { afterEach, describe, expect, it, vi } from 'vitest'
import { strFromU8, strToU8, unzipSync, zipSync } from 'fflate'
import { api } from '../api/client'
import type { DiagramsView } from '../api/types'

vi.mock('../lib/mermaid', () => ({
  renderMermaid: vi.fn(async (source: string) => {
    if (source.includes('broken')) throw new Error('Parse error on line 2')
    return `<svg>${source}</svg>`
  }),
}))
vi.mock('../lib/diagrams', () => ({
  svgToPng: vi.fn(async () => new Blob([new Uint8Array([0x89, 0x50, 0x4e, 0x47])])),
}))

const { buildDiagramsZip } = await import('../lib/diagramExport')

const view: DiagramsView = {
  problems: [],
  diagrams: [
    { id: 'overview', kind: 'OVERVIEW', title: 'Overview', mermaid: 'flowchart LR', plantuml: '@startuml\n@enduml' },
    { id: 'classes', kind: 'CLASS', title: 'Class diagram: everything', mermaid: 'classDiagram broken', plantuml: '@startuml\n@enduml' },
  ],
}

describe('diagrams zip', () => {
  afterEach(() => vi.restoreAllMocks())

  it('adds Mermaid SVG and PNG to the server zip and reports what Mermaid could not draw', async () => {
    const serverZip = zipSync({
      'diagrams/overview.mmd': strToU8('flowchart LR'),
      'diagrams/overview.puml': strToU8('@startuml'),
      'diagrams/overview.plantuml.svg': strToU8('<svg/>'),
    })
    vi.stubGlobal('fetch', vi.fn(async () => new Response(serverZip)))
    vi.spyOn(api, 'diagrams').mockResolvedValue(view)
    const progress: string[] = []

    const blob = await buildDiagramsZip('p', (done, total) => progress.push(`${done}/${total}`))
    const files = unzipSync(new Uint8Array(await blob.arrayBuffer()))

    expect(Object.keys(files)).toEqual([
      'diagrams/MERMAID-ERRORS.txt',
      'diagrams/overview.mermaid.png',
      'diagrams/overview.mermaid.svg',
      'diagrams/overview.mmd',
      'diagrams/overview.plantuml.svg',
      'diagrams/overview.puml',
    ])
    expect(strFromU8(files['diagrams/overview.mermaid.svg'])).toBe('<svg>flowchart LR</svg>')
    expect(files['diagrams/overview.mermaid.png'][1]).toBe(0x50)
    expect(strFromU8(files['diagrams/MERMAID-ERRORS.txt'])).toContain('classes: Parse error on line 2')
    expect(progress).toEqual(['1/2', '2/2'])
  })

  it('fails clearly when the server zip cannot be downloaded', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('nope', { status: 500 })))
    vi.spyOn(api, 'diagrams').mockResolvedValue(view)
    await expect(buildDiagramsZip('p')).rejects.toThrow('Could not download the diagrams (500)')
  })
})
