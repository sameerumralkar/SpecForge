import { useState } from 'react'
import { useParams } from 'react-router'
import {
  Box, Button, Card, CircularProgress, IconButton, InputAdornment, Stack, Table, TableBody, TableCell, TableHead, TableRow,
  TextField, Tooltip, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import SearchIcon from '@mui/icons-material/Search'
import { api } from '../api/client'
import { useFieldDefinitions, useRefreshProject } from '../api/hooks'
import type { FieldDefinition } from '../api/types'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ErrorSummary } from '../components/ErrorSummary'
import { ErrorCodeChips } from '../components/errors/ErrorCodes'
import { FieldDefinitionDialog } from '../components/inventory/FieldDefinitionDialog'
import { fieldSummary, freeFieldName } from '../lib/inventory'
import { mono } from '../theme'

/**
 * The field inventory: plain fields that recur across payloads (ids, amounts, currencies, emails), each with
 * its constraints and error codes. Objects are built from them: drag one from the field palette onto an
 * object's field list and it is copied in.
 */
export function FieldInventoryPage() {
  const { projectId } = useParams()
  const refresh = useRefreshProject()
  const definitions = useFieldDefinitions(projectId)
  const [query, setQuery] = useState('')
  const [editing, setEditing] = useState<FieldDefinition | null>(null)
  const [creating, setCreating] = useState(false)
  const [deleting, setDeleting] = useState<FieldDefinition | null>(null)
  const [error, setError] = useState<unknown>(null)

  if (definitions.error) return <Box sx={{ p: 3 }}><ErrorSummary error={definitions.error} /></Box>
  if (!definitions.data) return <CircularProgress sx={{ m: 4 }} />

  const all = definitions.data
  const q = query.trim().toLowerCase()
  const rows = all.filter((d) => !q || [d.field.name, d.field.description ?? '', fieldSummary(d.field), ...d.usedIn]
    .some((t) => t.toLowerCase().includes(q)))

  const duplicate = async (d: FieldDefinition) => {
    try {
      setError(null)
      const name = freeFieldName(`${d.field.name}Copy`, all.map((x) => x.field.name))
      setEditing(await api.createFieldDefinition(projectId!, { ...d.field, name }))
      await refresh(projectId!)
    } catch (e) {
      setError(e)
    }
  }
  const remove = async (d: FieldDefinition) => {
    setDeleting(null)
    try {
      setError(null)
      await api.deleteFieldDefinition(projectId!, d.id)
      await refresh(projectId!)
    } catch (e) {
      setError(e)
    }
  }

  return (
    <Box sx={{ p: 3, maxWidth: 1300 }}>
      <Stack direction="row" sx={{ alignItems: 'center', mb: 2 }} spacing={2}>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography variant="h5">Field inventory</Typography>
          <Typography variant="body2" color="text.secondary">
            Ready-made fields with their checks and error codes. When you build an object, drag them from the field inventory panel
            onto its field list: each is copied in, and can then be changed there.
          </Typography>
        </Box>
        <TextField size="small" sx={{ width: 260, flexShrink: 0 }} placeholder="Find a field" value={query} onChange={(e) => setQuery(e.target.value)}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)} sx={{ flexShrink: 0, whiteSpace: 'nowrap' }}>New field</Button>
      </Stack>
      <ErrorSummary error={error} />
      <Card variant="outlined">
        <Table size="small" aria-label="Field inventory">
          <TableHead>
            <TableRow>
              <TableCell sx={{ width: '18%' }}>Name</TableCell>
              <TableCell>Type and constraints</TableCell>
              <TableCell sx={{ width: '16%' }}>Error codes</TableCell>
              <TableCell sx={{ width: '22%' }}>Payload fields with this name</TableCell>
              <TableCell align="right" sx={{ width: 130 }} />
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.length === 0 && (
              <TableRow><TableCell colSpan={5}>
                <Typography variant="body2" color="text.secondary">
                  {q ? 'Nothing matches.' : 'No fields yet. Create one, or save a field from a payload with its bookmark button.'}
                </Typography>
              </TableCell></TableRow>
            )}
            {rows.map((d) => (
              <TableRow key={d.id} hover sx={{ '& td': { verticalAlign: 'top' } }}>
                <TableCell>
                  <Typography variant="body2" sx={{ fontFamily: mono.fontFamily, fontWeight: 600 }}>
                    {d.field.name}{d.field.required ? '' : '?'}
                  </Typography>
                  {d.field.description && <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{d.field.description}</Typography>}
                </TableCell>
                <TableCell>
                  <Typography variant="body2" sx={{ fontFamily: mono.fontFamily, fontSize: 12, wordBreak: 'break-word' }}>{fieldSummary(d.field)}</Typography>
                  {d.field.example && <Typography variant="caption" color="text.secondary">e.g. {d.field.example}</Typography>}
                </TableCell>
                <TableCell>
                  {d.field.errorCodeIds?.length ? <ErrorCodeChips ids={d.field.errorCodeIds} /> : <Typography variant="body2" color="text.secondary">none</Typography>}
                </TableCell>
                <TableCell>
                  {d.usedIn.length === 0
                    ? <Typography variant="body2" color="text.secondary">none yet</Typography>
                    : <Tooltip title={d.usedIn.join('\n')}>
                        <Typography variant="body2">{d.usedIn.slice(0, 3).join(', ')}{d.usedIn.length > 3 ? ` +${d.usedIn.length - 3}` : ''}</Typography>
                      </Tooltip>}
                </TableCell>
                <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                  <Tooltip title="Edit"><IconButton size="small" aria-label={`Edit ${d.field.name}`} onClick={() => setEditing(d)}><EditOutlinedIcon fontSize="small" /></IconButton></Tooltip>
                  <Tooltip title="Duplicate"><IconButton size="small" aria-label={`Duplicate ${d.field.name}`} onClick={() => duplicate(d)}><ContentCopyIcon fontSize="small" /></IconButton></Tooltip>
                  <Tooltip title="Delete"><IconButton size="small" aria-label={`Delete ${d.field.name}`} onClick={() => setDeleting(d)}><DeleteOutlinedIcon fontSize="small" /></IconButton></Tooltip>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>

      <FieldDefinitionDialog projectId={projectId!} open={creating || !!editing} editing={editing}
        onClose={() => { setCreating(false); setEditing(null) }} />
      <ConfirmDialog open={!!deleting} title={`Delete field ${deleting?.field.name}?`} onConfirm={() => deleting && remove(deleting)} onClose={() => setDeleting(null)}>
        Payload fields already copied from it stay as they are.
      </ConfirmDialog>
    </Box>
  )
}
