import type { Endpoint } from '../api/types'

/** Groups REST endpoints by their first tag and async endpoints by channel. */
export function groupEndpoints(endpoints: Endpoint[]): [string, Endpoint[]][] {
  const groups = new Map<string, Endpoint[]>()
  for (const e of endpoints) {
    const key = e.kind === 'REST' ? (e.tags[0] ?? 'Untagged') : `Channel ${e.channel}`
    groups.set(key, [...(groups.get(key) ?? []), e])
  }
  return [...groups.entries()].sort(([a], [b]) => {
    const ca = a.startsWith('Channel '), cb = b.startsWith('Channel ')
    return ca === cb ? a.localeCompare(b) : ca ? 1 : -1
  })
}
