import type { Payload, RuleConfig } from '../../api/types'
import { fieldPaths } from '../../lib/fieldPaths'
import type { RuleFormContext } from './inputs'

/** Reads a config value with a fallback, typed for the form that knows its shape. */
export function get<T>(config: RuleConfig, key: string, fallback: T): T {
  const v = config[key]
  return (v === undefined || v === null ? fallback : v) as T
}

/** Field paths of a payload plus the endpoint's parameter names, for autocomplete. */
export function pathOptions(payload: Payload | undefined, ctx: RuleFormContext, filter?: (type: string) => boolean): string[] {
  const paths = fieldPaths(payload, ctx.payloads).filter((p) => !filter || filter(p.type)).map((p) => p.path)
  const params = filter ? [] : ctx.parameters.map((p) => p.name).filter((n) => !paths.includes(n))
  return [...paths, ...params]
}

/** Converts a text box value to a number, or null when empty. */
export const num = (s: string): number | null => (s.trim() === '' || Number.isNaN(Number(s)) ? null : Number(s))
