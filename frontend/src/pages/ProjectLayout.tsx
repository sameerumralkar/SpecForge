import { useState } from 'react'
import { Outlet, useLocation, useMatch, useParams } from 'react-router'
import { Box } from '@mui/material'
import { Sidebar } from '../components/Sidebar'
import { SpecPanel } from '../components/SpecPanel'
import { ResizeHandle } from '../components/ResizeHandle'
import { useEndpoints } from '../api/hooks'
import { usePreview } from '../lib/preview'

export function ProjectLayout() {
  const { projectId } = useParams()
  const preview = usePreview()
  const endpointMatch = useMatch('/projects/:projectId/endpoints/:endpointId/*')
  const endpoints = useEndpoints(projectId!)
  const { pathname } = useLocation()
  const onOutputPage = /^\/projects\/[^/]+\/(spec|code|diagrams|inventory|errors)\/?$/.test(pathname)
  const focused = endpoints.data?.find((e) => e.id === endpointMatch?.params.endpointId)
  const [width, setWidth] = useState(() => Number(localStorage.getItem('preview-width')) || 520)
  const resize = (w: number) => {
    setWidth(w)
    localStorage.setItem('preview-width', String(w))
  }
  return (
    <>
      <Sidebar projectId={projectId!} />
      <Box component="main" sx={{ flex: 1, minWidth: 0, overflow: 'auto', containerType: 'inline-size' }}>
        <Outlet />
      </Box>
      {preview.open && !onOutputPage && (
        <>
          <ResizeHandle onResize={resize} min={360} max={1000} />
          <Box component="aside" sx={{ width, flexShrink: 0 }}>
            <SpecPanel projectId={projectId!} focus={focused && { name: focused.name, kind: focused.kind }} onClose={() => preview.setOpen(false)} />
          </Box>
        </>
      )}
    </>
  )
}
