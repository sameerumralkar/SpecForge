import { useState } from 'react'
import {
  Button, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, Radio, RadioGroup, TextField,
} from '@mui/material'
import { api } from '../api/client'
import { useRefreshProject } from '../api/hooks'
import type { Payload, PayloadRole } from '../api/types'
import { ErrorSummary } from './ErrorSummary'
import { errorText } from '../lib/errors'

/** Creates a payload: an inventory object, or a structure (a body). {@code role} picks the default. */
export function NewPayloadDialog({ projectId, open, onClose, onCreated, role: initialRole = 'STRUCTURE' }: {
  projectId: string
  open: boolean
  onClose: () => void
  onCreated: (payload: Payload) => void
  role?: PayloadRole
}) {
  const refresh = useRefreshProject()
  const [name, setName] = useState('')
  const [role, setRole] = useState<PayloadRole>(initialRole)
  const [error, setError] = useState<unknown>(null)

  const create = async () => {
    try {
      const payload = await api.createPayload(projectId, { name, fields: [], role })
      await refresh(projectId)
      setName('')
      setRole(initialRole)
      setError(null)
      onClose()
      onCreated(payload)
    } catch (e) {
      setError(e)
    }
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{role === 'OBJECT' ? 'New inventory object' : 'New payload'}</DialogTitle>
      <DialogContent>
        {errorText(error, 'name') ? null : <ErrorSummary error={error} />}
        <TextField label="Name" placeholder="OrderItem" value={name} onChange={(e) => setName(e.target.value)} autoFocus sx={{ mt: 1 }}
          onKeyDown={(e) => e.key === 'Enter' && create()}
          error={!!errorText(error, 'name')} helperText={errorText(error, 'name') ?? 'UpperCamelCase; add fields next'} />
        <RadioGroup value={role} onChange={(e) => setRole(e.target.value as PayloadRole)} sx={{ mt: 1 }} aria-label="Kind of payload">
          <FormControlLabel value="OBJECT" control={<Radio size="small" />} label="Object: a reusable building block for the inventory (Money, Address)" />
          <FormControlLabel value="STRUCTURE" control={<Radio size="small" />} label="Structure: a request, response or message body" />
        </RadioGroup>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={create}>Create</Button>
      </DialogActions>
    </Dialog>
  )
}
