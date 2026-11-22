import { describe, expect, it } from 'vitest'
import { syncPathParams, pathParamNames } from '../lib/paths'
import { currentPayloadId, fieldPaths } from '../lib/fieldPaths'
import { cleanConfig, defaultConfig } from '../lib/rules'
import { groupEndpoints } from '../lib/endpoints'
import type { Endpoint, Parameter, Payload } from '../api/types'

const payload = (id: string, name: string, fields: Payload['fields']): Payload => ({ id, projectId: 'p', name, fields })
const f = (name: string, type: Payload['fields'][number]['type'], extra: Partial<Payload['fields'][number]> = {}) =>
  ({ name, type, required: false, enumValues: [], ...extra })

describe('path parameters', () => {
  it('reads {names} from a path', () => {
    expect(pathParamNames('/a/{x}/b/{y}/{x}')).toEqual(['x', 'y'])
    expect(pathParamNames(null)).toEqual([])
  })

  it('adds new path parameters, drops removed ones and keeps the settings of the rest', () => {
    const params: Parameter[] = [
      { name: 'orderId', location: 'PATH', type: 'UUID', required: true, description: 'kept' },
      { name: 'gone', location: 'PATH', type: 'STRING', required: true },
      { name: 'page', location: 'QUERY', type: 'INTEGER', required: false },
    ]
    const synced = syncPathParams('/customers/{customerId}/orders/{orderId}', params)
    expect(synced.map((p) => `${p.location}:${p.name}:${p.type}`)).toEqual([
      'PATH:customerId:STRING', 'PATH:orderId:UUID', 'QUERY:page:INTEGER',
    ])
    expect(synced[1].description).toBe('kept')
  })
})

describe('field paths', () => {
  const address = payload('a', 'Address', [f('city', 'STRING')])
  const item = payload('i', 'Item', [f('sku', 'STRING')])
  const node = payload('n', 'Node', [f('label', 'STRING'), f('parent', 'OBJECT', { refPayloadId: 'n' })])
  const order = payload('o', 'Order', [
    f('id', 'UUID'), f('shipTo', 'OBJECT', { refPayloadId: 'a' }), f('items', 'ARRAY', { itemType: 'OBJECT', refPayloadId: 'i' }),
  ])
  const all = [address, item, node, order]

  it('lists nested and list-item paths', () => {
    expect(fieldPaths(order, all).map((p) => p.path)).toEqual(['id', 'shipTo', 'shipTo.city', 'items', 'items[].sku'])
  })

  it('does not loop on self-references', () => {
    expect(fieldPaths(node, all).map((p) => p.path)).toEqual(['label', 'parent'])
  })

  it('tracks the payload a rule works on through transforms', () => {
    const rules = [
      { type: 'VALIDATE', config: {} },
      { type: 'TRANSFORM', config: { targetPayloadId: 'o' } },
      { type: 'PERSIST', config: {} },
    ]
    expect(currentPayloadId('req', rules, 0)).toBe('req')
    expect(currentPayloadId('req', rules, 1)).toBe('req')
    expect(currentPayloadId('req', rules, 2)).toBe('o')
  })
})

describe('rule config helpers', () => {
  it('drops empty strings so the server sees "not set"', () => {
    expect(cleanConfig({ a: '', b: 'x', c: [{ d: '', e: 1 }], f: null })).toEqual({ b: 'x', c: [{ e: 1 }], f: null })
  })

  it('has a default for every rule type', () => {
    for (const t of ['VALIDATE', 'TRANSFORM', 'ENRICH', 'CONDITIONAL_ROUTE', 'PERSIST', 'PUBLISH_EVENT', 'SPLIT_AGGREGATE', 'ERROR_HANDLING'] as const) {
      expect(defaultConfig(t)).toBeTypeOf('object')
    }
  })
})

describe('sidebar grouping', () => {
  it('groups REST endpoints by first tag and async ones by channel, channels last', () => {
    const e = (name: string, kind: 'REST' | 'ASYNC', tags: string[], channel?: string) => ({ id: name, name, kind, tags, channel }) as unknown as Endpoint
    const groups = groupEndpoints([e('a', 'REST', ['Orders']), e('b', 'ASYNC', [], 'orders.created'), e('c', 'REST', []), e('d', 'REST', ['Orders', 'X'])])
    expect(groups.map(([g, items]) => `${g}=${items.map((i) => i.name).join(',')}`)).toEqual([
      'Orders=a,d', 'Untagged=c', 'Channel orders.created=b',
    ])
  })
})
