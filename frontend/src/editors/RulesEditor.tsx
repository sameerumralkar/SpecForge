import { useMemo, useState } from 'react'
import {
  Alert, Box, Button, Card, CardContent, CardHeader, Chip, CircularProgress, IconButton, Menu, MenuItem, Stack, Tooltip, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined'
import DragIndicatorIcon from '@mui/icons-material/DragIndicator'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import WarningAmberIcon from '@mui/icons-material/WarningAmber'
import {
  closestCenter, DndContext, KeyboardSensor, PointerSensor, useSensor, useSensors, type DragEndEvent,
} from '@dnd-kit/core'
import { arrayMove, SortableContext, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { keys, useMeta, useRefreshProject, useRuleChain } from '../api/hooks'
import type { Parameter, Payload, Rule, RuleChain, RuleRequest, RuleType, RuleWarning } from '../api/types'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ErrorSummary } from '../components/ErrorSummary'
import { currentPayloadId } from '../lib/fieldPaths'
import { defaultConfig, RULE_COLORS } from '../lib/rules'
import { RuleDialog, type RuleDraft } from './rules/RuleDialog'
import type { RuleFormContext } from './rules/inputs'

type Editing = { mode: 'create'; draft: RuleDraft; index: number } | { mode: 'edit'; rule: Rule; draft: RuleDraft; index: number }

/** The processing rules tab: an ordered, drag-and-drop list of rules with a plain-English summary. */
export function RulesEditor({ projectId, endpointId, payloads, parameters, endpointDirty }: {
  projectId: string
  endpointId: string
  payloads: Payload[]
  parameters: Parameter[]
  endpointDirty: boolean
}) {
  const chain = useRuleChain(projectId, endpointId)
  const meta = useMeta()
  const client = useQueryClient()
  const refresh = useRefreshProject()
  const [addMenu, setAddMenu] = useState<HTMLElement | null>(null)
  const [editing, setEditing] = useState<Editing | null>(null)
  const [dialogKey, setDialogKey] = useState(0)
  const [deleting, setDeleting] = useState<Rule | null>(null)
  const [error, setError] = useState<unknown>(null)
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }))

  const rules = useMemo(() => chain.data?.rules ?? [], [chain.data])
  const warningsByRule = useMemo(() => {
    const map = new Map<string, RuleWarning[]>()
    chain.data?.warnings.forEach((w) => map.set(w.ruleId, [...(map.get(w.ruleId) ?? []), w]))
    return map
  }, [chain.data])

  if (chain.error) return <ErrorSummary error={chain.error} />
  if (!chain.data) return <CircularProgress />

  const context = (index: number, self?: Rule): RuleFormContext => {
    const currentId = currentPayloadId(chain.data!.inputPayloadId, rules, index)
    return {
      payloads,
      current: payloads.find((p) => p.id === currentId),
      otherRules: rules.filter((r) => r.id !== self?.id).map((r) => r.name),
      parameters,
      meta: meta.data,
    }
  }

  const reorder = async (ids: string[]) => {
    const previous = chain.data
    // Show the new order at once; the server answer replaces it with fresh summaries.
    client.setQueryData<RuleChain>(keys.rules(projectId, endpointId), (c) =>
      c && { ...c, rules: ids.map((id) => c.rules.find((r) => r.id === id)!) })
    try {
      const updated = await api.reorderRules(projectId, endpointId, ids)
      client.setQueryData(keys.rules(projectId, endpointId), updated)
      setError(null)
    } catch (e) {
      client.setQueryData(keys.rules(projectId, endpointId), previous)
      setError(e)
    }
  }

  const onDragEnd = ({ active, over }: DragEndEvent) => {
    if (!over || active.id === over.id) return
    const from = rules.findIndex((r) => r.id === active.id)
    const to = rules.findIndex((r) => r.id === over.id)
    reorder(arrayMove(rules, from, to).map((r) => r.id))
  }

  const move = (index: number, by: number) => reorder(arrayMove(rules, index, index + by).map((r) => r.id))

  const openCreate = (type: RuleType) => {
    setAddMenu(null)
    setDialogKey((k) => k + 1)
    setEditing({ mode: 'create', index: rules.length, draft: { type, name: '', condition: '', config: defaultConfig(type) } })
  }

  const openEdit = (rule: Rule, index: number) => {
    setDialogKey((k) => k + 1)
    setEditing({ mode: 'edit', rule, index, draft: { type: rule.type, name: rule.name, condition: rule.condition ?? '', config: rule.config } })
  }

  const submit = async (request: RuleRequest) => {
    if (editing?.mode === 'edit') await api.updateRule(projectId, endpointId, editing.rule.id, request)
    else await api.createRule(projectId, endpointId, request)
    setEditing(null)
    await refresh(projectId)
  }

  const remove = async (rule: Rule) => {
    setDeleting(null)
    await api.deleteRule(projectId, endpointId, rule.id)
    await refresh(projectId)
  }

  return (
    <Box sx={{ display: 'flex', gap: 3, alignItems: 'flex-start', '@container (max-width: 980px)': { flexDirection: 'column', alignItems: 'stretch' } }}>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        {endpointDirty && (
          <Alert severity="info" sx={{ mb: 2 }}>The endpoint has unsaved changes. Rules are checked against the saved version.</Alert>
        )}
        <ErrorSummary error={error} />
        <Stack direction="row" sx={{ mb: 2, alignItems: 'center' }}>
          <Typography variant="body2" color="text.secondary" sx={{ flex: 1 }}>
            {chain.data.inputPayloadName
              ? <>Rules start with the <b>{chain.data.inputPayloadName}</b> and run top to bottom. Drag to reorder.</>
              : <>This endpoint has no input payload, so rules work with its parameters.</>}
          </Typography>
          <Button variant="contained" startIcon={<AddIcon />} onClick={(e) => setAddMenu(e.currentTarget)}>Add rule</Button>
          <Menu anchorEl={addMenu} open={!!addMenu} onClose={() => setAddMenu(null)}>
            {meta.data?.ruleTypes.map((t) => (
              <MenuItem key={t.type} onClick={() => openCreate(t.type)}>
                <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: RULE_COLORS[t.type], mr: 1.5 }} />{t.label}
              </MenuItem>
            ))}
          </Menu>
        </Stack>

        {rules.length === 0 ? (
          <Card variant="outlined"><CardContent><Typography color="text.secondary">No rules yet. The generated code will only pass data through.</Typography></CardContent></Card>
        ) : (
          <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
            <SortableContext items={rules.map((r) => r.id)} strategy={verticalListSortingStrategy}>
              <Stack spacing={1}>
                {rules.map((rule, i) => (
                  <RuleCard key={rule.id} rule={rule} index={i} count={rules.length} warnings={warningsByRule.get(rule.id) ?? []}
                    onEdit={() => openEdit(rule, i)} onDelete={() => setDeleting(rule)} onMove={(by) => move(i, by)} />
                ))}
              </Stack>
            </SortableContext>
          </DndContext>
        )}
      </Box>

      <Card variant="outlined" sx={{ width: 360, flexShrink: 0, position: 'sticky', top: 90, '@container (max-width: 980px)': { width: 'auto', position: 'static' } }}>
        <CardHeader title="In plain English" slotProps={{ title: { variant: 'subtitle1' } }} />
        <CardContent sx={{ pt: 0 }}>
          {chain.data.summary.length === 0
            ? <Typography variant="body2" color="text.secondary">Add rules to see what the endpoint will do.</Typography>
            : (
              <Stack component="ol" spacing={1} sx={{ m: 0, pl: 2.5 }} aria-label="Rule chain summary">
                {chain.data.summary.map((line, i) => (
                  <Typography component="li" variant="body2" key={i}>{line.replace(/^\d+\.\s*/, '')}</Typography>
                ))}
              </Stack>
            )}
          {chain.data.warnings.length > 0 && (
            <Alert severity="warning" sx={{ mt: 2 }}>
              {chain.data.warnings.length} warning{chain.data.warnings.length === 1 ? '' : 's'}: these will need fixing before code generation.
            </Alert>
          )}
        </CardContent>
      </Card>

      {editing && (
        <RuleDialog key={dialogKey} open title={editing.mode === 'edit' ? `Edit rule ${editing.index + 1}` : 'Add rule'}
          initial={editing.draft} ctx={context(editing.index, editing.mode === 'edit' ? editing.rule : undefined)}
          onSubmit={submit} onClose={() => setEditing(null)} />
      )}
      <ConfirmDialog open={!!deleting} title={`Delete rule "${deleting?.name}"?`} onConfirm={() => deleting && remove(deleting)} onClose={() => setDeleting(null)} />
    </Box>
  )
}

