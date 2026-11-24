import { ListSubheader, MenuItem } from '@mui/material'
import type { Payload } from '../../api/types'
import { byRole } from '../../lib/inventory'

/**
 * Menu items for a payload picker, grouped into inventory objects and structures. Returned as an
 * array (not a fragment) because MUI's Select reads its options from its direct children.
 */
export function payloadOptions(payloads: Payload[], selfId?: string) {
  const { objects, structures } = byRole(payloads)
  const item = (p: Payload) => <MenuItem key={p.id} value={p.id}>{p.name}{p.id === selfId ? ' (this payload)' : ''}</MenuItem>
  return [
    ...(objects.length ? [<ListSubheader key="objects">Inventory objects</ListSubheader>, ...objects.map(item)] : []),
    ...(structures.length ? [<ListSubheader key="structures">Structures</ListSubheader>, ...structures.map(item)] : []),
  ]
}
