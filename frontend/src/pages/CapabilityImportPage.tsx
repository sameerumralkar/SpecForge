import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import {
  Alert, AlertTitle, Autocomplete, Box, Button, Card, CardHeader, Chip, CircularProgress, Collapse, Dialog, DialogActions, DialogContent,
  DialogTitle, IconButton, InputAdornment, MenuItem, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, ToggleButton,
  ToggleButtonGroup, Tooltip, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import CallMergeIcon from '@mui/icons-material/CallMerge'
import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import SearchIcon from '@mui/icons-material/Search'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { keys, useCapabilities, useCapabilityImport, useEndpoints } from '../api/hooks'
import type { CapabilityImport, FindingSeverity, ImportFinding, ImportNode, ImportNodeView, ImportResolution } from '../api/types'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ErrorSummary } from '../components/ErrorSummary'
import { ScreenHelp } from '../components/help/ScreenHelp'
import { errorText } from '../lib/errors'
import { LevelChip } from './CapabilitiesPage'

const SEVERITY: Record<FindingSeverity, { label: string; color: 'error' | 'warning' | 'info' | 'default' | 'success' }> = {
  ERROR: { label: 'blocks applying', color: 'error' },
  DUPLICATE: { label: 'possible duplicate', color: 'warning' },
  OVERLAP: { label: 'overlap', color: 'info' },
  WARNING: { label: 'warning', color: 'default' },
  INFO: { label: 'in the map', color: 'success' },
}

type Filter = 'all' | 'attention' | 'DUPLICATE' | 'OVERLAP' | 'ERROR' | 'CREATE' | 'MERGE'

const toNode = (n: ImportNodeView): ImportNode => ({
  key: n.key, parentKey: n.parentKey, name: n.name, code: n.code, description: n.description, apis: n.apis,
  resolution: n.resolution, mergeTargetId: n.mergeTargetId, sourceRow: n.sourceRow,
})

/**
 * An imported capability file, as a draft: its tree beside the existing map, every possible duplicate and
 * overlap highlighted, inline edits, and a choice per capability: create it, merge it into an existing one,
 * or skip it (with everything below). Apply makes the changes.
 */
