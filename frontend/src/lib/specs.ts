export type SpecId = 'openapi' | 'asyncapi'
export type PreviewTab = SpecId | 'code' | 'diagrams'
export type Format = 'yaml' | 'json'

/**
 * A regular expression for the line where an endpoint starts in a generated spec, so the preview
 * can jump to it: the operationId in OpenAPI, the operation key in AsyncAPI.
 */
export function focusPattern(spec: SpecId, format: Format, name: string): string {
  if (spec === 'openapi') return format === 'yaml' ? `^\\s*operationId: ${name}$` : `"operationId": "${name}"`
  return format === 'yaml' ? `^  ${name}:$` : `^    "${name}": \\{`
}

