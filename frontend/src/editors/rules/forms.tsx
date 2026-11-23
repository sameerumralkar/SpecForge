import {
  Autocomplete, Box, Button, Chip, FormControlLabel, IconButton, Paper, Stack, Switch, TextField, ToggleButton,
  ToggleButtonGroup, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined'
import { mono } from '../../theme'
import { ExpressionInput, FieldPathInput, SelectInput, type RuleFormProps } from './inputs'
import { get, num, pathOptions } from './configHelpers'

type Row = Record<string, unknown>

/** A list of rows with add/remove; each row is rendered by {@code render}. */
function Rows({ rows, onChange, render, addLabel, newRow, error }: {
  rows: Row[]
  onChange: (rows: Row[]) => void
  render: (row: Row, set: (patch: Row) => void, i: number) => React.ReactNode
  addLabel: string
  newRow: () => Row
  error?: string
}) {
  return (
    <Stack spacing={1}>
      {rows.map((row, i) => (
        <Paper key={i} variant="outlined" sx={{ p: 1.5, display: 'flex', gap: 1, alignItems: 'flex-start' }}>
          <Box sx={{ flex: 1 }}>{render(row, (patch) => onChange(rows.map((r, j) => (j === i ? { ...r, ...patch } : r))), i)}</Box>
          <IconButton aria-label={`Remove row ${i + 1}`} onClick={() => onChange(rows.filter((_, j) => j !== i))}><DeleteOutlinedIcon fontSize="small" /></IconButton>
        </Paper>
      ))}
      {error && <Typography color="error" variant="body2">{error}</Typography>}
      <Box><Button startIcon={<AddIcon />} onClick={() => onChange([...rows, newRow()])}>{addLabel}</Button></Box>
    </Stack>
  )
}

const s = (v: unknown) => (typeof v === 'string' ? v : '')
const n = (v: unknown) => (typeof v === 'number' ? String(v) : '')

// ---- VALIDATE ----

const CHECK_HELP: Record<string, string> = {
  REQUIRED: 'must be present', NOT_BLANK: 'text must not be blank', PATTERN: 'regular expression', MIN: 'smallest number',
  MAX: 'largest number', MIN_LENGTH: 'fewest characters or items', MAX_LENGTH: 'most characters or items', CUSTOM: 'condition that must hold',
}

export function ValidateForm({ config, onChange, err, ctx }: RuleFormProps) {
  const checks = get<Row[]>(config, 'checks', [])
  const options = pathOptions(ctx.current, ctx)
  return (
    <Stack spacing={2}>
      <Rows rows={checks} onChange={(rows) => onChange({ ...config, checks: rows })} addLabel="Add check" error={err('checks')}
        newRow={() => ({ field: '', kind: 'REQUIRED' })}
        render={(c, set, i) => {
          const kind = s(c.kind)
          return (
            <Stack spacing={1}>
              <Stack direction="row" spacing={1}>
                <Box sx={{ flex: 2 }}>
                  <FieldPathInput label="Field" value={s(c.field)} onChange={(field) => set({ field })} options={options} error={err(`checks[${i}].field`)} />
                </Box>
                <Box sx={{ flex: 1 }}>
                  <SelectInput label="Check" value={kind} onChange={(kind) => set({ kind, value: '' })} options={ctx.meta?.validateCheckKinds ?? Object.keys(CHECK_HELP)}
                    error={err(`checks[${i}].kind`)} />
                </Box>
                {kind !== 'REQUIRED' && kind !== 'NOT_BLANK' && kind !== 'CUSTOM' && (
                  <Box sx={{ flex: 2 }}>
                    <TextField label="Value" value={s(c.value)} onChange={(e) => set({ value: e.target.value })}
                      slotProps={{ htmlInput: { style: { fontFamily: mono.fontFamily } } }}
                      error={!!err(`checks[${i}].value`)} helperText={err(`checks[${i}].value`) ?? CHECK_HELP[kind]} />
                  </Box>
                )}
              </Stack>
              {kind === 'CUSTOM' && (
                <ExpressionInput label="Condition" value={s(c.value)} onChange={(value) => set({ value })} meta={ctx.meta}
                  placeholder="total > 0 or status == 'DRAFT'" error={err(`checks[${i}].value`)} />
              )}
              <TextField label="Message (optional)" value={s(c.message)} onChange={(e) => set({ message: e.target.value })} />
            </Stack>
          )
        }} />
      <TextField label="HTTP status on failure" type="number" value={n(config.failureStatus)} sx={{ maxWidth: 220 }}
        onChange={(e) => onChange({ ...config, failureStatus: num(e.target.value) })} error={!!err('failureStatus')} helperText={err('failureStatus') ?? '400–499'} />
    </Stack>
  )
}

// ---- TRANSFORM ----

export function TransformForm({ config, onChange, err, ctx }: RuleFormProps) {
  const target = ctx.payloads.find((p) => p.id === config.targetPayloadId)
  const mappings = get<Row[]>(config, 'mappings', [])
  return (
    <Stack spacing={2}>
      <SelectInput label="Map to payload" value={s(config.targetPayloadId)} onChange={(id) => onChange({ ...config, targetPayloadId: id })}
        options={ctx.payloads.map((p) => ({ value: p.id, label: p.name }))} error={err('targetPayloadId')}
        helperText="Later steps work on this payload. Fields with the same name are copied automatically." />
      <Typography variant="subtitle2">Other mappings</Typography>
      <Rows rows={mappings} onChange={(rows) => onChange({ ...config, mappings: rows })} addLabel="Add mapping"
        newRow={() => ({ target: '', source: '' })}
        render={(m, set, i) => {
          const useExpr = m.expression != null && m.source == null
          return (
            <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
              <Box sx={{ flex: 2 }}>
                <FieldPathInput label={`${target?.name ?? 'Target'} field`} value={s(m.target)} onChange={(t) => set({ target: t })}
                  options={pathOptions(target, { ...ctx, parameters: [] })} error={err(`mappings[${i}].target`)} />
              </Box>
              <ToggleButtonGroup exclusive size="small" value={useExpr ? 'expr' : 'field'} sx={{ mt: 0.25, '& button': { textTransform: 'none' } }}
                onChange={(_, v) => v && (v === 'expr'
                  ? set({ expression: s(m.source), source: undefined })
                  : set({ source: s(m.expression), expression: undefined }))}>
                <ToggleButton value="field">field</ToggleButton>
                <ToggleButton value="expr">f(x)</ToggleButton>
              </ToggleButtonGroup>
              <Box sx={{ flex: 3 }}>
                {useExpr ? (
                  <ExpressionInput label="Expression" value={s(m.expression)} onChange={(expression) => set({ expression })} meta={ctx.meta}
                    placeholder="sum(items[].price)" error={err(`mappings[${i}].expression`) ?? err(`mappings[${i}]`)} />
                ) : (
                  <FieldPathInput label={`From ${ctx.current?.name ?? 'input'} field`} value={s(m.source)} onChange={(source) => set({ source })}
                    options={pathOptions(ctx.current, ctx)} error={err(`mappings[${i}].source`) ?? err(`mappings[${i}]`)} />
                )}
              </Box>
            </Stack>
          )
        }} />
    </Stack>
  )
}

// ---- ENRICH ----

export function EnrichForm({ config, onChange, err, ctx }: RuleFormProps) {
  const set = (patch: Row) => onChange({ ...config, ...patch })
  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={2}>
        <TextField label="Service name" placeholder="customer-service" value={s(config.serviceName)} onChange={(e) => set({ serviceName: e.target.value })}
          error={!!err('serviceName')} helperText={err('serviceName') ?? 'Becomes the client name and its base-URL property'} />
        <SelectInput label="Method" value={s(config.httpMethod)} onChange={(httpMethod) => set({ httpMethod })} sx={{ maxWidth: 130 }}
          options={['GET', 'POST', 'PUT', 'PATCH', 'DELETE']} />
      </Stack>
      <TextField label="URL" placeholder="/customers/{customerId}" value={s(config.url)} onChange={(e) => set({ url: e.target.value })}
        slotProps={{ htmlInput: { style: { fontFamily: mono.fontFamily } } }}
        error={!!err('url')} helperText={err('url') ?? 'Relative to the service base URL; {field} inserts a value'} />
      <Stack direction="row" spacing={2}>
        <SelectInput label="Response payload (optional)" value={s(config.responsePayloadId)} onChange={(id) => set({ responsePayloadId: id || null })}
          options={[{ value: '', label: 'None' }, ...ctx.payloads.map((p) => ({ value: p.id, label: p.name }))]} />
        <FieldPathInput label="Store result in field" value={s(config.targetField)} onChange={(targetField) => set({ targetField })}
          options={pathOptions(ctx.current, { ...ctx, parameters: [] }, (t) => t === 'OBJECT')} error={err('targetField')} />
      </Stack>
      <Stack direction="row" spacing={2}>
        <TextField label="Timeout (ms)" type="number" value={n(config.timeoutMs)} onChange={(e) => set({ timeoutMs: num(e.target.value) })}
          error={!!err('timeoutMs')} helperText={err('timeoutMs')} sx={{ maxWidth: 200 }} />
        <SelectInput label="Camel mode" value={s(config.mode)} onChange={(mode) => set({ mode })} sx={{ maxWidth: 220 }}
          options={[{ value: 'ENRICH', label: 'enrich (call)' }, { value: 'POLL_ENRICH', label: 'pollEnrich (poll)' }]}
          helperText="Only used by processing routes" />
      </Stack>
    </Stack>
  )
}

