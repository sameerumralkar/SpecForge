import { useState } from 'react'
import {
  Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Stack, TextField, Typography,
} from '@mui/material'
import UploadFileIcon from '@mui/icons-material/UploadFile'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { keys } from '../api/hooks'
import type { ImportResult } from '../api/types'
import { ErrorSummary } from './ErrorSummary'

interface PickedFile {
  name: string
  text: string
  /** True for a Spec Builder project export, false for an OpenAPI/AsyncAPI document. */
  project: boolean
}

function isProjectExport(text: string): boolean {
  try {
    return JSON.parse(text)?.format === 'spec-builder/project'
  } catch {
    return false
  }
}

/**
 * Creates a project from files: a Spec Builder export (.specbuilder.json), or one or more
 * OpenAPI / AsyncAPI documents in YAML or JSON.
 */
export function ImportDialog({ open, onClose, onImported }: {
  open: boolean
  onClose: () => void
  onImported: (projectId: string) => void
}) {
  const client = useQueryClient()
  const [files, setFiles] = useState<PickedFile[]>([])
  const [name, setName] = useState('')
  const [basePackage, setBasePackage] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<ImportResult | null>(null)

  const projectFiles = files.filter((f) => f.project)
  const mixed = projectFiles.length > 0 && files.length > 1

  const pick = async (list: FileList | null) => {
    if (!list) return
    const picked = await Promise.all([...list].map(async (f) => {
      const text = await f.text()
      return { name: f.name, text, project: isProjectExport(text) }
    }))
    setFiles(picked)
    setError(null)
  }

  const reset = () => {
    setFiles([])
    setName('')
    setBasePackage('')
    setError(null)
    setResult(null)
  }

  const close = () => {
    reset()
    onClose()
  }

  const submit = async () => {
    setBusy(true)
    setError(null)
    try {
      const r = projectFiles.length === 1
        ? await api.importProject(JSON.parse(projectFiles[0].text), name || undefined)
        : await api.importSpec(files.map((f) => f.text), name || undefined, basePackage || undefined)
      await client.invalidateQueries({ queryKey: keys.projects })
      if (r.warnings.length === 0) {
        reset()
        onClose()
        onImported(r.project.id)
      } else {
        setResult(r)
      }
    } catch (e) {
      setError(e)
    } finally {
      setBusy(false)
    }
  }

  if (result) {
    return (
      <Dialog open={open} onClose={close} maxWidth="md" fullWidth>
        <DialogTitle>Imported "{result.project.name}"</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: 1 }}>
            {result.project.endpointCount} endpoints and {result.project.payloadCount} payloads. Some things could not be imported exactly:
          </DialogContentText>
          <Box component="ul" sx={{ m: 0, pl: 2.5, maxHeight: 360, overflow: 'auto' }} aria-label="Import warnings">
            {result.warnings.map((w, i) => <Typography component="li" variant="body2" key={i}>{w}</Typography>)}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button variant="contained" onClick={() => { const id = result.project.id; close(); onImported(id) }}>Open project</Button>
        </DialogActions>
      </Dialog>
    )
  }

  return (
    <Dialog open={open} onClose={close} maxWidth="sm" fullWidth>
      <DialogTitle>Import a project</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          Choose a Spec Builder export (.specbuilder.json), or OpenAPI 3 / AsyncAPI 2–3 files in YAML or JSON.
          An OpenAPI and an AsyncAPI file together become one project.
        </DialogContentText>
        <ErrorSummary error={error} title="Not imported" />
        {mixed && <Alert severity="warning" sx={{ mb: 2 }}>A project export has to be imported on its own.</Alert>}
        <Stack spacing={2}>
          <Box>
            <Button component="label" variant="outlined" startIcon={<UploadFileIcon />}>
              Choose files
              <input hidden type="file" multiple accept=".json,.yaml,.yml" aria-label="Files to import"
                onChange={(e) => pick(e.target.files)} />
            </Button>
            <Stack direction="row" spacing={1} useFlexGap sx={{ mt: 1, flexWrap: 'wrap' }}>
              {files.map((f) => <Chip key={f.name} label={`${f.name} · ${f.project ? 'project export' : 'API spec'}`} />)}
            </Stack>
          </Box>
          <TextField label="Project name (optional)" value={name} onChange={(e) => setName(e.target.value)}
            helperText="Defaults to the name in the file" />
          {files.length > 0 && projectFiles.length === 0 && (
            <TextField label="Base package (optional)" value={basePackage} onChange={(e) => setBasePackage(e.target.value)}
              placeholder="com.example.orders" helperText="Defaults to com.example.<name>" />
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={close}>Cancel</Button>
        <Button variant="contained" onClick={submit} disabled={files.length === 0 || mixed || busy}>Import</Button>
      </DialogActions>
    </Dialog>
  )
}
