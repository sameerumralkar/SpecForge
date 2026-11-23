import { useState } from 'react'
import { Button, MenuItem, Stack, TextField, Tooltip, Typography } from '@mui/material'
import BookmarkAddIcon from '@mui/icons-material/BookmarkAdd'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { keys, useSnapshots } from '../api/hooks'
import type { SnapshotKind } from '../api/types'
import { snapshotLabel } from '../lib/changes'

/**
 * Chooses a snapshot to compare the current output with ("" = no comparison), and saves the
 * current output as a new snapshot.
 */
export function CompareBar({ projectId, kind, value, onChange, summary }: {
  projectId: string
  kind: SnapshotKind
  value: string
  onChange: (snapshotId: string) => void
  summary?: string
}) {
  const snapshots = useSnapshots(projectId, kind)
  const client = useQueryClient()
  const [saving, setSaving] = useState(false)

  const save = async () => {
    setSaving(true)
    try {
      await api.takeSnapshot(projectId, kind)
      await client.invalidateQueries({ queryKey: keys.snapshots(projectId, kind) })
    } finally {
      setSaving(false)
    }
  }

  return (
    <Stack direction="row" spacing={1} sx={{ px: 1.5, py: 0.75, alignItems: 'center', borderBottom: 1, borderColor: 'divider' }}>
      <TextField select value={value} onChange={(e) => onChange(e.target.value)} sx={{ minWidth: 260, flex: 1 }}
        slotProps={{ htmlInput: { 'aria-label': 'Compare with' }, select: { displayEmpty: true } }}>
        <MenuItem value="">No comparison</MenuItem>
        {snapshots.data?.map((s) => <MenuItem key={s.id} value={s.id}>Compare with {snapshotLabel(s)}</MenuItem>)}
      </TextField>
      {summary && <Typography variant="caption" color="text.secondary" sx={{ whiteSpace: 'nowrap' }}>{summary}</Typography>}
      <Tooltip title="Save the current output, to compare later changes with it. Downloads are saved too.">
        <span>
          <Button size="small" startIcon={<BookmarkAddIcon />} onClick={save} disabled={saving}>Save snapshot</Button>
        </span>
      </Tooltip>
    </Stack>
  )
}
