import { createBrowserRouter } from 'react-router'
import { AppShell } from './components/AppShell'
import { HomePage } from './pages/HomePage'
import { GuidePage } from './pages/GuidePage'
import { ProjectLayout } from './pages/ProjectLayout'
import { ProjectPage } from './pages/ProjectPage'
import { EndpointPage } from './pages/EndpointPage'
import { PayloadPage } from './pages/PayloadPage'
import { OutputPage } from './pages/OutputPage'
import { InventoryPage } from './pages/InventoryPage'
import { ErrorCodesPage } from './pages/ErrorCodesPage'
import { FieldInventoryPage } from './pages/FieldInventoryPage'
import { CapabilitiesPage } from './pages/CapabilitiesPage'
import { CapabilityImportPage } from './pages/CapabilityImportPage'

export const routes = [
  {
    path: '/',
    element: <AppShell />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'guide/:guideId?', element: <GuidePage /> },
      {
        path: 'projects/:projectId',
        element: <ProjectLayout />,
        children: [
          { index: true, element: <ProjectPage /> },
          { path: 'endpoints/:endpointId/:tab?', element: <EndpointPage /> },
          { path: 'payloads/:payloadId', element: <PayloadPage /> },
          { path: 'inventory', element: <InventoryPage /> },
          { path: 'errors', element: <ErrorCodesPage /> },
          { path: 'fields', element: <FieldInventoryPage /> },
          { path: 'capabilities', element: <CapabilitiesPage /> },
          { path: 'capabilities/imports/:importId', element: <CapabilityImportPage /> },
          { path: 'spec', element: <OutputPage view="spec" /> },
          { path: 'code', element: <OutputPage view="code" /> },
          { path: 'diagrams', element: <OutputPage view="diagrams" /> },
        ],
      },
    ],
  },
]

export const router = createBrowserRouter(routes)
