import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useDraggable } from '@dnd-kit/core'
import {
  Box, Card, CardHeader, IconButton, InputAdornment, List, ListItem, ListItemIcon, ListItemText, TextField, Tooltip, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import DragIndicatorIcon from '@mui/icons-material/DragIndicator'
import OpenInNewIcon from '@mui/icons-material/OpenInNew'
import SearchIcon from '@mui/icons-material/Search'
import type { Payload } from '../../api/types'
import { NewPayloadDialog } from '../NewPayloadDialog'

/** The project's inventory objects, to drag into fields, request and response bodies and messages. */
export function InventoryPalette({ projectId, payloads, excludeId }: { projectId: string; payloads: Payload[]; excludeId?: string }) {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [creating, setCreating] = useState(false)
  const objects = payloads.filter((p) => p.role === 'OBJECT' && p.id !== excludeId)
  const shown = objects.filter((p) => p.name.toLowerCase().includes(query.trim().toLowerCase()))

  return (
    <Card variant="outlined" component="aside" aria-label="Object inventory" sx={{ position: 'sticky', top: 16 }}>
      <CardHeader title="Object inventory" subheader="Drag an object onto a field list or a body"
        slotProps={{ title: { variant: 'subtitle2' }, subheader: { variant: 'caption' } }}
        action={<Tooltip title="New object"><IconButton aria-label="New inventory object" onClick={() => setCreating(true)}><AddIcon fontSize="small" /></IconButton></Tooltip>} />
      {objects.length > 6 && (
        <Box sx={{ px: 1.5, pb: 1 }}>
          <TextField size="small" fullWidth placeholder="Find an object" value={query} onChange={(e) => setQuery(e.target.value)}
            slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
        </Box>
      )}
      <List dense disablePadding sx={{ maxHeight: 'calc(100vh - 260px)', overflow: 'auto', pb: 1 }}>
        {objects.length === 0 && (
          <Typography variant="body2" color="text.secondary" sx={{ px: 2, pb: 1 }}>
            No objects yet. Mark a payload as an object, or create one with +.
          </Typography>
        )}
        {shown.map((p) => (
          <PaletteItem key={p.id} object={p} onOpen={() => navigate(`/projects/${projectId}/payloads/${p.id}`)} />
        ))}
      </List>
      <NewPayloadDialog projectId={projectId} open={creating} role="OBJECT" onClose={() => setCreating(false)} onCreated={() => {}} />
    </Card>
  )
}

function PaletteItem({ object, onOpen }: { object: Payload; onOpen: () => void }) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({ id: `object:${object.id}`, data: { object } })
  const fields = object.fields.map((f) => f.name).join(', ')
  return (
    <ListItem ref={setNodeRef} {...attributes} {...listeners} aria-label={`Drag object ${object.name}`}
      sx={{ cursor: 'grab', opacity: isDragging ? 0.4 : 1, '&:hover': { bgcolor: 'action.hover' }, touchAction: 'none' }}
      secondaryAction={
        <Tooltip title="Open">
          <IconButton edge="end" size="small" aria-label={`Open object ${object.name}`} onPointerDown={(e) => e.stopPropagation()} onClick={onOpen}>
            <OpenInNewIcon sx={{ fontSize: 16 }} />
          </IconButton>
        </Tooltip>
      }>
      <ListItemIcon sx={{ minWidth: 28 }}><DragIndicatorIcon fontSize="small" color="action" /></ListItemIcon>
      <ListItemText primary={object.name} secondary={fields || 'no fields'}
        slotProps={{ primary: { sx: { fontFamily: 'monospace', fontSize: 13, fontWeight: 500 } }, secondary: { noWrap: true, sx: { fontSize: 11 } } }} />
    </ListItem>
  )
}
