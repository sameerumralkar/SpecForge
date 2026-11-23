import { lazy, Suspense, useMemo, useState } from 'react'
import {
  Alert, AlertTitle, Box, Button, Checkbox, CircularProgress, Collapse, FormControlLabel, List, ListItemButton, ListItemText, Stack, Typography,
} from '@mui/material'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import DownloadIcon from '@mui/icons-material/Download'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { useQueryClient } from '@tanstack/react-query'
import { downloads } from '../api/client'
import { keys, useCode, useDiff } from '../api/hooks'
import type { ChangeStatus, FileChange } from '../api/types'
import { CHANGE_LABEL, languageOf } from '../lib/changes'
import { codeFocusPattern, fileTree, type TreeNode } from '../lib/fileTree'
import { CompareBar } from './CompareBar'
import { ErrorSummary } from './ErrorSummary'

const CodeEditor = lazy(() => import('./CodeEditor'))
const CodeDiff = lazy(() => import('./CodeDiff'))

/**
 * The generated Maven project: a file tree and the selected file, opened on the endpoint being
 * edited. Optionally compared with a saved snapshot, file by file.
 */
export function CodePanel({ projectId, focusEndpoint }: { projectId: string; focusEndpoint?: string }) {
  const code = useCode(projectId, true)
  const client = useQueryClient()
  const [chosen, setChosen] = useState<string | null>(null)
  const [compareId, setCompareId] = useState('')
  const [changedOnly, setChangedOnly] = useState(true)
  const diff = useDiff(projectId, compareId || null)

  const files = useMemo(() => code.data?.files ?? [], [code.data])
  const changes = useMemo(() => {
    const map = new Map<string, FileChange>()
    if (compareId) diff.data?.files.forEach((f) => map.set(f.path, f))
    return map
  }, [compareId, diff.data])
  const comparing = !!compareId && !!diff.data

  const paths = useMemo(() => {
    const all = new Set(files.map((f) => f.path))
    if (comparing) changes.forEach((c, path) => { if (c.status === 'REMOVED') all.add(path) })
    return [...all].filter((p) => !comparing || !changedOnly || (changes.get(p)?.status ?? 'UNCHANGED') !== 'UNCHANGED').sort()
  }, [files, changes, comparing, changedOnly])
  const tree = useMemo(() => fileTree(paths), [paths])

  const endpointFile = focusEndpoint ? code.data?.endpointFiles[focusEndpoint] : undefined
  const fallback = comparing ? paths[0] : endpointFile ?? files.find((f) => f.path.endsWith('README.md'))?.path
  const selected = chosen && paths.includes(chosen) ? chosen : fallback
  const file = files.find((f) => f.path === selected)
  const change = selected ? changes.get(selected) : undefined
  const errors = code.data?.problems.filter((p) => p.severity === 'ERROR') ?? []

  const counts = useMemo(() => {
    const c: Record<ChangeStatus, number> = { ADDED: 0, REMOVED: 0, CHANGED: 0, UNCHANGED: 0 }
    changes.forEach((f) => c[f.status]++)
    return c
  }, [changes])
  const summary = comparing
    ? counts.ADDED + counts.CHANGED + counts.REMOVED === 0 ? 'No changes'
      : `${counts.CHANGED} changed, ${counts.ADDED} added, ${counts.REMOVED} removed`
    : undefined

  const refreshSnapshots = () => setTimeout(() => client.invalidateQueries({ queryKey: keys.snapshots(projectId, 'CODE') }), 1500)

  if (code.error) return <Box sx={{ p: 2 }}><ErrorSummary error={code.error} /></Box>
  if (!code.data) return <CircularProgress sx={{ m: 3 }} />

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}>
      <Stack direction="row" spacing={1} sx={{ px: 1.5, py: 1, alignItems: 'center', borderBottom: 1, borderColor: 'divider' }}>
        <Typography variant="body2" color="text.secondary" sx={{ flex: 1 }}>
          {files.length} files · {code.data.target}
        </Typography>
        <Button size="small" startIcon={<DownloadIcon />} disabled={!code.data.ready} component="a"
          href={code.data.ready ? downloads.codeZip(projectId) : undefined} download onClick={refreshSnapshots}>
          Download .zip
        </Button>
      </Stack>
      <CompareBar projectId={projectId} kind="CODE" value={compareId} onChange={setCompareId} summary={summary} />
      {errors.length > 0 && (
        <Alert severity="error" sx={{ m: 1, maxHeight: 160, overflow: 'auto' }} aria-label="Generation problems">
          <AlertTitle>Fix {errors.length} problem{errors.length === 1 ? '' : 's'} before downloading</AlertTitle>
          {errors.map((p, i) => <Typography key={i} variant="body2"><b>{p.location}</b>: {p.message}</Typography>)}
        </Alert>
      )}
      <Box sx={{ display: 'flex', flex: 1, minHeight: 0 }}>
        <Box sx={{ width: 220, flexShrink: 0, overflow: 'auto', borderRight: 1, borderColor: 'divider' }} aria-label="Generated files">
          {comparing && (
            <FormControlLabel sx={{ px: 1 }} label={<Typography variant="caption">Changed files only</Typography>}
              control={<Checkbox size="small" checked={changedOnly} onChange={(e) => setChangedOnly(e.target.checked)} />} />
          )}
          {comparing && paths.length === 0 && <Typography variant="body2" color="text.secondary" sx={{ p: 1.5 }}>Nothing changed.</Typography>}
          <List dense disablePadding key={comparing ? `compare-${compareId}-${changedOnly}` : 'all'}>
            {tree.map((n) => <TreeItem key={n.path} node={n} depth={0} selected={selected} onSelect={setChosen} changes={changes} />)}
          </List>
        </Box>
        <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
          {selected && (
            <Typography variant="caption" sx={{ px: 1.5, py: 0.5, fontFamily: 'monospace', color: 'text.secondary', borderBottom: 1, borderColor: 'divider' }} noWrap>
              {selected}{change && change.status !== 'UNCHANGED' ? ` (${CHANGE_LABEL[change.status].text})` : ''}
            </Typography>
          )}
          <Box sx={{ flex: 1, minHeight: 0 }}>
            <Suspense fallback={<CircularProgress sx={{ m: 3 }} />}>
              {selected && change?.status === 'CHANGED' && (
                <CodeDiff before={change.before ?? ''} after={change.after ?? ''} language={languageOf(selected)} />
              )}
              {selected && change?.status === 'REMOVED' && (
                <CodeEditor value={change.before ?? ''} language={languageOf(selected)} readOnly height="100%" />
              )}
              {file && change?.status !== 'CHANGED' && change?.status !== 'REMOVED' && (
                <CodeEditor value={file.content} language={file.language} readOnly height="100%"
                  focus={focusEndpoint && file.path === endpointFile && !comparing ? codeFocusPattern(file.path, focusEndpoint) : undefined} />
              )}
            </Suspense>
          </Box>
        </Box>
      </Box>
    </Box>
  )
}

