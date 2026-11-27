import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { Box, Chip, CircularProgress, IconButton, Stack, Tooltip, Typography } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { ApiError, api } from '../api/client'
import { keys, usePayloads, useRefreshProject } from '../api/hooks'
import type { Payload, PayloadUsage } from '../api/types'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined'
import { EditorHeader } from '../components/EditorHeader'
import { ErrorSummary } from '../components/ErrorSummary'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { UsagesDialog } from '../components/UsagesDialog'
import { PayloadEditor } from '../editors/PayloadEditor'
import { InventoryPalette } from '../components/inventory/InventoryPalette'
import { FieldPalette } from '../components/inventory/FieldPalette'
import { inventoryField } from '../lib/inventory'
import { ObjectDndProvider } from '../components/inventory/ObjectDnd'
import { toPayloadRequest } from '../lib/mappers'
import { useDraft } from '../lib/useDraft'
import { UnsavedChangesGuard } from '../components/UnsavedChangesGuard'

export function PayloadPage() {
  const { projectId, payloadId } = useParams()
  const payloads = usePayloads(projectId!)
  if (payloads.error) return <Box sx={{ p: 3 }}><ErrorSummary error={payloads.error} /></Box>
  if (!payloads.data) return <CircularProgress sx={{ m: 4 }} />
  const payload = payloads.data.find((p) => p.id === payloadId)
  if (!payload) return <Typography sx={{ p: 3 }}>This payload no longer exists.</Typography>
  return <PayloadPageContent key={payload.id} projectId={projectId!} payload={payload} payloads={payloads.data} />
}

function PayloadPageContent({ projectId, payload, payloads }: { projectId: string; payload: Payload; payloads: Payload[] }) {
  const navigate = useNavigate()
  const refresh = useRefreshProject()
  const { draft, setDraft, dirty, saved, reset } = useDraft(toPayloadRequest(payload))
  const [error, setError] = useState<unknown>(null)
  const [saving, setSaving] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [blocked, setBlocked] = useState<PayloadUsage[] | null>(null)
  const usages = useQuery({ queryKey: [...keys.payloads(projectId), payload.id, 'usages'], queryFn: () => api.payloadUsages(projectId, payload.id) })

  const save = async () => {
    setSaving(true)
    try {
      const updated = await api.updatePayload(projectId, payload.id, draft)
      saved(toPayloadRequest(updated))
      setError(null)
      await refresh(projectId)
    } catch (e) {
      setError(e)
    } finally {
      setSaving(false)
    }
  }

  const duplicate = async () => {
    const copy = await api.duplicatePayload(projectId, payload.id)
    await refresh(projectId)
    navigate(`/projects/${projectId}/payloads/${copy.id}`)
  }

  const remove = async () => {
    setConfirmDelete(false)
    try {
      await api.deletePayload(projectId, payload.id)
      await refresh(projectId)
      navigate(`/projects/${projectId}`)
    } catch (e) {
      if (e instanceof ApiError && e.usages.length) setBlocked(e.usages)
      else setError(e)
    }
  }

  const goTo = (u: PayloadUsage) =>
    navigate(u.kind === 'PAYLOAD_FIELD' ? `/projects/${projectId}/payloads/${u.ownerId}` : `/projects/${projectId}/endpoints/${u.ownerId}`)

  return (
    <>
      <UnsavedChangesGuard dirty={dirty} />
      <EditorHeader
        help="payload"
        title={payload.name}
        subtitle={payload.role === 'OBJECT' ? 'Inventory object' : 'Structure'}
        dirty={dirty}
        saving={saving}
        onSave={save}
        onDiscard={() => { reset(); setError(null) }}
        actions={
          <>
            <Tooltip title="Duplicate"><IconButton aria-label="Duplicate payload" onClick={duplicate}><ContentCopyIcon fontSize="small" /></IconButton></Tooltip>
            <Tooltip title="Delete"><IconButton aria-label="Delete payload" onClick={() => (usages.data?.length ? setBlocked(usages.data) : setConfirmDelete(true))}><DeleteOutlinedIcon fontSize="small" /></IconButton></Tooltip>
          </>
        }
      />
      <Box sx={{ p: 3, maxWidth: 1500 }}>
        <ErrorSummary error={error} />
        <Stack direction="row" spacing={1} useFlexGap sx={{ mb: 2, alignItems: 'center', flexWrap: 'wrap' }} aria-label="Used by">
          <Typography variant="body2" color="text.secondary">Used by:</Typography>
          {usages.data?.length === 0 && <Typography variant="body2" color="text.secondary">nothing yet</Typography>}
          {usages.data?.map((u, i) => (
            <Chip key={i} variant="outlined" clickable onClick={() => goTo(u)} label={<><b>{u.ownerName}</b> · {u.detail}</>} />
          ))}
        </Stack>
        <ObjectDndProvider>
          <Box sx={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 270px', gap: 3, alignItems: 'start' }}>
            <PayloadEditor value={draft} onChange={setDraft} payloads={payloads} selfId={payload.id} error={error} />
            <Stack spacing={2} sx={{ position: 'sticky', top: 16 }}>
              <FieldPalette projectId={projectId} onAdd={(d) => setDraft({ ...draft, fields: [...draft.fields, inventoryField(d, draft.fields)] })} />
              <InventoryPalette projectId={projectId} payloads={payloads} excludeId={payload.id} sticky={false} />
            </Stack>
          </Box>
        </ObjectDndProvider>
      </Box>
      <ConfirmDialog open={confirmDelete} title={`Delete payload ${payload.name}?`} onConfirm={remove} onClose={() => setConfirmDelete(false)}>
        This cannot be undone.
      </ConfirmDialog>
      <UsagesDialog projectId={projectId} payloadName={payload.name} usages={blocked} onClose={() => setBlocked(null)} />
    </>
  )
}
