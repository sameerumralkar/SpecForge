import { lazy, Suspense, useMemo, useState } from 'react'
import {
  Alert, Box, CircularProgress, IconButton, ListSubheader, Menu, MenuItem, Stack, TextField, ToggleButton, ToggleButtonGroup, Tooltip, Typography,
} from '@mui/material'
import DownloadIcon from '@mui/icons-material/Download'
import FitScreenIcon from '@mui/icons-material/FitScreen'
import ZoomInIcon from '@mui/icons-material/ZoomIn'
import ZoomOutIcon from '@mui/icons-material/ZoomOut'
import { downloads } from '../api/client'
import { useDiagrams } from '../api/hooks'
import type { Diagram } from '../api/types'
import { diagramGroups, hash, saveBlob, shownDiagram, svgToPng, type Notation } from '../lib/diagrams'
import { ErrorSummary } from './ErrorSummary'

const MermaidView = lazy(() => import('./MermaidView'))
const CodeEditor = lazy(() => import('./CodeEditor'))

/** Class, overview and sequence diagrams, drawn with Mermaid (in the browser) or PlantUML (on the server). */
export function DiagramPanel({ projectId, focusEndpoint, endpointOnly = false }: {
  projectId: string
  focusEndpoint?: string
  /** Offer only the focused endpoint's diagrams (on the endpoint's own page). */
  endpointOnly?: boolean
}) {
  const diagrams = useDiagrams(projectId, true)
  const [choice, setChoice] = useState<{ id: string; for?: string } | null>(null)
  const setChosen = (id: string) => setChoice({ id, for: focusEndpoint })
  const [notation, setNotation] = useState<Notation>(() => (localStorage.getItem('diagram-notation') as Notation) ?? 'mermaid')
  const [view, setView] = useState<'diagram' | 'source'>('diagram')
  const [scale, setScale] = useState<number | null>(null) // null = fit to the panel width
  const [mermaidSvg, setMermaidSvg] = useState<string | null>(null)
  const [exportMenu, setExportMenu] = useState<HTMLElement | null>(null)
  const [exportError, setExportError] = useState<string | null>(null)

  const list = useMemo(() => (diagrams.data?.diagrams ?? []).filter((d) => !endpointOnly || d.endpointName === focusEndpoint),
    [diagrams.data, endpointOnly, focusEndpoint])
  const diagram: Diagram | undefined = shownDiagram(list, choice?.id ?? null, choice?.for, focusEndpoint)
  const own = focusEndpoint ? list.filter((d) => d.endpointName === focusEndpoint) : []
  const source = diagram ? (notation === 'mermaid' ? diagram.mermaid : diagram.plantuml) : ''

  const chooseNotation = (n: Notation) => {
    setNotation(n)
    localStorage.setItem('diagram-notation', n)
  }

  const exportAs = async (format: 'source' | 'svg' | 'png') => {
    setExportMenu(null)
    setExportError(null)
    if (!diagram) return
    if (notation === 'plantuml' || format === 'source') {
      const ext = format === 'source' ? (notation === 'mermaid' ? 'mmd' : 'puml') : format
      const a = document.createElement('a')
      a.href = downloads.diagram(projectId, diagram.id, ext)
      a.download = `${diagram.id}.${ext}`
      a.click()
      return
    }
    if (!mermaidSvg) return
    try {
      if (format === 'svg') saveBlob(new Blob([mermaidSvg], { type: 'image/svg+xml' }), `${diagram.id}.svg`)
      else saveBlob(await svgToPng(mermaidSvg), `${diagram.id}.png`)
    } catch (e) {
      setExportError(e instanceof Error ? e.message : String(e))
    }
  }

  if (diagrams.error) return <Box sx={{ p: 2 }}><ErrorSummary error={diagrams.error} /></Box>
  if (!diagrams.data) return <CircularProgress sx={{ m: 3 }} />

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}>
      <Stack direction="row" spacing={1} sx={{ px: 1.5, py: 1, alignItems: 'center', borderBottom: 1, borderColor: 'divider', flexWrap: 'wrap' }} useFlexGap>
        {!endpointOnly && <TextField select value={diagram?.id ?? ''} onChange={(e) => setChosen(e.target.value)} sx={{ minWidth: 220, flex: 1 }}
          slotProps={{ htmlInput: { 'aria-label': 'Diagram' } }}>
          {diagramGroups(list, focusEndpoint).flatMap((g) => [
            ...(g.label ? [<ListSubheader key={`h-${g.label}`}>{g.label}</ListSubheader>] : []),
            ...g.items.map((d) => <MenuItem key={d.id} value={d.id}>{d.title}</MenuItem>),
          ])}
        </TextField>}
        {endpointOnly && <Box sx={{ flex: 1 }} />}
        {own.length > 1 && (
          <ToggleButtonGroup exclusive size="small" aria-label="This endpoint's diagrams"
            value={diagram?.endpointName === focusEndpoint ? diagram?.kind : null}
            onChange={(_, kind) => { const d = own.find((x) => x.kind === kind); if (d) setChosen(d.id) }}
            sx={{ '& button': { textTransform: 'none', py: 0.25 } }}>
            {own.some((d) => d.kind === 'SEQUENCE') && <ToggleButton value="SEQUENCE">Sequence</ToggleButton>}
            {own.some((d) => d.kind === 'CLASS') && <ToggleButton value="CLASS">Classes</ToggleButton>}
          </ToggleButtonGroup>
        )}
        <ToggleButtonGroup exclusive size="small" value={notation} onChange={(_, n) => n && chooseNotation(n)} sx={{ '& button': { textTransform: 'none', py: 0.25 } }}>
          <ToggleButton value="mermaid">Mermaid</ToggleButton>
          <ToggleButton value="plantuml">PlantUML</ToggleButton>
        </ToggleButtonGroup>
        <ToggleButtonGroup exclusive size="small" value={view} onChange={(_, v) => v && setView(v)} sx={{ '& button': { textTransform: 'none', py: 0.25 } }}>
          <ToggleButton value="diagram">Diagram</ToggleButton>
          <ToggleButton value="source">Source</ToggleButton>
        </ToggleButtonGroup>
        {view === 'diagram' && (
          <>
            <IconButton aria-label="Zoom out" onClick={() => setScale((s) => Math.max(0.25, (s ?? 1) - 0.25))}><ZoomOutIcon fontSize="small" /></IconButton>
            <Typography variant="caption" sx={{ width: 34, textAlign: 'center' }}>{scale === null ? 'Fit' : `${Math.round(scale * 100)}%`}</Typography>
            <IconButton aria-label="Zoom in" onClick={() => setScale((s) => Math.min(3, (s ?? 1) + 0.25))}><ZoomInIcon fontSize="small" /></IconButton>
            <Tooltip title="Fit to width">
              <IconButton aria-label="Fit to width" onClick={() => setScale(null)} color={scale === null ? 'primary' : 'default'}><FitScreenIcon fontSize="small" /></IconButton>
            </Tooltip>
          </>
        )}
        <Tooltip title="Export">
          <IconButton aria-label="Export diagram" onClick={(e) => setExportMenu(e.currentTarget)} disabled={!diagram}><DownloadIcon fontSize="small" /></IconButton>
        </Tooltip>
        <Menu anchorEl={exportMenu} open={!!exportMenu} onClose={() => setExportMenu(null)}>
          <MenuItem onClick={() => exportAs('source')}>{notation === 'mermaid' ? '.mmd (Mermaid source)' : '.puml (PlantUML source)'}</MenuItem>
          <MenuItem onClick={() => exportAs('svg')} disabled={notation === 'mermaid' && !mermaidSvg}>.svg</MenuItem>
          <MenuItem onClick={() => exportAs('png')} disabled={notation === 'mermaid' && !mermaidSvg}>.png</MenuItem>
        </Menu>
      </Stack>
      {diagrams.data.problems.length > 0 && (
        <Alert severity="warning" sx={{ m: 1 }}>{diagrams.data.problems.map((p) => `${p.location}: ${p.message}`).join(' · ')}</Alert>
      )}
      {exportError && <Alert severity="error" sx={{ m: 1 }} onClose={() => setExportError(null)}>{exportError}</Alert>}
      <Box sx={{ flex: 1, minHeight: 0, overflow: 'auto', bgcolor: view === 'diagram' ? '#fff' : undefined }}>
        <Suspense fallback={<CircularProgress sx={{ m: 3 }} />}>
          {diagram && view === 'source' && <CodeEditor value={source} language="plaintext" readOnly height="100%" />}
          {diagram && view === 'diagram' && notation === 'mermaid' && (
            <MermaidView source={diagram.mermaid} scale={scale} onRendered={setMermaidSvg} />
          )}
          {diagram && view === 'diagram' && notation === 'plantuml' && (
            <Box sx={{ p: 2 }}>
              <img alt={`${diagram.title} (PlantUML)`} src={`${downloads.diagram(projectId, diagram.id, 'svg', true)}&v=${hash(diagram.plantuml)}`}
                style={scale === null ? { width: '100%', height: 'auto' } : { transform: `scale(${scale})`, transformOrigin: 'top left' }} />
            </Box>
          )}
        </Suspense>
      </Box>
    </Box>
  )
}
