import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import {
  Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, MenuItem, Stack, TextField,
} from '@mui/material'
import { api } from '../../api/client'
import type { Field, FieldDefinition, FieldType } from '../../api/types'
import { FieldConstraints } from '../../editors/FieldConstraints'
import { errorText } from '../../lib/errors'
import { emptyField, FIELD_TYPES } from '../../lib/mappers'
import { mono } from '../../theme'
import { ErrorSummary } from '../ErrorSummary'

/** The types a field-inventory entry can have: objects belong in the object inventory. */
export const PLAIN_TYPES: FieldType[] = FIELD_TYPES.filter((t) => t !== 'OBJECT')

/** Creates a field-inventory entry (optionally from an existing field), or edits one. */
export function FieldDefinitionDialog({ projectId, open, editing, initial, onClose, onSaved }: {
  projectId: string
  open: boolean
  editing?: FieldDefinition | null
  /** For a new entry: the field to start from, e.g. one being saved from a payload. */
  initial?: Field | null
  onClose: () => void
  onSaved?: (definition: FieldDefinition) => void
}) {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>{editing ? `Edit field ${editing.field.name}` : 'New inventory field'}</DialogTitle>
      {open && <FieldDefinitionForm key={editing?.id ?? 'new'} projectId={projectId} editing={editing} initial={initial} onClose={onClose} onSaved={onSaved} />}
    </Dialog>
  )
}

function FieldDefinitionForm({ projectId, editing, initial, onClose, onSaved }: {
  projectId: string
  editing?: FieldDefinition | null
  initial?: Field | null
  onClose: () => void
  onSaved?: (definition: FieldDefinition) => void
}) {
  const client = useQueryClient()
  const [draft, setDraft] = useState<Field>(() => {
    const start = editing?.field ?? initial ?? emptyField()
    return { ...start, refPayloadId: null, refPayloadName: null, errorCodeIds: start.errorCodeIds ?? [] }
  })
  const [error, setError] = useState<unknown>(null)
  const [saving, setSaving] = useState(false)
  const err = (f: string) => errorText(error, f)
  const patch = (p: Partial<Field>) => setDraft((d) => ({ ...d, ...p }))

  const save = async () => {
    setSaving(true)
    try {
      const body = { ...draft, name: draft.name.trim() }
      const saved = editing ? await api.updateFieldDefinition(projectId, editing.id, body) : await api.createFieldDefinition(projectId, body)
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
          <Stack direction="row" spacing={2} sx={{ alignItems: 'flex-start' }}>
            <TextField label="Name" required autoFocus value={draft.name} placeholder="currency" sx={{ flex: 1 }}
              onChange={(e) => patch({ name: e.target.value })}
              slotProps={{ htmlInput: { style: { fontFamily: mono.fontFamily } } }}
              error={!!err('name')} helperText={err('name') ?? 'lowerCamelCase, as it appears in payloads'} />
            <TextField select label="Type" value={draft.type ?? ''} sx={{ width: 170 }}
              onChange={(e) => {
                const type = e.target.value as FieldType
                patch({ type, itemType: type === 'ARRAY' ? (draft.itemType && draft.itemType !== 'OBJECT' ? draft.itemType : 'STRING') : null })
              }}
              error={!!err('type')} helperText={err('type')}>
              {PLAIN_TYPES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
            </TextField>
            {draft.type === 'ARRAY' && (
              <TextField select label="Item type" value={draft.itemType ?? ''} sx={{ width: 150 }}
                onChange={(e) => patch({ itemType: e.target.value as FieldType })} error={!!err('itemType')} helperText={err('itemType')}>
                {PLAIN_TYPES.filter((t) => t !== 'ARRAY').map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
              </TextField>
            )}
            <FormControlLabel sx={{ pt: 1 }} label="Required"
              control={<Checkbox checked={draft.required} onChange={(e) => patch({ required: e.target.checked })} />} />
          </Stack>
          <FieldConstraints field={draft} onChange={patch} err={err} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={save} disabled={saving}>{editing ? 'Save' : 'Add to inventory'}</Button>
      </DialogActions>
    </>
  )
}
