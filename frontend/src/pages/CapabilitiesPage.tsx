import { useMemo, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router'
import {
  Box, Breadcrumbs, Button, Card, CardContent, CardHeader, Checkbox, Chip, CircularProgress, Dialog, DialogActions, DialogContent,
  DialogTitle, IconButton, InputAdornment, Link, List, ListItemButton, ListItemIcon, ListItemText, MenuItem, Stack, Table, TableBody,
  TableCell, TableHead, TableRow, TextField, Tooltip, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import LinkIcon from '@mui/icons-material/Link'
import LinkOffIcon from '@mui/icons-material/LinkOff'
import SearchIcon from '@mui/icons-material/Search'
import UploadFileIcon from '@mui/icons-material/UploadFile'
import { CapabilityImportDialog } from '../components/capabilities/CapabilityImportDialog'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { keys, useCapabilities, useCapabilityApis, useCapabilityImports, useCapabilityLevels, useEndpoints } from '../api/hooks'
import type { Capability, CapabilityRequest, Endpoint } from '../api/types'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ErrorSummary } from '../components/ErrorSummary'
import { MethodChip } from '../components/MethodChip'
import { ScreenHelp } from '../components/help/ScreenHelp'
import { errorText } from '../lib/errors'

/** Colours for L1 to L8, from the broadest to the most detailed. */
const LEVEL_COLORS = ['#1a237e', '#283593', '#3949ab', '#5c6bc0', '#00796b', '#26a69a', '#8d6e63', '#a1887f']

export function LevelChip({ level, name }: { level: number; name?: string }) {
  return (
    <Tooltip title={name ? `Level ${level}: ${name}` : `Level ${level}`}>
      <Chip size="small" label={`L${level}`} sx={{ bgcolor: LEVEL_COLORS[level - 1], color: '#fff', fontWeight: 600, height: 20, fontSize: 11 }} />
    </Tooltip>
  )
}

/**
 * The functional capability model: what the business does, from L1 (the broadest) to L8, and the APIs
 * that realise each capability. Every level shows its API inventory: its own APIs and everything below it.
 */
export function CapabilitiesPage() {
  const { projectId } = useParams()
  const client = useQueryClient()
  const capabilities = useCapabilities(projectId)
  const levels = useCapabilityLevels(projectId)
  const endpoints = useEndpoints(projectId!)
  const [params, setParams] = useSearchParams()
  const selectedId = params.get('c')
  const [query, setQuery] = useState('')
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set())
  const [editing, setEditing] = useState<{ capability: Capability | null; parentId: string | null } | null>(null)
  const [deleting, setDeleting] = useState<Capability | null>(null)
  const [naming, setNaming] = useState(false)
  const [linking, setLinking] = useState<Capability | null>(null)
  const [importing, setImporting] = useState(false)
  const [error, setError] = useState<unknown>(null)

  const all = useMemo(() => capabilities.data ?? [], [capabilities.data])
  const byId = useMemo(() => new Map(all.map((c) => [c.id, c])), [all])
  if (capabilities.error) return <Box sx={{ p: 3 }}><ErrorSummary error={capabilities.error} /></Box>
  if (!capabilities.data || !levels.data) return <CircularProgress sx={{ m: 4 }} />

  const levelNames = levels.data.levels
  const selected = selectedId ? byId.get(selectedId) ?? null : null
  const select = (id: string | null) => setParams(id ? { c: id } : {}, { replace: true })
  const refresh = () => client.invalidateQueries({ queryKey: keys.capabilities(projectId!) })
  const run = async (action: () => Promise<unknown>) => {
    try {
      setError(null)
      await action()
      await refresh()
    } catch (e) {
      setError(e)
    }
  }

  // Hidden: below a collapsed capability, or (when searching) not matching and with nothing matching below.
  const q = query.trim().toLowerCase()
  const matches = (c: Capability) => !q || c.name.toLowerCase().includes(q) || (c.code ?? '').toLowerCase().includes(q)
    || (c.description ?? '').toLowerCase().includes(q)
  const keep = new Set<string>()
  if (q) {
    all.filter(matches).forEach((c) => {
      for (let p: Capability | undefined = c; p; p = p.parentId ? byId.get(p.parentId) : undefined) keep.add(p.id)
    })
  }
  const hiddenByCollapse = (c: Capability) => {
    for (let p = c.parentId ? byId.get(c.parentId) : undefined; p; p = p.parentId ? byId.get(p.parentId) : undefined) {
      if (collapsed.has(p.id)) return true
    }
    return false
  }
  const visible = all.filter((c) => (q ? keep.has(c.id) : !hiddenByCollapse(c)))
  const linked = new Set(all.flatMap((c) => c.endpointIds))
  const unmapped = (endpoints.data ?? []).filter((e) => !linked.has(e.id))
  const siblings = (c: Capability) => all.filter((s) => s.parentId === c.parentId)
  const move = (c: Capability, by: number) => run(() => api.updateCapability(projectId!, c.id, {
    name: c.name, code: c.code, description: c.description, parentId: c.parentId, position: c.position + by,
  }))
  const descendants = (c: Capability) => {
    const start = all.indexOf(c)
    let n = 0
    for (let i = start + 1; i < all.length && all[i].level > c.level; i++) n++
    return n
  }

  return (
    <Box sx={{ p: 3, maxWidth: 1500 }}>
      <Stack direction="row" sx={{ alignItems: 'center', mb: 2 }} spacing={2}>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <Typography variant="h5">Capabilities</Typography>
            <ScreenHelp screen="capabilities" />
          </Stack>
          <Typography variant="body2" color="text.secondary">
            What the business does, from L1 ({levelNames[0]}) down to L8 ({levelNames[7]}), and the APIs that realise each capability.
          </Typography>
        </Box>
        <TextField size="small" sx={{ width: 240, flexShrink: 0 }} placeholder="Find a capability" value={query} onChange={(e) => setQuery(e.target.value)}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
        <Button onClick={() => setNaming(true)} sx={{ flexShrink: 0 }}>Level names</Button>
        <Button startIcon={<UploadFileIcon />} onClick={() => setImporting(true)} sx={{ flexShrink: 0 }}>Import…</Button>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing({ capability: null, parentId: null })} sx={{ flexShrink: 0, whiteSpace: 'nowrap' }}>
          New L1 capability
        </Button>
      </Stack>
      <ErrorSummary error={error} />

      <Box sx={{ display: 'grid', gridTemplateColumns: '400px minmax(0, 1fr)', gap: 3, alignItems: 'start' }}>
        <Card variant="outlined" sx={{ position: 'sticky', top: 16 }}>
          <CardHeader title="Capability map" subheader={`${all.length} capabilities · ${linked.size} of ${endpoints.data?.length ?? 0} APIs mapped`}
            slotProps={{ title: { variant: 'subtitle1' } }} />
          <List dense aria-label="Capability map" sx={{ maxHeight: 'calc(100vh - 280px)', overflow: 'auto', pt: 0 }}>
            <ListItemButton selected={!selected} onClick={() => select(null)}>
              <ListItemText primary="Overview" secondary="Levels and APIs not mapped yet" />
            </ListItemButton>
            {all.length === 0 && (
              <Typography variant="body2" color="text.secondary" sx={{ px: 2, py: 1 }}>
                No capabilities yet. Start with an L1 capability, such as a business domain.
              </Typography>
            )}
            {visible.map((c) => (
              <ListItemButton key={c.id} selected={c.id === selectedId} onClick={() => select(c.id)} sx={{ pl: 1 + (c.level - 1) * 2 }}
                aria-label={`${c.name}, L${c.level}`}>
                <ListItemIcon sx={{ minWidth: 28 }}>
                  {c.childCount > 0
                    ? <IconButton size="small" aria-label={collapsed.has(c.id) ? `Expand ${c.name}` : `Collapse ${c.name}`}
                        onClick={(e) => {
                          e.stopPropagation()
                          setCollapsed((s) => { const n = new Set(s); if (n.has(c.id)) n.delete(c.id); else n.add(c.id); return n })
                        }}>
                        {collapsed.has(c.id) && !q ? <ChevronRightIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
                      </IconButton>
                    : null}
                </ListItemIcon>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flex: 1, minWidth: 0 }}>
                  <LevelChip level={c.level} name={c.levelName} />
                  <Typography variant="body2" noWrap sx={{ flex: 1, fontWeight: matches(c) && q ? 700 : 400 }}>{c.name}</Typography>
                  <Tooltip title={`${c.apiCount} API${c.apiCount === 1 ? '' : 's'} here and below`}>
                    <Chip size="small" variant="outlined" label={c.apiCount} sx={{ height: 20, fontSize: 11 }} />
                  </Tooltip>
                </Stack>
              </ListItemButton>
            ))}
          </List>
        </Card>

        {selected
          ? <CapabilityDetail projectId={projectId!} capability={selected} all={all} endpoints={endpoints.data ?? []}
              canMoveUp={selected.position > 0} canMoveDown={selected.position < siblings(selected).length - 1}
              onSelect={select} onEdit={() => setEditing({ capability: selected, parentId: selected.parentId })}
              onAddChild={() => setEditing({ capability: null, parentId: selected.id })}
              onDelete={() => setDeleting(selected)} onMove={(by) => move(selected, by)} onLink={() => setLinking(selected)}
              onUnlink={(endpointId) => run(() => api.setCapabilityApis(projectId!, selected.id, selected.endpointIds.filter((id) => id !== endpointId)))} />
          : <Overview levelNames={levelNames} all={all} unmapped={unmapped} projectId={projectId!} />}
      </Box>

      <CapabilityDialog projectId={projectId!} open={!!editing} editing={editing?.capability ?? null} parentId={editing?.parentId ?? null}
        all={all} onClose={() => setEditing(null)} onSaved={async (c) => { await refresh(); select(c.id) }} />
      <CapabilityImportDialog projectId={projectId!} open={importing} onClose={() => setImporting(false)} />
      <LevelNamesDialog projectId={projectId!} open={naming} levels={levelNames} onClose={() => setNaming(false)} />
      <LinkApisDialog projectId={projectId!} capability={linking} endpoints={endpoints.data ?? []} onClose={() => setLinking(null)} />
      <ConfirmDialog open={!!deleting} title={`Delete ${deleting?.name}?`} onClose={() => setDeleting(null)}
        onConfirm={() => { const d = deleting!; setDeleting(null); run(async () => { await api.deleteCapability(projectId!, d.id); select(d.parentId) }) }}>
        {deleting && descendants(deleting) > 0
          ? `The ${descendants(deleting)} capabilities below it are deleted too. The APIs stay; only their links to these capabilities go.`
          : 'The APIs stay; only their links to this capability go.'}
      </ConfirmDialog>
    </Box>
  )
}

