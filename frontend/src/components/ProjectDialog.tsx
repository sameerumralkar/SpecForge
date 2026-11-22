import { useState } from 'react'
import { Button, Dialog, DialogActions, DialogContent, DialogTitle } from '@mui/material'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { keys } from '../api/hooks'
import type { Project, ProjectRequest } from '../api/types'
import { ProjectForm } from '../editors/ProjectForm'
import { ErrorSummary } from './ErrorSummary'

const EMPTY: ProjectRequest = {
  name: '',
  basePackage: 'com.example.app',
  version: '1.0.0',
  description: '',
  defaultImplementationStyle: 'IMPLEMENTATION_CLASS',
  allowCircularReferences: false,
}

export function ProjectDialog({ open, onClose, onCreated }: {
  open: boolean
  onClose: () => void
  onCreated: (project: Project) => void
}) {
  const client = useQueryClient()
  const [draft, setDraft] = useState<ProjectRequest>(EMPTY)
  const [error, setError] = useState<unknown>(null)

  const create = async () => {
    try {
      const project = await api.createProject(draft)
      await client.invalidateQueries({ queryKey: keys.projects })
      setDraft(EMPTY)
      setError(null)
      onClose()
      onCreated(project)
    } catch (e) {
      setError(e)
    }
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>New project</DialogTitle>
      <DialogContent>
        <ErrorSummary error={error} />
        <ProjectForm value={draft} onChange={setDraft} error={error} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={create}>Create</Button>
      </DialogActions>
    </Dialog>
  )
}
