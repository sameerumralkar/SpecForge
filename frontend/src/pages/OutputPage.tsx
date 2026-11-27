import { useParams } from 'react-router'
import { Box, Stack, Typography } from '@mui/material'
import { ScreenHelp } from '../components/help/ScreenHelp'
import { SpecPanel } from '../components/SpecPanel'
import { CodePanel } from '../components/CodePanel'
import { DiagramPanel } from '../components/DiagramPanel'

export type OutputView = 'spec' | 'code' | 'diagrams'

const TEXT: Record<OutputView, { title: string; subtitle: string }> = {
  spec: { title: 'API spec', subtitle: 'The OpenAPI and AsyncAPI documents generated from the endpoints and payloads, with validation results.' },
  code: { title: 'Generated code', subtitle: 'The Spring Boot + Camel Maven project. Browse the files, compare with an earlier download, or download it as a zip.' },
  diagrams: { title: 'Diagrams', subtitle: 'An overview, class diagrams, and a sequence and class diagram for every endpoint, in Mermaid or PlantUML.' },
}

/** A full page for one of the project's outputs. */
export function OutputPage({ view }: { view: OutputView }) {
  const { projectId } = useParams()
  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', minHeight: 0 }}>
      <Box sx={{ px: 3, py: 1.5, borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <Typography variant="h6" component="h1">{TEXT[view].title}</Typography>
          <ScreenHelp screen={view} />
        </Stack>
        <Typography variant="body2" color="text.secondary">{TEXT[view].subtitle}</Typography>
      </Box>
      <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', bgcolor: 'background.paper' }}>
        {view === 'spec' && <SpecPanel projectId={projectId!} tabs={['openapi', 'asyncapi']} />}
        {view === 'code' && <CodePanel projectId={projectId!} />}
        {view === 'diagrams' && <DiagramPanel projectId={projectId!} />}
      </Box>
    </Box>
  )
}
