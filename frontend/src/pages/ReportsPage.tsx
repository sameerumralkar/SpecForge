import { useState, type ReactNode } from 'react'
import { Link as RouterLink, useNavigate, useParams, useSearchParams } from 'react-router'
import {
  Alert, Box, Chip, CircularProgress, FormControlLabel, Link, MenuItem, Stack, Switch, Tab, Tabs, TextField, ToggleButton, ToggleButtonGroup,
  Tooltip, Typography,
} from '@mui/material'
import { ScreenHelp } from '../components/help/ScreenHelp'
import { ErrorSummary } from '../components/ErrorSummary'
import { ReportTable, StatTiles, type Column } from '../components/reports/ReportTable'
import { useCapabilityMap, useProjects, useReport } from '../api/hooks'
import { CapabilityExplorer } from '../components/reports/CapabilityExplorer'
import type {
  EndpointReportRow, FieldReportRow, LinkedObjectCount, ObjectReportRow, ProjectEndpointCount, ProjectReportRow, ReportId,
} from '../api/types'
import { mono } from '../theme'

type ReportTab = ReportId | 'map' | 'trace'

export const REPORTS: { id: ReportTab; label: string; description: string }[] = [
  { id: 'projects', label: 'Projects', description: 'How many projects there are, with each one\'s settings and what it holds.' },
  { id: 'endpoints', label: 'Endpoints per project', description: 'How many endpoints each project has, then every endpoint with its details.' },
  { id: 'objects', label: 'Objects', description: 'How many objects (and structures) there are, with their fields, the objects they hold and where each is used.' },
  { id: 'fields', label: 'Fields and linked objects', description: 'Every payload field, and the object it holds if it holds one, with a count of fields per object.' },
  { id: 'map', label: 'Capability map', description: 'Click down from an L1 capability, level by level to L8, then to its endpoints, their objects and processing rules, and the objects\' fields.' },
  { id: 'trace', label: 'Where used', description: 'Click up from a field to the objects it is in, the endpoints that use them, and the capabilities above them, up to L1.' },
]

const date = (iso: string) => new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
const slugOf = (s: string) => s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')

function ToProject({ id, name }: { id: string; name: string }) {
  return <Link component={RouterLink} to={`/projects/${id}`}>{name}</Link>
}

function Code({ children }: { children: ReactNode }) {
  return <Box component="span" sx={{ fontFamily: mono.fontFamily, fontSize: 13 }}>{children}</Box>
}

function Role({ role }: { role: 'OBJECT' | 'STRUCTURE' | null }) {
  if (!role) return null
  return <Chip size="small" variant="outlined" color={role === 'OBJECT' ? 'primary' : 'default'} label={role === 'OBJECT' ? 'object' : 'structure'} sx={{ height: 20, fontSize: 11 }} />
}

/** A list cell: the first few items, and the rest on hover. */
function Few({ items, none = '—' }: { items: string[]; none?: string }) {
  if (items.length === 0) return <Typography variant="body2" color="text.secondary">{none}</Typography>
  const head = items.slice(0, 3).join(', ')
  return items.length <= 3 ? <span>{head}</span> : (
    <Tooltip title={<Box sx={{ whiteSpace: 'pre-line' }}>{items.join('\n')}</Box>}>
      <span>{head} <Typography component="span" variant="body2" color="primary">+{items.length - 3} more</Typography></span>
    </Tooltip>
  )
}

/**
 * Reports across the projects: (1) the projects, (2) the endpoints of each project, (3) the objects, and (4) every
 * field with the object it holds. Each can cover every project or one, and every table sorts, filters and
 * downloads as CSV.
 */
