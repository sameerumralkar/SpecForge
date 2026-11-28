// Mirrors the backend's API records (dev.specbuilder.api.dto).

export type ImplementationStyle = 'IMPLEMENTATION_CLASS' | 'PROCESSING_ROUTE'
export type EndpointKind = 'REST' | 'ASYNC'
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE' | 'HEAD' | 'OPTIONS'
export type ParameterLocation = 'PATH' | 'QUERY' | 'HEADER'
export type AsyncOperation = 'SEND' | 'RECEIVE'
export type MessagingProtocol = 'KAFKA' | 'JMS' | 'AMQP'
export type SecurityScheme = 'BEARER_JWT' | 'API_KEY' | 'BASIC' | 'OAUTH2'
export type FieldType =
  | 'STRING' | 'INTEGER' | 'LONG' | 'NUMBER' | 'DECIMAL' | 'BOOLEAN'
  | 'DATE' | 'DATE_TIME' | 'UUID' | 'OBJECT' | 'ARRAY'
export type RuleType =
  | 'VALIDATE' | 'TRANSFORM' | 'ENRICH' | 'CONDITIONAL_ROUTE'
  | 'PERSIST' | 'PUBLISH_EVENT' | 'SPLIT_AGGREGATE' | 'ERROR_HANDLING'

export interface ProjectRequest {
  name: string
  basePackage: string
  version: string
  description?: string | null
  defaultImplementationStyle: ImplementationStyle
  allowCircularReferences: boolean
}

export interface Project extends ProjectRequest {
  id: string
  payloadCount: number
  endpointCount: number
  createdAt: string
  updatedAt: string
}

export interface Field {
  name: string
  type: FieldType | null
  itemType?: FieldType | null
  refPayloadId?: string | null
  refPayloadName?: string | null
  required: boolean
  format?: string | null
  description?: string | null
  example?: string | null
  minLength?: number | null
  maxLength?: number | null
  minimum?: number | null
  maximum?: number | null
  pattern?: string | null
  enumValues: string[]
  /** Ids of the error codes this field can fail with; the first is the one checks on it use. Left out: kept as they are. */
  errorCodeIds?: string[]
}

/** OBJECT: a reusable building block in the object inventory. STRUCTURE: a request, response, message or working body. */
export type PayloadRole = 'OBJECT' | 'STRUCTURE'

export interface PayloadRequest {
  name: string
  description?: string | null
  example?: string | null
  fields: Field[]
  /** Left out: a new payload is a STRUCTURE, an updated one keeps its role. */
  role?: PayloadRole
}

export interface Payload extends PayloadRequest {
  id: string
  projectId: string
  role: PayloadRole
}

export interface PayloadUsage {
  kind: 'PAYLOAD_FIELD' | 'REQUEST_BODY' | 'RESPONSE_BODY' | 'MESSAGE' | 'RULE'
  ownerId: string
  ownerName: string
  detail: string
}

export interface Parameter {
  name: string
  location: ParameterLocation
  type: FieldType
  required: boolean
  description?: string | null
}

export interface ResponseDef {
  statusCode: string
  description?: string | null
  payloadId?: string | null
  payloadName?: string | null
}

export interface EndpointRequest {
  kind: EndpointKind
  name: string
  summary?: string | null
  description?: string | null
  tags: string[]
  implementationStyle?: ImplementationStyle | null
  parameters: Parameter[]
  httpMethod?: HttpMethod | null
  path?: string | null
  requestPayloadId?: string | null
  responses: ResponseDef[]
  security: SecurityScheme[]
  channel?: string | null
  operation?: AsyncOperation | null
  protocol?: MessagingProtocol | null
  messagePayloadId?: string | null
}

export interface Endpoint extends EndpointRequest {
  id: string
  projectId: string
  effectiveImplementationStyle: ImplementationStyle
  requestPayloadName?: string | null
  messagePayloadName?: string | null
  ruleCount: number
}

export type RuleConfig = Record<string, unknown>

export interface RuleRequest {
  type: RuleType
  name: string
  condition?: string | null
  config: RuleConfig
  position?: number
  /** Error codes the step can fail with. Left out on an update: kept as they are. */
  errorCodeIds?: string[]
}

export interface Rule {
  id: string
  endpointId: string
  position: number
  type: RuleType
  typeLabel: string
  name: string
  condition?: string | null
  config: RuleConfig
  summary: string
  errorCodeIds: string[]
}

export type ErrorCategory =
  | 'VALIDATION' | 'BUSINESS' | 'NOT_FOUND' | 'CONFLICT' | 'SECURITY'
  | 'DOWNSTREAM' | 'TIMEOUT' | 'PERSISTENCE' | 'MESSAGING' | 'TECHNICAL'

export interface ErrorCodeRequest {
  code: string
  message: string
  /** Left out: the category's default status. */
  httpStatus?: number | null
  category: ErrorCategory
  description?: string | null
}

