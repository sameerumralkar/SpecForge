import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { fireEvent, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EndpointDefinition } from '../editors/EndpointDefinition'
import { RequestResponse } from '../editors/RequestResponse'
import type { EndpointRequest, Payload } from '../api/types'
import { renderWithProviders } from './render'

const rest: EndpointRequest = {
  kind: 'REST', name: 'getOrder', tags: [], httpMethod: 'GET', path: '/orders/{orderId}',
  parameters: [{ name: 'orderId', location: 'PATH', type: 'UUID', required: true }],
  responses: [{ statusCode: '200', description: 'OK', payloadId: null }], security: [],
}
const payloads: Payload[] = [{ id: 'o', projectId: 'p', name: 'Order', fields: [] }]

function Both({ initial, onValue }: { initial: EndpointRequest; onValue: (v: EndpointRequest) => void }) {
  const [value, setValue] = useState(initial)
  const change = (v: EndpointRequest) => { setValue(v); onValue(v) }
  return (
    <>
      <EndpointDefinition value={value} onChange={change} allTags={[]} error={null} />
      <RequestResponse value={value} onChange={change} payloads={payloads} error={null} />
    </>
  )
}

describe('endpoint editors', () => {
  it('updates path parameters when the path field is left, keeping existing settings', async () => {
    const user = userEvent.setup()
    let latest = rest
    renderWithProviders(<Both initial={rest} onValue={(v) => (latest = v)} />)
    const path = screen.getByLabelText('Path')
    await user.clear(path)
    await user.type(path, '/customers/{{customerId}/orders/{{orderId}')
    expect(latest.parameters.map((p) => p.name)).toEqual(['orderId']) // unchanged while typing
    fireEvent.blur(path)
    expect(latest.parameters.map((p) => `${p.name}:${p.type}`)).toEqual(['customerId:STRING', 'orderId:UUID'])
    expect(screen.getByLabelText('Parameter 1 name')).toHaveValue('customerId')
  })

  it('has no request body for GET and lets you add query parameters and responses', async () => {
    const user = userEvent.setup()
    let latest = rest
    renderWithProviders(<Both initial={rest} onValue={(v) => (latest = v)} />)
    expect(screen.getByText('GET requests have no body')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'query' }))
    await user.type(screen.getByLabelText('Parameter 2 name'), 'limit')
    await user.click(screen.getByRole('button', { name: 'Add response' }))
    await user.type(screen.getByLabelText('Response 2 status'), '404')
    expect(latest.parameters[1]).toMatchObject({ name: 'limit', location: 'QUERY', required: false })
    expect(latest.responses.map((r) => r.statusCode)).toEqual(['200', '404'])
    expect(screen.queryByRole('button', { name: 'Remove parameter 1' })).not.toBeInTheDocument() // path params come from the path
  })

  it('shows channel, operation and message payload for async endpoints', () => {
    const asyncEndpoint: EndpointRequest = {
      kind: 'ASYNC', name: 'onPaid', tags: [], channel: 'payments.completed', operation: 'RECEIVE', protocol: 'KAFKA',
      messagePayloadId: 'o', parameters: [], responses: [], security: [],
    }
    renderWithProviders(<Both initial={asyncEndpoint} onValue={() => {}} />)
    expect(screen.getByLabelText('Channel / topic')).toHaveValue('payments.completed')
    expect(screen.getByText('Message headers')).toBeInTheDocument()
    expect(screen.queryByText('Responses')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'header' })).toBeInTheDocument()
  })
})