export function ReportsPage() {
  const { reportId } = useParams()
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const projects = useProjects()
  const report = REPORTS.find((r) => r.id === reportId) ?? REPORTS[0]
  const projectId = params.get('project') ?? undefined

  const setProject = (id: string) => setParams(id ? { project: id } : {}, { replace: true })
  const scopeName = projectId ? projects.data?.find((p) => p.id === projectId)?.name ?? 'project' : 'all projects'
  const file = (what: string) => `${what}-${slugOf(scopeName)}`

  return (
    <Box sx={{ flex: 1, overflow: 'auto', bgcolor: 'background.default' }}>
      <Box sx={{ p: 3, maxWidth: 1600, mx: 'auto' }}>
        <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 1 }}>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Typography variant="h5">Reports</Typography>
              <ScreenHelp screen="reports" />
            </Stack>
            <Typography variant="body2" color="text.secondary">{report.description}</Typography>
          </Box>
          <TextField select size="small" fullWidth={false} label="Projects in the report" value={projectId ?? ''} onChange={(e) => setProject(e.target.value)}
            sx={{ width: 300, flexShrink: 0 }} slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }}>
            <MenuItem value="">All projects</MenuItem>
            {projects.data?.map((p) => <MenuItem key={p.id} value={p.id}>{p.name}</MenuItem>)}
          </TextField>
        </Stack>
        <Tabs value={report.id} onChange={(_, id) => navigate({ pathname: `/reports/${id}`, search: params.toString() })}
          sx={{ mb: 3, borderBottom: 1, borderColor: 'divider' }} aria-label="Reports" variant="scrollable">
          {REPORTS.map((r, i) => <Tab key={r.id} value={r.id} label={`${i + 1}. ${r.label}`} />)}
        </Tabs>
        {report.id === 'projects' && <ProjectsReportView projectId={projectId} file={file} />}
        {report.id === 'endpoints' && <EndpointsReportView projectId={projectId} file={file} />}
        {report.id === 'objects' && <ObjectsReportView projectId={projectId} file={file} />}
        {report.id === 'fields' && <FieldsReportView projectId={projectId} file={file} />}
        {(report.id === 'map' || report.id === 'trace') && (
          <MapView projectId={projectId ?? projects.data?.[0]?.id} chosen={!!projectId} direction={report.id === 'map' ? 'down' : 'up'} />
        )}
      </Box>
    </Box>
  )
}

type ViewProps = { projectId?: string; file: (what: string) => string }

function Loading({ error }: { error: unknown }) {
  return error ? <ErrorSummary error={error} /> : <CircularProgress />
}

// ---- 1. Projects ----

const projectColumns: Column<ProjectReportRow>[] = [
  { id: 'name', label: 'Project', value: (r) => r.name, render: (r) => (
    <>
      <ToProject id={r.id} name={r.name} />
      {r.description && <Typography variant="caption" color="text.secondary" sx={{ display: 'block', maxWidth: 320 }}>{r.description}</Typography>}
    </>
  ) },
  { id: 'version', label: 'Version', value: (r) => r.version },
  { id: 'basePackage', label: 'Base package', value: (r) => r.basePackage, render: (r) => <Code>{r.basePackage}</Code> },
  { id: 'style', label: 'Default style', value: (r) => (r.defaultImplementationStyle === 'PROCESSING_ROUTE' ? 'Processing route' : 'Implementation class') },
  { id: 'endpoints', label: 'Endpoints', numeric: true, value: (r) => r.endpoints },
  { id: 'rest', label: 'REST', numeric: true, value: (r) => r.restEndpoints },
  { id: 'async', label: 'Async', numeric: true, value: (r) => r.asyncEndpoints },
  { id: 'objects', label: 'Objects', numeric: true, value: (r) => r.objects },
  { id: 'structures', label: 'Structures', numeric: true, value: (r) => r.structures },
  { id: 'fields', label: 'Fields', numeric: true, value: (r) => r.fields },
  { id: 'inventory', label: 'Field inventory', numeric: true, value: (r) => r.fieldDefinitions },
  { id: 'rules', label: 'Rules', numeric: true, value: (r) => r.rules },
  { id: 'errors', label: 'Error codes', numeric: true, value: (r) => r.errorCodes },
  { id: 'capabilities', label: 'Capabilities', numeric: true, value: (r) => r.capabilities },
  { id: 'mapped', label: 'APIs mapped', numeric: true, value: (r) => r.mappedEndpoints, render: (r) => `${r.mappedEndpoints} of ${r.endpoints}` },
  { id: 'created', label: 'Created', value: (r) => r.createdAt, render: (r) => date(r.createdAt) },
  { id: 'updated', label: 'Last changed', value: (r) => r.updatedAt, render: (r) => date(r.updatedAt) },
]

function ProjectsReportView({ projectId, file }: ViewProps) {
  const { data, error } = useReport('projects', projectId)
  if (!data) return <Loading error={error} />
  const t = data.totals
  return (
    <>
      <StatTiles label="Project totals" stats={[
        { label: 'Projects', value: t.projects },
        { label: 'Endpoints', value: t.endpoints, hint: `${t.restEndpoints} REST, ${t.asyncEndpoints} async` },
        { label: 'Objects', value: t.objects, hint: 'Payloads in the object inventory' },
        { label: 'Structures', value: t.structures, hint: 'Requests, responses and messages' },
        { label: 'Fields', value: t.fields, hint: 'Fields of every payload' },
        { label: 'Field inventory', value: t.fieldDefinitions },
        { label: 'Processing rules', value: t.rules },
        { label: 'Error codes', value: t.errorCodes },
        { label: 'Capabilities', value: t.capabilities },
      ]} />
      <ReportTable title="Projects" columns={projectColumns} rows={data.projects} rowKey={(r) => r.id} fileName={file('projects')}
        empty="No projects yet." />
    </>
  )
}