// ---- CONDITIONAL_ROUTE ----

export function ConditionalForm({ config, onChange, err, ctx }: RuleFormProps) {
  return (
    <Stack spacing={2}>
      <Rows rows={get<Row[]>(config, 'branches', [])} onChange={(rows) => onChange({ ...config, branches: rows })} addLabel="Add branch"
        error={err('branches')} newRow={() => ({ name: '', condition: '', description: '' })}
        render={(b, set, i) => (
          <Stack spacing={1}>
            <Stack direction="row" spacing={1}>
              <Box sx={{ flex: 1 }}>
                <TextField label="Branch name" placeholder="priorityOrder" value={s(b.name)} onChange={(e) => set({ name: e.target.value })}
                  error={!!err(`branches[${i}].name`)} helperText={err(`branches[${i}].name`)} />
              </Box>
              <Box sx={{ flex: 2 }}>
                <ExpressionInput label={i === 0 ? 'If' : 'Else if'} value={s(b.condition)} onChange={(condition) => set({ condition })} meta={ctx.meta}
                  placeholder="total > 1000" error={err(`branches[${i}].condition`)} />
              </Box>
            </Stack>
            <TextField label="What happens (optional)" value={s(b.description)} onChange={(e) => set({ description: e.target.value })} />
          </Stack>
        )} />
      <TextField label="Otherwise (optional)" placeholder="continue with the next step" value={s(config.otherwise)}
        onChange={(e) => onChange({ ...config, otherwise: e.target.value })} />
    </Stack>
  )
}

