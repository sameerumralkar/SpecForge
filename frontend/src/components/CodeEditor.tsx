import { useEffect, useRef } from 'react'
import Editor, { type OnMount } from '@monaco-editor/react'
import { monaco } from '../lib/monacoSetup'

/**
 * Monaco for JSON or YAML. Loaded lazily; tests replace it with a textarea. {@code focus} is a
 * regular expression: the first matching line is scrolled into view and highlighted.
 */
export default function CodeEditor({ value, onChange, language = 'json', height = 180, readOnly = false, focus }: {
  value: string
  onChange?: (value: string) => void
  language?: string
  height?: number | string
  readOnly?: boolean
  focus?: string
}) {
  const editorRef = useRef<monaco.editor.IStandaloneCodeEditor | null>(null)
  const decorations = useRef<monaco.editor.IEditorDecorationsCollection | null>(null)

  const reveal = () => {
    const editor = editorRef.current
    const model = editor?.getModel()
    if (!editor || !model) return
    decorations.current?.clear()
    if (!focus) return
    const match = model.findMatches(focus, false, true, true, null, false)[0]
    if (!match) return
    editor.revealLineNearTop(Math.max(1, match.range.startLineNumber - 5)) // show the operation header and summary above it
    decorations.current = editor.createDecorationsCollection([{
      range: match.range,
      options: { isWholeLine: true, className: 'spec-focus-line', overviewRuler: { color: '#3949ab', position: monaco.editor.OverviewRulerLane.Full } },
    }])
  }

  const onMount: OnMount = (editor) => {
    editorRef.current = editor
    reveal()
  }

  // Re-run when the text or the target changes (e.g. after a save or switching endpoints).
  useEffect(reveal, [value, focus]) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <Editor
      height={height}
      language={language}
      value={value}
      onChange={(v) => onChange?.(v ?? '')}
      onMount={onMount}
      options={{
        minimap: { enabled: false }, fontSize: 12, readOnly, scrollBeyondLastLine: false, tabSize: 2,
        lineNumbers: readOnly ? 'on' : 'off', domReadOnly: readOnly, renderLineHighlight: readOnly ? 'none' : 'line',
      }}
    />
  )
}
