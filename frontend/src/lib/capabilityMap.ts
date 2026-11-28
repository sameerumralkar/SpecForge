import type { CapabilityMap, MapCapability, MapEndpoint, MapField, MapPayload } from '../api/types'

/** A place on the map: what was clicked. */
export type MapNode =
  | { kind: 'capability'; id: string }
  | { kind: 'endpoint'; id: string }
  | { kind: 'payload'; id: string }
  | { kind: 'rule'; id: string; endpointId: string }
  | { kind: 'field'; name: string }

export interface MapItem {
  key: string
  primary: string
  secondary?: string
  /** A short tag shown beside the name, e.g. "L3", "object", "POST". */
  badge?: string
  /** What clicking opens; leave out for an item that opens nothing. */
  node?: MapNode
}

export interface MapSection { heading: string; items: MapItem[]; empty?: string }

export interface MapColumn {
  title: string
  subtitle?: string
  /** Where the item opens in the editor. */
  editor?: string
  sections: MapSection[]
}

export type Direction = 'down' | 'up'

export const sameNode = (a: MapNode | undefined, b: MapNode | undefined) =>
  !!a && !!b && a.kind === b.kind && (a.kind === 'field' ? a.name === (b as { name: string }).name : a.id === (b as { id: string }).id)

export const nodeKey = (n: MapNode) => (n.kind === 'field' ? `field:${n.name}` : `${n.kind}:${n.id}`)

const route = (e: MapEndpoint) => (e.kind === 'REST' ? `${e.method ?? ''} ${e.path ?? ''}`.trim() : `${e.operation ?? ''} ${e.channel ?? ''}`.trim())
const fieldType = (f: MapField, payloads: Map<string, MapPayload>) => {
  const ref = f.refPayloadId ? payloads.get(f.refPayloadId)?.name ?? '?' : null
  const t = f.type === 'ARRAY' ? `list of ${ref ?? f.itemType ?? '?'}` : f.type === 'OBJECT' ? ref ?? 'OBJECT' : f.type
  return [t, f.required ? 'required' : 'optional', f.constraints].filter(Boolean).join(' · ')
}
const role = (p: MapPayload) => (p.role === 'OBJECT' ? 'object' : 'structure')

/** Lookups over one project's map, and the columns each place opens, going down or up. */
export class MapIndex {
  readonly capabilities = new Map<string, MapCapability>()
  readonly endpoints = new Map<string, MapEndpoint>()
  readonly payloads = new Map<string, MapPayload>()
  private readonly children = new Map<string | null, MapCapability[]>()
  private readonly capsOfEndpoint = new Map<string, MapCapability[]>()

  readonly map: CapabilityMap

  constructor(map: CapabilityMap) {
    this.map = map
    for (const c of map.capabilities) {
      this.capabilities.set(c.id, c)
      const siblings = this.children.get(c.parentId) ?? []
      siblings.push(c)
      this.children.set(c.parentId, siblings)
      for (const e of c.endpointIds) this.capsOfEndpoint.set(e, [...(this.capsOfEndpoint.get(e) ?? []), c])
    }
    map.endpoints.forEach((e) => this.endpoints.set(e.id, e))
    map.payloads.forEach((p) => this.payloads.set(p.id, p))
  }

  levelName(level: number) {
    return this.map.levelNames[level - 1] ?? `Level ${level}`
  }

  childrenOf(id: string | null) {
    return this.children.get(id) ?? []
  }

  /** "Commerce › Ordering › Checkout", from L1 down to the capability. */
  pathOf(id: string): string[] {
    const out: string[] = []
    const seen = new Set<string>()
    for (let c = this.capabilities.get(id); c && !seen.has(c.id); c = c.parentId ? this.capabilities.get(c.parentId) : undefined) {
      seen.add(c.id)
      out.unshift(c.name)
    }
    return out
  }

  /** The API inventory: the endpoints linked to the capability or to anything below it. */
  inventory(id: string): Set<string> {
    const out = new Set<string>()
    const visit = (c: MapCapability) => {
      c.endpointIds.forEach((e) => out.add(e))
      this.childrenOf(c.id).forEach(visit)
    }
    const c = this.capabilities.get(id)
    if (c) visit(c)
    return out
  }

  capabilitiesOf(endpointId: string) {
    return this.capsOfEndpoint.get(endpointId) ?? []
  }

  /** The payloads whose fields hold this one, with the field. */
  holders(payloadId: string): { payload: MapPayload; field: MapField }[] {
    return this.map.payloads.flatMap((p) => p.fields.filter((f) => f.refPayloadId === payloadId && p.id !== payloadId).map((field) => ({ payload: p, field })))
  }

  payloadsWithField(name: string): { payload: MapPayload; field: MapField }[] {
    const key = name.toLowerCase()
    return this.map.payloads.flatMap((p) => p.fields.filter((f) => f.name.toLowerCase() === key).map((field) => ({ payload: p, field })))
  }