function RuleCard({ rule, index, count, warnings, onEdit, onDelete, onMove }: {
  rule: Rule
  index: number
  count: number
  warnings: RuleWarning[]
  onEdit: () => void
  onDelete: () => void
  onMove: (by: number) => void
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: rule.id })
  return (
    <Card ref={setNodeRef} variant="outlined" data-testid="rule-card"
      sx={{ transform: CSS.Transform.toString(transform), transition, opacity: isDragging ? 0.6 : 1, borderLeft: 4, borderLeftColor: RULE_COLORS[rule.type] }}>
      <Stack direction="row" spacing={1} sx={{ p: 1.5, alignItems: 'flex-start' }}>
        <IconButton aria-label={`Drag ${rule.name}`} sx={{ cursor: 'grab', mt: -0.5 }} {...attributes} {...listeners}>
          <DragIndicatorIcon fontSize="small" />
        </IconButton>
        <Typography variant="body2" color="text.secondary" sx={{ width: 18, pt: 0.25 }}>{index + 1}</Typography>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
            <Typography sx={{ fontWeight: 600 }}>{rule.name}</Typography>
            <Chip label={rule.typeLabel} sx={{ bgcolor: RULE_COLORS[rule.type], color: '#fff' }} />
            {rule.condition && <Chip variant="outlined" label={`only if ${rule.condition}`} sx={{ fontFamily: 'monospace' }} />}
            {warnings.length > 0 && (
              <Tooltip title={warnings.map((w) => w.message).join(' · ')}>
                <Chip icon={<WarningAmberIcon />} color="warning" label={warnings.length} />
              </Tooltip>
            )}
          </Stack>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>{rule.summary}</Typography>
          {warnings.map((w, i) => (
            <Typography key={i} variant="caption" color="warning.dark" sx={{ display: 'block' }}>⚠ {w.message}</Typography>
          ))}
        </Box>
        <Stack direction="row">
          <IconButton aria-label={`Move ${rule.name} up`} disabled={index === 0} onClick={() => onMove(-1)}><ArrowUpwardIcon fontSize="small" /></IconButton>
          <IconButton aria-label={`Move ${rule.name} down`} disabled={index === count - 1} onClick={() => onMove(1)}><ArrowDownwardIcon fontSize="small" /></IconButton>
          <IconButton aria-label={`Edit ${rule.name}`} onClick={onEdit}><EditOutlinedIcon fontSize="small" /></IconButton>
          <IconButton aria-label={`Delete ${rule.name}`} onClick={onDelete}><DeleteOutlinedIcon fontSize="small" /></IconButton>
        </Stack>
      </Stack>
    </Card>
  )
}
