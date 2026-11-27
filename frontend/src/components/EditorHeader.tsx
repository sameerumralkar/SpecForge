import type { ReactNode } from 'react'
import { Box, Button, Chip, Stack, Typography } from '@mui/material'
import { ScreenHelp } from './help/ScreenHelp'

/** Title bar of an editor page with Save / Discard, which are enabled only while there are changes. */
export function EditorHeader({ title, subtitle, chips, dirty, saving, onSave, onDiscard, actions, help }: {
  title: ReactNode
  /** The screen whose help the "?" opens. */
  help?: string
  subtitle?: ReactNode
  chips?: ReactNode
  dirty: boolean
  saving?: boolean
  onSave: () => void
  onDiscard: () => void
  actions?: ReactNode
}) {
  return (
    <Box sx={{ px: 3, py: 2, borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper', position: 'sticky', top: 0, zIndex: 2 }}>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', minWidth: 0 }}>
            <Typography variant="h6" noWrap sx={{ minWidth: 0 }} title={typeof title === 'string' ? title : undefined}>{title}</Typography>
            {help && <ScreenHelp screen={help} />}
            {chips}
            {dirty && <Chip label="Unsaved changes" color="warning" variant="outlined" />}
          </Stack>
          {subtitle && <Typography variant="body2" color="text.secondary" noWrap>{subtitle}</Typography>}
        </Box>
        {actions}
        <Button onClick={onDiscard} sx={{ flexShrink: 0 }} disabled={!dirty || saving}>Discard</Button>
        <Button variant="contained" onClick={onSave} disabled={!dirty || saving}>Save</Button>
      </Stack>
    </Box>
  )
}
