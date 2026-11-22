import { createBrowserRouter } from 'react-router'
import { AppShell } from './components/AppShell'
import { HomePage } from './pages/HomePage'
import { ProjectLayout } from './pages/ProjectLayout'
import { ProjectPage } from './pages/ProjectPage'
import { EndpointPage } from './pages/EndpointPage'
import { PayloadPage } from './pages/PayloadPage'

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
        ],
      },
    ],
  },
]

export const router = createBrowserRouter(routes)
