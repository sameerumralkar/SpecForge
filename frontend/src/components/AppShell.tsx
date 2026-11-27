import { useMemo, useState } from 'react'
import { Outlet, useNavigate, useParams } from 'react-router'
import { Alert, AppBar, Box, Button, ListItemText, Menu, MenuItem, Snackbar, TextField, ToggleButton, Toolbar, Tooltip, Typography } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown'
import BoltIcon from '@mui/icons-material/Bolt'
import VerticalSplitIcon from '@mui/icons-material/VerticalSplit'
import TipsAndUpdatesOutlinedIcon from '@mui/icons-material/TipsAndUpdatesOutlined'
import MenuBookOutlinedIcon from '@mui/icons-material/MenuBookOutlined'
import { FieldHelpLayer } from './help/FieldHelpLayer'
import { setFieldHelpOn, useFieldHelpOn } from './help/fieldHelpStore'
import { useQueryClient } from '@tanstack/react-query'
import { api, downloads } from '../api/client'
import { PreviewContext } from '../lib/preview'
import { buildDiagramsZip } from '../lib/diagramExport'
import { saveBlob } from '../lib/diagrams'
import { keys, useCode, useProjects } from '../api/hooks'
import { ProjectDialog } from './ProjectDialog'
import { ImportDialog } from './ImportDialog'

