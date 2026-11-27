import { useState } from 'react'
import { Box, Divider, Drawer, IconButton, Stack, Tooltip, Typography } from '@mui/material'
import CloseIcon from '@mui/icons-material/Close'
import HelpOutlineIcon from '@mui/icons-material/HelpOutlineOutlined'
import { screenHelp } from '../../lib/help'
import { setFieldHelpOn, useFieldHelpOn } from './fieldHelpStore'

/**
 * The "?" next to a screen's title: hovering says what the screen is; clicking opens a panel with what it
 * shows, what you can do there, and tips.
 */
export function ScreenHelp({ screen }: { screen: string }) {
  const [open, setOpen] = useState(false)
  const fieldHelpOn = useFieldHelpOn()
  const help = screenHelp(screen)
  if (!help) return null
  return (
    <>
      <Tooltip describeChild title={<><b>{help.title}</b><br />{help.summary}<br /><i>Click for more.</i></>}>
        <IconButton size="small" aria-label={`Help: ${help.title}`} onClick={() => setOpen(true)} color="primary">
          <HelpOutlineIcon fontSize="small" />
        </IconButton>
      </Tooltip>
      <Drawer anchor="right" open={open} onClose={() => setOpen(false)} slotProps={{ paper: { sx: { width: 420, p: 3 } } }}>
        <Stack direction="row" sx={{ alignItems: 'center', mb: 1 }}>
          <HelpOutlineIcon color="primary" sx={{ mr: 1 }} />
          <Typography variant="h6" sx={{ flex: 1 }} role="heading">{help.title}</Typography>
          <IconButton aria-label="Close help" onClick={() => setOpen(false)}><CloseIcon /></IconButton>
        </Stack>
        <Typography variant="body1" sx={{ mb: 2 }}>{help.summary}</Typography>
        {help.sections.map((s) => (
          <Box key={s.heading} sx={{ mb: 2 }}>
            <Typography variant="subtitle2" sx={{ mb: 0.5 }}>{s.heading}</Typography>
            <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
              {s.items.map((item) => <Typography key={item} component="li" variant="body2" sx={{ mb: 0.5 }}>{item}</Typography>)}
            </Box>
          </Box>
        ))}
        {help.tips && help.tips.length > 0 && (
          <Box sx={{ mb: 2, p: 1.5, bgcolor: 'info.50', border: 1, borderColor: 'info.light', borderRadius: 1 }}>
            <Typography variant="subtitle2" sx={{ mb: 0.5 }}>Tips</Typography>
            {help.tips.map((t) => <Typography key={t} variant="body2" sx={{ mb: 0.5 }}>{t}</Typography>)}
          </Box>
        )}
        <Divider sx={{ my: 2 }} />
        <Typography variant="body2" color="text.secondary">
          Hover over (or tab into) any field to see what it is for.{' '}
          <Box component="button" onClick={() => setFieldHelpOn(!fieldHelpOn)}
            sx={{ border: 0, p: 0, bgcolor: 'transparent', color: 'primary.main', cursor: 'pointer', font: 'inherit', textDecoration: 'underline' }}>
            {fieldHelpOn ? 'Turn field tips off' : 'Turn field tips on'}
          </Box>
          {help.guide && <><br />In the user guide: {help.guide}.</>}
        </Typography>
      </Drawer>
    </>
  )
}
