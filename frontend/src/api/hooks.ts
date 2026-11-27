import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type { SnapshotKind } from './types'

export const keys = {
  meta: ['meta'] as const,
  projects: ['projects'] as const,
  project: (id: string) => ['projects', id] as const,
  payloads: (projectId: string) => ['projects', projectId, 'payloads'] as const,
  endpoints: (projectId: string) => ['projects', projectId, 'endpoints'] as const,
  errorCodes: (projectId: string) => ['projects', projectId, 'error-codes'] as const,
  fieldDefinitions: (projectId: string) => ['projects', projectId, 'field-definitions'] as const,
  rules: (projectId: string, endpointId: string) => ['projects', projectId, 'endpoints', endpointId, 'rules'] as const,
  specs: (projectId: string) => ['projects', projectId, 'specs'] as const,
  code: (projectId: string) => ['projects', projectId, 'code'] as const,
  diagrams: (projectId: string) => ['projects', projectId, 'diagrams'] as const,
  snapshots: (projectId: string, kind: string) => ['projects', projectId, 'snapshots', kind] as const,
  diff: (projectId: string, snapshotId: string) => ['projects', projectId, 'diff', snapshotId] as const,
}

export const useMeta = () => useQuery({ queryKey: keys.meta, queryFn: api.meta, staleTime: Infinity })
export const useProjects = () => useQuery({ queryKey: keys.projects, queryFn: api.projects })
export const useProject = (id: string) => useQuery({ queryKey: keys.project(id), queryFn: () => api.project(id) })
export const usePayloads = (projectId: string) =>
  useQuery({ queryKey: keys.payloads(projectId), queryFn: () => api.payloads(projectId) })
export const useEndpoints = (projectId: string) =>
  useQuery({ queryKey: keys.endpoints(projectId), queryFn: () => api.endpoints(projectId) })
/** The project's error-code inventory; not fetched without a project. */
export const useFieldDefinitions = (projectId: string | undefined) =>
  useQuery({ queryKey: keys.fieldDefinitions(projectId ?? ''), queryFn: () => api.fieldDefinitions(projectId!), enabled: !!projectId })
export const useErrorCodes = (projectId: string | undefined) =>
  useQuery({ queryKey: keys.errorCodes(projectId ?? ''), queryFn: () => api.errorCodes(projectId!), enabled: !!projectId })
export const useRuleChain = (projectId: string, endpointId: string) =>
  useQuery({ queryKey: keys.rules(projectId, endpointId), queryFn: () => api.ruleChain(projectId, endpointId) })

/** The generated specs; refreshed with everything else in the project after each save. */
export const useSpecs = (projectId: string, enabled = true) =>
  useQuery({ queryKey: keys.specs(projectId), queryFn: () => api.specs(projectId), enabled, placeholderData: (previous) => previous })

/** The generated Maven project; only fetched while something shows it. */
export const useCode = (projectId: string, enabled: boolean) =>
  useQuery({ queryKey: keys.code(projectId), queryFn: () => api.code(projectId), enabled, placeholderData: (previous) => previous })

/** The project's diagrams in Mermaid and PlantUML; only fetched while something shows them. */
export const useDiagrams = (projectId: string, enabled: boolean) =>
  useQuery({ queryKey: keys.diagrams(projectId), queryFn: () => api.diagrams(projectId), enabled, placeholderData: (previous) => previous })

/** Saved generations of one kind, newest first. */
export const useSnapshots = (projectId: string, kind: SnapshotKind) =>
  useQuery({ queryKey: keys.snapshots(projectId, kind), queryFn: () => api.snapshots(projectId, kind) })

/** The current output compared with a snapshot; refreshed with the project like everything else. */
export const useDiff = (projectId: string, snapshotId: string | null) =>
  useQuery({ queryKey: keys.diff(projectId, snapshotId ?? ''), queryFn: () => api.diff(projectId, snapshotId!), enabled: !!snapshotId })

/**
 * Anything can change many things at once (a payload rename shows in endpoints and rule summaries),
 * so after a change we refresh everything under the project. The data is small.
 */
export function useRefreshProject() {
  const client = useQueryClient()
  return (projectId: string) =>
    Promise.all([
      client.invalidateQueries({ queryKey: keys.projects, exact: true }),
      client.invalidateQueries({ queryKey: keys.project(projectId) }),
    ])
}