export function AppShell() {
  const { projectId } = useParams()
  const navigate = useNavigate()
  const projects = useProjects()
  const client = useQueryClient()
  const [creating, setCreating] = useState(false)
  const [importing, setImporting] = useState(false)
  const [menu, setMenu] = useState<HTMLElement | null>(null)
  const [generateMenu, setGenerateMenu] = useState<HTMLElement | null>(null)
  const code = useCode(projectId ?? '', !!projectId && !!generateMenu)
  const [exporting, setExporting] = useState<string | null>(null)
  const [exportError, setExportError] = useState<string | null>(null)

  const exportDiagrams = async () => {
    setGenerateMenu(null)
    if (!projectId) return
    setExporting('Drawing diagrams…')
    try {
      const blob = await buildDiagramsZip(projectId, (done, total) => setExporting(`Drawing Mermaid diagrams: ${done} of ${total}`))
      const name = projects.data?.find((p) => p.id === projectId)?.name ?? 'project'
      saveBlob(blob, `${name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')}-diagrams.zip`)
    } catch (e) {
      setExportError(e instanceof Error ? e.message : String(e))
    } finally {
      setExporting(null)
    }
  }
  const codeErrors = code.data?.problems.filter((p) => p.severity === 'ERROR').length ?? 0
  const [previewOpen, setPreviewOpenState] = useState(() => localStorage.getItem('preview-open') !== 'false')
  const preview = useMemo(() => ({
    open: previewOpen,
    setOpen: (open: boolean) => { setPreviewOpenState(open); localStorage.setItem('preview-open', String(open)) },
  }), [previewOpen])

  const addSample = async () => {
    setMenu(null)
    const project = await api.createSampleProject()
    await client.invalidateQueries({ queryKey: keys.projects })
    navigate(`/projects/${project.id}`)
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh' }}>
      <AppBar position="static" elevation={0}>
        <Toolbar variant="dense" sx={{ gap: 2 }}>
          <Typography variant="h6" sx={{ fontSize: 17, fontWeight: 600, whiteSpace: 'nowrap' }}>
            Spec Builder
          </Typography>
          {projects.data && projects.data.length > 0 && (
            <TextField
              select
              fullWidth={false}
              value={projectId ?? ''}
              onChange={(e) => navigate(`/projects/${e.target.value}`)}
              aria-label="Project"
              sx={{ minWidth: 240, bgcolor: 'background.paper', borderRadius: 1 }}
              slotProps={{ select: { displayEmpty: true } }}
            >
              <MenuItem value="" disabled>
                Choose a project
              </MenuItem>
              {projects.data.map((p) => (
                <MenuItem key={p.id} value={p.id}>
                  {p.name}
                </MenuItem>
              ))}
            </TextField>
          )}
          <Box sx={{ flex: 1 }} />
          {projectId && (
            <>
              <ToggleButton value="preview" selected={previewOpen} onChange={() => preview.setOpen(!previewOpen)} size="small"
                sx={{ color: 'inherit', borderColor: 'rgba(255,255,255,0.4)', textTransform: 'none', py: 0.25, '&.Mui-selected': { color: 'inherit', bgcolor: 'rgba(255,255,255,0.18)' } }}>
                <VerticalSplitIcon fontSize="small" sx={{ mr: 0.75 }} />Preview
              </ToggleButton>
              <Button variant="contained" color="secondary" startIcon={<BoltIcon />} endIcon={<ArrowDropDownIcon />}
                onClick={(e) => setGenerateMenu(e.currentTarget)}>
                Generate
              </Button>
              <Menu anchorEl={generateMenu} open={!!generateMenu} onClose={() => setGenerateMenu(null)}>
                <MenuItem component="a" href={downloads.specsZip(projectId)} download onClick={() => {
                  setGenerateMenu(null)
                  setTimeout(() => client.invalidateQueries({ queryKey: keys.snapshots(projectId, 'SPECS') }), 1500)
                }}>
                  <ListItemText primary="Spec" secondary="OpenAPI + AsyncAPI, YAML and JSON (.zip)" />
                </MenuItem>
                <MenuItem component="a" href={code.data?.ready ? downloads.codeZip(projectId) : undefined} download disabled={!code.data?.ready} onClick={() => {
                  setGenerateMenu(null)
                  setTimeout(() => client.invalidateQueries({ queryKey: keys.snapshots(projectId, 'CODE') }), 1500)
                }}>
                  <ListItemText primary="Code" secondary={!code.data ? 'Checking…' : code.data.ready
                    ? 'Maven project: Spring Boot + Camel (.zip)'
                    : `Fix ${codeErrors} problem${codeErrors === 1 ? '' : 's'} first (see Preview → Code)`} />
                </MenuItem>
                <MenuItem onClick={exportDiagrams} disabled={!!exporting}>
                  <ListItemText primary="Diagrams" secondary="Mermaid and PlantUML: sources, SVG and PNG (.zip)" />
                </MenuItem>
                <MenuItem disabled><ListItemText primary="All" secondary="Everything in one zip (Phase 6)" /></MenuItem>
              </Menu>
            </>
          )}
          <Tooltip title="The user guide, in a new tab">
            <Button color="inherit" startIcon={<MenuBookOutlinedIcon />} href="/guide" target="_blank" rel="noreferrer" sx={{ textTransform: 'none' }}>
              User guide
            </Button>
          </Tooltip>
          <FieldTipsToggle />
          <Button color="inherit" startIcon={<AddIcon />} endIcon={<ArrowDropDownIcon />} onClick={(e) => setMenu(e.currentTarget)}>
            New project
          </Button>
          <Menu anchorEl={menu} open={!!menu} onClose={() => setMenu(null)}>
            <MenuItem onClick={() => { setMenu(null); setCreating(true) }}>Empty project…</MenuItem>
            <MenuItem onClick={addSample}>Copy of the Order Service sample</MenuItem>
            <MenuItem onClick={() => { setMenu(null); setImporting(true) }}>Import from files…</MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>
      <Box sx={{ flex: 1, minHeight: 0, display: 'flex' }}>
        <PreviewContext.Provider value={preview}>
          <Outlet />
          <FieldHelpLayer />
        </PreviewContext.Provider>
      </Box>
      <Snackbar open={!!exporting} message={exporting} anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }} />
      <Snackbar open={!!exportError} autoHideDuration={8000} onClose={() => setExportError(null)}>
        <Alert severity="error" onClose={() => setExportError(null)}>{exportError}</Alert>
      </Snackbar>
      <ImportDialog open={importing} onClose={() => setImporting(false)} onImported={(id) => navigate(`/projects/${id}`)} />
      <ProjectDialog open={creating} onClose={() => setCreating(false)} onCreated={(p) => navigate(`/projects/${p.id}`)} />
    </Box>
  )
}

/** Turns the field tips (help shown when you hover over or tab into a field) on and off. */
function FieldTipsToggle() {
  const on = useFieldHelpOn()
  return (
    <Tooltip title={on ? 'Field tips are on: hover over any field to see what it is for. Click to turn them off.' : 'Field tips are off. Click to show what each field is for when you hover over it.'}>
      <ToggleButton value="tips" selected={on} onChange={() => setFieldHelpOn(!on)} size="small" aria-label="Field tips"
        sx={{ color: 'inherit', borderColor: 'rgba(255,255,255,0.4)', textTransform: 'none', py: 0.25, '&.Mui-selected': { color: 'inherit', bgcolor: 'rgba(255,255,255,0.18)' } }}>
        <TipsAndUpdatesOutlinedIcon fontSize="small" sx={{ mr: 0.75 }} />Field tips
      </ToggleButton>
    </Tooltip>
  )
}
