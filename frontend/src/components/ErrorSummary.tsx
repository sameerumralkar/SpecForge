import { Alert, AlertTitle } from '@mui/material'
import { ApiError } from '../api/client'

/** Lists every problem the server reported, so nothing is missed even if a field isn't on screen. */
export function ErrorSummary({ error, title }: { error: unknown; title?: string }) {
  if (!error) return null
  if (!(error instanceof ApiError) || error.errors.length === 0) {
    return (
      <Alert severity="error" sx={{ mb: 2 }}>
        {error instanceof Error ? error.message : String(error)}
      </Alert>
    )
  }
  return (
    <Alert severity="error" sx={{ mb: 2 }}>
      <AlertTitle>{title ?? `Not saved: ${error.errors.length} problem${error.errors.length === 1 ? '' : 's'}`}</AlertTitle>
      <ul style={{ margin: 0, paddingLeft: 18 }}>
        {error.errors.map((e, i) => (
          <li key={i}>
            <code>{e.field}</code>: {e.message}
          </li>
        ))}
      </ul>
    </Alert>
  )
}
