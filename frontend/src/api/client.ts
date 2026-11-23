import type {
  Endpoint, EndpointRequest, Meta, Payload, PayloadRequest, PayloadUsage, Project, ProjectRequest,
  CodeView, DiagramsView, GenerationDiff, ImportResult, Rule, RuleChain, RuleRequest, Snapshot, SnapshotKind, SpecsView,
} from './types'

export interface FieldError {
  field: string
  message: string
}

/** A failed API call. {@code errors} holds per-field validation problems; {@code usages} a blocked payload delete. */
export class ApiError extends Error {
  readonly status: number
  readonly errors: FieldError[]
  readonly usages: PayloadUsage[]

  constructor(status: number, message: string, errors: FieldError[] = [], usages: PayloadUsage[] = []) {
    super(message)
    this.status = status
    this.errors = errors
    this.usages = usages
  }

  /** Messages for one field path, e.g. "fields[2].name" or "config.checks[0].value". */
  errorFor(field: string): string | undefined {
    const messages = this.errors.filter((e) => e.field === field).map((e) => e.message)
    return messages.length ? messages.join('; ') : undefined
  }

  /** Messages for a path and everything under it, e.g. all of "config.retry.*". */
  errorsUnder(prefix: string): FieldError[] {
    return this.errors.filter((e) => e.field === prefix || e.field.startsWith(prefix + '.') || e.field.startsWith(prefix + '['))
  }
}

async function request<T>(method: string, url: string, body?: unknown): Promise<T> {
  const response = await fetch(url, {
    method,
    headers: body === undefined ? { Accept: 'application/json' } : { Accept: 'application/json', 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (response.status === 204) {
    return undefined as T
  }
  const text = await response.text()
  const json = text ? JSON.parse(text) : undefined
  if (!response.ok) {
    throw new ApiError(response.status, json?.detail ?? `Request failed (${response.status})`, json?.errors ?? [], json?.usages ?? [])
  }
  return json as T
}

const p = (projectId: string) => `/api/projects/${projectId}`

export const api = {
  meta: () => request<Meta>('GET', '/api/meta'),

  projects: () => request<Project[]>('GET', '/api/projects'),
  project: (id: string) => request<Project>('GET', p(id)),
  createProject: (body: ProjectRequest) => request<Project>('POST', '/api/projects', body),
  updateProject: (id: string, body: ProjectRequest) => request<Project>('PUT', p(id), body),
  deleteProject: (id: string) => request<void>('DELETE', p(id)),
  createSampleProject: () => request<Project>('POST', '/api/projects/samples/order-service'),

  payloads: (projectId: string) => request<Payload[]>('GET', `${p(projectId)}/payloads`),
  createPayload: (projectId: string, body: PayloadRequest) => request<Payload>('POST', `${p(projectId)}/payloads`, body),
  updatePayload: (projectId: string, id: string, body: PayloadRequest) =>
    request<Payload>('PUT', `${p(projectId)}/payloads/${id}`, body),
  deletePayload: (projectId: string, id: string) => request<void>('DELETE', `${p(projectId)}/payloads/${id}`),
  duplicatePayload: (projectId: string, id: string) => request<Payload>('POST', `${p(projectId)}/payloads/${id}/duplicate`),
  payloadUsages: (projectId: string, id: string) => request<PayloadUsage[]>('GET', `${p(projectId)}/payloads/${id}/usages`),

  endpoints: (projectId: string) => request<Endpoint[]>('GET', `${p(projectId)}/endpoints`),
  createEndpoint: (projectId: string, body: EndpointRequest) => request<Endpoint>('POST', `${p(projectId)}/endpoints`, body),
  updateEndpoint: (projectId: string, id: string, body: EndpointRequest) =>
    request<Endpoint>('PUT', `${p(projectId)}/endpoints/${id}`, body),
  deleteEndpoint: (projectId: string, id: string) => request<void>('DELETE', `${p(projectId)}/endpoints/${id}`),
  duplicateEndpoint: (projectId: string, id: string) => request<Endpoint>('POST', `${p(projectId)}/endpoints/${id}/duplicate`),

  ruleChain: (projectId: string, endpointId: string) =>
    request<RuleChain>('GET', `${p(projectId)}/endpoints/${endpointId}/rules`),
  createRule: (projectId: string, endpointId: string, body: RuleRequest) =>
    request<Rule>('POST', `${p(projectId)}/endpoints/${endpointId}/rules`, body),
  updateRule: (projectId: string, endpointId: string, ruleId: string, body: RuleRequest) =>
    request<Rule>('PUT', `${p(projectId)}/endpoints/${endpointId}/rules/${ruleId}`, body),
  deleteRule: (projectId: string, endpointId: string, ruleId: string) =>
    request<void>('DELETE', `${p(projectId)}/endpoints/${endpointId}/rules/${ruleId}`),
  reorderRules: (projectId: string, endpointId: string, ruleIds: string[]) =>
    request<RuleChain>('PUT', `${p(projectId)}/endpoints/${endpointId}/rules/order`, { ruleIds }),

  specs: (projectId: string) => request<SpecsView>('GET', `${p(projectId)}/specs`),
  code: (projectId: string) => request<CodeView>('GET', `${p(projectId)}/code`),
  diagrams: (projectId: string) => request<DiagramsView>('GET', `${p(projectId)}/diagrams`),

  importProject: (file: unknown, name?: string) =>
    request<ImportResult>('POST', `/api/projects/import${name ? `?name=${encodeURIComponent(name)}` : ''}`, file),
  importSpec: (documents: string[], name?: string, basePackage?: string) =>
    request<ImportResult>('POST', '/api/projects/import/spec', { documents, name: name || null, basePackage: basePackage || null }),

  snapshots: (projectId: string, kind: SnapshotKind) => request<Snapshot[]>('GET', `${p(projectId)}/generations?kind=${kind}`),
  takeSnapshot: (projectId: string, kind: SnapshotKind) => request<Snapshot>('POST', `${p(projectId)}/generations`, { kind }),
  diff: (projectId: string, snapshotId: string) => request<GenerationDiff>('GET', `${p(projectId)}/generations/${snapshotId}/diff`),
}

/** Download links (the browser saves the file; no fetch needed). */
export const downloads = {
  specFile: (projectId: string, file: string) => `${p(projectId)}/specs/${file}`,
  specsZip: (projectId: string) => `${p(projectId)}/generate/specs.zip`,
  codeZip: (projectId: string) => `${p(projectId)}/generate/code.zip`,
  diagramsZip: (projectId: string) => `${p(projectId)}/generate/diagrams.zip`,
  exportProject: (projectId: string) => `${p(projectId)}/export`,
  /** A diagram as a file: .mmd/.puml source, or PlantUML-rendered .svg/.png. */
  diagram: (projectId: string, id: string, format: string, inline = false) =>
    `${p(projectId)}/diagrams/${id}.${format}${inline ? '?download=false' : ''}`,
}
