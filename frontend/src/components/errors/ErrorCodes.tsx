import { useState } from 'react'
import { useParams } from 'react-router'
import {
  Autocomplete, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField, Tooltip, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import ReportOutlinedIcon from '@mui/icons-material/ReportOutlined'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import { useErrorCodes, useMeta } from '../../api/hooks'
import type { ErrorCategory, ErrorCode, ErrorCodeRequest } from '../../api/types'
import { errorText } from '../../lib/errors'
import { mono } from '../../theme'
import { ErrorSummary } from '../ErrorSummary'

export const CATEGORIES: { category: ErrorCategory; defaultStatus: number; description: string }[] = [
  { category: 'VALIDATION', defaultStatus: 400, description: 'A value in the request is missing or malformed' },
  { category: 'BUSINESS', defaultStatus: 422, description: 'The request is well formed but breaks a business rule' },
  { category: 'NOT_FOUND', defaultStatus: 404, description: 'Something the request refers to does not exist' },
  { category: 'CONFLICT', defaultStatus: 409, description: 'The request clashes with the current state' },
  { category: 'SECURITY', defaultStatus: 403, description: 'The caller may not do this' },
  { category: 'DOWNSTREAM', defaultStatus: 503, description: 'A service this one depends on failed' },
  { category: 'TIMEOUT', defaultStatus: 504, description: 'A service this one depends on did not answer in time' },
  { category: 'PERSISTENCE', defaultStatus: 500, description: 'Saving or loading data failed' },
  { category: 'MESSAGING', defaultStatus: 500, description: 'Publishing or receiving a message failed' },
  { category: 'TECHNICAL', defaultStatus: 500, description: 'Any other internal failure' },
]

/** Chip colours by category: client mistakes, business refusals, and failures on our side. */
export function categoryColor(c: ErrorCategory): 'warning' | 'secondary' | 'error' | 'info' {
  if (c === 'VALIDATION') return 'warning'
  if (c === 'BUSINESS' || c === 'CONFLICT' || c === 'SECURITY') return 'secondary'
  if (c === 'NOT_FOUND') return 'info'
  return 'error'
}

export const useCategories = () => useMeta().data?.errorCodeCategories ?? CATEGORIES

const emptyRequest = (): ErrorCodeRequest => ({ code: '', message: '', httpStatus: null, category: 'VALIDATION', description: '' })

/** Create or edit one error code. */
export function ErrorCodeDialog({ projectId, open, editing, onClose, onSaved }: {
  projectId: string
  open: boolean
  editing?: ErrorCode | null
  onClose: () => void
  onSaved?: (code: ErrorCode) => void
}) {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{editing ? `Edit ${editing.code}` : 'New error code'}</DialogTitle>
      {open && <ErrorCodeForm key={editing?.id ?? 'new'} projectId={projectId} editing={editing} onClose={onClose} onSaved={onSaved} />}
    </Dialog>
  )
}

