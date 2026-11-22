import type { Mermaid } from 'mermaid'

let instance: Promise<Mermaid> | null = null
let counter = 0

/** Loads Mermaid once. SVG text labels (not HTML), so exported PNGs draw correctly. */
export function loadMermaid(): Promise<Mermaid> {
  instance ??= import('mermaid').then(({ default: m }) => {
    m.initialize({
      startOnLoad: false, securityLevel: 'strict', htmlLabels: false, theme: 'default',
      flowchart: { wrappingWidth: 320 },
    })
    return m
  })
  return instance
}

/** Renders Mermaid text to an SVG string. */
export async function renderMermaid(source: string): Promise<string> {
  const mermaid = await loadMermaid()
  const { svg } = await mermaid.render(`mermaid-${++counter}`, source)
  return svg
}
