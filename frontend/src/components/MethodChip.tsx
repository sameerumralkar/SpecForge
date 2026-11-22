import { Chip } from '@mui/material'
import type { Endpoint } from '../api/types'

const METHOD_COLORS: Record<string, string> = {
  GET: '#2e7d32', POST: '#1565c0', PUT: '#ef6c00', PATCH: '#6a1b9a', DELETE: '#c62828', HEAD: '#546e7a', OPTIONS: '#546e7a',
  SEND: '#00838f', RECEIVE: '#4527a0',
}

export function MethodChip({ endpoint }: { endpoint: Pick<Endpoint, 'kind' | 'httpMethod' | 'operation'> }) {
  const label = (endpoint.kind === 'REST' ? endpoint.httpMethod : endpoint.operation) ?? '?'
  return (
    <Chip
      label={label}
      sx={{ bgcolor: METHOD_COLORS[label] ?? '#757575', color: '#fff', fontWeight: 600, fontSize: 10, height: 18, minWidth: 64, '& .MuiChip-label': { px: 0.75 } }}
    />
  )
}
