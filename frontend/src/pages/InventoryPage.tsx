import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import {
  Box, Button, Card, CardHeader, Chip, CircularProgress, IconButton, InputAdornment, Link, Stack, Table, TableBody, TableCell,
  TableHead, TableRow, TextField, Tooltip, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import MoveDownIcon from '@mui/icons-material/MoveDown'
import MoveUpIcon from '@mui/icons-material/MoveUp'
import SearchIcon from '@mui/icons-material/Search'
import { useQueries } from '@tanstack/react-query'
import { ApiError, api } from '../api/client'
import { keys, usePayloads, useRefreshProject } from '../api/hooks'
import type { Payload, PayloadRole, PayloadUsage } from '../api/types'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ErrorSummary } from '../components/ErrorSummary'
import { NewPayloadDialog } from '../components/NewPayloadDialog'
import { UsagesDialog } from '../components/UsagesDialog'
import { byRole } from '../lib/inventory'
import { toPayloadRequest } from '../lib/mappers'

/**
 * The object inventory: the project's reusable building blocks, with create, edit, duplicate and
 * delete. Structures (bodies) are listed below it, and can be moved into the inventory.
 */
export function InventoryPage() {
  const { projectId } = useParams()
  const navigate = useNavigate()
  const refresh = useRefreshProject()
  const payloads = usePayloads(projectId!)
  const [query, setQuery] = useState('')
  const [creating, setCreating] = useState(false)
  const [confirm, setConfirm] = useState<Payload | null>(null)
  const [blocked, setBlocked] = useState<{ name: string; usages: PayloadUsage[] } | null>(null)
  const [error, setError] = useState<unknown>(null)

  const all = payloads.data ?? []
  const usages = useQueries({
    queries: all.map((p) => ({ queryKey: [...keys.payloads(projectId!), p.id, 'usages'], queryFn: () => api.payloadUsages(projectId!, p.id) })),
  })
  const usagesOf = (id: string) => usages[all.findIndex((p) => p.id === id)]?.data

  if (payloads.error) return <Box sx={{ p: 3 }}><ErrorSummary error={payloads.error} /></Box>
  if (!payloads.data) return <CircularProgress sx={{ m: 4 }} />

  const q = query.trim().toLowerCase()
  const matches = (p: Payload) => !q || p.name.toLowerCase().includes(q) || p.fields.some((f) => f.name.toLowerCase().includes(q))
  const { objects, structures } = byRole(all.filter(matches))
  const open = (p: Payload) => navigate(`/projects/${projectId}/payloads/${p.id}`)

  const run = async (action: () => Promise<unknown>) => {
    try {
      setError(null)
      await action()
      await refresh(projectId!)
    } catch (e) {
      setError(e)
    }
  }
  const duplicate = (p: Payload) => run(async () => open(await api.duplicatePayload(projectId!, p.id)))
  const setRole = (p: Payload, role: PayloadRole) => run(() => api.updatePayload(projectId!, p.id, { ...toPayloadRequest(p), role }))
  const remove = async (p: Payload) => {
    setConfirm(null)
    try {
      await api.deletePayload(projectId!, p.id)
      await refresh(projectId!)
    } catch (e) {
      if (e instanceof ApiError && e.usages.length) setBlocked({ name: p.name, usages: e.usages })
      else setError(e)
    }
  }
  const askDelete = (p: Payload) => {
    const used = usagesOf(p.id)
    if (used?.length) setBlocked({ name: p.name, usages: used })
    else setConfirm(p)
  }

  const table = (rows: Payload[], role: PayloadRole) => (
    <Table size="small" aria-label={role === 'OBJECT' ? 'Inventory objects' : 'Structures'}>
      <TableHead>
        <TableRow>
          <TableCell sx={{ width: '20%' }}>Name</TableCell>
          <TableCell>Fields</TableCell>
          <TableCell sx={{ width: '22%' }}>Used by</TableCell>
          <TableCell align="right" sx={{ width: 170 }} />
        </TableRow>
      </TableHead>
      <TableBody>
        {rows.length === 0 && (
          <TableRow><TableCell colSpan={4}>
            <Typography variant="body2" color="text.secondary">
              {q ? 'Nothing matches.' : role === 'OBJECT' ? 'No objects yet. Create one, or move a structure into the inventory.' : 'No structures.'}
            </Typography>
          </TableCell></TableRow>
        )}
        {rows.map((p) => {
          const used = usagesOf(p.id)
          return (
            <TableRow key={p.id} hover sx={{ '& td': { verticalAlign: 'top' } }}>
              <TableCell>
                <Link component="button" variant="body2" onClick={() => open(p)} sx={{ fontFamily: 'monospace', fontWeight: 600 }}>{p.name}</Link>
                {p.description && <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{p.description}</Typography>}
              </TableCell>
              <TableCell>
                <Stack direction="row" useFlexGap spacing={0.5} sx={{ flexWrap: 'wrap' }}>
                  {p.fields.length === 0 && <Typography variant="body2" color="text.secondary">none</Typography>}
                  {p.fields.map((f) => (
                    <Chip key={f.name} size="small" variant="outlined" sx={{ fontFamily: 'monospace', fontSize: 11 }}
                      label={`${f.name}${f.required ? '' : '?'}: ${fieldType(f, all)}`} />
                  ))}
                </Stack>
              </TableCell>
              <TableCell>
                {used === undefined ? <CircularProgress size={14} /> : used.length === 0
                  ? <Typography variant="body2" color="text.secondary">nothing</Typography>
                  : <Tooltip title={used.map((u) => `${u.ownerName}: ${u.detail}`).join('\n')}>
                      <Typography variant="body2">{used.length} place{used.length === 1 ? '' : 's'}: {[...new Set(used.map((u) => u.ownerName))].slice(0, 3).join(', ')}{used.length > 3 ? '…' : ''}</Typography>
                    </Tooltip>}
              </TableCell>
              <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                <Tooltip title="Edit"><IconButton size="small" aria-label={`Edit ${p.name}`} onClick={() => open(p)}><EditOutlinedIcon fontSize="small" /></IconButton></Tooltip>
                <Tooltip title="Duplicate"><IconButton size="small" aria-label={`Duplicate ${p.name}`} onClick={() => duplicate(p)}><ContentCopyIcon fontSize="small" /></IconButton></Tooltip>
                {role === 'OBJECT'
                  ? <Tooltip title="Move out of the inventory (make it a structure)"><IconButton size="small" aria-label={`Make ${p.name} a structure`} onClick={() => setRole(p, 'STRUCTURE')}><MoveDownIcon fontSize="small" /></IconButton></Tooltip>
                  : <Tooltip title="Move into the inventory"><IconButton size="small" aria-label={`Move ${p.name} into the inventory`} onClick={() => setRole(p, 'OBJECT')}><MoveUpIcon fontSize="small" /></IconButton></Tooltip>}
                <Tooltip title="Delete"><IconButton size="small" aria-label={`Delete ${p.name}`} onClick={() => askDelete(p)}><DeleteOutlinedIcon fontSize="small" /></IconButton></Tooltip>
              </TableCell>
            </TableRow>
          )
        })}
      </TableBody>
    </Table>
  )

  return (
    <Box sx={{ p: 3, maxWidth: 1300 }}>
      <Stack direction="row" sx={{ alignItems: 'center', mb: 2 }} spacing={2}>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography variant="h5">Object inventory</Typography>
          <Typography variant="body2" color="text.secondary">
            Reusable building blocks. Drag them from the inventory panel into a payload's fields, a request or response body, or a message.
          </Typography>
        </Box>
        <TextField size="small" fullWidth={false} sx={{ width: 260, flexShrink: 0 }} placeholder="Find by name or field" value={query} onChange={(e) => setQuery(e.target.value)}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)} sx={{ flexShrink: 0, whiteSpace: 'nowrap' }}>New object</Button>
      </Stack>
      <ErrorSummary error={error} />
      <Card variant="outlined" sx={{ mb: 3 }}>
        <CardHeader title={`Objects (${objects.length})`} slotProps={{ title: { variant: 'subtitle1' } }} />
        {table(objects, 'OBJECT')}
      </Card>
      <Card variant="outlined">
        <CardHeader title={`Structures (${structures.length})`} subheader="Request, response, message and working bodies"
          slotProps={{ title: { variant: 'subtitle1' } }} />
        {table(structures, 'STRUCTURE')}
      </Card>

      <NewPayloadDialog projectId={projectId!} open={creating} role="OBJECT" onClose={() => setCreating(false)} onCreated={open} />
      <ConfirmDialog open={!!confirm} title={`Delete ${confirm?.name}?`} onConfirm={() => confirm && remove(confirm)} onClose={() => setConfirm(null)}>
        This cannot be undone.
      </ConfirmDialog>
      <UsagesDialog projectId={projectId!} payloadName={blocked?.name ?? ''} usages={blocked?.usages ?? null} onClose={() => setBlocked(null)} />
    </Box>
  )
}

function fieldType(f: Payload['fields'][number], all: Payload[]): string {
  const ref = f.refPayloadId ? all.find((p) => p.id === f.refPayloadId)?.name ?? '?' : undefined
  if (f.type === 'ARRAY') return `${f.itemType === 'OBJECT' ? ref : f.itemType}[]`
  return f.type === 'OBJECT' ? ref ?? 'object' : (f.type ?? '?').toLowerCase()
}