  /** Every field name, with the payloads that have it. */
  fieldNames(): { name: string; payloads: number; types: string[] }[] {
    const by = new Map<string, { name: string; payloads: Set<string>; types: Set<string> }>()
    for (const p of this.map.payloads) {
      for (const f of p.fields) {
        const k = f.name.toLowerCase()
        const e = by.get(k) ?? { name: f.name, payloads: new Set(), types: new Set() }
        e.payloads.add(p.id)
        e.types.add(f.type === 'ARRAY' ? 'list' : f.type)
        by.set(k, e)
      }
    }
    return [...by.values()].map((e) => ({ name: e.name, payloads: e.payloads.size, types: [...e.types].sort() }))
      .sort((a, b) => a.name.localeCompare(b.name, undefined, { sensitivity: 'base' }))
  }

  /**
   * The endpoints that use a payload: directly (as a body, response, message or in a rule), or through the
   * objects that hold it, e.g. "request body, through Order.total".
   */
  endpointsUsing(payloadId: string): { endpoint: MapEndpoint; how: string[]; direct: boolean }[] {
    const found = new Map<string, { endpoint: MapEndpoint; how: string[]; direct: boolean }>()
    const add = (e: MapEndpoint, how: string, direct: boolean) => {
      const entry = found.get(e.id) ?? { endpoint: e, how: [], direct: false }
      if (!entry.how.includes(how)) entry.how.push(how)
      entry.direct ||= direct
      found.set(e.id, entry)
    }
    // Walk up through the holders, breadth first, remembering the field path from the top down.
    const queue: { id: string; via: string[] }[] = [{ id: payloadId, via: [] }]
    const seen = new Set<string>([payloadId])
    while (queue.length) {
      const { id, via } = queue.shift()!
      for (const e of this.map.endpoints) {
        for (const u of e.uses) if (u.payloadId === id) add(e, via.length ? `${u.how}, through ${via.join(' › ')}` : u.how, via.length === 0)
      }
      for (const h of this.holders(id)) {
        if (seen.has(h.payload.id)) continue
        seen.add(h.payload.id)
        queue.push({ id: h.payload.id, via: [`${h.payload.name}.${h.field.name}`, ...via] })
      }
    }
    return [...found.values()].sort((a, b) => Number(b.direct) - Number(a.direct) || a.endpoint.name.localeCompare(b.endpoint.name))
  }

  // ---- Columns ----

  root(direction: Direction): MapColumn {
    if (direction === 'up') {
      const names = this.fieldNames()
      return {
        title: 'Fields',
        subtitle: `${names.length} field names across ${this.map.payloads.length} payloads`,
        sections: [{ heading: 'Field names', empty: 'No fields yet.', items: names.map((f) => ({
          key: f.name, primary: f.name, secondary: `in ${f.payloads} payload${f.payloads === 1 ? '' : 's'} · ${f.types.join(', ')}`, node: { kind: 'field', name: f.name },
        })) }],
      }
    }
    const top = this.childrenOf(null)
    const mapped = new Set(this.map.capabilities.flatMap((c) => c.endpointIds))
    const unmapped = this.map.endpoints.filter((e) => !mapped.has(e.id))
    return {
      title: `L1 · ${this.levelName(1)}`,
      subtitle: `${this.map.capabilities.length} capabilities, ${this.map.endpoints.length} endpoints`,
      sections: [
        { heading: 'L1 capabilities', empty: 'No capabilities yet: build the map on the Capabilities page.', items: top.map((c) => this.capabilityItem(c)) },
        ...(unmapped.length ? [{ heading: 'Endpoints not mapped to a capability', items: unmapped.map((e) => this.endpointItem(e)) }] : []),
      ],
    }
  }

