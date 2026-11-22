import { lazy, Suspense, useMemo, useState } from 'react'
import { Alert, AlertTitle, Box, Button, CircularProgress, Collapse, List, ListItemButton, ListItemText, Stack, Typography } from '@mui/material'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import DownloadIcon from '@mui/icons-material/Download'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { downloads } from '../api/client'
import { useCode } from '../api/hooks'
import { codeFocusPattern, fileTree, type TreeNode } from '../lib/fileTree'
import { ErrorSummary } from './ErrorSummary'

const CodeEditor = lazy(() => import('./CodeEditor'))

/** The generated Maven project: a file tree and the selected file, opened on the endpoint being edited. */
export function CodePanel({ projectId, focusEndpoint }: { projectId: string; focusEndpoint?: string }) {
  const code = useCode(projectId, true)
  const [chosen, setChosen] = useState<string | null>(null)
  const files = useMemo(() => code.data?.files ?? [], [code.data])
  const tree = useMemo(() => fileTree(files.map((f) => f.path)), [files])
  const endpointFile = focusEndpoint ? code.data?.endpointFiles[focusEndpoint] : undefined
  const selected = chosen && files.some((f) => f.path === chosen) ? chosen : endpointFile ?? files.find((f) => f.path.endsWith('README.md'))?.path
  const file = files.find((f) => f.path === selected)
  const errors = code.data?.problems.filter((p) => p.severity === 'ERROR') ?? []

  if (code.error) return <Box sx={{ p: 2 }}><ErrorSummary error={code.error} /></Box>
  if (!code.data) return <CircularProgress sx={{ m: 3 }} />

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}>
      <Stack direction="row" spacing={1} sx={{ px: 1.5, py: 1, alignItems: 'center', borderBottom: 1, borderColor: 'divider' }}>
        <Typography variant="body2" color="text.secondary" sx={{ flex: 1 }}>
          {files.length} files · {code.data.target}
        </Typography>
        <Button size="small" startIcon={<DownloadIcon />} disabled={!code.data.ready} component="a"
          href={code.data.ready ? downloads.codeZip(projectId) : undefined} download>
          Download .zip
        </Button>
      </Stack>
      {errors.length > 0 && (
        <Alert severity="error" sx={{ m: 1, maxHeight: 160, overflow: 'auto' }} aria-label="Generation problems">
          <AlertTitle>Fix {errors.length} problem{errors.length === 1 ? '' : 's'} before downloading</AlertTitle>
          {errors.map((p, i) => <Typography key={i} variant="body2"><b>{p.location}</b>: {p.message}</Typography>)}
        </Alert>
      )}
      <Box sx={{ display: 'flex', flex: 1, minHeight: 0 }}>
        <Box sx={{ width: 210, flexShrink: 0, overflow: 'auto', borderRight: 1, borderColor: 'divider' }} aria-label="Generated files">
          <List dense disablePadding>
            {tree.map((n) => <TreeItem key={n.path} node={n} depth={0} selected={selected} onSelect={setChosen} />)}
          </List>
        </Box>
        <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
          {file && (
            <>
              <Typography variant="caption" sx={{ px: 1.5, py: 0.5, fontFamily: 'monospace', color: 'text.secondary', borderBottom: 1, borderColor: 'divider' }} noWrap>
                {file.path}
              </Typography>
              <Box sx={{ flex: 1, minHeight: 0 }}>
                <Suspense fallback={<CircularProgress sx={{ m: 3 }} />}>
                  <CodeEditor value={file.content} language={file.language} readOnly height="100%"
                    focus={focusEndpoint && file.path === endpointFile ? codeFocusPattern(file.path, focusEndpoint) : undefined} />
                </Suspense>
              </Box>
            </>
          )}
        </Box>
      </Box>
    </Box>
  )
}

function TreeItem({ node, depth, selected, onSelect }: {
  node: TreeNode
  depth: number
  selected?: string
  onSelect: (path: string) => void
}) {
  const containsSelected = !!selected && selected.startsWith(node.path + '/')
  const [open, setOpen] = useState(depth < 1 || containsSelected)
  const isOpen = open || containsSelected
  if (node.file) {
    return (
      <ListItemButton selected={node.path === selected} onClick={() => onSelect(node.path)} sx={{ pl: 1 + depth * 1.5, py: 0 }}>
        <ListItemText primary={node.name} slotProps={{ primary: { noWrap: true, sx: { fontSize: 12, fontFamily: 'monospace' } } }} />
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
        {node.children.map((c) => <TreeItem key={c.path} node={c} depth={depth + 1} selected={selected} onSelect={onSelect} />)}
      </Collapse>
    </>
  )
}
