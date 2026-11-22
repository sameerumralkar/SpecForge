import type { ReactNode } from 'react'
import { Button, Dialog, DialogActions, DialogContent, DialogTitle } from '@mui/material'

export function ConfirmDialog({ open, title, children, confirmLabel = 'Delete', onConfirm, onClose }: {
  open: boolean
  title: string
  children?: ReactNode
  confirmLabel?: string
  onConfirm: () => void
  onClose: () => void
}) {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      {children && <DialogContent>{children}</DialogContent>}
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button color="error" variant="contained" onClick={onConfirm}>
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