// ---- PERSIST ----

export function PersistForm({ config, onChange, err, ctx }: RuleFormProps) {
  const set = (patch: Row) => onChange({ ...config, ...patch })
  return (
    <Stack spacing={2}>
    <Stack direction="row" spacing={2}>
      <TextField label="Entity name" placeholder="OrderEntity" value={s(config.entityName)} onChange={(e) => set({ entityName: e.target.value })}
        error={!!err('entityName')} helperText={err('entityName') ?? 'JPA entity class to generate'} />
      <SelectInput label="Operation" value={s(config.operation)} onChange={(operation) => set({ operation })} sx={{ maxWidth: 180 }}
        options={ctx.meta?.persistOperations ?? ['SAVE', 'UPDATE', 'DELETE', 'FIND_BY_ID']} />
      <FieldPathInput label="Id field" value={s(config.idField)} onChange={(idField) => set({ idField })} options={pathOptions(ctx.current, ctx)}
        error={err('idField')} helperText="Needed for UPDATE, DELETE and FIND_BY_ID" />
    </Stack>
    {config.operation === 'FIND_BY_ID' && (
      <FieldPathInput label="Load into field (optional)" value={s(config.targetField)} onChange={(targetField) => set({ targetField })}
        options={pathOptions(ctx.current, { ...ctx, parameters: [] }, (t) => t === 'OBJECT')} error={err('targetField')}
        helperText={`Keep the ${ctx.current?.name ?? "payload"} and put the loaded record into this field, e.g. to check it in later steps. Empty: the loaded record replaces the payload.`} />
    )}
    </Stack>
  )
}

