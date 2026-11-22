import type { Parameter } from '../api/types'

/** The {param} names in a path template, in order. */
export function pathParamNames(path: string | null | undefined): string[] {
  const names: string[] = []
  for (const m of (path ?? '').matchAll(/\{([A-Za-z_][A-Za-z0-9_]*)}/g)) {
    if (!names.includes(m[1])) names.push(m[1])
  }
  return names
}

/**
 * Keeps PATH parameters in step with the path: adds one for each new {name} (as STRING) and drops
 * those no longer in the path. Query and header parameters are untouched.
 */
export function syncPathParams(path: string | null | undefined, params: Parameter[]): Parameter[] {
  const names = pathParamNames(path)
  const kept = params.filter((p) => p.location !== 'PATH' || names.includes(p.name))
  const missing = names
    .filter((n) => !kept.some((p) => p.location === 'PATH' && p.name === n))
    .map<Parameter>((name) => ({ name, location: 'PATH', type: 'STRING', required: true, description: null }))
  const pathParams = names
    .map((n) => kept.find((p) => p.location === 'PATH' && p.name === n) ?? missing.find((p) => p.name === n)!)
  return [...pathParams, ...kept.filter((p) => p.location !== 'PATH')]
}