// ---- 2. Endpoints per project ----

const perProjectColumns: Column<ProjectEndpointCount>[] = [
  { id: 'project', label: 'Project', value: (r) => r.projectName, render: (r) => <ToProject id={r.projectId} name={r.projectName} /> },
  { id: 'total', label: 'Endpoints', numeric: true, value: (r) => r.total },
  { id: 'rest', label: 'REST', numeric: true, value: (r) => r.rest },
  { id: 'async', label: 'Async', numeric: true, value: (r) => r.async },
  { id: 'rules', label: 'With rules', numeric: true, value: (r) => r.withRules },
  { id: 'mapped', label: 'Mapped to a capability', numeric: true, value: (r) => r.mapped },
]

const endpointColumns: Column<EndpointReportRow>[] = [
  { id: 'project', label: 'Project', value: (r) => r.projectName, render: (r) => <ToProject id={r.projectId} name={r.projectName} /> },
  { id: 'name', label: 'Endpoint', value: (r) => r.name, render: (r) => (
    <>
      <Link component={RouterLink} to={`/projects/${r.projectId}/endpoints/${r.id}`}><Code>{r.name}</Code></Link>
      {r.summary && <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{r.summary}</Typography>}
    </>
  ) },
  { id: 'kind', label: 'Kind', value: (r) => r.kind },
  { id: 'route', label: 'Method and path / channel', value: (r) => (r.kind === 'REST' ? `${r.method ?? ''} ${r.path ?? ''}` : `${r.operation ?? ''} ${r.channel ?? ''} (${r.protocol ?? ''})`),
    render: (r) => <Code>{r.kind === 'REST' ? <><b>{r.method}</b> {r.path}</> : <><b>{r.operation}</b> {r.channel} <Typography component="span" variant="caption">{r.protocol}</Typography></>}</Code> },
  { id: 'tags', label: 'Tags', value: (r) => r.tags, render: (r) => <Few items={r.tags} /> },
  { id: 'request', label: 'Request / message', value: (r) => (r.kind === 'REST' ? r.requestPayload : r.messagePayload) },
  { id: 'responses', label: 'Responses', value: (r) => r.responses, render: (r) => <Few items={r.responses} /> },
  { id: 'parameters', label: 'Parameters', numeric: true, value: (r) => r.parameters },
  { id: 'rules', label: 'Rules', numeric: true, value: (r) => r.rules },
  { id: 'style', label: 'Style', value: (r) => (r.implementationStyle === 'PROCESSING_ROUTE' ? 'Route' : 'Class') },
  { id: 'security', label: 'Security', value: (r) => r.security, render: (r) => <Few items={r.security} /> },
  { id: 'capabilities', label: 'Capabilities', value: (r) => r.capabilities, render: (r) => <Few items={r.capabilities} none="not mapped" /> },
]

function EndpointsReportView({ projectId, file }: ViewProps) {
  const { data, error } = useReport('endpoints', projectId)
  const [kind, setKind] = useState<'ALL' | 'REST' | 'ASYNC'>('ALL')
  if (!data) return <Loading error={error} />
  const rest = data.perProject.reduce((n, p) => n + p.rest, 0)
  const rows = kind === 'ALL' ? data.endpoints : data.endpoints.filter((e) => e.kind === kind)
  return (
    <>
      <StatTiles label="Endpoint totals" stats={[
        { label: 'Endpoints', value: data.total },
        { label: 'REST', value: rest },
        { label: 'Async', value: data.total - rest },
        { label: 'With rules', value: data.perProject.reduce((n, p) => n + p.withRules, 0) },
        { label: 'Mapped to a capability', value: data.perProject.reduce((n, p) => n + p.mapped, 0) },
        { label: 'Projects', value: data.perProject.length },
      ]} />
      <ReportTable title="Endpoints per project" columns={perProjectColumns} rows={data.perProject} rowKey={(r) => r.projectId}
        fileName={file('endpoints-per-project')} empty="No projects yet." />
      <ReportTable title="Endpoint details" columns={endpointColumns} rows={rows} rowKey={(r) => r.id} fileName={file('endpoints')}
        empty="No endpoints yet."
        toolbar={(
          <ToggleButtonGroup size="small" exclusive value={kind} onChange={(_, v) => v && setKind(v)} aria-label="Endpoint kind">
            <ToggleButton value="ALL">All</ToggleButton>
            <ToggleButton value="REST">REST</ToggleButton>
            <ToggleButton value="ASYNC">Async</ToggleButton>
          </ToggleButtonGroup>
        )} />
    </>
  )
}

