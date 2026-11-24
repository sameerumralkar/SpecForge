import { useState } from 'react'
import {
  Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, List, ListItem, ListItemIcon,
  ListItemText, MenuItem, Stack, TextField, Typography,
} from '@mui/material'
import AccountTreeOutlinedIcon from '@mui/icons-material/AccountTreeOutlined'
import AddLinkIcon from '@mui/icons-material/AddLink'
import DataObjectIcon from '@mui/icons-material/DataObject'
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined'
import LinkOffIcon from '@mui/icons-material/LinkOff'
import { useQueries, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import { keys, useEndpoints, usePayloads } from '../../api/hooks'
import type { ErrorCode, ErrorLink } from '../../api/types'
import { mono } from '../../theme'
import { ErrorSummary } from '../ErrorSummary'

const sameLink = (a: ErrorLink, b: ErrorLink) =>
  a.kind === b.kind && (a.kind === 'FIELD' ? a.payloadId === b.payloadId && a.field === b.field : a.ruleId === b.ruleId)

export const linkIcon = (kind: ErrorLink['kind']) =>
  kind === 'FIELD' ? <DataObjectIcon fontSize="small" /> : kind === 'RULE' ? <AccountTreeOutlinedIcon fontSize="small" /> : <FactCheckOutlinedIcon fontSize="small" />

/** Links one error code to payload fields and processing rules; saves them all at once. */
export function ErrorLinksDialog({ projectId, code, onClose }: { projectId: string; code: ErrorCode | null; onClose: () => void }) {
  return (
    <Dialog open={!!code} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        Link <Box component="span" sx={{ fontFamily: mono.fontFamily }}>{code?.code}</Box>
        <Typography variant="body2" color="text.secondary">{code?.message}</Typography>
      </DialogTitle>
      {code && <LinksForm key={code.id} projectId={projectId} code={code} onClose={onClose} />}
    </Dialog>
  )
}

function LinksForm({ projectId, code, onClose }: { projectId: string; code: ErrorCode; onClose: () => void }) {
  const client = useQueryClient()
  const payloads = usePayloads(projectId).data ?? []
  const endpoints = useEndpoints(projectId).data ?? []
  const chains = useQueries({
    queries: endpoints.map((e) => ({ queryKey: keys.rules(projectId, e.id), queryFn: () => api.ruleChain(projectId, e.id) })),
  })
  const [links, setLinks] = useState<ErrorLink[]>(code.links.filter((l) => l.kind !== 'CHECK'))
  const checks = code.links.filter((l) => l.kind === 'CHECK')
  const [payloadId, setPayloadId] = useState('')
  const [field, setField] = useState('')
  const [endpointId, setEndpointId] = useState('')
  const [ruleId, setRuleId] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [saving, setSaving] = useState(false)

  const payload = payloads.find((p) => p.id === payloadId)
  const rules = chains[endpoints.findIndex((e) => e.id === endpointId)]?.data?.rules ?? []
  const add = (link: ErrorLink) => setLinks((ls) => (ls.some((l) => sameLink(l, link)) ? ls : [...ls, link]))
  const describe = (l: ErrorLink) => {
    if (l.detail) return l.detail
    if (l.kind === 'FIELD') return `${payloads.find((p) => p.id === l.payloadId)?.name ?? '?'}.${l.field}`
    const endpoint = endpoints[chains.findIndex((c) => c.data?.rules.some((r) => r.id === l.ruleId))]
    const rule = chains.flatMap((c) => c.data?.rules ?? []).find((r) => r.id === l.ruleId)
    return `${endpoint?.name ?? '?'} › ${rule?.name ?? '?'}`
  }

  const save = async () => {
    setSaving(true)
    try {
      await api.setErrorCodeLinks(projectId, code.id, links.map((l) => ({ kind: l.kind, payloadId: l.payloadId, field: l.field, ruleId: l.ruleId })))
      await client.invalidateQueries({ queryKey: ['projects', projectId] })
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
        <Typography variant="subtitle2">Linked to</Typography>
        <List dense aria-label="Links">
          {links.length === 0 && checks.length === 0 && (
            <Typography variant="body2" color="text.secondary" sx={{ py: 1 }}>Not linked yet. Add a field or a processing rule below.</Typography>
          )}
          {links.map((l) => (
            <ListItem key={l.kind + (l.ruleId ?? l.payloadId + '.' + l.field)}
              secondaryAction={<IconButton edge="end" aria-label={`Unlink ${describe(l)}`} onClick={() => setLinks(links.filter((x) => !sameLink(x, l)))}><LinkOffIcon fontSize="small" /></IconButton>}>
              <ListItemIcon sx={{ minWidth: 32 }}>{linkIcon(l.kind)}</ListItemIcon>
              <ListItemText primary={describe(l)} secondary={l.kind === 'FIELD' ? 'field: checks on it fail with this code' : 'processing rule'}
                slotProps={{ primary: { sx: { fontFamily: mono.fontFamily, fontSize: 13 } } }} />
            </ListItem>
          ))}
          {checks.map((l, i) => (
            <ListItem key={'check' + i}>
              <ListItemIcon sx={{ minWidth: 32 }}>{linkIcon('CHECK')}</ListItemIcon>
              <ListItemText primary={l.detail} secondary="a Validate check picks this code; change it in the rule"
                slotProps={{ primary: { sx: { fontFamily: mono.fontFamily, fontSize: 13 } } }} />
            </ListItem>
          ))}
        </List>

        <Stack spacing={2} sx={{ mt: 1 }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <Chip size="small" icon={linkIcon('FIELD')} label="Field" />
            <TextField select size="small" label="Payload" value={payloadId} sx={{ minWidth: 220 }}
              onChange={(e) => { setPayloadId(e.target.value); setField('') }}>
              {payloads.map((p) => <MenuItem key={p.id} value={p.id}>{p.name}</MenuItem>)}
            </TextField>
            <TextField select size="small" label="Field" value={field} sx={{ minWidth: 200 }} disabled={!payload}
              onChange={(e) => setField(e.target.value)}>
              {(payload?.fields ?? []).map((f) => <MenuItem key={f.name} value={f.name}>{f.name}</MenuItem>)}
            </TextField>
            <Button startIcon={<AddLinkIcon />} disabled={!payload || !field}
              onClick={() => { add({ kind: 'FIELD', payloadId, field }); setField('') }}>Link field</Button>
          </Stack>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <Chip size="small" icon={linkIcon('RULE')} label="Rule" />
            <TextField select size="small" label="Endpoint" value={endpointId} sx={{ minWidth: 220 }}
              onChange={(e) => { setEndpointId(e.target.value); setRuleId('') }}>
              {endpoints.map((e) => <MenuItem key={e.id} value={e.id}>{e.name}</MenuItem>)}
            </TextField>
            <TextField select size="small" label="Processing rule" value={ruleId} sx={{ minWidth: 200 }} disabled={!endpointId}
              onChange={(e) => setRuleId(e.target.value)}>
              {rules.map((r, i) => <MenuItem key={r.id} value={r.id}>{i + 1}. {r.name} ({r.typeLabel})</MenuItem>)}
            </TextField>
            <Button startIcon={<AddLinkIcon />} disabled={!ruleId} onClick={() => { add({ kind: 'RULE', ruleId }); setRuleId('') }}>Link rule</Button>
          </Stack>
          <Alert severity="info" variant="outlined">
            A check on a linked field fails with the field's first code. A Validate or Error handling rule answers with its first code
            when nothing more specific applies. Other rules list their codes in the generated code and the specs.
          </Alert>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={save} disabled={saving}>Save links</Button>
      </DialogActions>
    </>
  )
}
