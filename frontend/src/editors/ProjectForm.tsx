import { FormControlLabel, MenuItem, Stack, Switch, TextField } from '@mui/material'
import type { ImplementationStyle, ProjectRequest } from '../api/types'
import { errorText } from '../lib/errors'
import { STYLE_LABEL } from '../lib/rules'

export function ProjectForm({ value, onChange, error }: {
  value: ProjectRequest
  onChange: (value: ProjectRequest) => void
  error: unknown
}) {
  const set = <K extends keyof ProjectRequest>(key: K, v: ProjectRequest[K]) => onChange({ ...value, [key]: v })
  const err = (f: string) => errorText(error, f)
  return (
    <Stack spacing={2} sx={{ pt: 1 }}>
      <TextField label="Name" value={value.name} onChange={(e) => set('name', e.target.value)} error={!!err('name')} helperText={err('name')} />
      <Stack direction="row" spacing={2}>
        <TextField label="Base package" value={value.basePackage} onChange={(e) => set('basePackage', e.target.value)}
          error={!!err('basePackage')} helperText={err('basePackage') ?? 'Java package of the generated code'} />
        <TextField label="Version" value={value.version} onChange={(e) => set('version', e.target.value)} sx={{ maxWidth: 180 }}
          error={!!err('version')} helperText={err('version')} />
      </Stack>
      <TextField label="Description" multiline minRows={2} value={value.description ?? ''} onChange={(e) => set('description', e.target.value)}
        error={!!err('description')} helperText={err('description')} />
      <TextField select label="Default implementation style" value={value.defaultImplementationStyle}
        onChange={(e) => set('defaultImplementationStyle', e.target.value as ImplementationStyle)}
        helperText="Each endpoint can override this">
        {(Object.keys(STYLE_LABEL) as ImplementationStyle[]).map((s) => <MenuItem key={s} value={s}>{STYLE_LABEL[s]}</MenuItem>)}
      </TextField>
      <FormControlLabel
        control={<Switch checked={value.allowCircularReferences} onChange={(e) => set('allowCircularReferences', e.target.checked)} />}
        label="Allow payloads to refer to each other in a circle (e.g. a tree of categories)"
      />
    </Stack>
  )
}
