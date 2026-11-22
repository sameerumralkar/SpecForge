import { Box } from '@mui/material'

/** A vertical bar to drag; reports the new width of the panel to its right. */
export function ResizeHandle({ onResize, min, max }: { onResize: (width: number) => void; min: number; max: number }) {
  const start = (e: React.PointerEvent) => {
    e.preventDefault()
    const move = (ev: PointerEvent) => onResize(Math.min(max, Math.max(min, window.innerWidth - ev.clientX)))
    const stop = () => {
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerup', stop)
      document.body.style.cursor = ''
    }
    document.body.style.cursor = 'col-resize'
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', stop)
  }
  return (
    <Box role="separator" aria-orientation="vertical" aria-label="Resize preview" onPointerDown={start}
      sx={{ width: 6, flexShrink: 0, cursor: 'col-resize', borderLeft: 1, borderColor: 'divider', '&:hover': { bgcolor: 'primary.light', opacity: 0.4 } }} />
  )
}