export function CapabilityImportPage() {
  const { projectId, importId } = useParams()
  const navigate = useNavigate()
  const client = useQueryClient()
  const draft = useCapabilityImport(projectId!, importId!)
  const existing = useCapabilities(projectId)
  const endpoints = useEndpoints(projectId!)
  const [filter, setFilter] = useState<Filter>('all')
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<string | null>(null)
  const [editing, setEditing] = useState<ImportNodeView | 'new' | null>(null)
  const [newParent, setNewParent] = useState<string | null>(null)
  const [confirm, setConfirm] = useState<'apply' | 'discard' | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const [showWarnings, setShowWarnings] = useState(false)

  const caps = useMemo(() => existing.data ?? [], [existing.data])
  if (draft.error) return <Box sx={{ p: 3 }}><ErrorSummary error={draft.error} /></Box>
  if (!draft.data || !existing.data) return <CircularProgress sx={{ m: 4 }} />
  const d = draft.data
  const editable = d.status === 'DRAFT'
  const nodes = d.nodes
  const byKey = new Map(nodes.map((n) => [n.key, n]))
  const depth = (n: ImportNodeView) => { let k = 0; for (let p = n.parentKey; p; p = byKey.get(p)?.parentKey ?? null) k++; return k }
  const skipped = (n: ImportNodeView) => { for (let p: ImportNodeView | undefined = n; p; p = p.parentKey ? byKey.get(p.parentKey) : undefined) if (p.resolution === 'SKIP') return true; return false }

  const set = (next: CapabilityImport) => client.setQueryData([...keys.capabilities(projectId!), 'imports', importId], next)
  const save = async (changed: ImportNode[], targetParentId: string | null = d.targetParentId) => {
    setBusy(true)
    try {
      setError(null)
      set(await api.updateCapabilityImport(projectId!, importId!, targetParentId, changed))
    } catch (e) {
      setError(e)
    } finally {
      setBusy(false)
    }
  }
  const patch = (key: string, p: Partial<ImportNode>) => save(nodes.map((n) => (n.key === key ? { ...toNode(n), ...p } : toNode(n))))
  const resolve = (n: ImportNodeView, resolution: ImportResolution, mergeTargetId: string | null = null) =>
    patch(n.key, { resolution, mergeTargetId: resolution === 'MERGE' ? mergeTargetId ?? n.mergeTargetId ?? suggestion(n) : null })
  const remove = (key: string) => {
    const gone = new Set([key])
    let grew = true
    while (grew) { grew = false; for (const n of nodes) if (n.parentKey && gone.has(n.parentKey) && !gone.has(n.key)) { gone.add(n.key); grew = true } }
    save(nodes.filter((n) => !gone.has(n.key)).map(toNode))
  }
  const mergeExactMatches = () => save(nodes.map((n) => {
    const match = n.findings.find((f) => f.type === 'MATCH')
    return match && n.resolution === 'CREATE' ? { ...toNode(n), resolution: 'MERGE', mergeTargetId: match.existingId } : toNode(n)
  }))
  const apply = async () => {
    setConfirm(null)
    setBusy(true)
    try {
      setError(null)
      set(await api.applyCapabilityImport(projectId!, importId!))
      // The map changed; this draft's view is already up to date.
      await client.invalidateQueries({ queryKey: keys.capabilities(projectId!), predicate: (q) => q.queryKey[4] !== importId })
    } catch (e) {
      setError(e)
    } finally {
      setBusy(false)
    }
  }
  const discard = async () => {
    setConfirm(null)
    await api.discardCapabilityImport(projectId!, importId!)
    await client.invalidateQueries({ queryKey: keys.capabilities(projectId!) })
    navigate(`/projects/${projectId}/capabilities`)
  }

  const worst = (n: ImportNodeView) => n.findings.map((f) => f.severity)
  const q = query.trim().toLowerCase()
  const shown = nodes.filter((n) => {
    if (q && !n.name.toLowerCase().includes(q) && !(n.code ?? '').toLowerCase().includes(q)) return false
    switch (filter) {
      case 'all': return true
      case 'attention': return worst(n).some((s) => s === 'ERROR' || s === 'DUPLICATE' || s === 'OVERLAP')
      case 'CREATE': case 'MERGE': return n.resolution === filter && !skipped(n)
      default: return worst(n).includes(filter)
    }
  })
  const exactMatches = nodes.filter((n) => n.resolution === 'CREATE' && n.findings.some((f) => f.type === 'MATCH')).length
  const focus = selected ? byKey.get(selected) : undefined
  const related = new Map<string, FindingSeverity>()
  focus?.findings.forEach((f) => { if (f.existingId && !related.has(f.existingId)) related.set(f.existingId, f.severity) })
  if (focus?.mergeTargetId) related.set(focus.mergeTargetId, 'INFO')
  const s = d.summary

  return (
    <Box sx={{ p: 3, maxWidth: 1700 }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1 }}>
        <Tooltip title="Back to the capability map"><IconButton aria-label="Back to capabilities" onClick={() => navigate(`/projects/${projectId}/capabilities`)}><ArrowBackIcon /></IconButton></Tooltip>
        <Typography variant="h5">Import: {d.fileName}</Typography>
        <ScreenHelp screen="capability-import" />
        <Chip size="small" variant="outlined" label={d.format + (d.sheet ? ` · sheet ${d.sheet}` : '')} />
        <Chip size="small" color={d.status === 'DRAFT' ? 'warning' : 'success'} label={d.status === 'DRAFT' ? 'Draft: nothing changed yet' : 'Applied'} />
        <Box sx={{ flex: 1 }} />
        {editable && <Button color="inherit" onClick={() => setConfirm('discard')}>Discard</Button>}
        {editable && (
          <Tooltip title={s.errors > 0 ? 'Fix or skip the capabilities marked "blocks applying" first' : 'Create, merge and link as chosen below'}>
            <span><Button variant="contained" disabled={busy || s.errors > 0} onClick={() => setConfirm('apply')}>Apply import</Button></span>
          </Tooltip>
        )}
      </Stack>
      <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap', mb: 2 }} aria-label="Import summary">
        <Chip label={`${s.total} capabilities`} />
        <Chip color="primary" variant="outlined" label={`${s.create} new`} />
        <Chip color="success" variant="outlined" label={`${s.merge} merge into the map`} />
        {s.skip > 0 && <Chip variant="outlined" label={`${s.skip} skipped`} />}
        <Chip color="warning" label={`${s.duplicates} possible duplicates`} />
        <Chip color="info" label={`${s.overlaps} overlaps`} />
        {s.errors > 0 && <Chip color="error" label={`${s.errors} blocking`} />}
      </Stack>
      <ErrorSummary error={error} />
      {d.result && (
        <Alert severity="success" sx={{ mb: 2 }} action={<Button color="inherit" onClick={() => navigate(`/projects/${projectId}/capabilities`)}>Open the map</Button>}>
          <AlertTitle>Applied</AlertTitle>
          Created {d.result.created}, merged {d.result.merged}, skipped {d.result.skipped}; linked {d.result.apisLinked} API{d.result.apisLinked === 1 ? '' : 's'}.
        </Alert>
      )}
      {d.warnings.length > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }} action={<Button color="inherit" size="small" onClick={() => setShowWarnings((w) => !w)}>{showWarnings ? 'Hide' : 'Show'}</Button>}>
          {d.warnings.length} note{d.warnings.length === 1 ? '' : 's'} from reading the file.
          <Collapse in={showWarnings}><Box component="ul" sx={{ m: 0, pl: 2 }}>{d.warnings.map((w) => <li key={w}>{w}</li>)}</Box></Collapse>
        </Alert>
      )}

      <Stack direction="row" spacing={2} useFlexGap sx={{ mb: 2, alignItems: 'center', flexWrap: 'wrap' }}>
        <TextField select size="small" label="Import under" value={d.targetParentId ?? ''} disabled={!editable || busy} sx={{ minWidth: 320 }}
          onChange={(e) => save(nodes.map(toNode), e.target.value || null)} slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }}
          helperText={d.targetParentPath ? `Its top capabilities go below ${d.targetParentPath}` : 'Its top capabilities become L1'}>
          <MenuItem value="">The top of the map (L1)</MenuItem>
          {caps.filter((c) => c.level < 8).map((c) => <MenuItem key={c.id} value={c.id} sx={{ pl: 2 + (c.level - 1) * 2 }}>L{c.level} · {c.name}</MenuItem>)}
        </TextField>
        <ToggleButtonGroup size="small" exclusive value={filter} onChange={(_, f) => f && setFilter(f)} aria-label="Show">
          <ToggleButton value="all">All</ToggleButton>
          <ToggleButton value="attention">Needs a look</ToggleButton>
          <ToggleButton value="DUPLICATE">Duplicates</ToggleButton>
          <ToggleButton value="OVERLAP">Overlaps</ToggleButton>
          <ToggleButton value="ERROR">Blocking</ToggleButton>
          <ToggleButton value="CREATE">New</ToggleButton>
          <ToggleButton value="MERGE">Merging</ToggleButton>
        </ToggleButtonGroup>
        <TextField size="small" placeholder="Find an imported capability" value={query} onChange={(e) => setQuery(e.target.value)} sx={{ width: 240 }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
        {editable && exactMatches > 0 && (
          <Button startIcon={<CallMergeIcon />} onClick={mergeExactMatches}>Merge the {exactMatches} already in the map</Button>
        )}
        {editable && <Button startIcon={<AddIcon />} onClick={() => { setNewParent(null); setEditing('new') }}>Add capability</Button>}
        {busy && <CircularProgress size={18} />}
      </Stack>

      <Box sx={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 360px', gap: 3, alignItems: 'start' }}>
        <Card variant="outlined">
          <Table size="small" aria-label="Imported capabilities" sx={{ tableLayout: 'fixed', '& td': { verticalAlign: 'top' } }}>
            <TableHead>
              <TableRow>
                <TableCell sx={{ width: '28%' }}>Imported capability</TableCell>
                <TableCell sx={{ width: 140 }}>Code</TableCell>
                <TableCell>Compared with the map</TableCell>
                <TableCell sx={{ width: 230 }}>What applying does</TableCell>
                <TableCell sx={{ width: 104 }} />
              </TableRow>
            </TableHead>
            <TableBody>
              {shown.length === 0 && <TableRow><TableCell colSpan={5}><Typography variant="body2" color="text.secondary">Nothing to show.</Typography></TableCell></TableRow>}
              {shown.map((n) => {
                const i = nodes.indexOf(n)
                const off = skipped(n)
                return (
                  <TableRow key={n.key} hover selected={n.key === selected} onClick={() => setSelected(n.key)} sx={{ opacity: off ? 0.45 : 1, cursor: 'pointer' }}
                    aria-label={`Imported ${n.name}`}>
                    <TableCell>
                      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', pl: depth(n) * 1.5 }}>
                        <LevelChip level={n.level} />
                        {editable
                          ? <InlineText value={n.name} label={`Imported name ${i + 1}`} onCommit={(v) => v.trim() && patch(n.key, { name: v.trim() })}
                              error={errorText(error, `nodes[${i}].name`)} />
                          : <Typography variant="body2">{n.name}</Typography>}
                      </Stack>
                      <Typography variant="caption" color="text.secondary" sx={{ pl: depth(n) * 1.5, display: 'block', mt: 0.5 }}>
                        {n.sourceRow != null && `Row ${n.sourceRow}`}
                        {n.apis.length > 0 && ` · APIs: ${n.apis.join(', ')}`}
                        {n.description && ` · ${n.description.length > 80 ? n.description.slice(0, 77) + '…' : n.description}`}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      {editable
                        ? <InlineText value={n.code ?? ''} label={`Imported code ${i + 1}`} mono onCommit={(v) => patch(n.key, { code: v.trim() || null })} />
                        : <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>{n.code}</Typography>}
                    </TableCell>
                    <TableCell>
                      <Stack spacing={0.5}>
                        {n.findings.length === 0 && (n.resolution === 'MERGE'
                          ? <Typography variant="body2" color="success.main">Merges into {n.mergeTargetPath}</Typography>
                          : <Typography variant="body2" color="text.secondary">New: nothing like it in the map</Typography>)}
                        {n.findings.map((f, j) => <FindingRow key={j} finding={f} editable={editable && !off}
                          onMerge={f.existingId ? () => resolve(n, 'MERGE', f.existingId) : undefined} />)}
                      </Stack>
                    </TableCell>
                    <TableCell onClick={(e) => e.stopPropagation()}>
                      <Stack spacing={1}>
                        <TextField select size="small" value={n.resolution} disabled={!editable} label={`Resolution ${i + 1}`}
                          slotProps={{ inputLabel: { sx: { display: 'none' } }, htmlInput: { 'aria-label': `Resolution ${i + 1}` } }}
                          onChange={(e) => resolve(n, e.target.value as ImportResolution)}>
                          <MenuItem value="CREATE">Create as new</MenuItem>
                          <MenuItem value="MERGE">Merge into an existing one</MenuItem>
                          <MenuItem value="SKIP">Skip (and what is below)</MenuItem>
                        </TextField>
                        {n.resolution === 'MERGE' && (
                          <Autocomplete size="small" options={caps} value={caps.find((c) => c.id === n.mergeTargetId) ?? null} disabled={!editable}
                            getOptionLabel={(c) => `L${c.level} ${c.name}`} isOptionEqualToValue={(a, b) => a.id === b.id}
                            renderOption={(props, c) => {
                              const { key, ...rest } = props as typeof props & { key: string }
                              return <li key={key} {...rest}><Stack><Typography variant="body2">L{c.level} {c.name}</Typography>
                                <Typography variant="caption" color="text.secondary">{c.path.join(' › ')}</Typography></Stack></li>
                            }}
                            onChange={(_, c) => c && patch(n.key, { resolution: 'MERGE', mergeTargetId: c.id })}
                            renderInput={(params) => <TextField {...params} label={`Merge target ${i + 1}`} placeholder="Merge into…"
                              error={!!errorText(error, `nodes[${i}].mergeTargetId`)} />} />
                        )}
                      </Stack>
                    </TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                      {editable && <>
                        <Tooltip title="Edit details, or move it"><IconButton size="small" aria-label={`Edit ${n.name}`} onClick={() => setEditing(n)}><EditOutlinedIcon fontSize="small" /></IconButton></Tooltip>
                        <Tooltip title="Add a capability below it"><IconButton size="small" aria-label={`Add below ${n.name}`} onClick={() => { setNewParent(n.key); setEditing('new') }}><AddIcon fontSize="small" /></IconButton></Tooltip>
                        <Tooltip title="Remove it, and what is below it, from the import"><IconButton size="small" aria-label={`Remove ${n.name}`} onClick={() => remove(n.key)}><DeleteOutlinedIcon fontSize="small" /></IconButton></Tooltip>
                      </>}
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        </Card>

        <Card variant="outlined" sx={{ position: 'sticky', top: 16 }}>
          <CardHeader title="Your capability map" slotProps={{ title: { variant: 'subtitle1' } }}
            subheader={focus ? `Highlighted: what ${focus.name} matches, duplicates or overlaps` : 'Select an imported capability to see what it is compared with'} />
          <Box component="ul" aria-label="Existing capabilities" sx={{ listStyle: 'none', m: 0, px: 2, pb: 2, maxHeight: 'calc(100vh - 300px)', overflow: 'auto' }}>
            {caps.length === 0 && <Typography variant="body2" color="text.secondary">The map is empty: everything in the file is new.</Typography>}
            {caps.map((c) => {
              const sev = related.get(c.id)
              const flagged = nodes.filter((n) => n.findings.some((f) => f.existingId === c.id && f.severity !== 'INFO')).length
              return (
                <Box component="li" key={c.id} sx={{
                  pl: (c.level - 1) * 1.5, py: 0.25, borderRadius: 1,
                  bgcolor: sev ? 'action.selected' : undefined,
                  outline: sev ? '2px solid' : 'none', outlineColor: sev ? (SEVERITY[sev].color === 'default' ? 'grey.500' : `${SEVERITY[sev].color}.main`) : undefined,
                }} aria-current={sev ? 'true' : undefined}>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <LevelChip level={c.level} name={c.levelName} />
                    <Typography variant="body2" sx={{ flex: 1, fontWeight: sev ? 700 : 400 }}>{c.name}</Typography>
                    {flagged > 0 && <Tooltip title={`${flagged} imported capabilit${flagged === 1 ? 'y looks' : 'ies look'} like this one`}>
                      <Chip size="small" color="warning" variant="outlined" label={flagged} sx={{ height: 18, fontSize: 10 }} /></Tooltip>}
                  </Stack>
                </Box>
              )
            })}
          </Box>
        </Card>
      </Box>

      <NodeDialog open={!!editing} node={editing === 'new' ? null : editing} parentKey={editing === 'new' ? newParent : undefined}
        nodes={nodes} apis={(endpoints.data ?? []).map((e) => e.name)}
        onClose={() => setEditing(null)}
        onSave={async (next) => {
          const exists = nodes.some((n) => n.key === next.key)
          await save(exists ? nodes.map((n) => (n.key === next.key ? next : toNode(n))) : [...nodes.map(toNode), next])
          setEditing(null)
        }} />
      <ConfirmDialog open={confirm === 'apply'} title="Apply this import?" confirmLabel="Apply" onConfirm={apply} onClose={() => setConfirm(null)}>
        {s.create} capabilities will be created, {s.merge} merged into existing ones, and {s.skip} skipped.
        {s.duplicates > 0 && ` ${s.duplicates} still look like duplicates of capabilities in the map.`}
      </ConfirmDialog>
      <ConfirmDialog open={confirm === 'discard'} title="Discard this import?" confirmLabel="Discard" onConfirm={discard} onClose={() => setConfirm(null)}>
        The draft and your edits to it are deleted. The capability map is not changed.
      </ConfirmDialog>
    </Box>
  )

  /** The existing capability most like n: the exact match, else its strongest duplicate. */
  function suggestion(n: ImportNodeView): string | null {
    const ranked = [...n.findings].filter((f) => f.existingId).sort((a, b) => order(a) - order(b))
    return ranked[0]?.existingId ?? null
  }
}

const order = (f: ImportFinding) => ['INFO', 'DUPLICATE', 'ERROR', 'OVERLAP', 'WARNING'].indexOf(f.severity) * 1000 - (f.score ?? 0)

function FindingRow({ finding: f, editable, onMerge }: { finding: ImportFinding; editable: boolean; onMerge?: () => void }) {
  const sev = SEVERITY[f.severity]
  return (
    <Box>
      <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
        <Chip size="small" color={sev.color} variant={f.severity === 'OVERLAP' || f.severity === 'WARNING' ? 'outlined' : 'filled'} label={sev.label}
          sx={{ height: 20, fontSize: 11 }} />
        {editable && onMerge && f.type !== 'API_ALREADY_LINKED' && (
          <Tooltip describeChild title={`Merge into ${f.existingPath}: no second capability is created`}>
            <Button size="small" startIcon={<CallMergeIcon sx={{ fontSize: '14px !important' }} />} sx={{ minWidth: 0, py: 0, fontSize: 11 }}
              onClick={(e) => { e.stopPropagation(); onMerge() }}>Merge</Button>
          </Tooltip>
        )}
      </Stack>
      <Typography variant="caption" sx={{ display: 'block', lineHeight: 1.5, mt: 0.25 }}>{f.message}</Typography>
    </Box>
  )
}

/** A text field that saves when you leave it or press Enter. */
function InlineText({ value, label, onCommit, mono, error }: { value: string; label: string; onCommit: (v: string) => void; mono?: boolean; error?: string }) {
  const [text, setText] = useState(value)
  const [last, setLast] = useState(value)
  if (value !== last) { setLast(value); setText(value) }
  return (
    <TextField size="small" variant="standard" value={text} fullWidth onChange={(e) => setText(e.target.value)} error={!!error} helperText={error}
      onClick={(e) => e.stopPropagation()} onBlur={() => text !== value && onCommit(text)}
      onKeyDown={(e) => { if (e.key === 'Enter') (e.target as HTMLInputElement).blur() }}
      slotProps={{ htmlInput: { 'aria-label': label, style: mono ? { fontFamily: 'monospace', fontSize: 13 } : { fontSize: 14 } } }} />
  )
}

function NodeDialog({ open, node, parentKey, nodes, apis, onClose, onSave }: {
  open: boolean
  node: ImportNodeView | null
  /** For a new node: where it goes. */
  parentKey?: string | null
  nodes: ImportNodeView[]
  apis: string[]
  onClose: () => void
  onSave: (n: ImportNode) => Promise<void>
}) {
  const [draft, setDraft] = useState<ImportNode | null>(null)
  const below = new Set<string>()
  if (node) {
    below.add(node.key)
    let grew = true
    while (grew) { grew = false; for (const n of nodes) if (n.parentKey && below.has(n.parentKey) && !below.has(n.key)) { below.add(n.key); grew = true } }
  }
  const start = () => setDraft(node ? toNode(node) : {
    key: `new-${Date.now()}`, parentKey: parentKey ?? null, name: '', code: null, description: null, apis: [], resolution: 'CREATE', mergeTargetId: null, sourceRow: null,
  })
  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth slotProps={{ transition: { onEnter: start } }}>
      <DialogTitle>{node ? `Edit ${node.name}` : 'Add a capability to the import'}</DialogTitle>
      {draft && (
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField select label="Place in the import" value={draft.parentKey ?? ''} onChange={(e) => setDraft({ ...draft, parentKey: e.target.value || null })}
              slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }} helperText="Move it under another imported capability, or to the top of the import">
              <MenuItem value="">Top of the import</MenuItem>
              {nodes.filter((n) => !below.has(n.key)).map((n) => <MenuItem key={n.key} value={n.key}>{n.path.join(' › ')}</MenuItem>)}
            </TextField>
            <TextField label="Name" required autoFocus value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
            <TextField label="Code (optional)" value={draft.code ?? ''} onChange={(e) => setDraft({ ...draft, code: e.target.value || null })}
              slotProps={{ htmlInput: { style: { fontFamily: 'monospace' } } }} />
            <TextField label="Description (optional)" multiline minRows={3} value={draft.description ?? ''}
              onChange={(e) => setDraft({ ...draft, description: e.target.value || null })} />
            <Autocomplete multiple freeSolo options={apis} value={draft.apis} onChange={(_, v) => setDraft({ ...draft, apis: v as string[] })}
              renderValue={(values, getItemProps) => values.map((v, index) => <Chip size="small" label={v} {...getItemProps({ index })} key={v} />)}
              renderInput={(params) => <TextField {...params} label="APIs to link" placeholder="Endpoint name"
                helperText="Linked to the capability when the import is applied" />} />
          </Stack>
        </DialogContent>
      )}
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={!draft?.name.trim()} onClick={() => draft && onSave({ ...draft, name: draft.name.trim() })}>
          {node ? 'Save' : 'Add'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

