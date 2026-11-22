import type { Endpoint, EndpointRequest, Field, FieldType, Payload, PayloadRequest } from '../api/types'

export const FIELD_TYPES: FieldType[] = ['STRING', 'INTEGER', 'LONG', 'NUMBER', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATE_TIME', 'UUID', 'OBJECT', 'ARRAY']

export const emptyField = (): Field => ({ name: '', type: 'STRING', required: false, enumValues: [] })

/** The editable part of a payload, as the update API takes it. */
export const toPayloadRequest = (p: Payload): PayloadRequest => ({
  name: p.name,
  description: p.description ?? '',
  example: p.example ?? '',
  fields: p.fields.map((f) => ({ ...f, refPayloadName: undefined })),
})

/** The editable part of an endpoint, as the update API takes it. */
export const toEndpointRequest = (e: Endpoint): EndpointRequest => ({
  kind: e.kind,
  name: e.name,
  summary: e.summary ?? '',
  description: e.description ?? '',
  tags: e.tags,
  implementationStyle: e.implementationStyle ?? null,
  parameters: e.parameters,
  httpMethod: e.httpMethod ?? null,
  path: e.path ?? null,
  requestPayloadId: e.requestPayloadId ?? null,
  responses: e.responses.map(({ statusCode, description, payloadId }) => ({ statusCode, description: description ?? '', payloadId: payloadId ?? null })),
  security: e.security,
  channel: e.channel ?? null,
  operation: e.operation ?? null,
  protocol: e.protocol ?? null,
  messagePayloadId: e.messagePayloadId ?? null,
})
