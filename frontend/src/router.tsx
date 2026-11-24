import { createBrowserRouter } from 'react-router'
import { AppShell } from './components/AppShell'
import { HomePage } from './pages/HomePage'
import { ProjectLayout } from './pages/ProjectLayout'
import { ProjectPage } from './pages/ProjectPage'
import { EndpointPage } from './pages/EndpointPage'
import { PayloadPage } from './pages/PayloadPage'
import { OutputPage } from './pages/OutputPage'
import { InventoryPage } from './pages/InventoryPage'

export const routes = [
  {
    path: '/',
    element: <AppShell />,
    children: [
      { index: true, element: <HomePage /> },
      {
        path: 'projects/:projectId',
        element: <ProjectLayout />,
        children: [
          { index: true, element: <ProjectPage /> },
          { path: 'endpoints/:endpointId/:tab?', element: <EndpointPage /> },
          { path: 'payloads/:payloadId', element: <PayloadPage /> },
          { path: 'inventory', element: <InventoryPage /> },
          { path: 'spec', element: <OutputPage view="spec" /> },
          { path: 'code', element: <OutputPage view="code" /> },
          { path: 'diagrams', element: <OutputPage view="diagrams" /> },
        ],
      },
    ],
  },
]

export const router = createBrowserRouter(routes)
