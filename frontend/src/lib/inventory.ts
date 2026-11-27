import type { Field, FieldDefinition, Payload } from '../api/types'

/** Objects first, then structures, each by name: the order pickers list them in. */
export function byRole(payloads: Payload[]): { objects: Payload[]; structures: Payload[] } {
  const sorted = [...payloads].sort((a, b) => a.name.localeCompare(b.name))
  return { objects: sorted.filter((p) => p.role === 'OBJECT'), structures: sorted.filter((p) => p.role !== 'OBJECT') }
}

/** "MoneyAmount" → "moneyAmount", made unique among the existing field names. */
export function fieldNameFor(objectName: string, existing: string[]): string {
  return freeFieldName(objectName.charAt(0).toLowerCase() + objectName.slice(1), existing)
}

/** A field holding the object, as dropping it on a field list adds. */
export function objectField(object: Payload, existing: Field[]): Field {
  return { name: fieldNameFor(object.name, existing.map((f) => f.name)), type: 'OBJECT', refPayloadId: object.id, required: false, enumValues: [] }
}

/** "currency", or "currency2" when the payload already has one: a name not yet taken (ignoring case). */
export function freeFieldName(base: string, existing: string[]): string {
  const taken = new Set(existing.map((n) => n.toLowerCase()))
  let name = base
  for (let n = 2; taken.has(name.toLowerCase()); n++) name = `${base}${n}`
  return name
}

/** A copy of a field-inventory entry, as adding it to a payload's field list makes. */
export function inventoryField(definition: FieldDefinition, existing: Field[]): Field {
  const { refPayloadName: _ignored, ...field } = definition.field
  return { ...field, enumValues: [...field.enumValues], errorCodeIds: [...(field.errorCodeIds ?? [])],
    name: freeFieldName(field.name, existing.map((f) => f.name)) }
}

/** "string · 3..3 chars · /[A-Z]{3}/": the constraints of a field in one line. */
export function fieldSummary(f: Field): string {
  const type = f.type === 'ARRAY' ? `${(f.itemType ?? '?').toLowerCase()}[]` : (f.type ?? '?').toLowerCase()
  const parts = [type + (f.format ? ` (${f.format})` : '')]
  const range = (lo?: number | null, hi?: number | null) => (lo ?? '') + '..' + (hi ?? '')
  if (f.minLength != null || f.maxLength != null) parts.push(`${range(f.minLength, f.maxLength)} ${f.type === 'ARRAY' ? 'items' : 'chars'}`)
  if (f.minimum != null || f.maximum != null) parts.push(range(f.minimum, f.maximum))
  if (f.pattern) parts.push(`/${f.pattern}/`)
  if (f.enumValues.length) parts.push(f.enumValues.join(' | '))
  return parts.join(' · ')
}