// ---- PUBLISH_EVENT ----

export function PublishForm({ config, onChange, err, ctx }: RuleFormProps) {
  const set = (patch: Row) => onChange({ ...config, ...patch })
  const event = ctx.payloads.find((p) => p.id === config.eventPayloadId) ?? ctx.current
  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={2}>
        <SelectInput label="Protocol" value={s(config.protocol)} onChange={(protocol) => set({ protocol })} options={['KAFKA', 'JMS', 'AMQP']} sx={{ maxWidth: 140 }} />
        <TextField label="Topic / destination" placeholder="orders.created" value={s(config.destination)} onChange={(e) => set({ destination: e.target.value })}
          slotProps={{ htmlInput: { style: { fontFamily: mono.fontFamily } } }} error={!!err('destination')} helperText={err('destination')} />
      </Stack>
      <Stack direction="row" spacing={2}>
        <SelectInput label="Event payload" value={s(config.eventPayloadId)} onChange={(id) => set({ eventPayloadId: id || null })}
          options={[{ value: '', label: `Send the current payload${ctx.current ? ` (${ctx.current.name})` : ''}` }, ...ctx.payloads.map((p) => ({ value: p.id, label: p.name }))]} />
        {config.protocol === 'KAFKA' && (
          <FieldPathInput label="Message key field" value={s(config.keyField)} onChange={(keyField) => set({ keyField })}
            options={pathOptions(event, { ...ctx, parameters: [] })} error={err('keyField')} />
        )}
      </Stack>
    </Stack>
  )
}

// ---- SPLIT_AGGREGATE ----

export function SplitForm({ config, onChange, err, ctx }: RuleFormProps) {
  const set = (patch: Row) => onChange({ ...config, ...patch })
  const arrays = pathOptions(ctx.current, { ...ctx, parameters: [] }, (t) => t === 'ARRAY')
  const collection = s(config.collectionField)
  const itemPaths = pathOptions(ctx.current, { ...ctx, parameters: [] })
    .filter((p) => p.startsWith(collection + '[].'))
    .map((p) => p.slice(collection.length + 3))
  return (
    <Stack spacing={2}>
      <FieldPathInput label="List to split" value={collection} onChange={(collectionField) => set({ collectionField })} options={arrays}
        error={err('collectionField')} />
      <Stack direction="row" spacing={2} sx={{ alignItems: 'flex-start' }}>
        <SelectInput label="Then" value={s(config.aggregation)} onChange={(aggregation) => set({ aggregation })} sx={{ maxWidth: 260 }}
          options={[{ value: 'COLLECT_LIST', label: 'collect results into a list' }, { value: 'SUM', label: 'add up a field' },
            { value: 'COUNT', label: 'count the results' }, { value: 'NONE', label: 'nothing' }]} />
        {config.aggregation === 'SUM' && (
          <FieldPathInput label="Item field to add up" value={s(config.sumField)} onChange={(sumField) => set({ sumField })} options={itemPaths}
            error={err('sumField')} />
        )}
      </Stack>
      <FormControlLabel control={<Switch checked={config.parallel === true} onChange={(e) => set({ parallel: e.target.checked })} />}
        label="Process items in parallel" />
    </Stack>
  )
}

// ---- ERROR_HANDLING ----

