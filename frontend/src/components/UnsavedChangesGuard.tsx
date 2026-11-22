import { useEffect } from 'react'
import { useBlocker } from 'react-router'
import { ConfirmDialog } from './ConfirmDialog'

/** Asks before navigating away (in the app or closing the tab) while {@code dirty}. */
export function UnsavedChangesGuard({ dirty }: { dirty: boolean }) {
  const blocker = useBlocker(({ currentLocation, nextLocation }) =>
    dirty && currentLocation.pathname.split('/').slice(0, 5).join('/') !== nextLocation.pathname.split('/').slice(0, 5).join('/'))
  useEffect(() => {
    if (!dirty) return
    const handler = (e: BeforeUnloadEvent) => e.preventDefault()
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [dirty])
  return (
    <ConfirmDialog
      open={blocker.state === 'blocked'}
      title="Leave without saving?"
      confirmLabel="Discard changes"
      onConfirm={() => blocker.proceed?.()}
      onClose={() => blocker.reset?.()}
    >
      Your changes on this page have not been saved.
    </ConfirmDialog>
  )
}
