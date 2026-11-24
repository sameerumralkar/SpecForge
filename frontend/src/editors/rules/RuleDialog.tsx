import { useState } from 'react'
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Stack, TextField } from '@mui/material'
import { ApiError } from '../../api/client'
import type { RuleConfig, RuleRequest, RuleType } from '../../api/types'
import { ErrorSummary } from '../../components/ErrorSummary'
import { cleanConfig, defaultConfig } from '../../lib/rules'
import { RULE_FORMS } from './ruleForms'
import { ExpressionInput, SelectInput, type RuleFormContext } from './inputs'
import { ErrorCodePicker } from '../../components/errors/ErrorCodes'

const ERROR_CODE_HELP: Partial<Record<RuleType, string>> = {
  VALIDATE: 'Used by checks whose field has no code, and when the payload breaks its schema',
  ERROR_HANDLING: 'The error response answers with the first code (its status and message, unless the fallback sets them)',
}

export interface RuleDraft {
  type: RuleType
  name: string
  condition: string
  config: RuleConfig
  errorCodeIds?: string[]
}

/** Create or edit one rule: shared settings on top, the form for its type below. */
export function RuleDialog({ open, title, initial, ctx, onSubmit, onClose }: {
  open: boolean
  title: string
  initial: RuleDraft
  ctx: RuleFormContext
  onSubmit: (request: RuleRequest) => Promise<void>
  onClose: () => void
}) {
  const [draft, setDraft] = useState<RuleDraft>(initial)
  const [error, setError] = useState<unknown>(null)
  const [saving, setSaving] = useState(false)
  const Form = RULE_FORMS[draft.type]
  const apiError = error instanceof ApiError ? error : null
  const err = (path: string) => apiError?.errorFor(path === '' ? 'config' : `config.${path}`)

  const submit = async () => {
    setSaving(true)
    try {
      await onSubmit({
        type: draft.type,
        name: draft.name,
        condition: draft.condition.trim() || null,
        config: cleanConfig(draft.config) as RuleConfig,
        errorCodeIds: draft.errorCodeIds,
      })
    } catch (e) {
      setError(e)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <ErrorSummary error={error} />
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Stack direction="row" spacing={2}>
            <SelectInput label="Type" value={draft.type} sx={{ maxWidth: 240 }}
              onChange={(type) => setDraft({ ...draft, type: type as RuleType, config: defaultConfig(type as RuleType) })}
              options={(ctx.meta?.ruleTypes ?? []).map((t) => ({ value: t.type, label: t.label }))} />
            <TextField label="Name" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} autoFocus
              error={!!apiError?.errorFor('name')} helperText={apiError?.errorFor('name') ?? 'Short, unique on this endpoint, e.g. "Enrich customer"'} />
          </Stack>
          {draft.type !== 'ERROR_HANDLING' && (
            <ExpressionInput label="Only when (optional)" value={draft.condition} onChange={(condition) => setDraft({ ...draft, condition })}
              meta={ctx.meta} placeholder="e.g. total > 100" error={apiError?.errorFor('condition')}
              helperText={`Skip this step unless the condition holds${ctx.current ? ` (fields of ${ctx.current.name})` : ''}`} />
          )}
          <Divider />
          <Form config={draft.config} onChange={(config) => setDraft({ ...draft, config })} err={err} ctx={ctx} />
          <Divider />
          <ErrorCodePicker value={draft.errorCodeIds ?? []} onChange={(errorCodeIds) => setDraft({ ...draft, errorCodeIds })}
            label="Error codes of this step" error={apiError?.errorFor('errorCodeIds')}
            helperText={ERROR_CODE_HELP[draft.type] ?? 'Listed in the generated code and the specs'} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={submit} disabled={saving}>Save rule</Button>
      </DialogActions>
    </Dialog>
  )
}
