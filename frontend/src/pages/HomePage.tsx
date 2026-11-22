import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { Box, Button, CircularProgress, Stack, Typography } from '@mui/material'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { keys, useProjects } from '../api/hooks'
import { ProjectDialog } from '../components/ProjectDialog'
import { ErrorSummary } from '../components/ErrorSummary'

export function HomePage() {
  const projects = useProjects()
  const navigate = useNavigate()
  const client = useQueryClient()
  const [creating, setCreating] = useState(false)

  if (projects.isPending) return <CircularProgress sx={{ m: 4 }} />
  if (projects.error) {
    return (
      <Box sx={{ p: 4 }}>
        <ErrorSummary error={projects.error} />
        <Typography>Is the backend running? Start it with <code>./mvnw spring-boot:run</code> in <code>backend/</code>.</Typography>
      </Box>
    )
  }
  if (projects.data.length > 0) return <Navigate to={`/projects/${projects.data[0].id}`} replace />

  const addSample = async () => {
    const p = await api.createSampleProject()
    await client.invalidateQueries({ queryKey: keys.projects })
    navigate(`/projects/${p.id}`)
  }

  return (
    <Box sx={{ m: 'auto', textAlign: 'center' }}>
      <Typography variant="h5" gutterBottom>No projects yet</Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Start from the Order Service sample, or from an empty project.
      </Typography>
      <Stack direction="row" spacing={2} sx={{ justifyContent: 'center' }}>
        <Button variant="contained" onClick={addSample}>Add the sample</Button>
        <Button variant="outlined" onClick={() => setCreating(true)}>Empty project</Button>
      </Stack>
      <ProjectDialog open={creating} onClose={() => setCreating(false)} onCreated={(p) => navigate(`/projects/${p.id}`)} />
    </Box>
  )
}