export function ErrorHandlingForm({ config, onChange, err, ctx }: RuleFormProps) {
  const set = (patch: Row) => onChange({ ...config, ...patch })
  const retry = config.retry as Row | null | undefined
  const fallback = config.fallback as Row | null | undefined
  return (
    <Stack spacing={2}>
      {err('') && <Typography color="error" variant="body2">{err('')}</Typography>}
      <Stack direction="row" spacing={2}>
        <Autocomplete multiple options={ctx.meta?.errorCategories ?? ['ANY']} value={get<string[]>(config, 'on', [])}
          onChange={(_, on) => set({ on })} sx={{ flex: 1 }}
          renderValue={(values, getItemProps) => values.map((v, index) => <Chip label={v} {...getItemProps({ index })} key={v} />)}
          renderInput={(params) => <TextField {...params} label="On errors" helperText="Empty means any error" />} />
        <SelectInput label="Applies to" value={s(config.targetRule)} onChange={(t) => set({ targetRule: t || null })} sx={{ flex: 1 }}
          options={[{ value: '', label: 'The whole chain' }, ...ctx.otherRules.map((r) => ({ value: r, label: `Step "${r}"` }))]} />
      </Stack>

      <Paper variant="outlined" sx={{ p: 1.5 }}>
        <FormControlLabel label="Retry" control={<Switch checked={!!retry}
          onChange={(e) => set({ retry: e.target.checked ? { maxAttempts: 3, backoffMs: 500, multiplier: 2 } : null })} />} />
        {retry && (
          <Stack direction="row" spacing={2} sx={{ mt: 1 }}>
            <TextField label="Attempts" type="number" value={n(retry.maxAttempts)} onChange={(e) => set({ retry: { ...retry, maxAttempts: num(e.target.value) } })}
              error={!!err('retry.maxAttempts')} helperText={err('retry.maxAttempts') ?? '1–10'} />
            <TextField label="Backoff (ms)" type="number" value={n(retry.backoffMs)} onChange={(e) => set({ retry: { ...retry, backoffMs: num(e.target.value) } })}
              error={!!err('retry.backoffMs')} helperText={err('retry.backoffMs')} />
            <TextField label="Backoff multiplier" type="number" value={n(retry.multiplier)} onChange={(e) => set({ retry: { ...retry, multiplier: num(e.target.value) } })}
              error={!!err('retry.multiplier')} helperText={err('retry.multiplier') ?? '1–10'} />
          </Stack>
        )}
      </Paper>

      <Paper variant="outlined" sx={{ p: 1.5 }}>
        <FormControlLabel label="Fallback" control={<Switch checked={!!fallback}
          onChange={(e) => set({ fallback: e.target.checked ? { type: 'ERROR_RESPONSE', httpStatus: 503, message: '' } : null })} />} />
        {fallback && (
          <Stack direction="row" spacing={2} sx={{ mt: 1 }}>
            <SelectInput label="Type" value={s(fallback.type)} onChange={(type) => set({ fallback: { ...fallback, type } })} sx={{ maxWidth: 220 }}
              options={[{ value: 'ERROR_RESPONSE', label: 'Error response' }, { value: 'DEFAULT_RESPONSE', label: 'Default response' }]}
              error={err('fallback.type')} />
            <TextField label="HTTP status" type="number" value={n(fallback.httpStatus)} sx={{ maxWidth: 140 }}
              onChange={(e) => set({ fallback: { ...fallback, httpStatus: num(e.target.value) } })}
              error={!!err('fallback.httpStatus')} helperText={err('fallback.httpStatus')} />
            <TextField label="Message" value={s(fallback.message)} onChange={(e) => set({ fallback: { ...fallback, message: e.target.value } })} />
          </Stack>
        )}
      </Paper>

      <TextField label="Dead-letter destination (optional)" placeholder="orders.DLT" value={s(config.deadLetterDestination)}
        onChange={(e) => set({ deadLetterDestination: e.target.value || null })}
        slotProps={{ htmlInput: { style: { fontFamily: mono.fontFamily } } }}
        error={!!err('deadLetterDestination')} helperText={err('deadLetterDestination') ?? 'Where failed messages go after the retries'} />
    </Stack>
  )
}

