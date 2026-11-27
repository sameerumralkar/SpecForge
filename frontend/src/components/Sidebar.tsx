import { useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import {
  Box, Divider, IconButton, List, ListItemButton, ListItemIcon, ListItemText, ListSubheader, Menu, MenuItem, Tooltip, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import MoreVertIcon from '@mui/icons-material/MoreVert'
import SettingsIcon from '@mui/icons-material/Settings'
import DescriptionIcon from '@mui/icons-material/Description'
import CodeIcon from '@mui/icons-material/Code'
import AccountTreeIcon from '@mui/icons-material/AccountTree'
import Inventory2OutlinedIcon from '@mui/icons-material/Inventory2Outlined'
import ReportOutlinedIcon from '@mui/icons-material/ReportOutlined'
import ShortTextIcon from '@mui/icons-material/ShortText'
import { ApiError, api } from '../api/client'
import { useEndpoints, usePayloads, useProject, useRefreshProject } from '../api/hooks'
import type { Endpoint, Payload, PayloadUsage } from '../api/types'
import { MethodChip } from './MethodChip'
import { groupEndpoints } from '../lib/endpoints'
import { byRole } from '../lib/inventory'
import { ConfirmDialog } from './ConfirmDialog'
import { UsagesDialog } from './UsagesDialog'
import { NewEndpointDialog } from './NewEndpointDialog'
import { NewPayloadDialog } from './NewPayloadDialog'

type Target = { kind: 'endpoint'; item: Endpoint } | { kind: 'payload'; item: Payload }

const MENU = [
  { label: 'Overview & settings', path: '', icon: <SettingsIcon fontSize="small" /> },
  { label: 'Field inventory', path: 'fields', icon: <ShortTextIcon fontSize="small" /> },
  { label: 'Object inventory', path: 'inventory', icon: <Inventory2OutlinedIcon fontSize="small" /> },
  { label: 'Error codes', path: 'errors', icon: <ReportOutlinedIcon fontSize="small" /> },
  { label: 'API spec', path: 'spec', icon: <DescriptionIcon fontSize="small" /> },
  { label: 'Generated code', path: 'code', icon: <CodeIcon fontSize="small" /> },
  { label: 'Diagrams', path: 'diagrams', icon: <AccountTreeIcon fontSize="small" /> },
]


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
  const payloadGroups = useMemo(() => {
    const { objects, structures } = byRole(payloads.data ?? [])
    return [['Inventory objects', objects], ['Structures', structures]] as const
  }, [payloads.data])
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
      <Box sx={{ px: 2, pt: 1.5, pb: 0.5 }}>
        <Typography sx={{ fontWeight: 600 }} noWrap>{project.data?.name ?? '…'}</Typography>
        {project.data && <Typography variant="caption" color="text.secondary">{project.data.basePackage} · v{project.data.version}</Typography>}
      </Box>
      <List dense aria-label="Project menu">
        {MENU.map((m) => {
          const to = m.path ? `${base}/${m.path}` : base
          return (
            <ListItemButton key={m.label} selected={pathname === to} onClick={() => navigate(to)} sx={{ py: 0.75 }}>
              <ListItemIcon sx={{ minWidth: 34 }}>{m.icon}</ListItemIcon>
              <ListItemText primary={m.label} slotProps={{ primary: { sx: { fontWeight: 500 } } }} />
            </ListItemButton>
          )
        })}
      </List>
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
        {payloadGroups.map(([group, items]) => items.length > 0 && (
          <Box key={group}>
            <Typography variant="overline" color="text.secondary" sx={{ px: 2, display: 'block', lineHeight: 2.2 }}>{group}</Typography>
            {items.map((p) => (
              <ListItemButton key={p.id} selected={pathname === `${base}/payloads/${p.id}`} onClick={() => navigate(`${base}/payloads/${p.id}`)}>
                <ListItemText primary={p.name} secondary={`${p.fields.length} field${p.fields.length === 1 ? '' : 's'}`} />
                {itemMenu({ kind: 'payload', item: p })}
              </ListItemButton>
            ))}
          </Box>
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
