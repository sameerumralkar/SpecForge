import {
  Box, Button, Card, CardContent, CardHeader, Checkbox, IconButton, MenuItem, Stack, Table, TableBody, TableCell,
  TableHead, TableRow, TextField, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined'
import type { EndpointRequest, FieldType, Parameter, ParameterLocation, Payload, ResponseDef } from '../api/types'
import { ObjectDropZone } from '../components/inventory/ObjectDnd'
import { payloadOptions } from '../components/inventory/PayloadOptions'
import { errorText } from '../lib/errors'
import { mono } from '../theme'

const SCALARS: FieldType[] = ['STRING', 'INTEGER', 'LONG', 'NUMBER', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATE_TIME', 'UUID']

function PayloadSelect({ label, value, onChange, payloads, error, disabled, helper, allowNone = true }: {
  label: string
  value: string | null | undefined
  onChange: (id: string | null) => void
  payloads: Payload[]
  error?: string
  disabled?: boolean
  helper?: string
  allowNone?: boolean
}) {
  return (
    <TextField select label={label} value={value ?? ''} onChange={(e) => onChange(e.target.value || null)} disabled={disabled}
      error={!!error} helperText={error ?? helper}>
      {allowNone && <MenuItem value=""><em>None</em></MenuItem>}
      {payloadOptions(payloads)}
    </TextField>
  )
}

/** Request/Response tab: parameters, request body and responses (REST) or message and headers (async). */
export function RequestResponse({ value, onChange, payloads, error }: {
  value: EndpointRequest
  onChange: (value: EndpointRequest) => void
  payloads: Payload[]
  error: unknown
}) {
  const err = (f: string) => errorText(error, f)
  const rest = value.kind === 'REST'
  const locations: ParameterLocation[] = rest ? ['PATH', 'QUERY', 'HEADER'] : ['HEADER']
  const setParam = (i: number, patch: Partial<Parameter>) =>
    onChange({ ...value, parameters: value.parameters.map((p, j) => (j === i ? { ...p, ...patch } : p)) })
  const addParam = (location: ParameterLocation) =>
    onChange({ ...value, parameters: [...value.parameters, { name: '', location, type: 'STRING', required: location === 'PATH', description: '' }] })
  const setResponse = (i: number, patch: Partial<ResponseDef>) =>
    onChange({ ...value, responses: value.responses.map((r, j) => (j === i ? { ...r, ...patch } : r)) })
  const noBody = value.httpMethod === 'GET' || value.httpMethod === 'HEAD'

  return (
    <Stack spacing={3}>
      {!rest && (
        <ObjectDropZone id="message" label="the message payload" hint="Use {name} as the message"
          onDrop={(o) => onChange({ ...value, messagePayloadId: o.id })}>
          <Card variant="outlined">
            <CardContent>
              <PayloadSelect label="Message payload" value={value.messagePayloadId} allowNone={false}
                onChange={(id) => onChange({ ...value, messagePayloadId: id })} payloads={payloads} error={err('messagePayloadId')} />
            </CardContent>
          </Card>
        </ObjectDropZone>
      )}

      <Card variant="outlined">
        <CardHeader title={rest ? 'Parameters' : 'Message headers'} slotProps={{ title: { variant: 'subtitle1' } }}
          subheader={err('parameters')}
          action={
            <Stack direction="row">
              {locations.map((l) => (
                <Button key={l} startIcon={<AddIcon />} onClick={() => addParam(l)}>{rest ? l.toLowerCase() : 'header'}</Button>
              ))}
            </Stack>
          } />
        <Table>
          <TableHead>
            <TableRow>
              <TableCell sx={{ width: '24%' }}>Name</TableCell>
              {rest && <TableCell sx={{ width: 110 }}>In</TableCell>}
              <TableCell sx={{ width: 150 }}>Type</TableCell>
              <TableCell align="center" sx={{ width: 80 }}>Required</TableCell>
              <TableCell>Description</TableCell>
              <TableCell sx={{ width: 50 }} />
            </TableRow>
          </TableHead>
          <TableBody>
            {value.parameters.length === 0 && (
              <TableRow><TableCell colSpan={6}><Typography variant="body2" color="text.secondary">None.</Typography></TableCell></TableRow>
            )}
            {value.parameters.map((p, i) => {
              const at = `parameters[${i}].`
              const isPath = p.location === 'PATH'
              return (
                <TableRow key={i} sx={{ '& td': { verticalAlign: 'top' } }}>
                  <TableCell>
                    <TextField value={p.name} disabled={isPath}
                      onChange={(e) => setParam(i, { name: e.target.value })}
                      slotProps={{ htmlInput: { 'aria-label': `Parameter ${i + 1} name`, style: { fontFamily: mono.fontFamily } } }}
                      error={!!err(at + 'name')} helperText={err(at + 'name') ?? (isPath ? 'from the path' : undefined)} />
                  </TableCell>
                  {rest && <TableCell><Typography variant="body2" sx={{ pt: 1 }}>{p.location}</Typography></TableCell>}
                  <TableCell>
                    <TextField select value={p.type} slotProps={{ htmlInput: { 'aria-label': `Parameter ${i + 1} type` } }} onChange={(e) => setParam(i, { type: e.target.value as FieldType })}
                      error={!!err(at + 'type')} helperText={err(at + 'type')}>
                      {SCALARS.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
                    </TextField>
                  </TableCell>
                  <TableCell align="center">
                    <Checkbox checked={isPath || p.required} disabled={isPath} onChange={(e) => setParam(i, { required: e.target.checked })} />
                  </TableCell>
                  <TableCell>
                    <TextField value={p.description ?? ''} onChange={(e) => setParam(i, { description: e.target.value })} />
                  </TableCell>
                  <TableCell>
                    {!isPath && (
                      <IconButton aria-label={`Remove parameter ${i + 1}`} onClick={() => onChange({ ...value, parameters: value.parameters.filter((_, j) => j !== i) })}>
                        <DeleteOutlinedIcon fontSize="small" />
                      </IconButton>
                    )}
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </Card>

      {rest && (
        <>
          <ObjectDropZone id="request-body" label="the request body" hint="Use {name} as the request body" disabled={noBody}
            onDrop={(o) => onChange({ ...value, requestPayloadId: o.id })}>
            <Card variant="outlined">
              <CardContent>
                <PayloadSelect label="Request body" value={value.requestPayloadId} disabled={noBody && !value.requestPayloadId}
                  onChange={(id) => onChange({ ...value, requestPayloadId: id })} payloads={payloads} error={err('requestPayloadId')}
                  helper={noBody ? `${value.httpMethod} requests have no body` : undefined} />
              </CardContent>
            </Card>
          </ObjectDropZone>

          <Card variant="outlined">
            <CardHeader title="Responses" slotProps={{ title: { variant: 'subtitle1' } }}
              action={<Button startIcon={<AddIcon />} onClick={() => onChange({ ...value, responses: [...value.responses, { statusCode: '', description: '', payloadId: null }] })}>Add response</Button>} />
            {err('responses') && <Typography color="error" variant="body2" sx={{ px: 2 }}>{err('responses')}</Typography>}
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell sx={{ width: 130 }}>Status</TableCell>
                  <TableCell>Description</TableCell>
                  <TableCell sx={{ width: '30%' }}>Body</TableCell>
                  <TableCell sx={{ width: 50 }} />
                </TableRow>
              </TableHead>
              <TableBody>
                {value.responses.map((r, i) => {
                  const at = `responses[${i}].`
                  return (
                    <TableRow key={i} sx={{ '& td': { verticalAlign: 'top' } }}>
                      <TableCell>
                        <TextField value={r.statusCode} placeholder="200" slotProps={{ htmlInput: { 'aria-label': `Response ${i + 1} status` } }}
                          onChange={(e) => setResponse(i, { statusCode: e.target.value })} error={!!err(at + 'statusCode')} helperText={err(at + 'statusCode')} />
                      </TableCell>
                      <TableCell>
                        <TextField value={r.description ?? ''} onChange={(e) => setResponse(i, { description: e.target.value })} />
                      </TableCell>
                      <TableCell>
                        <ObjectDropZone id={`response-${i}`} label={`response ${r.statusCode || i + 1}`} hint="Use {name}"
                          onDrop={(o) => setResponse(i, { payloadId: o.id })}>
                          <Box>
                            <PayloadSelect label="" value={r.payloadId} onChange={(id) => setResponse(i, { payloadId: id })} payloads={payloads}
                              error={err(at + 'payloadId')} />
                          </Box>
                        </ObjectDropZone>
                      </TableCell>
                      <TableCell>
                        <IconButton aria-label={`Remove response ${i + 1}`} onClick={() => onChange({ ...value, responses: value.responses.filter((_, j) => j !== i) })}>
                          <DeleteOutlinedIcon fontSize="small" />
                        </IconButton>
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </Card>
        </>
      )}
    </Stack>
  )
}
