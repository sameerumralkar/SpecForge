import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { Badge, Box, CircularProgress, IconButton, Stack, Tab, Tabs, Tooltip, Typography } from '@mui/material'
import { ApiError, api } from '../api/client'
import { useEndpoints, usePayloads, useProject, useRefreshProject } from '../api/hooks'
import type { Endpoint, Payload, Project } from '../api/types'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined'
import { EditorHeader } from '../components/EditorHeader'
import { ErrorSummary } from '../components/ErrorSummary'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { MethodChip } from '../components/MethodChip'
import { EndpointDefinition } from '../editors/EndpointDefinition'
import { toEndpointRequest } from '../lib/mappers'
import { RequestResponse } from '../editors/RequestResponse'
import { EndpointCapabilities } from '../components/capabilities/EndpointCapabilities'
import { InventoryPalette } from '../components/inventory/InventoryPalette'
import { ObjectDndProvider } from '../components/inventory/ObjectDnd'
import { RulesEditor } from '../editors/RulesEditor'
import { ImplementationTab } from '../editors/ImplementationTab'
import { useDraft } from '../lib/useDraft'
import { UnsavedChangesGuard } from '../components/UnsavedChangesGuard'
import { syncPathParams } from '../lib/paths'
import { DiagramPanel } from '../components/DiagramPanel'

const TABS = ['definition', 'io', 'rules', 'implementation', 'diagrams'] as const
type TabId = (typeof TABS)[number]

/** Which tab shows the field a server error is about, so that tab can be flagged. */
function tabOf(field: string): TabId {
  if (/^(parameters|requestPayloadId|responses|messagePayloadId)/.test(field)) return 'io'
  if (field === 'implementationStyle') return 'implementation'
  return 'definition'
}

export function EndpointPage() {
  const { projectId, endpointId } = useParams()
  const endpoints = useEndpoints(projectId!)
  const payloads = usePayloads(projectId!)
  const project = useProject(projectId!)
  const error = endpoints.error ?? payloads.error ?? project.error
  if (error) return <Box sx={{ p: 3 }}><ErrorSummary error={error} /></Box>
  if (!endpoints.data || !payloads.data || !project.data) return <CircularProgress sx={{ m: 4 }} />
  const endpoint = endpoints.data.find((e) => e.id === endpointId)
  if (!endpoint) return <Typography sx={{ p: 3 }}>This endpoint no longer exists.</Typography>
  return <EndpointEditor key={endpoint.id} endpoint={endpoint} endpoints={endpoints.data} payloads={payloads.data} project={project.data} />
}