function ErrorCodeForm({ projectId, editing, onClose, onSaved }: {
  projectId: string
  editing?: ErrorCode | null
  onClose: () => void
  onSaved?: (code: ErrorCode) => void
}) {
  const client = useQueryClient()
  const categories = useCategories()
  const [draft, setDraft] = useState<ErrorCodeRequest>(() => editing
    ? { code: editing.code, message: editing.message, httpStatus: editing.httpStatus ?? null, category: editing.category, description: editing.description ?? '' }
    : emptyRequest())
  const [error, setError] = useState<unknown>(null)
  const [saving, setSaving] = useState(false)
  const err = (f: string) => errorText(error, f)
  const defaultStatus = categories.find((c) => c.category === draft.category)?.defaultStatus

  const save = async () => {
    setSaving(true)
    try {
      const body = { ...draft, code: draft.code.trim().toUpperCase(), description: draft.description || null }
      const saved = editing ? await api.updateErrorCode(projectId, editing.id, body) : await api.createErrorCode(projectId, body)
      await client.invalidateQueries({ queryKey: ['projects', projectId] })
      onSaved?.(saved)
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
          <Stack direction="row" spacing={2}>
            <TextField label="Code" required value={draft.code} autoFocus sx={{ maxWidth: 200 }}
              onChange={(e) => setDraft({ ...draft, code: e.target.value.toUpperCase() })}
              slotProps={{ htmlInput: { style: { fontFamily: mono.fontFamily } } }}
              error={!!err('code')} helperText={err('code') ?? 'e.g. PAY-001'} />
            <TextField select label="Category" value={draft.category} sx={{ flex: 1 }}
              onChange={(e) => setDraft({ ...draft, category: e.target.value as ErrorCategory })}
              error={!!err('category')} helperText={err('category') ?? categories.find((c) => c.category === draft.category)?.description}>
              {categories.map((c) => <MenuItem key={c.category} value={c.category}>{c.category} ({c.defaultStatus})</MenuItem>)}
            </TextField>
            <TextField label="HTTP status" type="number" value={draft.httpStatus ?? ''} sx={{ maxWidth: 130 }}
              placeholder={defaultStatus ? String(defaultStatus) : undefined}
              onChange={(e) => setDraft({ ...draft, httpStatus: e.target.value === '' ? null : Number(e.target.value) })}
              error={!!err('httpStatus')} helperText={err('httpStatus') ?? `default ${defaultStatus}`} />
          </Stack>
          <TextField label="Message" required value={draft.message} onChange={(e) => setDraft({ ...draft, message: e.target.value })}
            error={!!err('message')} helperText={err('message') ?? 'What callers see, e.g. "Insufficient funds"'} />
          <TextField label="Description (optional)" multiline minRows={2} value={draft.description ?? ''}
            onChange={(e) => setDraft({ ...draft, description: e.target.value })}
            error={!!err('description')} helperText={err('description') ?? 'When it happens and what the caller can do'} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={save} disabled={saving}>{editing ? 'Save' : 'Create'}</Button>
      </DialogActions>
    </>
  )
}

/** One code as a small chip, with its message and status on hover. */
export function ErrorCodeChip({ code, onDelete, ...rest }: { code: ErrorCode; onDelete?: (event: React.SyntheticEvent) => void } & Record<string, unknown>) {
  return (
    <Tooltip title={`${code.status} · ${code.message}`}>
      <Chip size="small" variant="outlined" color={categoryColor(code.category)} label={code.code} onDelete={onDelete}
        sx={{ fontFamily: mono.fontFamily, fontSize: 11 }} {...rest} />
    </Tooltip>
  )
}

/** The codes behind a list of ids, as chips. Ids of codes that no longer exist are left out. */
export function ErrorCodeChips({ ids }: { ids?: string[] | null }) {
  const { projectId } = useParams()
  const codes = useErrorCodes(projectId).data ?? []
  const shown = (ids ?? []).map((id) => codes.find((c) => c.id === id)).filter((c): c is ErrorCode => !!c)
  if (!shown.length) return null
  return (
    <Stack direction="row" useFlexGap spacing={0.5} sx={{ flexWrap: 'wrap', alignItems: 'center' }} aria-label="Error codes">
      <ReportOutlinedIcon sx={{ fontSize: 14, color: 'text.secondary' }} />
      {shown.map((c) => <ErrorCodeChip key={c.id} code={c} />)}
    </Stack>
  )
}

const NEW = '__new__'

/**
 * Picks error codes from the project's inventory, in order (the first is the one checks use).
 * "New error code…" creates one on the spot and adds it. Shows nothing outside a project.
 */
export function ErrorCodePicker({ value, onChange, label = 'Error codes', helperText, single, error }: {
  value: string[]
  onChange: (ids: string[]) => void
  label?: string
  helperText?: string
  /** Pick at most one code. */
  single?: boolean
  error?: string
}) {
  const { projectId } = useParams()
  const codes = useErrorCodes(projectId)
  const [creating, setCreating] = useState(false)
  if (!projectId) return null
  const all = codes.data ?? []
  const selected = value.map((id) => all.find((c) => c.id === id)).filter((c): c is ErrorCode => !!c)
  const options: (ErrorCode | typeof NEW)[] = [...all, NEW]

  return (
    <Box>
      <Autocomplete<ErrorCode | typeof NEW, boolean>
        multiple={!single}
        size="small"
        options={options}
        loading={codes.isLoading}
        value={single ? (selected[0] ?? null) : selected}
        isOptionEqualToValue={(a, b) => a !== NEW && b !== NEW && a.id === b.id}
        getOptionLabel={(o) => (o === NEW ? 'New error code…' : `${o.code} ${o.message}`)}
        filterOptions={(opts, { inputValue }) => {
          const q = inputValue.trim().toLowerCase()
          return opts.filter((o) => o === NEW || !q || o.code.toLowerCase().includes(q) || o.message.toLowerCase().includes(q))
        }}
        onChange={(_, v) => {
          const list = (Array.isArray(v) ? v : v ? [v] : []) as (ErrorCode | typeof NEW)[]
          if (list.includes(NEW)) {
            setCreating(true)
            return
          }
          onChange((list as ErrorCode[]).map((c) => c.id))
        }}
        renderOption={({ key, ...props }, o) => (
          <li key={key} {...props}>
            {o === NEW
              ? <Stack direction="row" spacing={1} sx={{ alignItems: 'center', color: 'primary.main' }}><AddIcon fontSize="small" /><span>New error code…</span></Stack>
              : <Box>
                  <Typography component="span" sx={{ fontFamily: mono.fontFamily, fontSize: 13, fontWeight: 600, mr: 1 }}>{o.code}</Typography>
                  <Typography component="span" variant="caption" color="text.secondary">{o.status} · {o.message}</Typography>
                </Box>}
          </li>
        )}
        renderValue={(v, getItemProps) => single
          ? (v && v !== NEW ? <ErrorCodeChip code={v as ErrorCode} /> : null)
          : (v as ErrorCode[]).map((c, index) => <ErrorCodeChip code={c} {...getItemProps({ index })} key={c.id} />)}
        renderInput={(params) => (
          <TextField {...params} label={label} placeholder={selected.length ? undefined : 'Pick from the error codes'}
            error={!!error} helperText={error ?? helperText} />
        )} />
      <ErrorCodeDialog projectId={projectId} open={creating} onClose={() => setCreating(false)}
        onSaved={(c) => onChange(single ? [c.id] : [...value, c.id])} />
    </Box>
  )
}
