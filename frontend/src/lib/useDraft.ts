import { useCallback, useState } from 'react'

/**
 * Local edit state for a form backed by the server. {@code dirty} compares against the last saved
 * value; {@code saved(v)} records a successful save. Leaving the page with unsaved changes asks first.
 */
export function useDraft<T>(initial: T) {
  const [draft, setDraft] = useState<T>(initial)
  const [base, setBase] = useState(() => JSON.stringify(initial))
  const dirty = JSON.stringify(draft) !== base
  const saved = useCallback((value: T) => {
    setDraft(value)
    setBase(JSON.stringify(value))
  }, [])
  const reset = useCallback(() => setDraft(JSON.parse(base) as T), [base])
  return { draft, setDraft, dirty, saved, reset }
}
