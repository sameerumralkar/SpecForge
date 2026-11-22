import { useState } from 'react'
import {
  Autocomplete, Box, IconButton, InputAdornment, MenuItem, Popover, TextField, Tooltip, Typography,
} from '@mui/material'
import FunctionsIcon from '@mui/icons-material/Functions'
import type { Meta, Parameter, Payload, RuleConfig } from '../../api/types'
import { mono } from '../../theme'

export interface RuleFormContext {
  payloads: Payload[]
  /** The payload this rule works on (the input, or the target of an earlier TRANSFORM). */
  current?: Payload
  /** Names of the endpoint's other rules. */
  otherRules: string[]
  parameters: Parameter[]
  meta?: Meta
}

export interface RuleFormProps {
  config: RuleConfig
  onChange: (config: RuleConfig) => void
  /** Server error for a config path such as "checks[0].value". */
  err: (path: string) => string | undefined
  ctx: RuleFormContext
}



export function FieldPathInput({ label, value, onChange, options, error, helperText }: {
  label: string
  value: string
  onChange: (value: string) => void
  options: string[]
  error?: string
  helperText?: string
}) {
  return (
    <Autocomplete
      freeSolo
      options={options}
      value={value}
      inputValue={value}
      onInputChange={(_, v) => onChange(v)}
      renderInput={(params) => (
        <TextField {...params} label={label} error={!!error} helperText={error ?? helperText}
          slotProps={{ ...params.slotProps, htmlInput: { ...params.slotProps.htmlInput, style: { fontFamily: mono.fontFamily } } }} />
      )}
    />
  )
}

/** A text box for the shared expression syntax, with a reference of operators and functions. */
export function ExpressionInput({ label, value, onChange, error, helperText, meta, placeholder }: {
  label: string
  value: string
  onChange: (value: string) => void
  error?: string
  helperText?: string
  meta?: Meta
  placeholder?: string
}) {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null)
  return (
    <>
      <TextField
        label={label}
        value={value}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        error={!!error}
        helperText={error ?? helperText}
        slotProps={{
          htmlInput: { style: { fontFamily: mono.fontFamily } },
          input: {
            endAdornment: (
              <InputAdornment position="end">
                <Tooltip title="Expression syntax">
                  <IconButton aria-label="Expression syntax" edge="end" onClick={(e) => setAnchor(e.currentTarget)}>
                    <FunctionsIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              </InputAdornment>
            ),
          },
        }}
      />
      <Popover open={!!anchor} anchorEl={anchor} onClose={() => setAnchor(null)} anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}>
        <ExpressionHelp meta={meta} />
      </Popover>
    </>
  )
}

export function ExpressionHelp({ meta }: { meta?: Meta }) {
  return (
    <Box sx={{ p: 2, maxWidth: 520 }}>
      <Typography variant="subtitle2" gutterBottom>Expression syntax</Typography>
      <Typography variant="body2" component="div" sx={{ '& code': mono, mb: 1 }}>
        Fields by name: <code>status</code>, <code>customer.email</code>; list items with <code>[]</code>: <code>items[].quantity</code>.
        Text in single quotes: <code>'PAID'</code>. Operators: <code>== != &lt; &lt;= &gt; &gt;=</code>, <code>+ - * / %</code>,
        <code> and</code>, <code>or</code>, <code>not</code>. A list path only works inside sum, count, min, max, any or all.
      </Typography>
      {meta?.expressionFunctions.map((f) => (
        <Typography key={f.name} variant="body2" sx={{ display: 'flex', gap: 1 }}>
          <Box component="code" sx={{ ...mono, color: 'primary.main', flexShrink: 0 }}>{f.example}</Box>
          <Box component="span" sx={{ color: 'text.secondary' }}>{f.description}</Box>
        </Typography>
      ))}
    </Box>
  )
}

export function SelectInput({ label, value, onChange, options, error, helperText, sx }: {
  label: string
  value: string | null | undefined
  onChange: (value: string) => void
  options: (string | { value: string; label: string })[]
  error?: string
  helperText?: string
  sx?: object
}) {
  return (
    <TextField select label={label} value={value ?? ''} onChange={(e) => onChange(e.target.value)} error={!!error} helperText={error ?? helperText} sx={sx}>
      {options.map((o) => {
        const opt = typeof o === 'string' ? { value: o, label: o } : o
        return <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>
      })}
    </TextField>
  )
}
