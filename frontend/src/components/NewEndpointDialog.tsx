import { useState } from 'react'
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField, ToggleButton, ToggleButtonGroup } from '@mui/material'
import { api } from '../api/client'
import { useMeta, usePayloads, useRefreshProject } from '../api/hooks'
import type { Endpoint, EndpointKind, EndpointRequest, HttpMethod } from '../api/types'
import { syncPathParams } from '../lib/paths'
import { ErrorSummary } from './ErrorSummary'
import { errorText } from '../lib/errors'

/** Asks only what a new endpoint needs to be valid; everything else is edited afterwards. */
export function NewEndpointDialog({ projectId, open, onClose, onCreated }: {
  projectId: string
  open: boolean
  onClose: () => void
  onCreated: (endpoint: Endpoint) => void
}) {
  const meta = useMeta()
  const payloads = usePayloads(projectId)
  const refresh = useRefreshProject()
  const [kind, setKind] = useState<EndpointKind>('REST')
  const [name, setName] = useState('')
  const [method, setMethod] = useState<HttpMethod>('GET')
  const [path, setPath] = useState('/')
  const [channel, setChannel] = useState('')
  const [operation, setOperation] = useState<'SEND' | 'RECEIVE'>('RECEIVE')
  const [messagePayloadId, setMessagePayloadId] = useState('')
  const [error, setError] = useState<unknown>(null)

  const create = async () => {
    const body: EndpointRequest = kind === 'REST'
      ? { kind, name, tags: [], httpMethod: method, path, parameters: syncPathParams(path, []),
          responses: [{ statusCode: '200', description: 'OK' }], security: [] }
      : { kind, name, tags: [], channel, operation, protocol: 'KAFKA', messagePayloadId: messagePayloadId || null,
          parameters: [], responses: [], security: [] }
    try {
      const endpoint = await api.createEndpoint(projectId, body)
      await refresh(projectId)
      setError(null)
      setName('')
      onClose()
      onCreated(endpoint)
    } catch (e) {
      setError(e)
    }
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>New endpoint</DialogTitle>
      <DialogContent>
        <ErrorSummary error={error} />
        <Stack spacing={2} sx={{ pt: 1 }}>
          <ToggleButtonGroup exclusive size="small" value={kind} onChange={(_, v) => v && setKind(v)}>
            <ToggleButton value="REST">REST (OpenAPI)</ToggleButton>
            <ToggleButton value="ASYNC">Async (AsyncAPI)</ToggleButton>
          </ToggleButtonGroup>
          <TextField label="Name (operation id)" placeholder="createOrder" value={name} onChange={(e) => setName(e.target.value)}
            error={!!errorText(error, 'name')} helperText={errorText(error, 'name') ?? 'lowerCamelCase, e.g. createOrder'} autoFocus />
          {kind === 'REST' ? (
            <Stack direction="row" spacing={2}>
              <TextField select label="Method" value={method} onChange={(e) => setMethod(e.target.value as HttpMethod)} sx={{ width: 140 }} fullWidth={false}>
                {(meta.data?.httpMethods ?? ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']).map((m) => <MenuItem key={m} value={m}>{m}</MenuItem>)}
              </TextField>
              <TextField label="Path" value={path} onChange={(e) => setPath(e.target.value)} placeholder="/orders/{orderId}"
                error={!!errorText(error, 'path')} helperText={errorText(error, 'path') ?? 'Path parameters are added for each {name}'} />
            </Stack>
          ) : (
            <>
              <Stack direction="row" spacing={2}>
                <TextField select label="Operation" value={operation} onChange={(e) => setOperation(e.target.value as 'SEND' | 'RECEIVE')} sx={{ width: 160 }} fullWidth={false}>
                  <MenuItem value="RECEIVE">RECEIVE (listen)</MenuItem>
                  <MenuItem value="SEND">SEND (publish)</MenuItem>
                </TextField>
                <TextField label="Channel / topic" value={channel} onChange={(e) => setChannel(e.target.value)} placeholder="orders.created"
                  error={!!errorText(error, 'channel')} helperText={errorText(error, 'channel')} />
              </Stack>
              <TextField select label="Message payload" value={messagePayloadId} onChange={(e) => setMessagePayloadId(e.target.value)}
                error={!!errorText(error, 'messagePayloadId')} helperText={errorText(error, 'messagePayloadId') ?? (payloads.data?.length ? undefined : 'Create a payload first')}>
                {payloads.data?.map((p) => <MenuItem key={p.id} value={p.id}>{p.name}</MenuItem>)}
              </TextField>
            </>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={create}>Create</Button>
      </DialogActions>
    </Dialog>
  )
}
