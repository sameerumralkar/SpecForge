import { useEffect, useState } from 'react'
import { Alert, Box, CircularProgress } from '@mui/material'
import { renderMermaid } from '../lib/mermaid'

/** {@code scale} null fits the diagram to the panel width. */
export default function MermaidView({ source, scale, onRendered }: {
  source: string
  scale: number | null
  onRendered?: (svg: string | null) => void
}) {
  const [svg, setSvg] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    renderMermaid(source)
      .then((svg) => {
        if (cancelled) return
        setSvg(svg)
        setError(null)
        onRendered?.(svg)
      })
      .catch((e: unknown) => {
        if (cancelled) return
        setSvg(null)
        setError(e instanceof Error ? e.message : String(e))
        onRendered?.(null)
      })
    return () => {
      cancelled = true
    }
  }, [source]) // eslint-disable-line react-hooks/exhaustive-deps

  if (error) return <Alert severity="error" sx={{ m: 2, whiteSpace: 'pre-wrap' }}>Mermaid could not draw this diagram: {error}</Alert>
  if (!svg) return <CircularProgress sx={{ m: 3 }} />
  return (
    <Box aria-label="Mermaid diagram" sx={scale === null
      ? { p: 2, '& svg': { width: '100%', height: 'auto' } }
      : { p: 2, transform: `scale(${scale})`, transformOrigin: 'top left', '& svg': { maxWidth: 'none !important' } }}
      dangerouslySetInnerHTML={{ __html: svg }} />
  )
}
