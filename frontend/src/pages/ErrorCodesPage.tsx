import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import {
  Box, Button, Card, Chip, CircularProgress, IconButton, InputAdornment, Stack, Table, TableBody, TableCell, TableHead,
  TableRow, TextField, Tooltip, Typography,
} from '@mui/material'
import { ScreenHelp } from '../components/help/ScreenHelp'
import AddIcon from '@mui/icons-material/Add'
import AddLinkIcon from '@mui/icons-material/AddLink'
import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import SearchIcon from '@mui/icons-material/Search'
import { api } from '../api/client'
import { useErrorCodes, useRefreshProject } from '../api/hooks'
import type { ErrorCategory, ErrorCode, ErrorLink } from '../api/types'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ErrorSummary } from '../components/ErrorSummary'
import { categoryColor, ErrorCodeDialog, useCategories } from '../components/errors/ErrorCodes'
import { ErrorLinksDialog, linkIcon } from '../components/errors/ErrorLinksDialog'
import { mono } from '../theme'

/**
 * The error-code inventory: every code the project's APIs can answer with, its message and HTTP status,
 * and the fields and processing rules it is linked to.
 */
export function ErrorCodesPage() {
  const { projectId } = useParams()
  const navigate = useNavigate()
  const refresh = useRefreshProject()
  const codes = useErrorCodes(projectId)
  const categories = useCategories()
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState<ErrorCategory | null>(null)
  const [editing, setEditing] = useState<ErrorCode | null>(null)
  const [creating, setCreating] = useState(false)
  const [linking, setLinking] = useState<ErrorCode | null>(null)
  const [deleting, setDeleting] = useState<ErrorCode | null>(null)
  const [error, setError] = useState<unknown>(null)

  if (codes.error) return <Box sx={{ p: 3 }}><ErrorSummary error={codes.error} /></Box>
  if (!codes.data) return <CircularProgress sx={{ m: 4 }} />

  const q = query.trim().toLowerCase()
  const shown = codes.data.filter((c) => (!category || c.category === category)
    && (!q || c.code.toLowerCase().includes(q) || c.message.toLowerCase().includes(q)
      || c.links.some((l) => (l.detail ?? '').toLowerCase().includes(q))))
  const counts = new Map<ErrorCategory, number>()
  codes.data.forEach((c) => counts.set(c.category, (counts.get(c.category) ?? 0) + 1))

  const open = (l: ErrorLink) => {
    if (l.kind === 'FIELD' && l.payloadId) navigate(`/projects/${projectId}/payloads/${l.payloadId}`)
    else if (l.endpointId) navigate(`/projects/${projectId}/endpoints/${l.endpointId}/rules`)
  }
  const remove = async (c: ErrorCode) => {
    setDeleting(null)
    try {
      setError(null)
      await api.deleteErrorCode(projectId!, c.id)
      await refresh(projectId!)
    } catch (e) {
      setError(e)
    }
  }

  return (
    <Box sx={{ p: 3, maxWidth: 1400 }}>
      <Stack direction="row" sx={{ alignItems: 'center', mb: 2 }} spacing={2}>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <Typography variant="h5">Error codes</Typography>
            <ScreenHelp screen="errors" />
          </Stack>
          <Typography variant="body2" color="text.secondary">
            The errors the APIs can answer with. Link a code to fields (checks on them fail with it) or to processing rules.
            Generated code returns the code in the problem response; the specs list it as <code>x-error-codes</code>.
          </Typography>
        </Box>
        <TextField size="small" fullWidth={false} sx={{ width: 260, flexShrink: 0 }} placeholder="Find a code, message or link" value={query}
          onChange={(e) => setQuery(e.target.value)}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)} sx={{ flexShrink: 0, whiteSpace: 'nowrap' }}>New error code</Button>
      </Stack>
      <Stack direction="row" useFlexGap spacing={1} sx={{ flexWrap: 'wrap', mb: 2 }} aria-label="Filter by category">
        <Chip label={`All (${codes.data.length})`} color={category ? 'default' : 'primary'} onClick={() => setCategory(null)} />
        {categories.filter((c) => counts.has(c.category)).map((c) => (
          <Chip key={c.category} label={`${c.category} (${counts.get(c.category)})`} variant={category === c.category ? 'filled' : 'outlined'}
            color={categoryColor(c.category)} onClick={() => setCategory(category === c.category ? null : c.category)} />
        ))}
      </Stack>
      <ErrorSummary error={error} />
      <Card variant="outlined">
        <Table size="small" aria-label="Error codes">
          <TableHead>
            <TableRow>
              <TableCell sx={{ width: 130 }}>Code</TableCell>
              <TableCell>Message</TableCell>
              <TableCell sx={{ width: 70 }}>Status</TableCell>
              <TableCell sx={{ width: 120 }}>Category</TableCell>
              <TableCell sx={{ width: '34%' }}>Linked to</TableCell>
              <TableCell align="right" sx={{ width: 130 }} />
            </TableRow>
          </TableHead>
          <TableBody>
            {shown.length === 0 && (
              <TableRow><TableCell colSpan={6}>
                <Typography variant="body2" color="text.secondary">
                  {codes.data.length ? 'Nothing matches.' : 'No error codes yet. Create one, then link it to the fields and rules that fail with it.'}
                </Typography>
              </TableCell></TableRow>
            )}
            {shown.map((c) => (
              <TableRow key={c.id} hover sx={{ '& td': { verticalAlign: 'top' } }}>
                <TableCell sx={{ fontFamily: mono.fontFamily, fontWeight: 600, whiteSpace: 'nowrap' }}>{c.code}</TableCell>
                <TableCell>
                  {c.message}
                  {c.description && <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{c.description}</Typography>}
                </TableCell>
                <TableCell>
                  <Tooltip title={c.httpStatus == null ? 'the category default' : 'set on this code'}>
                    <span style={{ opacity: c.httpStatus == null ? 0.7 : 1 }}>{c.status}</span>
                  </Tooltip>
                </TableCell>
                <TableCell><Chip size="small" variant="outlined" color={categoryColor(c.category)} label={c.category} /></TableCell>
                <TableCell>
                  {c.links.length === 0
                    ? <Typography variant="body2" color="text.secondary">not linked</Typography>
                    : <Stack direction="row" useFlexGap spacing={0.5} sx={{ flexWrap: 'wrap' }}>
                        {c.links.map((l, i) => (
                          <Chip key={i} size="small" icon={linkIcon(l.kind)} label={l.detail} onClick={() => open(l)}
                            sx={{ fontFamily: mono.fontFamily, fontSize: 11, maxWidth: '100%' }} />
                        ))}
                      </Stack>}
                </TableCell>
                <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                  <Tooltip title="Link to fields and rules"><IconButton size="small" aria-label={`Link ${c.code}`} onClick={() => setLinking(c)}><AddLinkIcon fontSize="small" /></IconButton></Tooltip>
                  <Tooltip title="Edit"><IconButton size="small" aria-label={`Edit ${c.code}`} onClick={() => setEditing(c)}><EditOutlinedIcon fontSize="small" /></IconButton></Tooltip>
                  <Tooltip title="Delete"><IconButton size="small" aria-label={`Delete ${c.code}`} onClick={() => setDeleting(c)}><DeleteOutlinedIcon fontSize="small" /></IconButton></Tooltip>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>

      <ErrorCodeDialog projectId={projectId!} open={creating || !!editing} editing={editing}
        onClose={() => { setCreating(false); setEditing(null) }} onSaved={(saved) => { if (creating) setLinking(saved) }} />
      <ErrorLinksDialog projectId={projectId!} code={linking} onClose={() => setLinking(null)} />
      <ConfirmDialog open={!!deleting} title={`Delete ${deleting?.code}?`} onConfirm={() => deleting && remove(deleting)} onClose={() => setDeleting(null)}>
        {deleting?.links.length
          ? `It is linked in ${deleting.links.length} place${deleting.links.length === 1 ? '' : 's'}; deleting it unlinks it from all of them.`
          : 'This cannot be undone.'}
      </ConfirmDialog>
    </Box>
  )
}
