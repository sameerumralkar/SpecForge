import { useState } from 'react'
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField } from '@mui/material'
import { api } from '../api/client'
import { useRefreshProject } from '../api/hooks'
import type { Payload } from '../api/types'
import { ErrorSummary } from './ErrorSummary'
import { errorText } from '../lib/errors'

export function NewPayloadDialog({ projectId, open, onClose, onCreated }: {
  projectId: string
  open: boolean
  onClose: () => void
  onCreated: (payload: Payload) => void
}) {
  const refresh = useRefreshProject()
  const [name, setName] = useState('')
  const [error, setError] = useState<unknown>(null)

  const create = async () => {
    try {
      const payload = await api.createPayload(projectId, { name, fields: [] })
      await refresh(projectId)
      setName('')
      setError(null)
      onClose()
      onCreated(payload)
    } catch (e) {
      setError(e)
    }
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>New payload</DialogTitle>
      <DialogContent>
        {errorText(error, 'name') ? null : <ErrorSummary error={error} />}
        <TextField label="Name" placeholder="OrderItem" value={name} onChange={(e) => setName(e.target.value)} autoFocus sx={{ mt: 1 }}
          onKeyDown={(e) => e.key === 'Enter' && create()}
          error={!!errorText(error, 'name')} helperText={errorText(error, 'name') ?? 'UpperCamelCase; add fields next'} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={create}>Create</Button>
      </DialogActions>
    </Dialog>
  )
}
