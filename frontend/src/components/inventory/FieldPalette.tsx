import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useDraggable } from '@dnd-kit/core'
import {
  Box, Card, CardHeader, IconButton, InputAdornment, List, ListItem, ListItemIcon, ListItemText, TextField, Tooltip, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import DragIndicatorIcon from '@mui/icons-material/DragIndicator'
import OpenInNewIcon from '@mui/icons-material/OpenInNew'
import PlaylistAddIcon from '@mui/icons-material/PlaylistAdd'
import SearchIcon from '@mui/icons-material/Search'
import { useFieldDefinitions } from '../../api/hooks'
import type { FieldDefinition } from '../../api/types'
import { fieldSummary } from '../../lib/inventory'
import { FieldDefinitionDialog } from './FieldDefinitionDialog'

/** The project's field inventory, to drag (or add with a click) into a payload's field list. */
export function FieldPalette({ projectId, onAdd }: { projectId: string; onAdd: (definition: FieldDefinition) => void }) {
  const navigate = useNavigate()
  const definitions = useFieldDefinitions(projectId).data ?? []
  const [query, setQuery] = useState('')
  const [creating, setCreating] = useState(false)
  const q = query.trim().toLowerCase()
  const shown = definitions.filter((d) => !q || d.field.name.toLowerCase().includes(q) || (d.field.description ?? '').toLowerCase().includes(q))

  return (
    <Card variant="outlined" component="aside" aria-label="Field inventory">
      <CardHeader title="Field inventory" subheader="Drag a field onto the field list, or click + on it"
        slotProps={{ title: { variant: 'subtitle2' }, subheader: { variant: 'caption' } }}
        action={<>
          <Tooltip title="Open the field inventory"><IconButton aria-label="Open the field inventory" onClick={() => navigate(`/projects/${projectId}/fields`)}><OpenInNewIcon fontSize="small" /></IconButton></Tooltip>
          <Tooltip title="New inventory field"><IconButton aria-label="New inventory field" onClick={() => setCreating(true)}><AddIcon fontSize="small" /></IconButton></Tooltip>
        </>} />
      {definitions.length > 6 && (
        <Box sx={{ px: 1.5, pb: 1 }}>
          <TextField size="small" fullWidth placeholder="Find a field" value={query} onChange={(e) => setQuery(e.target.value)}
            slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
        </Box>
      )}
      <List dense disablePadding sx={{ maxHeight: 'calc(50vh - 120px)', overflow: 'auto', pb: 1 }}>
        {definitions.length === 0 && (
          <Typography variant="body2" color="text.secondary" sx={{ px: 2, pb: 1 }}>
            No fields yet. Create one with +, or save a field from the list with its bookmark button.
          </Typography>
        )}
        {shown.map((d) => <PaletteField key={d.id} definition={d} onAdd={() => onAdd(d)} />)}
      </List>
      <FieldDefinitionDialog projectId={projectId} open={creating} onClose={() => setCreating(false)} />
    </Card>
  )
}

function PaletteField({ definition, onAdd }: { definition: FieldDefinition; onAdd: () => void }) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({ id: `field:${definition.id}`, data: { definition } })
  const f = definition.field
  return (
    <ListItem ref={setNodeRef} {...attributes} {...listeners} aria-label={`Drag field ${f.name}`}
      sx={{ cursor: 'grab', opacity: isDragging ? 0.4 : 1, '&:hover': { bgcolor: 'action.hover' }, touchAction: 'none' }}
      secondaryAction={
        <Tooltip title="Add to the fields">
          <IconButton edge="end" size="small" aria-label={`Add field ${f.name}`} onPointerDown={(e) => e.stopPropagation()} onClick={onAdd}>
            <PlaylistAddIcon sx={{ fontSize: 18 }} />
          </IconButton>
        </Tooltip>
      }>
      <ListItemIcon sx={{ minWidth: 28 }}><DragIndicatorIcon fontSize="small" color="action" /></ListItemIcon>
      <Tooltip title={f.description ?? ''} placement="left">
        <ListItemText primary={`${f.name}${f.required ? '' : '?'}`} secondary={fieldSummary(f)}
          slotProps={{ primary: { sx: { fontFamily: 'monospace', fontSize: 13, fontWeight: 500 } }, secondary: { noWrap: true, sx: { fontSize: 11 } } }} />
      </Tooltip>
    </ListItem>
  )
}