  /** The column a node opens; {@code null} when the node is no longer on the map. */
  column(node: MapNode, direction: Direction): MapColumn | null {
    const p = this.map.projectId
    switch (node.kind) {
      case 'capability': {
        const c = this.capabilities.get(node.id)
        if (!c) return null
        const head = { title: `L${c.level} ${c.name}`, subtitle: this.pathOf(c.id).join(' › '), editor: `/projects/${p}/capabilities` }
        if (direction === 'up') {
          const parent = c.parentId ? this.capabilities.get(c.parentId) : undefined
          return { ...head, sections: [parent
            ? { heading: `Above it: L${parent.level} · ${this.levelName(parent.level)}`, items: [this.capabilityItem(parent)] }
            : { heading: 'Above it', items: [], empty: 'Nothing: this is an L1 capability, the top of the map.' }] }
        }
        const kids = this.childrenOf(c.id)
        return { ...head, sections: [
          ...(c.level < 8 ? [{ heading: `L${c.level + 1} · ${this.levelName(c.level + 1)}`, items: kids.map((k) => this.capabilityItem(k)), empty: 'No capabilities below this one.' }] : []),
          { heading: 'Endpoints linked here', items: c.endpointIds.map((id) => this.endpoints.get(id)).filter((e): e is MapEndpoint => !!e).map((e) => this.endpointItem(e)),
            empty: kids.length ? 'None here: open a capability below to see its endpoints.' : 'No endpoints linked to this capability.' },
        ] }
      }
      case 'endpoint': {
        const e = this.endpoints.get(node.id)
        if (!e) return null
        const head = { title: e.name, subtitle: [route(e), e.summary].filter(Boolean).join(' · '), editor: `/projects/${p}/endpoints/${e.id}` }
        if (direction === 'up') {
          return { ...head, sections: [{ heading: 'Capabilities it is linked to', empty: 'Not mapped to any capability yet.',
            items: this.capabilitiesOf(e.id).map((c) => ({ ...this.capabilityItem(c), secondary: this.pathOf(c.id).join(' › ') })) }] }
        }
        const byPayload = new Map<string, string[]>()
        e.uses.forEach((u) => byPayload.set(u.payloadId, [...(byPayload.get(u.payloadId) ?? []), u.how]))
        return { ...head, sections: [
          { heading: 'Objects', empty: 'This endpoint uses no payloads.', items: [...byPayload].flatMap(([id, how]) => {
            const pl = this.payloads.get(id)
            return pl ? [{ key: id, primary: pl.name, badge: role(pl), secondary: how.join(', '), node: { kind: 'payload', id } as MapNode }] : []
          }) },
          { heading: 'Processing rules', empty: 'No processing rules.', items: e.rules.map((r) => ({
            key: r.id, primary: `${r.position}. ${r.name}`, secondary: [r.typeLabel, r.condition && `when ${r.condition}`].filter(Boolean).join(' · '),
            node: { kind: 'rule', id: r.id, endpointId: e.id } as MapNode,
          })) },
        ] }
      }
      case 'rule': {
        const e = this.endpoints.get(node.endpointId)
        const r = e?.rules.find((x) => x.id === node.id)
        if (!e || !r) return null
        return {
          title: `${r.position}. ${r.name}`, subtitle: `${r.typeLabel} step of ${e.name}`, editor: `/projects/${p}/endpoints/${e.id}/rules`,
          sections: [
            { heading: 'The step', items: [
              { key: 'type', primary: r.typeLabel, secondary: 'Rule type' },
              { key: 'when', primary: r.condition ? `when ${r.condition}` : 'Always runs', secondary: 'Condition' },
              { key: 'codes', primary: `${r.errorCodes} error code${r.errorCodes === 1 ? '' : 's'}`, secondary: 'It can fail with' },
            ] },
            { heading: 'Objects it uses', empty: 'This step refers to no other payload.', items: r.payloadIds.flatMap((id) => {
              const pl = this.payloads.get(id)
              return pl ? [{ key: id, primary: pl.name, badge: role(pl), node: { kind: 'payload', id } as MapNode }] : []
            }) },
          ],
        }
      }
      case 'payload': {
        const pl = this.payloads.get(node.id)
        if (!pl) return null
        const head = { title: pl.name, subtitle: [role(pl), pl.description].filter(Boolean).join(' · '), editor: `/projects/${p}/payloads/${pl.id}` }
        if (direction === 'up') {
          return { ...head, sections: [
            { heading: 'Endpoints using it', empty: 'No endpoint uses it, directly or through another object.', items: this.endpointsUsing(pl.id).map(({ endpoint, how }) => ({
              ...this.endpointItem(endpoint), secondary: how.join('; '),
            })) },
            { heading: 'Objects that hold it', empty: 'No other object holds it.', items: this.holders(pl.id).map(({ payload, field }) => ({
              key: `${payload.id}:${field.name}`, primary: payload.name, badge: role(payload), secondary: `field ${field.name}`, node: { kind: 'payload', id: payload.id } as MapNode,
            })) },
          ] }
        }
        return { ...head, sections: [{ heading: `Fields (${pl.fields.length})`, empty: 'No fields yet.', items: pl.fields.map((f) => ({
          key: f.name, primary: f.name, secondary: fieldType(f, this.payloads),
          badge: f.refPayloadId ? 'object' : undefined,
          node: f.refPayloadId ? { kind: 'payload', id: f.refPayloadId } as MapNode : { kind: 'field', name: f.name } as MapNode,
        })) }] }
      }
      case 'field': {
        const uses = this.payloadsWithField(node.name)
        if (!uses.length) return null
        return {
          title: node.name, subtitle: `Field in ${uses.length} payload${uses.length === 1 ? '' : 's'}`,
          sections: [{ heading: 'Objects it is used in', items: uses.map(({ payload, field }) => ({
            key: payload.id, primary: payload.name, badge: role(payload), secondary: fieldType(field, this.payloads), node: { kind: 'payload', id: payload.id } as MapNode,
          })) }],
        }
      }
    }
  }

  private capabilityItem(c: MapCapability): MapItem {
    const apis = this.inventory(c.id).size
    const below = this.childrenOf(c.id).length
    return {
      key: c.id, primary: c.name, badge: `L${c.level}`, node: { kind: 'capability', id: c.id },
      secondary: [c.code, `${apis} API${apis === 1 ? '' : 's'}`, below ? `${below} below` : null].filter(Boolean).join(' · '),
    }
  }

  private endpointItem(e: MapEndpoint): MapItem {
    return { key: e.id, primary: e.name, badge: e.kind === 'REST' ? e.method ?? 'REST' : e.operation ?? 'ASYNC', secondary: route(e), node: { kind: 'endpoint', id: e.id } }
  }
}
