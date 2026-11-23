import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { Alert, Box, Button, Card, CardContent, CircularProgress, Stack, Typography } from '@mui/material'
import { useQueryClient } from '@tanstack/react-query'
import { api, downloads } from '../api/client'
import FileDownloadIcon from '@mui/icons-material/FileDownload'
import { keys, useProject } from '../api/hooks'
import type { Project, ProjectRequest } from '../api/types'
import { EditorHeader } from '../components/EditorHeader'
import { ErrorSummary } from '../components/ErrorSummary'
import { errorText } from '../lib/errors'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ProjectForm } from '../editors/ProjectForm'
import { useDraft } from '../lib/useDraft'
import { UnsavedChangesGuard } from '../components/UnsavedChangesGuard'

const toRequest = (p: Project): ProjectRequest => ({
  name: p.name, basePackage: p.basePackage, version: p.version, description: p.description ?? '',
  defaultImplementationStyle: p.defaultImplementationStyle, allowCircularReferences: p.allowCircularReferences,
})

export function ProjectPage() {
  const { projectId } = useParams()
  const project = useProject(projectId!)
  if (!project.data) return project.error ? <Box sx={{ p: 3 }}><ErrorSummary error={project.error} /></Box> : <CircularProgress sx={{ m: 4 }} />
  return <ProjectSettings key={project.data.id} project={project.data} />
}

function ProjectSettings({ project }: { project: Project }) {
  const navigate = useNavigate()
  const client = useQueryClient()
  const { draft, setDraft, dirty, saved, reset } = useDraft(toRequest(project))
  const [error, setError] = useState<unknown>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)

  const save = async () => {
    try {
      const updated = await api.updateProject(project.id, draft)
      saved(toRequest(updated))
      setError(null)
      await client.invalidateQueries({ queryKey: keys.projects })
    } catch (e) {
      setError(e)
    }
  }

  const remove = async () => {
    await api.deleteProject(project.id)
    client.removeQueries({ queryKey: keys.project(project.id) })
    await client.invalidateQueries({ queryKey: keys.projects })
    navigate('/')
  }

  return (
    <>
      <UnsavedChangesGuard dirty={dirty} />
      <EditorHeader title="Project settings" subtitle={project.name} dirty={dirty} onSave={save} onDiscard={() => { reset(); setError(null) }} />
      <Box sx={{ p: 3, maxWidth: 820 }}>
        <ErrorSummary error={error} />
        {errorText(error, 'allowCircularReferences') && <Alert severity="warning" sx={{ mb: 2 }}>{errorText(error, 'allowCircularReferences')}</Alert>}
        <Card variant="outlined">
          <CardContent>
            <ProjectForm value={draft} onChange={setDraft} error={error} />
          </CardContent>
        </Card>
        <Stack direction="row" spacing={2} sx={{ mt: 3 }}>
          <Card variant="outlined" sx={{ flex: 1 }}>
            <CardContent>
              <Typography variant="h4">{project.endpointCount}</Typography>
              <Typography color="text.secondary">endpoints</Typography>
            </CardContent>
          </Card>
          <Card variant="outlined" sx={{ flex: 1 }}>
            <CardContent>
              <Typography variant="h4">{project.payloadCount}</Typography>
              <Typography color="text.secondary">payloads</Typography>
            </CardContent>
          </Card>
          <Card variant="outlined" sx={{ flex: 2 }}>
            <CardContent>
              <Typography color="text.secondary">Last changed</Typography>
              <Typography>{new Date(project.updatedAt).toLocaleString()}</Typography>
            </CardContent>
          </Card>
        </Stack>
        <Stack direction="row" spacing={2} sx={{ mt: 4 }}>
          <Button variant="outlined" startIcon={<FileDownloadIcon />} component="a" href={downloads.exportProject(project.id)} download>
            Export project (JSON)
          </Button>
          <Box sx={{ flex: 1 }} />
          <Button color="error" variant="outlined" onClick={() => setConfirmDelete(true)}>Delete project…</Button>
        </Stack>
      </Box>
      <ConfirmDialog open={confirmDelete} title={`Delete ${project.name}?`} onConfirm={remove} onClose={() => setConfirmDelete(false)}>
        All its endpoints, payloads and rules are deleted. This cannot be undone.
      </ConfirmDialog>
    </>
  )
}