function Overview({ levelNames, all, unmapped, projectId }: { levelNames: string[]; all: Capability[]; unmapped: Endpoint[]; projectId: string }) {
  const navigate = useNavigate()
  const imports = useCapabilityImports(projectId)
  return (
    <Stack spacing={3}>
      {(imports.data?.length ?? 0) > 0 && (
        <Card variant="outlined">
          <CardHeader title="Imports" subheader="Files read into drafts. Open one to review, edit and apply it."
            slotProps={{ title: { variant: 'subtitle1' } }} />
          <Table size="small" aria-label="Imports">
            <TableBody>
              {imports.data!.map((i) => (
                <TableRow key={i.id} hover>
                  <TableCell><Link component="button" variant="body2" onClick={() => navigate(`/projects/${projectId}/capabilities/imports/${i.id}`)}>{i.fileName}</Link></TableCell>
                  <TableCell>{i.format}</TableCell>
                  <TableCell>{i.total} capabilities</TableCell>
                  <TableCell><Chip size="small" color={i.status === 'DRAFT' ? 'warning' : 'success'} label={i.status === 'DRAFT' ? 'Draft' : 'Applied'} /></TableCell>
                  <TableCell>{new Date(i.updatedAt).toLocaleString()}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}
      <Card variant="outlined">
        <CardHeader title="Levels" subheader="Rename them with Level names" slotProps={{ title: { variant: 'subtitle1' } }} />
        <Table size="small" aria-label="Levels">
          <TableBody>
            {levelNames.map((n, i) => (
              <TableRow key={i}>
                <TableCell sx={{ width: 60 }}><LevelChip level={i + 1} /></TableCell>
                <TableCell>{n}</TableCell>
                <TableCell align="right" sx={{ width: 160 }}>
                  <Typography variant="body2" color="text.secondary">{all.filter((c) => c.level === i + 1).length} capabilities</Typography>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>
      <Card variant="outlined">
        <CardHeader title={`APIs not mapped to a capability (${unmapped.length})`}
          subheader="Link each one to the capability it realises, from the capability or from the API's Definition tab"
          slotProps={{ title: { variant: 'subtitle1' } }} />
        <CardContent sx={{ pt: 0 }}>
          {unmapped.length === 0
            ? <Typography variant="body2" color="text.secondary">Every API is mapped.</Typography>
            : <Stack direction="row" useFlexGap spacing={1} sx={{ flexWrap: 'wrap' }} aria-label="APIs not mapped">
                {unmapped.map((e) => (
                  <Chip key={e.id} variant="outlined" clickable onClick={() => navigate(`/projects/${projectId}/endpoints/${e.id}`)}
                    label={<Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}><MethodChip endpoint={e} /><span>{e.name}</span></Stack>} />
                ))}
              </Stack>}
        </CardContent>
      </Card>
    </Stack>
  )
}

function CapabilityDetail({ projectId, capability: c, all, endpoints, canMoveUp, canMoveDown, onSelect, onEdit, onAddChild, onDelete, onMove, onLink, onUnlink }: {
  projectId: string
  capability: Capability
  all: Capability[]
  endpoints: Endpoint[]
  canMoveUp: boolean
  canMoveDown: boolean
  onSelect: (id: string | null) => void
  onEdit: () => void
  onAddChild: () => void
  onDelete: () => void
  onMove: (by: number) => void
  onLink: () => void
  onUnlink: (endpointId: string) => void
}) {
  const navigate = useNavigate()
  const apis = useCapabilityApis(projectId, c.id)
  const byId = new Map(endpoints.map((e) => [e.id, e]))
  const ancestors: Capability[] = []
  for (let p = c.parentId ? all.find((x) => x.id === c.parentId) : undefined; p; p = p.parentId ? all.find((x) => x.id === p!.parentId) : undefined) ancestors.unshift(p)
  const children = all.filter((x) => x.parentId === c.id)

  return (
    <Stack spacing={3}>
      <Card variant="outlined" aria-label="Capability">
        <CardHeader
          title={<Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}><LevelChip level={c.level} name={c.levelName} /><span>{c.name}</span>
            {c.code && <Typography component="span" variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace' }}>{c.code}</Typography>}</Stack>}
          subheader={
            <Breadcrumbs sx={{ fontSize: 13 }} aria-label="Path">
              {ancestors.map((a) => <Link key={a.id} component="button" variant="body2" onClick={() => onSelect(a.id)}>{a.name}</Link>)}
              <Typography variant="body2" color="text.secondary">L{c.level} · {c.levelName}</Typography>
            </Breadcrumbs>}
          action={<>
            <Tooltip title="Move up among its siblings"><span><IconButton aria-label="Move up" disabled={!canMoveUp} onClick={() => onMove(-1)}><ArrowUpwardIcon fontSize="small" /></IconButton></span></Tooltip>
            <Tooltip title="Move down among its siblings"><span><IconButton aria-label="Move down" disabled={!canMoveDown} onClick={() => onMove(1)}><ArrowDownwardIcon fontSize="small" /></IconButton></span></Tooltip>
            <Tooltip title="Edit, or move it under another capability"><IconButton aria-label="Edit capability" onClick={onEdit}><EditOutlinedIcon fontSize="small" /></IconButton></Tooltip>
            <Tooltip title="Delete it and everything below it"><IconButton aria-label="Delete capability" onClick={onDelete}><DeleteOutlinedIcon fontSize="small" /></IconButton></Tooltip>
          </>} />
        <CardContent sx={{ pt: 0 }}>
          <Typography variant="body2" color={c.description ? 'text.primary' : 'text.secondary'} sx={{ whiteSpace: 'pre-wrap' }}>
            {c.description || 'No description.'}
          </Typography>
        </CardContent>
      </Card>

      <Card variant="outlined">
        <CardHeader title={`API inventory at L${c.level} (${apis.data?.length ?? c.apiCount})`}
          subheader={`The APIs of ${c.name} and of every capability below it`}
          slotProps={{ title: { variant: 'subtitle1' } }}
          action={<Button startIcon={<LinkIcon />} onClick={onLink}>Link APIs…</Button>} />
        {apis.isLoading ? <CircularProgress sx={{ m: 2 }} /> : (
          <Table size="small" aria-label="API inventory">
            <TableHead>
              <TableRow>
                <TableCell>API</TableCell>
                <TableCell>Route or channel</TableCell>
                <TableCell>Linked to</TableCell>
                <TableCell align="right" sx={{ width: 56 }} />
              </TableRow>
            </TableHead>
            <TableBody>
              {apis.data?.length === 0 && (
                <TableRow><TableCell colSpan={4}><Typography variant="body2" color="text.secondary">
                  No APIs here or below yet. Use Link APIs, or link from an API's Definition tab.
                </Typography></TableCell></TableRow>
              )}
              {apis.data?.map((a) => {
                const e = byId.get(a.endpointId)
                return (
                  <TableRow key={a.endpointId} hover>
                    <TableCell>
                      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                        {e && <MethodChip endpoint={e} />}
                        <Link component="button" variant="body2" onClick={() => navigate(`/projects/${projectId}/endpoints/${a.endpointId}`)}>{a.name}</Link>
                      </Stack>
                      {a.summary && <Typography variant="caption" color="text.secondary">{a.summary}</Typography>}
                    </TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{a.kind === 'REST' ? `${a.httpMethod} ${a.path}` : `${a.operation} ${a.channel}`}</TableCell>
                    <TableCell>
                      <Stack direction="row" useFlexGap spacing={0.5} sx={{ flexWrap: 'wrap' }}>
                        {a.direct && <Chip size="small" color="primary" label="this capability" />}
                        {a.via.map((v) => (
                          <Chip key={v.capabilityId} size="small" variant="outlined" clickable onClick={() => onSelect(v.capabilityId)}
                            label={`L${v.level} ${v.name}`} />
                        ))}
                      </Stack>
                    </TableCell>
                    <TableCell align="right">
                      {a.direct && (
                        <Tooltip title="Unlink from this capability"><IconButton size="small" aria-label={`Unlink ${a.name}`} onClick={() => onUnlink(a.endpointId)}>
                          <LinkOffIcon fontSize="small" />
                        </IconButton></Tooltip>
                      )}
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        )}
      </Card>

      <Card variant="outlined">
        <CardHeader title={c.level < 8 ? `Below ${c.name} (L${c.level + 1})` : 'Below'}
          slotProps={{ title: { variant: 'subtitle1' } }}
          action={c.level < 8 ? <Button startIcon={<AddIcon />} onClick={onAddChild}>Add L{c.level + 1} capability</Button> : undefined} />
        <CardContent sx={{ pt: 0 }}>
          {c.level >= 8 && <Typography variant="body2" color="text.secondary">L8 is the most detailed level: nothing goes below it.</Typography>}
          {c.level < 8 && children.length === 0 && <Typography variant="body2" color="text.secondary">Nothing below it yet.</Typography>}
          <Stack spacing={1}>
            {children.map((k) => (
              <Stack key={k.id} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <LevelChip level={k.level} name={k.levelName} />
                <Link component="button" variant="body2" onClick={() => onSelect(k.id)}>{k.name}</Link>
                <Typography variant="caption" color="text.secondary">{k.apiCount} API{k.apiCount === 1 ? '' : 's'}</Typography>
              </Stack>
            ))}
          </Stack>
        </CardContent>
      </Card>
    </Stack>
  )
}

function CapabilityDialog({ projectId, open, editing, parentId, all, onClose, onSaved }: {
  projectId: string
  open: boolean
  editing: Capability | null
  parentId: string | null
  all: Capability[]
  onClose: () => void
  onSaved: (c: Capability) => void
}) {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{editing ? `Edit ${editing.name}` : 'New capability'}</DialogTitle>
      {open && <CapabilityForm key={editing?.id ?? `new-${parentId}`} projectId={projectId} editing={editing} parentId={parentId} all={all} onClose={onClose} onSaved={onSaved} />}
    </Dialog>
  )
}

function CapabilityForm({ projectId, editing, parentId, all, onClose, onSaved }: {
  projectId: string
  editing: Capability | null
  parentId: string | null
  all: Capability[]
  onClose: () => void
  onSaved: (c: Capability) => void
}) {
  const [draft, setDraft] = useState<CapabilityRequest>(() => ({
    name: editing?.name ?? '', code: editing?.code ?? '', description: editing?.description ?? '', parentId: editing ? editing.parentId : parentId,
  }))
  const [error, setError] = useState<unknown>(null)
  const [saving, setSaving] = useState(false)
  const err = (f: string) => errorText(error, f)
  // A capability can't go under itself or anything below it.
  const below = new Set<string>()
  if (editing) {
    const start = all.findIndex((c) => c.id === editing.id)
    below.add(editing.id)
    for (let i = start + 1; i < all.length && all[i].level > editing.level; i++) below.add(all[i].id)
  }
  const parents = all.filter((c) => !below.has(c.id) && c.level < 8)
  const parent = all.find((c) => c.id === draft.parentId)
  const level = parent ? parent.level + 1 : 1

  const save = async () => {
    setSaving(true)
    try {
      const body = { ...draft, code: draft.code || null, description: draft.description || null, parentId: draft.parentId || null }
      const saved = editing ? await api.updateCapability(projectId, editing.id, body) : await api.createCapability(projectId, body)
      onSaved(saved)
      onClose()
    } catch (e) {
      setError(e)
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <DialogContent>
        <ErrorSummary error={error} />
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField select label="Parent capability" value={draft.parentId ?? ''} onChange={(e) => setDraft({ ...draft, parentId: e.target.value || null })}
            error={!!err('parentId')} helperText={err('parentId') ?? `It will be an L${level} capability`}
            slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }}>
            <MenuItem value="">None: an L1 capability</MenuItem>
            {parents.map((c) => <MenuItem key={c.id} value={c.id} sx={{ pl: 2 + (c.level - 1) * 2 }}>L{c.level} · {c.name}</MenuItem>)}
          </TextField>
          <TextField label="Name" required autoFocus value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })}
            error={!!err('name')} helperText={err('name') ?? 'What the business does, e.g. "Card payments" or "Issue refund"'} />
          <TextField label="Code (optional)" value={draft.code ?? ''} onChange={(e) => setDraft({ ...draft, code: e.target.value })}
            error={!!err('code')} helperText={err('code') ?? 'An id from your capability model, e.g. PAY.02.03'}
            slotProps={{ htmlInput: { style: { fontFamily: 'monospace' } } }} />
          <TextField label="Description (optional)" multiline minRows={3} value={draft.description ?? ''}
            onChange={(e) => setDraft({ ...draft, description: e.target.value })} error={!!err('description')} helperText={err('description')} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={save} disabled={saving}>{editing ? 'Save' : 'Create'}</Button>
      </DialogActions>
    </>
  )
}

function LevelNamesDialog({ projectId, open, levels, onClose }: { projectId: string; open: boolean; levels: string[]; onClose: () => void }) {
  const client = useQueryClient()
  const [names, setNames] = useState<string[]>(levels)
  const [error, setError] = useState<unknown>(null)
  const save = async (values: string[]) => {
    try {
      await api.setCapabilityLevels(projectId, values)
      await client.invalidateQueries({ queryKey: keys.capabilities(projectId) })
      onClose()
    } catch (e) {
      setError(e)
    }
  }
  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth slotProps={{ transition: { onEnter: () => { setNames(levels); setError(null) } } }}>
      <DialogTitle>Level names</DialogTitle>
      <DialogContent>
        <ErrorSummary error={error} />
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          What each level means in your organisation's capability model, from the broadest (L1) to the most detailed (L8).
        </Typography>
        <Stack spacing={1.5}>
          {names.map((n, i) => (
            <TextField key={i} size="small" label={`L${i + 1} name`} value={n} error={!!errorText(error, `levels[${i}]`)}
              helperText={errorText(error, `levels[${i}]`)} onChange={(e) => setNames(names.map((x, j) => (j === i ? e.target.value : x)))} />
          ))}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={() => save([])}>Use the defaults</Button>
        <Box sx={{ flex: 1 }} />
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={() => save(names)}>Save</Button>
      </DialogActions>
    </Dialog>
  )
}

function LinkApisDialog({ projectId, capability, endpoints, onClose }: {
  projectId: string
  capability: Capability | null
  endpoints: Endpoint[]
  onClose: () => void
}) {
  const client = useQueryClient()
  const [chosen, setChosen] = useState<Set<string>>(new Set())
  const [query, setQuery] = useState('')
  const [error, setError] = useState<unknown>(null)
  const q = query.trim().toLowerCase()
  const shown = endpoints.filter((e) => !q || e.name.toLowerCase().includes(q) || (e.path ?? e.channel ?? '').toLowerCase().includes(q))
  const save = async () => {
    try {
      await api.setCapabilityApis(projectId, capability!.id, [...chosen])
      await client.invalidateQueries({ queryKey: keys.capabilities(projectId) })
      onClose()
    } catch (e) {
      setError(e)
    }
  }
  return (
    <Dialog open={!!capability} onClose={onClose} maxWidth="sm" fullWidth
      slotProps={{ transition: { onEnter: () => { setChosen(new Set(capability?.endpointIds ?? [])); setQuery(''); setError(null) } } }}>
      <DialogTitle>APIs that realise {capability?.name}</DialogTitle>
      <DialogContent>
        <ErrorSummary error={error} />
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          Tick the APIs linked to this capability itself. APIs linked below it already show in its inventory.
        </Typography>
        <TextField size="small" fullWidth placeholder="Find an API" value={query} onChange={(e) => setQuery(e.target.value)} sx={{ mb: 1 }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
        <List dense aria-label="APIs" sx={{ maxHeight: 360, overflow: 'auto' }}>
          {shown.map((e) => (
            <ListItemButton key={e.id} onClick={() => setChosen((s) => { const n = new Set(s); if (n.has(e.id)) n.delete(e.id); else n.add(e.id); return n })}>
              <Checkbox edge="start" checked={chosen.has(e.id)} tabIndex={-1} slotProps={{ input: { 'aria-label': e.name } }} />
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <MethodChip endpoint={e} />
                <ListItemText primary={e.name} secondary={e.kind === 'REST' ? e.path : e.channel} />
              </Stack>
            </ListItemButton>
          ))}
        </List>
      </DialogContent>
      <DialogActions>
        <Typography variant="caption" color="text.secondary" sx={{ flex: 1, pl: 2 }}>{chosen.size} selected</Typography>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={save}>Save links</Button>
      </DialogActions>
    </Dialog>
  )
}
