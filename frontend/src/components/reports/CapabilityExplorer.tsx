import { useEffect, useMemo, useRef, useState } from 'react'
import { Link as RouterLink } from 'react-router'
import {
  Box, Breadcrumbs, Card, Chip, IconButton, InputAdornment, Link, List, ListItemButton, ListItemText, ListSubheader, Stack, TextField,
  Tooltip, Typography,
} from '@mui/material'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import OpenInNewIcon from '@mui/icons-material/OpenInNew'
import SearchIcon from '@mui/icons-material/Search'
import type { CapabilityMap } from '../../api/types'
import { MapIndex, nodeKey, sameNode, type Direction, type MapColumn, type MapItem, type MapNode } from '../../lib/capabilityMap'

const badgeColor = (badge: string): 'primary' | 'secondary' | 'default' | 'success' | 'warning' =>
  /^L\d$/.test(badge) ? 'primary' : badge === 'object' ? 'secondary' : /^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS|SEND|RECEIVE)$/.test(badge) ? 'success' : 'default'

/**
 * Walks a project's capability map in columns. Down: from an L1 capability through the levels below it to
 * the endpoints, then their objects and processing rules, then the objects' fields. Up: from a field to the
 * objects it is in, the endpoints that use them, and the capabilities above, up to L1. Each click opens the
 * next column; clicking further left starts again from there.
 */
export function CapabilityExplorer({ map, direction }: { map: CapabilityMap; direction: Direction }) {
  const index = useMemo(() => new MapIndex(map), [map])
  const [path, setPath] = useState<MapNode[]>([])
  const [filter, setFilter] = useState('')
  const strip = useRef<HTMLDivElement>(null)

  useEffect(() => { setPath([]); setFilter('') }, [map.projectId, direction])

  // Columns: the root, then one per click. A node that has gone from the map ends the trail.
  const columns: { column: MapColumn; node?: MapNode }[] = [{ column: index.root(direction) }]
  for (const node of path) {
    const column = index.column(node, direction)
    if (!column) break
    columns.push({ column, node })
  }

  useEffect(() => {
    strip.current?.scrollTo?.({ left: strip.current.scrollWidth, behavior: 'smooth' })
  }, [path.length])

  const open = (depth: number, node: MapNode) => setPath((p) => [...p.slice(0, depth), node])

  return (
    <Box>
      <Breadcrumbs aria-label="Where you are" sx={{ mb: 1.5, minHeight: 24 }} maxItems={12}>
        <Link component="button" type="button" underline="hover" onClick={() => setPath([])}>
          {direction === 'down' ? 'Capability map' : 'Fields'}
        </Link>
        {columns.slice(1).map(({ column, node }, i) => (
          i === columns.length - 2
            ? <Typography key={nodeKey(node!)} color="text.primary" variant="body2">{column.title}</Typography>
            : <Link key={nodeKey(node!)} component="button" type="button" underline="hover" onClick={() => setPath((p) => p.slice(0, i + 1))}>{column.title}</Link>
        ))}
      </Breadcrumbs>
      <Box ref={strip} role="group" aria-label={direction === 'down' ? 'Capability map, top down' : 'Where used, bottom up'}
        sx={{ display: 'flex', gap: 1.5, overflowX: 'auto', pb: 1, alignItems: 'stretch' }}>
        {columns.map(({ column }, depth) => (
          <ColumnCard key={depth === 0 ? 'root' : nodeKey(columns[depth].node!)} column={column} selected={path[depth]}
            filter={depth === 0 ? filter : undefined} onFilter={depth === 0 ? setFilter : undefined}
            onOpen={(node) => open(depth, node)} />
        ))}
      </Box>
    </Box>
  )
}

function ColumnCard({ column, selected, onOpen, filter, onFilter }: {
  column: MapColumn
  selected?: MapNode
  onOpen: (node: MapNode) => void
  filter?: string
  onFilter?: (q: string) => void
}) {
  const q = filter?.trim().toLowerCase() ?? ''
  const match = (i: MapItem) => !q || `${i.primary} ${i.secondary ?? ''} ${i.badge ?? ''}`.toLowerCase().includes(q)
  return (
    <Card variant="outlined" role="region" aria-label={column.title}
      sx={{ width: 320, flexShrink: 0, display: 'flex', flexDirection: 'column', maxHeight: 'calc(100vh - 300px)', minHeight: 320 }}>
      <Box sx={{ px: 2, pt: 1.5, pb: 1, borderBottom: 1, borderColor: 'divider' }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <Typography variant="subtitle2" sx={{ flex: 1, fontWeight: 600, wordBreak: 'break-word' }}>{column.title}</Typography>
          {column.editor && (
            <Tooltip title="Open in the editor">
              <IconButton size="small" component={RouterLink} to={column.editor} aria-label={`Open ${column.title} in the editor`}>
                <OpenInNewIcon sx={{ fontSize: 16 }} />
              </IconButton>
            </Tooltip>
          )}
        </Stack>
        {column.subtitle && <Typography variant="caption" color="text.secondary" sx={{ display: 'block', wordBreak: 'break-word' }}>{column.subtitle}</Typography>}
        {onFilter && (
          <TextField size="small" fullWidth sx={{ mt: 1 }} placeholder="Filter this column" value={filter} onChange={(e) => onFilter(e.target.value)}
            slotProps={{ htmlInput: { 'aria-label': 'Filter the first column' }, input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
        )}
      </Box>
      <Box sx={{ overflow: 'auto', flex: 1 }}>
        {column.sections.map((s) => {
          const items = s.items.filter(match)
          return (
            <List key={s.heading} dense disablePadding aria-label={s.heading}
              subheader={<ListSubheader sx={{ lineHeight: '32px', bgcolor: 'grey.50', fontSize: 12, textTransform: 'uppercase', letterSpacing: 0.4 }}>{s.heading}</ListSubheader>}>
              {items.length === 0 && (
                <Typography variant="body2" color="text.secondary" sx={{ px: 2, py: 1 }}>{q && s.items.length ? 'Nothing matches.' : s.empty ?? 'None.'}</Typography>
              )}
              {items.map((i) => {
                const text = (
                  <ListItemText primary={i.primary} secondary={i.secondary}
                    slotProps={{ primary: { variant: 'body2', sx: { fontWeight: 500, wordBreak: 'break-word' } }, secondary: { variant: 'caption', sx: { wordBreak: 'break-word' } } }} />
                )
                const badge = i.badge && <Chip size="small" label={i.badge} color={badgeColor(i.badge)} variant="outlined" sx={{ mr: 1, height: 20, fontSize: 11, flexShrink: 0 }} />
                return i.node ? (
                  <ListItemButton key={i.key} selected={sameNode(i.node, selected)} onClick={() => onOpen(i.node!)}
                    aria-current={sameNode(i.node, selected) ? 'true' : undefined} sx={{ alignItems: 'flex-start' }}>
                    {badge}{text}<ChevronRightIcon fontSize="small" sx={{ color: 'text.disabled', mt: 0.25 }} />
                  </ListItemButton>
                ) : (
                  <Box key={i.key} component="li" sx={{ display: 'flex', alignItems: 'flex-start', px: 2, py: 0.5 }}>{badge}{text}</Box>
                )
              })}
            </List>
          )
        })}
      </Box>
    </Card>
  )
}
