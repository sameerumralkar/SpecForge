import type { ChangeStatus, Snapshot } from '../api/types'

export const CHANGE_LABEL: Record<ChangeStatus, { letter: string; color: string; text: string }> = {
  ADDED: { letter: 'A', color: '#2e7d32', text: 'added' },
  REMOVED: { letter: 'D', color: '#c62828', text: 'removed' },
  CHANGED: { letter: 'M', color: '#ef6c00', text: 'changed' },
  UNCHANGED: { letter: '', color: 'inherit', text: 'unchanged' },
}

/** "Downloaded 22 Nov, 14:05 · 42 files" */
export function snapshotLabel(s: Snapshot): string {
  const when = new Date(s.createdAt).toLocaleString(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
  return `${s.reason === 'DOWNLOAD' ? 'Downloaded' : 'Snapshot'} ${when} · ${s.fileCount} files`
}

/** Monaco language for a file name. */
export function languageOf(path: string): string {
  if (path.endsWith('.java')) return 'java'
  if (path.endsWith('.xml')) return 'xml'
  if (path.endsWith('.json')) return 'json'
  if (path.endsWith('.yml') || path.endsWith('.yaml')) return 'yaml'
  if (path.endsWith('.md')) return 'markdown'
  if (path.endsWith('mvnw')) return 'shell'
  return 'plaintext'
}