/** Where a code is used. CHECK links (a Validate check that picks the code) are read-only here. */
export interface ErrorLink {
  kind: 'FIELD' | 'RULE' | 'CHECK'
  payloadId?: string | null
  payloadName?: string | null
  field?: string | null
  endpointId?: string | null
  endpointName?: string | null
  ruleId?: string | null
  ruleName?: string | null
  ruleType?: RuleType | null
  detail?: string | null
}

/** An entry of the field inventory: a plain field to copy into payloads. */
export interface FieldDefinition {
  id: string
  projectId: string
  field: Field
  /** Payload fields with the same name, "Money.currency". */
  usedIn: string[]
}

export interface ErrorCode extends ErrorCodeRequest {
  id: string
  projectId: string
  /** The effective status: httpStatus, or the category's default. */
  status: number
  links: ErrorLink[]
}

export interface RuleWarning {
  ruleId: string
  ruleName: string
  message: string
}

export interface RuleChain {
  endpointId: string
  endpointName: string
  effectiveImplementationStyle: ImplementationStyle
  inputPayloadId?: string | null
  inputPayloadName?: string | null
  rules: Rule[]
  summary: string[]
  warnings: RuleWarning[]
}

export interface Meta {
  fieldTypes: FieldType[]
  httpMethods: HttpMethod[]
  parameterLocations: ParameterLocation[]
  asyncOperations: AsyncOperation[]
  protocols: MessagingProtocol[]
  securitySchemes: SecurityScheme[]
  implementationStyles: ImplementationStyle[]
  payloadRoles?: PayloadRole[]
  ruleTypes: { type: RuleType; label: string }[]
  validateCheckKinds: string[]
  enrichModes: string[]
  persistOperations: string[]
  aggregations: string[]
  errorCategories: string[]
  fallbackTypes: string[]
  errorCodeCategories?: { category: ErrorCategory; defaultStatus: number; description: string }[]
  expressionFunctions: { name: string; example: string; description: string }[]
}

export interface Problem {
  severity: 'ERROR' | 'WARNING' | 'INFO'
  source: string
  location: string
  message: string
}

export interface SpecView {
  id: 'openapi' | 'asyncapi'
  present: boolean
  yaml?: string | null
  json?: string | null
  valid: boolean
  problems: Problem[]
}

export interface SpecsView {
  openapi: SpecView
  asyncapi: SpecView
}

export interface CodeFile {
  path: string
  language: string
  content: string
}

export interface CodeView {
  target: string
  ready: boolean
  files: CodeFile[]
  endpointFiles: Record<string, string>
  problems: Problem[]
}

export type DiagramKind = 'CLASS' | 'OVERVIEW' | 'SEQUENCE'

export interface Diagram {
  id: string
  kind: DiagramKind
  title: string
  endpointName?: string | null
  mermaid: string
  plantuml: string
}

export interface DiagramsView {
  diagrams: Diagram[]
  problems: Problem[]
}

export interface ImportResult {
  project: Project
  warnings: string[]
}

export type SnapshotKind = 'CODE' | 'SPECS'

export interface Snapshot {
  id: string
  kind: SnapshotKind
  reason: 'DOWNLOAD' | 'MANUAL'
  createdAt: string
  fileCount: number
}

export type ChangeStatus = 'ADDED' | 'REMOVED' | 'CHANGED' | 'UNCHANGED'

export interface FileChange {
  path: string
  status: ChangeStatus
  before?: string | null
  after?: string | null
}

export interface GenerationDiff {
  baseline: Snapshot
  files: FileChange[]
}

// ---------------------------------------------------------------- functional capabilities (L1 to L8)

export interface Capability {
  id: string
  projectId: string
  parentId: string | null
  level: number
  levelName: string
  position: number
  name: string
  code: string | null
  description: string | null
  /** Names from the L1 capability down to this one. */
  path: string[]
  /** APIs linked to this capability itself. */
  endpointIds: string[]
  /** Its API inventory: its own APIs plus everything linked below it. */
  apiCount: number
  childCount: number
}

export interface CapabilityRequest {
  name: string
  code?: string | null
  description?: string | null
  parentId?: string | null
  position?: number | null
}

export interface CapabilityApi {
  endpointId: string
  name: string
  kind: EndpointKind
  httpMethod: HttpMethod | null
  path: string | null
  channel: string | null
  operation: AsyncOperation | null
  summary: string | null
  direct: boolean
  via: { capabilityId: string; name: string; level: number }[]
}

// ---------------------------------------------------------------- capability imports

export type ImportResolution = 'CREATE' | 'MERGE' | 'SKIP'
export type FindingSeverity = 'ERROR' | 'DUPLICATE' | 'OVERLAP' | 'WARNING' | 'INFO'
export type FindingType = 'MATCH' | 'SAME_CODE' | 'SIMILAR_NAME' | 'CONTAINED_NAME' | 'SIMILAR_DESCRIPTION' | 'API_ALREADY_LINKED'
  | 'DUPLICATE_IN_FILE' | 'TOO_DEEP' | 'UNKNOWN_API' | 'MERGE_TARGET_MISSING'

/** One capability of an import draft, as stored. */
export interface ImportNode {
  key: string
  parentKey: string | null
  name: string
  code: string | null
  description: string | null
  apis: string[]
  resolution: ImportResolution
  mergeTargetId: string | null
  sourceRow: number | null
}

