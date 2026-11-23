import { lazy, Suspense, useState } from 'react'
import {
  Alert, Box, Chip, CircularProgress, GlobalStyles, IconButton, LinearProgress, Stack, Tab, Tabs, ToggleButton,
  ToggleButtonGroup, Tooltip, Typography,
} from '@mui/material'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import CloseIcon from '@mui/icons-material/Close'
import DownloadIcon from '@mui/icons-material/Download'
import ErrorIcon from '@mui/icons-material/Error'
import RemoveCircleOutlineIcon from '@mui/icons-material/RemoveCircleOutlined'
import { downloads } from '../api/client'
import { useSpecs } from '../api/hooks'
import type { EndpointKind, Problem, SpecView } from '../api/types'
import { ErrorSummary } from './ErrorSummary'
import { focusPattern, type Format, type PreviewTab, type SpecId } from '../lib/specs'
import { CodePanel } from './CodePanel'
import { CompareBar } from './CompareBar'
import { useDiff } from '../api/hooks'
import { DiagramPanel } from './DiagramPanel'
import AccountTreeIcon from '@mui/icons-material/AccountTree'
import CodeIcon from '@mui/icons-material/Code'

const CodeEditor = lazy(() => import('./CodeEditor'))
const CodeDiff = lazy(() => import('./CodeDiff'))


function StatusIcon({ spec }: { spec?: SpecView }) {
  if (!spec) return null
  if (!spec.present) return <RemoveCircleOutlineIcon fontSize="small" color="disabled" />
  return spec.valid ? <CheckCircleIcon fontSize="small" color="success" /> : <ErrorIcon fontSize="small" color="error" />
}

const SEVERITY_COLOR = { ERROR: 'error', WARNING: 'warning', INFO: 'default' } as const

/** Live preview of the generated OpenAPI and AsyncAPI documents (with validation results) and of the generated code. */
export function SpecPanel({ projectId, focus, onClose }: {
  projectId: string
  /** The endpoint being edited; the preview jumps to it. */
  focus?: { name: string; kind: EndpointKind }
  onClose: () => void
}) {
  const specs = useSpecs(projectId)
  const [chosen, setChosen] = useState<PreviewTab | null>(() => {
    const saved = localStorage.getItem('preview-tab')
    return saved === 'code' || saved === 'diagrams' ? saved : null
  })
  const [format, setFormat] = useState<Format>(() => (localStorage.getItem('spec-format') as Format) ?? 'yaml')
  const tab: PreviewTab = chosen ?? (focus?.kind === 'ASYNC' ? 'asyncapi' : 'openapi')
  const choose = (t: PreviewTab) => {
    setChosen(t)
    localStorage.setItem('preview-tab', t === 'code' || t === 'diagrams' ? t : 'spec')
  }
  const isSpec = tab === 'openapi' || tab === 'asyncapi'
  const spec = isSpec ? specs.data?.[tab] : undefined
  const [compareId, setCompareId] = useState('')
  const diff = useDiff(projectId, compareId || null)
  const change = compareId && isSpec ? diff.data?.files.find((f) => f.path === `${tab}.${format}`) : undefined
  const compareSummary = change ? (change.status === 'UNCHANGED' ? 'No changes in this file' : `This file is ${change.status.toLowerCase()}`) : undefined
  const text = (format === 'yaml' ? spec?.yaml : spec?.json) ?? ''
  const focusMatches = isSpec && focus && (focus.kind === 'ASYNC') === (tab === 'asyncapi')
  const problems = spec?.problems ?? []
  const errors = problems.filter((p) => p.severity === 'ERROR').length

  const setFormatAndRemember = (f: Format) => {
    setFormat(f)
    localStorage.setItem('spec-format', f)
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', bgcolor: 'background.paper' }} aria-label="Preview">
      <GlobalStyles styles={{ '.spec-focus-line': { background: 'rgba(57, 73, 171, 0.12)' } }} />
      <Stack direction="row" sx={{ alignItems: 'center', borderBottom: 1, borderColor: 'divider', pl: 1 }}>
        <Tabs value={tab} onChange={(_, t: PreviewTab) => choose(t)} variant="scrollable" scrollButtons="auto"
          sx={{ flex: 1, minHeight: 40, '& .MuiTab-root': { minHeight: 40, minWidth: 0, px: 1.5 } }}>
          <Tab value="openapi" label="OpenAPI 3.1" icon={<StatusIcon spec={specs.data?.openapi} />} iconPosition="end" />
          <Tab value="asyncapi" label="AsyncAPI 3.0" icon={<StatusIcon spec={specs.data?.asyncapi} />} iconPosition="end" />
          <Tab value="code" label="Code" icon={<CodeIcon fontSize="small" />} iconPosition="end" />
          <Tab value="diagrams" label="Diagrams" icon={<AccountTreeIcon fontSize="small" />} iconPosition="end" />
        </Tabs>
        {isSpec && <>
        <ToggleButtonGroup exclusive size="small" value={format} onChange={(_, f) => f && setFormatAndRemember(f)} sx={{ mr: 1 }}>
          <ToggleButton value="yaml" sx={{ textTransform: 'none', py: 0.25 }}>YAML</ToggleButton>
          <ToggleButton value="json" sx={{ textTransform: 'none', py: 0.25 }}>JSON</ToggleButton>
        </ToggleButtonGroup>
        <Tooltip title={`Download ${tab}.${format}`}>
          <span>
            <IconButton aria-label={`Download ${tab}.${format}`} disabled={!spec?.present} component="a"
              href={downloads.specFile(projectId, `${tab}.${format}`)} download>
              <DownloadIcon fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
        </>}
        <IconButton aria-label="Close preview" onClick={onClose}><CloseIcon fontSize="small" /></IconButton>
      </Stack>
      {specs.isFetching ? <LinearProgress sx={{ height: 2 }} /> : <Box sx={{ height: 2 }} />}

      {tab === 'code' && <CodePanel projectId={projectId} focusEndpoint={focus?.name} />}
      {tab === 'diagrams' && <DiagramPanel projectId={projectId} focusEndpoint={focus?.name} />}
      {isSpec && specs.error && <Box sx={{ p: 2 }}><ErrorSummary error={specs.error} /></Box>}
      {isSpec && !specs.data && !specs.error && <CircularProgress sx={{ m: 3 }} />}
      {spec && !spec.present && (
        <Box sx={{ p: 2 }}>
          <Alert severity="info">{spec.problems[0]?.message ?? 'Nothing to generate yet.'}</Alert>
        </Box>
      )}
      {isSpec && <CompareBar projectId={projectId} kind="SPECS" value={compareId} onChange={setCompareId} summary={compareSummary} />}
      {spec?.present && (
        <>
          <Box sx={{ flex: 1, minHeight: 0 }}>
            <Suspense fallback={<CircularProgress sx={{ m: 3 }} />}>
              {change && change.status !== 'UNCHANGED' ? (
                <CodeDiff before={change.before ?? ''} after={change.after ?? ''} language={format} />
              ) : <CodeEditor value={text} language={format} readOnly height="100%"
                focus={focusMatches ? focusPattern(tab as SpecId, format, focus!.name) : undefined} />}
            </Suspense>
          </Box>
          <ProblemList spec={tab as SpecId} problems={problems} errors={errors} />
        </>
      )}
    </Box>
  )
}

