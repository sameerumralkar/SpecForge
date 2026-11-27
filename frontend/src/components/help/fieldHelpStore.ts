import { useSyncExternalStore } from 'react'

/** Whether field tips show on hover and focus. On by default; remembered in this browser. */
const KEY = 'field-help'
const listeners = new Set<() => void>()

function read(): boolean {
  try {
    return localStorage.getItem(KEY) !== 'off'
  } catch {
    return true
  }
}

let on = read()

export function setFieldHelpOn(value: boolean) {
  on = value
  try {
    localStorage.setItem(KEY, value ? 'on' : 'off')
  } catch {
    // private mode: keep it for this page only
  }
  listeners.forEach((l) => l())
}

export function isFieldHelpOn() {
  return on
}

export function useFieldHelpOn() {
  return useSyncExternalStore((l) => { listeners.add(l); return () => listeners.delete(l) }, () => on)
}