export interface ImportFinding {
  type: FindingType
  severity: FindingSeverity
  message: string
  existingId: string | null
  existingPath: string | null
  existingLevel: number | null
  otherKey: string | null
  score: number | null
}

export interface ImportNodeView extends ImportNode {
  level: number
  path: string[]
  mergeTargetPath: string | null
  findings: ImportFinding[]
}

export interface CapabilityImport {
  id: string
  projectId: string
  fileName: string
  format: string
  sheet: string | null
  status: 'DRAFT' | 'APPLIED' | 'DISCARDED'
  targetParentId: string | null
  targetParentPath: string | null
  warnings: string[]
  nodes: ImportNodeView[]
  summary: { total: number; create: number; merge: number; skip: number; matches: number; duplicates: number; overlaps: number; errors: number }
  result: { created: number; merged: number; skipped: number; apisLinked: number } | null
  createdAt: string
  updatedAt: string
}

export interface CapabilityImportItem {
  id: string
  fileName: string
  format: string
  status: CapabilityImport['status']
  total: number
  createdAt: string
  updatedAt: string
}

// ---- Reports ----

export type ReportId = 'projects' | 'endpoints' | 'objects' | 'fields'

export interface ProjectTotals {
  projects: number; endpoints: number; restEndpoints: number; asyncEndpoints: number; objects: number; structures: number
  fields: number; fieldDefinitions: number; errorCodes: number; capabilities: number; rules: number
}

export interface ProjectReportRow {
  id: string; name: string; description: string | null; version: string; basePackage: string; defaultImplementationStyle: string
  createdAt: string; updatedAt: string; endpoints: number; restEndpoints: number; asyncEndpoints: number; objects: number
  structures: number; fields: number; fieldDefinitions: number; errorCodes: number; capabilities: number; rules: number
  mappedEndpoints: number
}

export interface ProjectsReport { totals: ProjectTotals; projects: ProjectReportRow[] }

export interface ProjectEndpointCount { projectId: string; projectName: string; total: number; rest: number; async: number; withRules: number; mapped: number }

export interface EndpointReportRow {
  projectId: string; projectName: string; id: string; name: string; kind: 'REST' | 'ASYNC'; method: string | null; path: string | null
  operation: string | null; channel: string | null; protocol: string | null; summary: string | null; tags: string[]
  implementationStyle: string; requestPayload: string | null; responses: string[]; messagePayload: string | null
  parameters: number; rules: number; security: string[]; capabilities: string[]
}

export interface EndpointsReport { total: number; perProject: ProjectEndpointCount[]; endpoints: EndpointReportRow[] }

export interface ObjectReportRow {
  projectId: string; projectName: string; id: string; name: string; role: 'OBJECT' | 'STRUCTURE'; description: string | null
  fields: number; requiredFields: number; objectFields: number; linkedObjects: string[]; usedBy: number; usages: string[]
}

export interface ObjectsReport { total: number; objects: number; structures: number; payloads: ObjectReportRow[] }

export interface LinkedObjectCount {
  projectId: string; projectName: string; objectId: string; objectName: string; objectRole: 'OBJECT' | 'STRUCTURE'; fields: number; heldBy: string[]
}

export interface FieldReportRow {
  projectId: string; projectName: string; payloadId: string; payloadName: string; payloadRole: 'OBJECT' | 'STRUCTURE'; position: number
  name: string; type: string; itemType: string | null; required: boolean; linkedObjectId: string | null; linkedObject: string | null
  linkedObjectRole: 'OBJECT' | 'STRUCTURE' | null; constraints: string; description: string | null; inFieldInventory: boolean
}

export interface FieldsReport { total: number; linked: number; linkedObjects: number; perObject: LinkedObjectCount[]; fields: FieldReportRow[] }

export interface ReportData { projects: ProjectsReport; endpoints: EndpointsReport; objects: ObjectsReport; fields: FieldsReport }

// ---- The capability map ----

export interface MapCapability { id: string; parentId: string | null; level: number; name: string; code: string | null; description: string | null; endpointIds: string[] }
export interface MapUse { payloadId: string; how: string }
export interface MapRule { id: string; position: number; name: string; type: string; typeLabel: string; condition: string | null; payloadIds: string[]; errorCodes: number }
export interface MapEndpoint {
  id: string; name: string; kind: 'REST' | 'ASYNC'; method: string | null; path: string | null; operation: string | null; channel: string | null
  protocol: string | null; summary: string | null; uses: MapUse[]; rules: MapRule[]
}
export interface MapField { name: string; type: string; itemType: string | null; required: boolean; refPayloadId: string | null; constraints: string; description: string | null }
export interface MapPayload { id: string; name: string; role: 'OBJECT' | 'STRUCTURE'; description: string | null; fields: MapField[] }
export interface CapabilityMap {
  projectId: string; projectName: string; levelNames: string[]; capabilities: MapCapability[]; endpoints: MapEndpoint[]; payloads: MapPayload[]
}
