import { BASE_HELP } from './base'
import { EXTRA_HELP } from './extensions'
import type { HelpBundle, ScreenHelpEntry } from './types'

export type { ScreenHelpEntry } from './types'

const bundles: HelpBundle[] = [BASE_HELP, ...EXTRA_HELP]

/** "Pattern (regex) *" → "pattern (regex)". */
export function normalizeLabel(label: string): string {
  return label.replace(/\s*\*\s*$/, '').replace(/ | /g, ' ').replace(/\s+/g, ' ').trim().toLowerCase()
}

export function screenHelp(id: string): ScreenHelpEntry | undefined {
  for (let i = bundles.length - 1; i >= 0; i--) {
    const s = bundles[i].screens?.[id]
    if (s) return s
  }
  return undefined
}

/** The help for a field label, if there is any. Later bundles win, so editions can refine the base text. */
export function fieldHelp(label: string | null | undefined): string | undefined {
  if (!label) return undefined
  const key = normalizeLabel(label)
  for (let i = bundles.length - 1; i >= 0; i--) {
    const f = bundles[i].fields?.[key]
    if (f) return f
  }
  for (let i = bundles.length - 1; i >= 0; i--) {
    const hit = bundles[i].patterns?.find(([re]) => re.test(key))
    if (hit) return hit[1]
  }
  return undefined
}
