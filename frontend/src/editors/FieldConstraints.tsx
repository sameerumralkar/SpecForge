import { Autocomplete, Chip, Stack, TextField } from '@mui/material'
import type { Field, FieldType } from '../api/types'
import { ErrorCodePicker } from '../components/errors/ErrorCodes'
import { mono } from '../theme'

const NUMERIC: FieldType[] = ['INTEGER', 'LONG', 'NUMBER', 'DECIMAL']

function numberOrNull(s: string): number | null {
  return s.trim() === '' || Number.isNaN(Number(s)) ? null : Number(s)
}

/** A field's details and constraints: description, example, format, limits, pattern, allowed values and error codes. */
export function FieldConstraints({ field: f, onChange, err }: {
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
