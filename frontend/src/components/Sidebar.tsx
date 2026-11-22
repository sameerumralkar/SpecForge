import { useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import {
  Box, Divider, IconButton, List, ListItemButton, ListItemText, ListSubheader, Menu, MenuItem, Tooltip, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import MoreVertIcon from '@mui/icons-material/MoreVert'
import SettingsIcon from '@mui/icons-material/Settings'
import { ApiError, api } from '../api/client'
import { useEndpoints, usePayloads, useProject, useRefreshProject } from '../api/hooks'
import type { Endpoint, Payload, PayloadUsage } from '../api/types'
import { MethodChip } from './MethodChip'
import { groupEndpoints } from '../lib/endpoints'
import { ConfirmDialog } from './ConfirmDialog'
import { UsagesDialog } from './UsagesDialog'
import { NewEndpointDialog } from './NewEndpointDialog'
import { NewPayloadDialog } from './NewPayloadDialog'

type Target = { kind: 'endpoint'; item: Endpoint } | { kind: 'payload'; item: Payload }


export function Sidebar({ projectId }: { projectId: string }) {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const project = useProject(projectId)
  const endpoints = useEndpoints(projectId)
  const payloads = usePayloads(projectId)
  const refresh = useRefreshProject()

  const [menu, setMenu] = useState<{ anchor: HTMLElement; target: Target } | null>(null)
  const [confirm, setConfirm] = useState<Target | null>(null)
  const [blocked, setBlocked] = useState<{ name: string; usages: PayloadUsage[] } | null>(null)
  const [newEndpoint, setNewEndpoint] = useState(false)
  const [newPayload, setNewPayload] = useState(false)

  const groups = useMemo(() => groupEndpoints(endpoints.data ?? []), [endpoints.data])
  const base = `/projects/${projectId}`

  const duplicate = async (t: Target) => {
    setMenu(null)
    const copy = t.kind === 'endpoint' ? await api.duplicateEndpoint(projectId, t.item.id) : await api.duplicatePayload(projectId, t.item.id)
    await refresh(projectId)
    navigate(`${base}/${t.kind}s/${copy.id}`)
  }

  const remove = async (t: Target) => {
    setConfirm(null)
    try {
      if (t.kind === 'endpoint') await api.deleteEndpoint(projectId, t.item.id)
      else await api.deletePayload(projectId, t.item.id)
      await refresh(projectId)
      if (pathname.includes(t.item.id)) navigate(base)
    } catch (e) {
      if (e instanceof ApiError && e.usages.length) setBlocked({ name: t.item.name, usages: e.usages })
      else throw e
    }
  }

  const itemMenu = (target: Target) => (
    <IconButton
      edge="end"
      aria-label={`Actions for ${target.item.name}`}
      onClick={(e) => { e.stopPropagation(); setMenu({ anchor: e.currentTarget, target }) }}
    >
      <MoreVertIcon fontSize="small" />
    </IconButton>
  )

  return (
    <Box component="nav" sx={{ width: 290, flexShrink: 0, borderRight: 1, borderColor: 'divider', bgcolor: 'background.paper', overflow: 'auto' }}>
      <ListItemButton selected={pathname === base} onClick={() => navigate(base)}>
        <ListItemText
          primary={project.data?.name ?? '…'}
          secondary={project.data ? `${project.data.basePackage} · v${project.data.version}` : undefined}
          slotProps={{ primary: { sx: { fontWeight: 600 } } }}
        />
        <SettingsIcon fontSize="small" color="action" />
      </ListItemButton>
      <Divider />

      <List dense subheader={
        <ListSubheader sx={{ display: 'flex', alignItems: 'center' }}>
          <Box sx={{ flex: 1 }}>Endpoints</Box>
          <Tooltip title="New endpoint"><IconButton aria-label="New endpoint" onClick={() => setNewEndpoint(true)}><AddIcon fontSize="small" /></IconButton></Tooltip>
        </ListSubheader>
      }>
        {groups.length === 0 && <Typography variant="body2" color="text.secondary" sx={{ px: 2, py: 1 }}>No endpoints yet</Typography>}
        {groups.map(([group, items]) => (
          <Box key={group}>
            <Typography variant="overline" color="text.secondary" sx={{ px: 2, display: 'block', lineHeight: 2.2 }}>{group}</Typography>
            {items.map((e) => (
              <ListItemButton key={e.id} selected={pathname.startsWith(`${base}/endpoints/${e.id}`)} onClick={() => navigate(`${base}/endpoints/${e.id}`)} sx={{ pl: 2, gap: 1 }}>
                <MethodChip endpoint={e} />
                <ListItemText
                  primary={e.name}
                  secondary={e.kind === 'REST' ? e.path : e.channel}
                  slotProps={{ secondary: { noWrap: true, sx: { fontFamily: 'monospace', fontSize: 11 } } }}
                />
                {itemMenu({ kind: 'endpoint', item: e })}
              </ListItemButton>
            ))}
          </Box>
        ))}
      </List>
      <Divider />

      <List dense subheader={
        <ListSubheader sx={{ display: 'flex', alignItems: 'center' }}>
          <Box sx={{ flex: 1 }}>Payloads</Box>
          <Tooltip title="New payload"><IconButton aria-label="New payload" onClick={() => setNewPayload(true)}><AddIcon fontSize="small" /></IconButton></Tooltip>
        </ListSubheader>
      }>
        {payloads.data?.length === 0 && <Typography variant="body2" color="text.secondary" sx={{ px: 2, py: 1 }}>No payloads yet</Typography>}
        {payloads.data?.map((p) => (
          <ListItemButton key={p.id} selected={pathname === `${base}/payloads/${p.id}`} onClick={() => navigate(`${base}/payloads/${p.id}`)}>
            <ListItemText primary={p.name} secondary={`${p.fields.length} field${p.fields.length === 1 ? '' : 's'}`} />
            {itemMenu({ kind: 'payload', item: p })}
          </ListItemButton>
        ))}
      </List>

      <Menu anchorEl={menu?.anchor} open={!!menu} onClose={() => setMenu(null)}>
        <MenuItem onClick={() => menu && duplicate(menu.target)}>Duplicate</MenuItem>
        <MenuItem onClick={() => { setConfirm(menu!.target); setMenu(null) }} sx={{ color: 'error.main' }}>Delete…</MenuItem>
      </Menu>
      <ConfirmDialog
        open={!!confirm}
        title={`Delete ${confirm?.kind} ${confirm?.item.name}?`}
        onConfirm={() => confirm && remove(confirm)}
        onClose={() => setConfirm(null)}
      >
        {confirm?.kind === 'endpoint' ? 'Its processing rules are deleted too.' : 'This cannot be undone.'}
      </ConfirmDialog>
      <UsagesDialog projectId={projectId} payloadName={blocked?.name ?? ''} usages={blocked?.usages ?? null} onClose={() => setBlocked(null)} />
      <NewEndpointDialog projectId={projectId} open={newEndpoint} onClose={() => setNewEndpoint(false)}
        onCreated={(e) => navigate(`${base}/endpoints/${e.id}`)} />
      <NewPayloadDialog projectId={projectId} open={newPayload} onClose={() => setNewPayload(false)}
        onCreated={(p) => navigate(`${base}/payloads/${p.id}`)} />
    </Box>
  )
}
