import type { FieldType, Payload } from '../api/types'

export interface FieldPath {
  path: string
  type: FieldType
}

/**
 * Every field path of a payload, the way rules write them: "customer.email", "items[].sku".
 * Follows references a few levels deep and never loops on circular payloads.
 */
export function fieldPaths(payload: Payload | undefined, all: Payload[], maxDepth = 3): FieldPath[] {
  if (!payload) return []
  const byId = new Map(all.map((p) => [p.id, p]))
  const out: FieldPath[] = []
  const walk = (p: Payload, prefix: string, depth: number, seen: Set<string>) => {
    for (const f of p.fields) {
      if (!f.name || !f.type) continue
      const path = prefix + f.name
      out.push({ path, type: f.type })
      const ref = f.refPayloadId ? byId.get(f.refPayloadId) : undefined
      if (!ref || depth >= maxDepth || seen.has(ref.id)) continue
      const next = new Set(seen).add(ref.id)
      if (f.type === 'OBJECT') walk(ref, path + '.', depth + 1, next)
      if (f.type === 'ARRAY' && f.itemType === 'OBJECT') walk(ref, path + '[].', depth + 1, next)
    }
  }
  walk(payload, '', 0, new Set([payload.id]))
  return out
}

/** The payload a rule at {@code index} works on: the input, replaced by the target of each earlier TRANSFORM. */
export function currentPayloadId(
  inputPayloadId: string | null | undefined,
  rules: { type: string; config: Record<string, unknown> }[],
  index: number,
): string | null {
  let current = inputPayloadId ?? null
  for (const rule of rules.slice(0, index)) {
    if (rule.type === 'TRANSFORM' && typeof rule.config.targetPayloadId === 'string') {
      current = rule.config.targetPayloadId
    }
  }
  return current
}
