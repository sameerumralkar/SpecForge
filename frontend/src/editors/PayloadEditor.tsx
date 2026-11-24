import { Fragment, lazy, Suspense, useState } from 'react'
import {
  Autocomplete, Box, Button, Card, CardContent, CardHeader, Checkbox, Chip, Collapse, IconButton, MenuItem, Stack,
  Table, TableBody, TableCell, TableHead, TableRow, TextField, ToggleButton, ToggleButtonGroup, Tooltip, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined'
import TuneIcon from '@mui/icons-material/Tune'
import type { Field, FieldType, Payload, PayloadRequest, PayloadRole } from '../api/types'
import { ObjectDropZone } from '../components/inventory/ObjectDnd'
import { payloadOptions } from '../components/inventory/PayloadOptions'
import { objectField } from '../lib/inventory'
import { ErrorCodeChips, ErrorCodePicker } from '../components/errors/ErrorCodes'
import { errorText } from '../lib/errors'
import { mono } from '../theme'
import { emptyField, FIELD_TYPES } from '../lib/mappers'

const CodeEditor = lazy(() => import('../components/CodeEditor'))

const NUMERIC: FieldType[] = ['INTEGER', 'LONG', 'NUMBER', 'DECIMAL']



/** Does this field point at another payload? */
const needsRef = (f: Field) => f.type === 'OBJECT' || (f.type === 'ARRAY' && f.itemType === 'OBJECT')

/** The number of constraints set on a field, shown on its "constraints" button. */
const constraintCount = (f: Field) =>
  [f.format, f.description, f.example, f.minLength, f.maxLength, f.minimum, f.maximum, f.pattern]
    .filter((v) => v !== null && v !== undefined && v !== '').length + (f.enumValues.length ? 1 : 0) + (f.errorCodeIds?.length ? 1 : 0)

export function PayloadEditor({ value, onChange, payloads, selfId, error }: {
  value: PayloadRequest
  onChange: (value: PayloadRequest) => void
  payloads: Payload[]
  selfId?: string
  error: unknown
}) {
  const [open, setOpen] = useState<Set<number>>(new Set())
  const err = (f: string) => errorText(error, f)

  const setFields = (fields: Field[]) => onChange({ ...value, fields })
  const setField = (i: number, patch: Partial<Field>) => setFields(value.fields.map((f, j) => (j === i ? { ...f, ...patch } : f)))
  const move = (i: number, by: number) => {
    const fields = [...value.fields]
    const [f] = fields.splice(i, 1)
    fields.splice(i + by, 0, f)
    setFields(fields)
  }
  const toggle = (i: number) => setOpen((s) => {
    const n = new Set(s)
    if (n.has(i)) n.delete(i)
    else n.add(i)
    return n
  })

  const changeType = (i: number, type: FieldType) => {
    const f = value.fields[i]
    setField(i, {
      type,
      itemType: type === 'ARRAY' ? (f.itemType ?? 'STRING') : null,
      refPayloadId: type === 'OBJECT' || (type === 'ARRAY' && f.itemType === 'OBJECT') ? f.refPayloadId : null,
    })
  }

  return (
    <Stack spacing={3}>
      <Card variant="outlined">
        <CardContent>
          <Stack spacing={2}>
            <TextField label="Name" value={value.name} onChange={(e) => onChange({ ...value, name: e.target.value })}
              error={!!err('name')} helperText={err('name') ?? 'UpperCamelCase. Renaming updates every reference.'} />
            <TextField label="Description" multiline minRows={2} value={value.description ?? ''}
              onChange={(e) => onChange({ ...value, description: e.target.value })} error={!!err('description')} helperText={err('description')} />
            <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
              <ToggleButtonGroup exclusive size="small" value={value.role ?? 'STRUCTURE'} aria-label="Kind of payload"
                onChange={(_, role: PayloadRole | null) => role && onChange({ ...value, role })}>
                <ToggleButton value="OBJECT">Inventory object</ToggleButton>
                <ToggleButton value="STRUCTURE">Structure</ToggleButton>
              </ToggleButtonGroup>
              <Typography variant="body2" color="text.secondary">
                {(value.role ?? 'STRUCTURE') === 'OBJECT'
                  ? 'A reusable building block: it appears in the object inventory, ready to drag into other payloads and bodies.'
                  : 'A request, response, message or working body, built from fields and inventory objects.'}
              </Typography>
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      <ObjectDropZone id="payload-fields" label="the field list" hint="Add {name} as a field"
        onDrop={(object) => setFields([...value.fields, objectField(object, value.fields)])}>
      <Card variant="outlined">
        <CardHeader title="Fields" slotProps={{ title: { variant: 'subtitle1' } }}
          action={<Button startIcon={<AddIcon />} onClick={() => setFields([...value.fields, emptyField()])}>Add field</Button>} />
        {err('fields') && <Typography color="error" variant="body2" sx={{ px: 2, pb: 1 }}>{err('fields')}</Typography>}
        <Table sx={{ tableLayout: 'fixed' }}>
          <TableHead>
            <TableRow>
              <TableCell sx={{ width: '28%' }}>Name</TableCell>
              <TableCell sx={{ width: 172 }}>Type</TableCell>
              <TableCell>Items / object</TableCell>
              <TableCell align="center" sx={{ width: 84 }}>Required</TableCell>
              <TableCell align="right" sx={{ width: 168 }} />
            </TableRow>
          </TableHead>
          <TableBody>
            {value.fields.length === 0 && (
              <TableRow><TableCell colSpan={5}><Typography color="text.secondary" variant="body2">No fields yet.</Typography></TableCell></TableRow>
            )}
            {value.fields.map((f, i) => {
              const at = `fields[${i}].`
              const n = constraintCount(f)
              return (
                <Fragment key={i}>
                  <TableRow sx={{ '& td': { borderBottom: open.has(i) ? 0 : undefined, verticalAlign: 'top' } }}>
                    <TableCell>
                      <TextField value={f.name} placeholder="fieldName"
                        onChange={(e) => setField(i, { name: e.target.value })}
                        error={!!err(at + 'name')} helperText={err(at + 'name')} slotProps={{ htmlInput: { 'aria-label': `Field ${i + 1} name`, style: { fontFamily: mono.fontFamily } } }} />
                      {!open.has(i) && <Box sx={{ mt: 0.5 }}><ErrorCodeChips ids={f.errorCodeIds} /></Box>}
                    </TableCell>
                    <TableCell>
                      <TextField select value={f.type ?? ''} slotProps={{ htmlInput: { 'aria-label': `Field ${i + 1} type` } }} onChange={(e) => changeType(i, e.target.value as FieldType)}
                        error={!!err(at + 'type')} helperText={err(at + 'type')}>
                        {FIELD_TYPES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
                      </TextField>
                    </TableCell>
                    <TableCell>
                      <Stack direction="row" spacing={1}>
                        {f.type === 'ARRAY' && (
                          <TextField select label="Item type" value={f.itemType ?? ''} sx={{ maxWidth: 150 }}
                            onChange={(e) => setField(i, { itemType: e.target.value as FieldType, refPayloadId: e.target.value === 'OBJECT' ? f.refPayloadId : null })}
                            error={!!err(at + 'itemType')} helperText={err(at + 'itemType')}>
                            {FIELD_TYPES.filter((t) => t !== 'ARRAY').map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
                          </TextField>
                        )}
                        {needsRef(f) && (
                          <TextField select label="Object" value={f.refPayloadId ?? ''} onChange={(e) => setField(i, { refPayloadId: e.target.value })}
                            error={!!err(at + 'refPayloadId')} helperText={err(at + 'refPayloadId')} slotProps={{ htmlInput: { 'aria-label': `Field ${i + 1} object` } }}>
                            {payloadOptions(payloads, selfId)}
                          </TextField>
                        )}
                      </Stack>
                    </TableCell>
                    <TableCell align="center">
                      <Checkbox checked={f.required} onChange={(e) => setField(i, { required: e.target.checked })} slotProps={{ input: { 'aria-label': `Field ${i + 1} required` } }} />
                    </TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      <Tooltip title="Constraints and details">
                        <IconButton aria-label={`Constraints of field ${i + 1}`} onClick={() => toggle(i)} color={n ? 'primary' : 'default'}>
                          <TuneIcon fontSize="small" />{n > 0 && <Typography variant="caption" sx={{ ml: 0.25 }}>{n}</Typography>}
                        </IconButton>
                      </Tooltip>
                      <IconButton aria-label={`Move field ${i + 1} up`} disabled={i === 0} onClick={() => move(i, -1)}><ArrowUpwardIcon fontSize="small" /></IconButton>
                      <IconButton aria-label={`Move field ${i + 1} down`} disabled={i === value.fields.length - 1} onClick={() => move(i, 1)}><ArrowDownwardIcon fontSize="small" /></IconButton>
                      <IconButton aria-label={`Remove field ${i + 1}`} onClick={() => setFields(value.fields.filter((_, j) => j !== i))}><DeleteOutlineIcon fontSize="small" /></IconButton>
                    </TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell colSpan={5} sx={{ py: 0, bgcolor: 'grey.50' }}>
                      <Collapse in={open.has(i) || Object.keys(fieldErrorsOf(error, at)).length > 0} unmountOnExit>
                        <FieldConstraints field={f} onChange={(patch) => setField(i, patch)} err={(k) => err(at + k)} />
                      </Collapse>
                    </TableCell>
                  </TableRow>
                </Fragment>
              )
            })}
          </TableBody>
        </Table>
      </Card>
      </ObjectDropZone>

      <Card variant="outlined">
        <CardHeader title="Example (JSON)" slotProps={{ title: { variant: 'subtitle1' } }} subheader="Used in the generated specs" />
        <CardContent sx={{ pt: 0 }}>
          <Box sx={{ border: 1, borderColor: err('example') ? 'error.main' : 'divider', borderRadius: 1, overflow: 'hidden' }}>
            <Suspense fallback={<Box sx={{ height: 180 }} />}>
              <CodeEditor value={value.example ?? ''} onChange={(example) => onChange({ ...value, example })} />
            </Suspense>
          </Box>
          {err('example') && <Typography color="error" variant="caption">{err('example')}</Typography>}
        </CardContent>
      </Card>
    </Stack>
  )
}

/** Server errors on a field's constraint inputs, so the details open when one of them is wrong. */
function fieldErrorsOf(error: unknown, prefix: string): Record<string, string> {
  const out: Record<string, string> = {}
  for (const k of ['format', 'description', 'example', 'minLength', 'maxLength', 'minimum', 'maximum', 'pattern', 'enumValues', 'errorCodeIds']) {
    const e = errorText(error, prefix + k)
    if (e) out[k] = e
  }
  return out
}

function numberOrNull(s: string): number | null {
  return s.trim() === '' || Number.isNaN(Number(s)) ? null : Number(s)
}

function FieldConstraints({ field: f, onChange, err }: {
  field: Field
  onChange: (patch: Partial<Field>) => void
  err: (key: string) => string | undefined
}) {
  const valueType = f.type === 'ARRAY' ? f.itemType : f.type
  const isString = valueType === 'STRING'
  const isNumeric = !!valueType && NUMERIC.includes(valueType)
  const hasLength = f.type === 'STRING' || f.type === 'ARRAY'
  const num = (key: 'minLength' | 'maxLength' | 'minimum' | 'maximum', label: string) => (
    <TextField label={label} type="number" value={f[key] ?? ''} onChange={(e) => onChange({ [key]: numberOrNull(e.target.value) })}
      error={!!err(key)} helperText={err(key)} sx={{ maxWidth: 160 }} />
  )
  return (
    <Stack spacing={2} sx={{ py: 2 }}>
      <Stack direction="row" spacing={2}>
        <TextField label="Description" value={f.description ?? ''} onChange={(e) => onChange({ description: e.target.value })} error={!!err('description')} helperText={err('description')} />
        <TextField label="Example" value={f.example ?? ''} onChange={(e) => onChange({ example: e.target.value })} sx={{ maxWidth: 220 }} />
        <TextField label="Format" placeholder="email, uri, …" value={f.format ?? ''} onChange={(e) => onChange({ format: e.target.value })} sx={{ maxWidth: 180 }}
          error={!!err('format')} helperText={err('format')} />
      </Stack>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'flex-start' }}>
        {hasLength && num('minLength', f.type === 'ARRAY' ? 'Min items' : 'Min length')}
        {hasLength && num('maxLength', f.type === 'ARRAY' ? 'Max items' : 'Max length')}
        {isNumeric && num('minimum', 'Minimum')}
        {isNumeric && num('maximum', 'Maximum')}
        {isString && (
          <TextField label="Pattern (regex)" value={f.pattern ?? ''} onChange={(e) => onChange({ pattern: e.target.value })}
            error={!!err('pattern')} helperText={err('pattern')} slotProps={{ htmlInput: { style: { fontFamily: mono.fontFamily } } }} />
        )}
      </Stack>
      {isString && (
        <Autocomplete multiple freeSolo options={[]} value={f.enumValues}
          onChange={(_, v) => onChange({ enumValues: v as string[] })}
          renderValue={(values, getItemProps) => values.map((v, index) => <Chip label={v} {...getItemProps({ index })} key={v} />)}
          renderInput={(params) => (
            <TextField {...params} label="Allowed values" placeholder="Type a value and press Enter"
              error={!!err('enumValues')} helperText={err('enumValues') ?? 'Leave empty to allow any text'} />
          )} />
      )}
      <ErrorCodePicker value={f.errorCodeIds ?? []} onChange={(errorCodeIds) => onChange({ errorCodeIds })} error={err('errorCodeIds')}
        helperText="What callers get when this field is wrong; checks on the field use the first code" />
    </Stack>
  )
}
