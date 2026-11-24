import type { ReactNode } from 'react'
import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ThemeProvider } from '@mui/material'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { theme } from '../theme'
import type { Meta } from '../api/types'

export const META: Meta = {
  fieldTypes: ['STRING', 'INTEGER', 'LONG', 'NUMBER', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATE_TIME', 'UUID', 'OBJECT', 'ARRAY'],
  httpMethods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'],
  parameterLocations: ['PATH', 'QUERY', 'HEADER'],
  asyncOperations: ['SEND', 'RECEIVE'],
  protocols: ['KAFKA', 'JMS', 'AMQP'],
  securitySchemes: ['BEARER_JWT', 'API_KEY', 'BASIC', 'OAUTH2'],
  implementationStyles: ['IMPLEMENTATION_CLASS', 'PROCESSING_ROUTE'],
  ruleTypes: [
    { type: 'VALIDATE', label: 'Validate' }, { type: 'TRANSFORM', label: 'Transform / map' }, { type: 'ENRICH', label: 'Enrich' },
    { type: 'CONDITIONAL_ROUTE', label: 'Conditional route' }, { type: 'PERSIST', label: 'Persist' },
    { type: 'PUBLISH_EVENT', label: 'Publish event' }, { type: 'SPLIT_AGGREGATE', label: 'Split / aggregate' },
    { type: 'ERROR_HANDLING', label: 'Error handling' },
  ],
  validateCheckKinds: ['REQUIRED', 'NOT_BLANK', 'PATTERN', 'MIN', 'MAX', 'MIN_LENGTH', 'MAX_LENGTH', 'CUSTOM'],
  enrichModes: ['ENRICH', 'POLL_ENRICH'],
  persistOperations: ['SAVE', 'UPDATE', 'DELETE', 'FIND_BY_ID'],
  aggregations: ['COLLECT_LIST', 'SUM', 'COUNT', 'NONE'],
  errorCategories: ['ANY', 'VALIDATION', 'DOWNSTREAM_UNAVAILABLE', 'TIMEOUT', 'PERSISTENCE', 'MESSAGING'],
  fallbackTypes: ['ERROR_RESPONSE', 'DEFAULT_RESPONSE'],
  expressionFunctions: [{ name: 'sum', example: 'sum(items[].price)', description: 'adds up' }],
}

/** Renders inside the same providers as the app, with query data seeded so nothing hits the network. */
export function renderWithProviders(ui: ReactNode, seed: [readonly unknown[], unknown][] = [], path = '/') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } })
  client.setQueryData(['meta'], META)
  seed.forEach(([key, data]) => client.setQueryData(key, data))
  const router = createMemoryRouter([{ path: '*', element: ui }], { initialEntries: [path] })
  return {
    client,
    ...render(
      <QueryClientProvider client={client}>
        <ThemeProvider theme={theme}>
          <RouterProvider router={router} />
        </ThemeProvider>
      </QueryClientProvider>,
    ),
  }
}
