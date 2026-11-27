import { useState } from 'react'
import { useNavigate } from 'react-router'
import { Autocomplete, Box, Card, CardContent, CardHeader, Chip, Stack, TextField, Typography } from '@mui/material'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import { keys, useCapabilities } from '../../api/hooks'
import type { Capability } from '../../api/types'
import { ErrorSummary } from '../ErrorSummary'

/** Links an API to the capabilities it realises. Changes are saved straight away. */
export function EndpointCapabilities({ projectId, endpointId }: { projectId: string; endpointId: string }) {
  const navigate = useNavigate()
  const client = useQueryClient()
  const capabilities = useCapabilities(projectId)
  const [error, setError] = useState<unknown>(null)
  const [saved, setSaved] = useState(false)
  const all = capabilities.data ?? []
  const value = all.filter((c) => c.endpointIds.includes(endpointId))

  const change = async (next: Capability[]) => {
    try {
      setError(null)
      await api.setEndpointCapabilities(projectId, endpointId, next.map((c) => c.id))
      await client.invalidateQueries({ queryKey: keys.capabilities(projectId) })
      setSaved(true)
    } catch (e) {
      setError(e)
    }
  }

  return (
    <Card variant="outlined">
      <CardHeader title="Capabilities" subheader="The functional capabilities this API realises. Changes are saved straight away."
        slotProps={{ title: { variant: 'subtitle1' } }} />
      <CardContent sx={{ pt: 0 }}>
        <ErrorSummary error={error} />
        {all.length === 0
          ? <Typography variant="body2" color="text.secondary">
              The project has no capability model yet. Build one on the <Box component="a" sx={{ cursor: 'pointer', color: 'primary.main' }}
                onClick={() => navigate(`/projects/${projectId}/capabilities`)}>Capabilities</Box> page.
            </Typography>
          : <Autocomplete multiple options={all} value={value} disableCloseOnSelect
              getOptionLabel={(c) => c.name} isOptionEqualToValue={(a, b) => a.id === b.id}
              onChange={(_, next) => change(next)}
              renderOption={(props, c) => {
                const { key, ...rest } = props as typeof props & { key: string }
                return (
                  <li key={key} {...rest}>
                    <Stack sx={{ pl: (c.level - 1) * 1.5 }}>
                      <Typography variant="body2"><b>L{c.level}</b> · {c.name}</Typography>
                      <Typography variant="caption" color="text.secondary">{c.path.join(' › ')}</Typography>
                    </Stack>
                  </li>
                )
              }}
              renderValue={(values, getItemProps) => values.map((c, index) => (
                <Chip {...getItemProps({ index })} key={c.id} size="small" label={`L${c.level} ${c.name}`} title={c.path.join(' › ')} />
              ))}
              renderInput={(params) => <TextField {...params} label="Linked capabilities" placeholder="Pick a capability"
                helperText={saved ? 'Saved.' : 'The API then shows in the inventory of each capability and of every level above it.'} />} />}
      </CardContent>
    </Card>
  )
}
