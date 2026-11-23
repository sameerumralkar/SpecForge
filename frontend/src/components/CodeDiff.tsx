import { DiffEditor } from '@monaco-editor/react'
import '../lib/monacoSetup'

/** Side-by-side diff of two versions of a file. Loaded lazily; tests replace it. */
export default function CodeDiff({ before, after, language, height = '100%' }: {
  before: string
  after: string
  language: string
  height?: number | string
}) {
  return (
    <DiffEditor
      height={height}
      language={language}
      original={before}
      modified={after}
      options={{ readOnly: true, renderSideBySide: true, minimap: { enabled: false }, fontSize: 12, scrollBeyondLastLine: false }}
    />
  )
}