function EndpointEditor({ endpoint, endpoints, payloads, project }: {
  endpoint: Endpoint
  endpoints: Endpoint[]
  payloads: Payload[]
  project: Project
}) {
  const { tab: tabParam } = useParams()
  const tab: TabId = TABS.includes(tabParam as TabId) ? (tabParam as TabId) : 'definition'
  const navigate = useNavigate()
  const refresh = useRefreshProject()
  const { draft, setDraft, dirty, saved, reset } = useDraft(toEndpointRequest(endpoint))
  const [error, setError] = useState<unknown>(null)
  const [saving, setSaving] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const base = `/projects/${project.id}/endpoints/${endpoint.id}`

  const allTags = useMemo(() => [...new Set(endpoints.flatMap((e) => e.tags))].sort(), [endpoints])
  const tabsWithErrors = useMemo(
    () => new Set(error instanceof ApiError ? error.errors.map((e) => tabOf(e.field)) : []),
    [error],
  )

  const save = async () => {
    setSaving(true)
    try {
      const body = draft.kind === 'REST' ? { ...draft, parameters: syncPathParams(draft.path, draft.parameters) } : draft
      const updated = await api.updateEndpoint(project.id, endpoint.id, body)
      saved(toEndpointRequest(updated))
      setError(null)
      await refresh(project.id)
    } catch (e) {
      setError(e)
    } finally {
      setSaving(false)
    }
  }

  const duplicate = async () => {
    const copy = await api.duplicateEndpoint(project.id, endpoint.id)
    await refresh(project.id)
    navigate(`/projects/${project.id}/endpoints/${copy.id}`)
  }

  const remove = async () => {
    await api.deleteEndpoint(project.id, endpoint.id)
    await refresh(project.id)
    navigate(`/projects/${project.id}`)
  }

  const label = (id: TabId, text: string) => (
    <Badge color="error" variant="dot" invisible={!tabsWithErrors.has(id)}>{text}</Badge>
  )

  return (
    <>
      <UnsavedChangesGuard dirty={dirty} />
      <EditorHeader
        help={`endpoint-${tab}`}
        title={endpoint.name}
        chips={<MethodChip endpoint={endpoint} />}
        subtitle={endpoint.kind === 'REST'
          ? <code>{endpoint.path}</code>
          : <><code>{endpoint.channel}</code> · {endpoint.protocol}</>}
        dirty={dirty}
        saving={saving}
        onSave={save}
        onDiscard={() => { reset(); setError(null) }}
        actions={
          <>
            <Tooltip title="Duplicate"><IconButton aria-label="Duplicate endpoint" onClick={duplicate}><ContentCopyIcon fontSize="small" /></IconButton></Tooltip>
            <Tooltip title="Delete"><IconButton aria-label="Delete endpoint" onClick={() => setConfirmDelete(true)}><DeleteOutlinedIcon fontSize="small" /></IconButton></Tooltip>
          </>
        }
      />
      <Box sx={{ px: 3, borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
        <Tabs value={tab} variant="scrollable" scrollButtons="auto" onChange={(_, t: TabId) => {
          if (draft.kind === 'REST') setDraft({ ...draft, parameters: syncPathParams(draft.path, draft.parameters) })
          navigate(`${base}/${t}`)
        }}>
          <Tab value="definition" label={label('definition', 'Definition')} />
          <Tab value="io" label={label('io', 'Request / Response')} />
          <Tab value="rules" label={`Processing rules (${endpoint.ruleCount})`} />
          <Tab value="implementation" label={label('implementation', 'Implementation')} />
          <Tab value="diagrams" label="Diagrams" />
        </Tabs>
      </Box>
      <Box sx={{ p: 3, maxWidth: tab === 'rules' || tab === 'diagrams' ? undefined : tab === 'io' ? 1400 : 1100 }}>
        {tab !== 'rules' && <ErrorSummary error={error} />}
        {tab === 'definition' && (
          <Stack spacing={3}>
            <EndpointDefinition value={draft} onChange={setDraft} allTags={allTags} error={error} />
            <EndpointCapabilities projectId={project.id} endpointId={endpoint.id} />
          </Stack>
        )}
        {tab === 'io' && (
          <ObjectDndProvider>
            <Box sx={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 270px', gap: 3, alignItems: 'start' }}>
              <RequestResponse value={draft} onChange={setDraft} payloads={payloads} error={error} />
              <InventoryPalette projectId={project.id} payloads={payloads} />
            </Box>
          </ObjectDndProvider>
        )}
        {tab === 'rules' && (
          <RulesEditor projectId={project.id} endpointId={endpoint.id} payloads={payloads} parameters={endpoint.parameters} endpointDirty={dirty} />
        )}
        {tab === 'diagrams' && (
          <Box sx={{ height: 'calc(100vh - 230px)', minHeight: 480, display: 'flex', flexDirection: 'column', border: 1, borderColor: 'divider', borderRadius: 1, overflow: 'hidden', bgcolor: 'background.paper' }}>
            <Typography variant="body2" color="text.secondary" sx={{ px: 1.5, pt: 1 }}>
              How {endpoint.name} runs (sequence) and the generated classes it uses (classes). All diagrams are under Diagrams in the left menu.
            </Typography>
            <DiagramPanel projectId={project.id} focusEndpoint={endpoint.name} endpointOnly />
          </Box>
        )}
        {tab === 'implementation' && (
          <ImplementationTab projectId={project.id} endpointId={endpoint.id} kind={draft.kind} protocol={draft.protocol} value={draft.implementationStyle}
            projectDefault={project.defaultImplementationStyle} onChange={(implementationStyle) => setDraft({ ...draft, implementationStyle })} />
        )}
      </Box>
      <ConfirmDialog open={confirmDelete} title={`Delete endpoint ${endpoint.name}?`} onConfirm={remove} onClose={() => setConfirmDelete(false)}>
        Its processing rules are deleted too. This cannot be undone.
      </ConfirmDialog>
    </>
  )
}
