import type { Field, Payload } from '../api/types'

/** Objects first, then structures, each by name: the order pickers list them in. */
export function byRole(payloads: Payload[]): { objects: Payload[]; structures: Payload[] } {
  const sorted = [...payloads].sort((a, b) => a.name.localeCompare(b.name))
  return { objects: sorted.filter((p) => p.role === 'OBJECT'), structures: sorted.filter((p) => p.role !== 'OBJECT') }
}

/** "MoneyAmount" → "moneyAmount", made unique among the existing field names. */
export function fieldNameFor(objectName: string, existing: string[]): string {
  const base = objectName.charAt(0).toLowerCase() + objectName.slice(1)
  const taken = new Set(existing.map((n) => n.toLowerCase()))
  let name = base
  for (let n = 2; taken.has(name.toLowerCase()); n++) name = `${base}${n}`
  return name
}

/** A field holding the object, as dropping it on a field list adds. */
export function objectField(object: Payload, existing: Field[]): Field {
  return { name: fieldNameFor(object.name, existing.map((f) => f.name)), type: 'OBJECT', refPayloadId: object.id, required: false, enumValues: [] }
}
