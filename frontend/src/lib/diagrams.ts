import type { Diagram } from '../api/types'

export type Notation = 'mermaid' | 'plantuml'

/** The diagram to show first: the open endpoint's sequence diagram, else the overview. */
export function defaultDiagram(diagrams: Diagram[], endpointName?: string): Diagram | undefined {
  return diagrams.find((d) => endpointName && d.endpointName === endpointName && d.kind === 'SEQUENCE')
    ?? diagrams.find((d) => endpointName && d.endpointName === endpointName)
    ?? diagrams.find((d) => d.kind === 'OVERVIEW')
    ?? diagrams[0]
}

/**
 * The diagram to show. {@code chosenFor} is the endpoint that was open when the diagram was
 * chosen. A choice of that endpoint's own diagram carries over when another endpoint opens:
 * having picked createOrder's class diagram, opening getOrder shows getOrder's. Any other
 * choice stays as it is.
 */
export function shownDiagram(diagrams: Diagram[], chosenId: string | null, chosenFor: string | undefined, endpointName?: string): Diagram | undefined {
  const chosen = diagrams.find((d) => d.id === chosenId)
  if (chosen?.endpointName && chosen.endpointName === chosenFor && endpointName && chosenFor !== endpointName) {
    return diagrams.find((d) => d.endpointName === endpointName && d.kind === chosen.kind) ?? defaultDiagram(diagrams, endpointName)
  }
  return chosen ?? defaultDiagram(diagrams, endpointName)
}

/** Diagram choices in groups: the open endpoint's, the project's, then each endpoint's. */
export function diagramGroups(diagrams: Diagram[], endpointName?: string): { label: string | null; items: Diagram[] }[] {
  const own = endpointName ? diagrams.filter((d) => d.endpointName === endpointName) : []
  const project = diagrams.filter((d) => !d.endpointName)
  const others = diagrams.filter((d) => d.endpointName && d.endpointName !== endpointName)
  return [
    { label: own.length ? `This endpoint: ${endpointName}` : null, items: own },
    { label: own.length ? 'Project' : null, items: project },
    { label: 'Sequence diagrams', items: others.filter((d) => d.kind === 'SEQUENCE') },
    { label: 'Endpoint class diagrams', items: others.filter((d) => d.kind === 'CLASS') },
  ].filter((g) => g.items.length > 0)
}

/** A short hash of a text, to make image URLs change when the diagram does. */
export function hash(text: string): string {
  let h = 0
  for (let i = 0; i < text.length; i++) h = (Math.imul(31, h) + text.charCodeAt(i)) | 0
  return (h >>> 0).toString(36)
}

/** Saves a blob as a file. */
export function saveBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** Draws an SVG onto a canvas (at 2× for sharpness) and returns it as PNG. */
export async function svgToPng(svg: string): Promise<Blob> {
  const doc = new DOMParser().parseFromString(svg, 'image/svg+xml').documentElement
  const viewBox = doc.getAttribute('viewBox')?.split(/[\s,]+/).map(Number)
  const width = viewBox?.[2] ?? Number.parseFloat(doc.getAttribute('width') ?? '800')
  const height = viewBox?.[3] ?? Number.parseFloat(doc.getAttribute('height') ?? '600')
  doc.setAttribute('width', String(width))
  doc.setAttribute('height', String(height))
  const source = new XMLSerializer().serializeToString(doc)
  const image = new Image()
  image.src = URL.createObjectURL(new Blob([source], { type: 'image/svg+xml' }))
  await image.decode()
  const canvas = document.createElement('canvas')
  canvas.width = Math.ceil(width * 2)
  canvas.height = Math.ceil(height * 2)
  const ctx = canvas.getContext('2d')!
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  ctx.scale(2, 2)
  ctx.drawImage(image, 0, 0, width, height)
  URL.revokeObjectURL(image.src)
  return new Promise((resolve, reject) => canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('PNG export failed'))), 'image/png'))
}
