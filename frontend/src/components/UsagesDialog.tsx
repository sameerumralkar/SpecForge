import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, List, ListItemButton, ListItemText } from '@mui/material'
import { useNavigate } from 'react-router'
import type { PayloadUsage } from '../api/types'

const KIND_LABEL: Record<PayloadUsage['kind'], string> = {
  PAYLOAD_FIELD: 'Payload',
  REQUEST_BODY: 'Endpoint',
  RESPONSE_BODY: 'Endpoint',
  MESSAGE: 'Endpoint',
  RULE: 'Endpoint',
}

/** Shown when a payload cannot be deleted: every place that still uses it, each a link. */
export function UsagesDialog({ projectId, payloadName, usages, onClose }: {
  projectId: string
  payloadName: string
  usages: PayloadUsage[] | null
  onClose: () => void
}) {
  const navigate = useNavigate()
  const go = (u: PayloadUsage) => {
    onClose()
    navigate(u.kind === 'PAYLOAD_FIELD'
      ? `/projects/${projectId}/payloads/${u.ownerId}`
      : `/projects/${projectId}/endpoints/${u.ownerId}/${u.kind === 'RULE' ? 'rules' : 'io'}`)
  }
  return (
    <Dialog open={!!usages} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{payloadName} is still in use</DialogTitle>
      <DialogContent>
        <DialogContentText>Remove these references before deleting it:</DialogContentText>
        <List dense>
          {usages?.map((u, i) => (
            <ListItemButton key={i} onClick={() => go(u)}>
              <ListItemText primary={`${KIND_LABEL[u.kind]} ${u.ownerName}`} secondary={u.detail} />
            </ListItemButton>
          ))}
        </List>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  )
}