const VALID_TEXT: Record<SpecId, string> = {
  openapi: 'Valid OpenAPI 3.1: passes the official JSON Schema, swagger-parser and $ref checks.',
  asyncapi: 'Valid AsyncAPI 3.0: passes the official JSON Schema and $ref checks.',
}

function ProblemList({ spec, problems, errors }: { spec: SpecId; problems: Problem[]; errors: number }) {
  const shown = problems.filter((p) => p.severity !== 'INFO')
  const notes = problems.filter((p) => p.severity === 'INFO')
  return (
    <Box sx={{ borderTop: 1, borderColor: 'divider', maxHeight: '35%', overflow: 'auto', p: 1.5 }} aria-label="Validation results">
      {errors === 0 && shown.length === 0 ? (
        <Typography variant="body2" color="success.main" sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
          <CheckCircleIcon fontSize="small" /> {VALID_TEXT[spec]}
        </Typography>
      ) : (
        <Typography variant="body2" sx={{ fontWeight: 600, mb: 0.5 }}>
          {errors} error{errors === 1 ? '' : 's'}, {shown.length - errors} warning{shown.length - errors === 1 ? '' : 's'}
        </Typography>
      )}
      {shown.map((p, i) => (
        <Stack key={i} direction="row" spacing={1} sx={{ alignItems: 'baseline', py: 0.25 }}>
          <Chip label={p.severity} color={SEVERITY_COLOR[p.severity]} sx={{ height: 18, fontSize: 10 }} />
          <Typography variant="body2" sx={{ wordBreak: 'break-word' }}>
            {p.location && <code style={{ fontSize: 11 }}>{p.location}</code>} {p.message}
            <Typography component="span" variant="caption" color="text.secondary"> ({p.source})</Typography>
          </Typography>
        </Stack>
      ))}
      {notes.map((p, i) => (
        <Typography key={i} variant="caption" color="text.secondary" sx={{ display: 'block' }}>ℹ {p.message}</Typography>
      ))}
    </Box>
  )
}