// ---- 3. Objects ----

const objectColumns: Column<ObjectReportRow>[] = [
  { id: 'project', label: 'Project', value: (r) => r.projectName, render: (r) => <ToProject id={r.projectId} name={r.projectName} /> },
  { id: 'name', label: 'Name', value: (r) => r.name, render: (r) => (
    <>
      <Link component={RouterLink} to={`/projects/${r.projectId}/payloads/${r.id}`}><Code>{r.name}</Code></Link>
      {r.description && <Typography variant="caption" color="text.secondary" sx={{ display: 'block', maxWidth: 320 }}>{r.description}</Typography>}
    </>
  ) },
  { id: 'role', label: 'Role', value: (r) => (r.role === 'OBJECT' ? 'object' : 'structure'), render: (r) => <Role role={r.role} /> },
  { id: 'fields', label: 'Fields', numeric: true, value: (r) => r.fields },
  { id: 'required', label: 'Required', numeric: true, value: (r) => r.requiredFields },
  { id: 'objectFields', label: 'Fields holding objects', numeric: true, value: (r) => r.objectFields },
  { id: 'linked', label: 'Objects it holds', value: (r) => r.linkedObjects, render: (r) => <Few items={r.linkedObjects} /> },
  { id: 'usedBy', label: 'Uses', numeric: true, value: (r) => r.usedBy },
  { id: 'usages', label: 'Used by', value: (r) => r.usages, render: (r) => <Few items={r.usages} none="not used" /> },
]

function ObjectsReportView({ projectId, file }: ViewProps) {
  const { data, error } = useReport('objects', projectId)
  const [role, setRole] = useState<'OBJECT' | 'STRUCTURE' | 'ALL'>('OBJECT')
  if (!data) return <Loading error={error} />
  const rows = role === 'ALL' ? data.payloads : data.payloads.filter((p) => p.role === role)
  const objects = data.payloads.filter((p) => p.role === 'OBJECT')
  return (
    <>
      <StatTiles label="Object totals" stats={[
        { label: 'Objects', value: data.objects, hint: 'Payloads in the object inventory' },
        { label: 'Structures', value: data.structures, hint: 'Requests, responses and messages' },
        { label: 'Objects not used', value: objects.filter((o) => o.usedBy === 0).length, hint: 'Objects nothing refers to yet' },
        { label: 'Fields in objects', value: objects.reduce((n, o) => n + o.fields, 0) },
      ]} />
      <ReportTable title="Object details" columns={objectColumns} rows={rows} rowKey={(r) => r.id} fileName={file(role === 'ALL' ? 'payloads' : role === 'OBJECT' ? 'objects' : 'structures')}
        empty={role === 'OBJECT' ? 'No objects yet: payloads in the object inventory show here.' : 'Nothing yet.'}
        toolbar={(
          <ToggleButtonGroup size="small" exclusive value={role} onChange={(_, v) => v && setRole(v)} aria-label="Payloads to show">
            <ToggleButton value="OBJECT">Objects</ToggleButton>
            <ToggleButton value="STRUCTURE">Structures</ToggleButton>
            <ToggleButton value="ALL">All</ToggleButton>
          </ToggleButtonGroup>
        )} />
    </>
  )
}

// ---- 4. Fields and linked objects ----

const linkedColumns: Column<LinkedObjectCount>[] = [
  { id: 'project', label: 'Project', value: (r) => r.projectName, render: (r) => <ToProject id={r.projectId} name={r.projectName} /> },
  { id: 'object', label: 'Linked object', value: (r) => r.objectName, render: (r) => (
    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
      <Link component={RouterLink} to={`/projects/${r.projectId}/payloads/${r.objectId}`}><Code>{r.objectName}</Code></Link>
      <Role role={r.objectRole} />
    </Stack>
  ) },
  { id: 'fields', label: 'Fields holding it', numeric: true, value: (r) => r.fields },
  { id: 'heldBy', label: 'Held by', value: (r) => r.heldBy, render: (r) => <Code><Few items={r.heldBy} /></Code> },
]

