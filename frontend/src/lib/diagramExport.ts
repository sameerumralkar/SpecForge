import { strToU8, unzipSync, zipSync } from 'fflate'
import { api, downloads } from '../api/client'
import { svgToPng } from './diagrams'
import { renderMermaid } from './mermaid'

/**
 * The diagrams zip with Mermaid images added. The server provides the sources and PlantUML's
 * images; Mermaid only renders in a browser, so its SVG and PNG are made here.
 * {@code onProgress} gets (done, total) as diagrams are drawn.
 */
export async function buildDiagramsZip(projectId: string, onProgress?: (done: number, total: number) => void): Promise<Blob> {
  const [response, view] = await Promise.all([fetch(downloads.diagramsZip(projectId)), api.diagrams(projectId)])
  if (!response.ok) throw new Error(`Could not download the diagrams (${response.status})`)
  const files = unzipSync(new Uint8Array(await response.arrayBuffer()))
  const failures: string[] = []
  let done = 0
  for (const d of view.diagrams) {
    try {
      const svg = await renderMermaid(d.mermaid)
      files[`diagrams/${d.id}.mermaid.svg`] = strToU8(svg)
      files[`diagrams/${d.id}.mermaid.png`] = new Uint8Array(await (await svgToPng(svg)).arrayBuffer())
    } catch (e) {
      failures.push(`${d.id}: ${e instanceof Error ? e.message : String(e)}`)
    }
    onProgress?.(++done, view.diagrams.length)
  }
  if (failures.length) {
    files['diagrams/MERMAID-ERRORS.txt'] = strToU8(`Mermaid could not draw these diagrams:\n${failures.join('\n')}\n`)
  }
  const sorted = Object.fromEntries(Object.entries(files).sort(([a], [b]) => a.localeCompare(b)))
  return new Blob([zipSync(sorted, { level: 6 })], { type: 'application/zip' })
}