function TreeItem({ node, depth, selected, onSelect, changes }: {
  node: TreeNode
  depth: number
  selected?: string
  onSelect: (path: string) => void
  changes: Map<string, FileChange>
}) {
  const containsSelected = !!selected && selected.startsWith(node.path + '/')
  // When comparing, start with every folder open so all changes are visible.
  const [open, setOpen] = useState(depth < 1 || containsSelected || changes.size > 0)
  const isOpen = open || containsSelected
  if (node.file) {
    const status = changes.get(node.path)?.status
    const label = status ? CHANGE_LABEL[status] : undefined
    return (
      <ListItemButton selected={node.path === selected} onClick={() => onSelect(node.path)} sx={{ pl: 1 + depth * 1.5, py: 0 }}>
        <ListItemText primary={node.name} slotProps={{ primary: { noWrap: true, sx: {
          fontSize: 12, fontFamily: 'monospace', color: label?.color,
          textDecoration: status === 'REMOVED' ? 'line-through' : undefined,
        } } }} />
        {label?.letter && <Typography variant="caption" sx={{ color: label.color, fontWeight: 700, ml: 0.5 }} aria-label={label.text}>{label.letter}</Typography>}
      </ListItemButton>
    )
  }
  return (
    <>
      <ListItemButton onClick={() => setOpen(!isOpen)} sx={{ pl: 0.5 + depth * 1.5, py: 0 }} aria-expanded={isOpen}>
        {isOpen ? <ExpandMoreIcon sx={{ fontSize: 16 }} /> : <ChevronRightIcon sx={{ fontSize: 16 }} />}
        <ListItemText primary={node.name} slotProps={{ primary: { noWrap: true, sx: { fontSize: 12, fontWeight: 500 } } }} />
      </ListItemButton>
      <Collapse in={isOpen} unmountOnExit>
        {node.children.map((c) => <TreeItem key={c.path} node={c} depth={depth + 1} selected={selected} onSelect={onSelect} changes={changes} />)}
      </Collapse>
    </>
  )
}