const fieldColumns: Column<FieldReportRow>[] = [
  { id: 'project', label: 'Project', value: (r) => r.projectName, render: (r) => <ToProject id={r.projectId} name={r.projectName} /> },
  { id: 'payload', label: 'In payload', value: (r) => r.payloadName, render: (r) => (
    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
      <Link component={RouterLink} to={`/projects/${r.projectId}/payloads/${r.payloadId}`}><Code>{r.payloadName}</Code></Link>
      <Role role={r.payloadRole} />
    </Stack>
  ) },
  { id: 'name', label: 'Field', value: (r) => r.name, render: (r) => (
    <>
      <Code><b>{r.name}</b>{r.required ? '' : '?'}</Code>
      {r.description && <Typography variant="caption" color="text.secondary" sx={{ display: 'block', maxWidth: 280 }}>{r.description}</Typography>}
    </>
  ) },
  { id: 'type', label: 'Type', value: (r) => (r.type === 'ARRAY' ? `ARRAY of ${r.itemType ?? '?'}` : r.type), render: (r) => <Code>{r.type === 'ARRAY' ? `list of ${r.itemType ?? '?'}` : r.type}</Code> },
  { id: 'required', label: 'Required', value: (r) => r.required },
  { id: 'linked', label: 'Linked object', value: (r) => r.linkedObject, render: (r) => (r.linkedObject ? (
    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
      <Link component={RouterLink} to={`/projects/${r.projectId}/payloads/${r.linkedObjectId}`}><Code>{r.linkedObject}</Code></Link>
      <Role role={r.linkedObjectRole} />
    </Stack>
  ) : <Typography variant="body2" color="text.secondary">—</Typography>) },
  { id: 'constraints', label: 'Constraints', value: (r) => r.constraints, render: (r) => <Code>{r.constraints || '—'}</Code> },
  { id: 'inventory', label: 'In field inventory', value: (r) => r.inFieldInventory },
]

function FieldsReportView({ projectId, file }: ViewProps) {
  const { data, error } = useReport('fields', projectId)
  const [onlyLinked, setOnlyLinked] = useState(false)
  if (!data) return <Loading error={error} />
  const rows = onlyLinked ? data.fields.filter((f) => f.linkedObjectId) : data.fields
  return (
    <>
      <StatTiles label="Field totals" stats={[
        { label: 'Fields', value: data.total, hint: 'Fields of every payload' },
        { label: 'Fields with a linked object', value: data.linked, hint: 'OBJECT fields, and lists of objects' },
        { label: 'Plain fields', value: data.total - data.linked },
        { label: 'Objects linked', value: data.linkedObjects, hint: 'Different objects that fields hold' },
        { label: 'In the field inventory', value: data.fields.filter((f) => f.inFieldInventory).length, hint: 'Fields whose name is in the field inventory' },
      ]} />
      <ReportTable title="Fields per linked object" columns={linkedColumns} rows={data.perObject} rowKey={(r) => r.objectId}
        fileName={file('linked-objects')} empty="No field holds an object yet." />
      <ReportTable title="Field details" columns={fieldColumns} rows={rows} rowKey={(r) => `${r.payloadId}:${r.position}`} fileName={file(onlyLinked ? 'fields-with-linked-objects' : 'fields')}
        empty="No fields yet."
        toolbar={<FormControlLabel control={<Switch checked={onlyLinked} onChange={(e) => setOnlyLinked(e.target.checked)} />} label="Only fields with a linked object" />} />
    </>
  )
}

// ---- 5. and 6. The capability map, down and up ----

function MapView({ projectId, chosen, direction }: { projectId?: string; chosen: boolean; direction: 'down' | 'up' }) {
  const { data, error } = useCapabilityMap(projectId)
  if (!projectId) return <Typography color="text.secondary">No projects yet.</Typography>
  if (!data) return <Loading error={error} />
  return (
    <>
      {!chosen && (
        <Alert severity="info" sx={{ mb: 2 }}>
          The map shows one project at a time: this is <b>{data.projectName}</b>. Choose another in <b>Projects in the report</b>.
        </Alert>
      )}
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
        {direction === 'down'
          ? 'Click a capability to open the level below it, then an endpoint to see its objects and processing rules, then an object to see its fields. A field that holds an object opens that object.'
          : 'Click a field to see the objects it is used in, an object to see the endpoints that use it (directly, or through the objects that hold it), an endpoint to see its capabilities, and a capability to go up a level, to L1.'}
      </Typography>
      <CapabilityExplorer map={data} direction={direction} />
    </>
  )
}
