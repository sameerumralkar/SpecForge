import { useEffect, useRef, useState } from 'react'
import { Box, Paper, Popper, Typography } from '@mui/material'
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined'
import { fieldHelp } from '../../lib/help'
import { isFieldHelpOn } from './fieldHelpStore'

/** The label a person sees for the field under {@code target}, and the element to point the tip at. */
export function fieldUnder(target: Element | null): { label: string; anchor: HTMLElement } | null {
  if (!target || !(target instanceof Element)) return null
  // Menus, pop-ups and dialogs' own buttons are not fields.
  if (target.closest('[role="listbox"], [role="menu"], .MuiTooltip-popper, [data-field-help-tip]')) return null
  const explicit = target.closest<HTMLElement>('[data-help]')
  if (explicit) return { label: explicit.dataset.help!, anchor: explicit }
  const choice = target.closest<HTMLElement>('.MuiFormControlLabel-root')
  if (choice) {
    const text = choice.querySelector('.MuiFormControlLabel-label')?.textContent
    return text ? { label: text, anchor: choice } : null
  }
  const box = target.closest<HTMLElement>('.MuiCheckbox-root, .MuiSwitch-root, .MuiRadio-root')
  if (box) {
    const label = box.querySelector('input')?.getAttribute('aria-label')
    return label ? { label, anchor: box } : null
  }
  const control = target.closest<HTMLElement>('.MuiFormControl-root')
  if (control) {
    const label = control.querySelector(':scope > label, :scope > .MuiFormLabel-root')?.textContent
      ?? control.querySelector('input, textarea, [role="combobox"]')?.getAttribute('aria-label')
    return label ? { label, anchor: control } : null
  }
  const group = target.closest<HTMLElement>('.MuiToggleButtonGroup-root[aria-label]')
  if (group) return { label: group.getAttribute('aria-label')!, anchor: group }
  return null
}

/**
 * Shows what a field is for when you hover over it or tab into it, on every screen. The text comes from
 * the help registry, found by the field's label. Turn it off with the "Field tips" switch.
 */
export function FieldHelpLayer() {
  const [tip, setTip] = useState<{ label: string; text: string; anchor: HTMLElement } | null>(null)
  const timer = useRef<number | undefined>(undefined)
  const current = useRef<HTMLElement | null>(null)

  useEffect(() => {
    const show = (target: EventTarget | null, delay: number) => {
      if (!isFieldHelpOn()) return
      const found = fieldUnder(target as Element)
      if (!found) return
      if (current.current === found.anchor) return
      const text = fieldHelp(found.label)
      window.clearTimeout(timer.current)
      if (!text) {
        current.current = null
        setTip(null)
        return
      }
      current.current = found.anchor
      timer.current = window.setTimeout(() => setTip({ label: found.label.replace(/\s*\*\s*$/, ''), text, anchor: found.anchor }), delay)
    }
    const hide = (related: EventTarget | null) => {
      if (current.current && related instanceof Node && current.current.contains(related)) return
      window.clearTimeout(timer.current)
      current.current = null
      setTip(null)
    }
    const over = (e: MouseEvent) => show(e.target, 450)
    const out = (e: MouseEvent) => hide(e.relatedTarget)
    const focus = (e: FocusEvent) => show(e.target, 250)
    const blur = (e: FocusEvent) => hide(e.relatedTarget)
    const key = (e: KeyboardEvent) => { if (e.key === 'Escape') hide(null) }
    const scroll = () => hide(null)
    document.addEventListener('mouseover', over)
    document.addEventListener('mouseout', out)
    document.addEventListener('focusin', focus)
    document.addEventListener('focusout', blur)
    document.addEventListener('keydown', key)
    window.addEventListener('scroll', scroll, true)
    return () => {
      window.clearTimeout(timer.current)
      document.removeEventListener('mouseover', over)
      document.removeEventListener('mouseout', out)
      document.removeEventListener('focusin', focus)
      document.removeEventListener('focusout', blur)
      document.removeEventListener('keydown', key)
      window.removeEventListener('scroll', scroll, true)
    }
  }, [])

  const open = !!tip && tip.anchor.isConnected
  return (
    <Popper open={open} anchorEl={tip?.anchor} placement="top-start" sx={{ zIndex: 1600, pointerEvents: 'none' }} aria-label="Field help"
      modifiers={[{ name: 'offset', options: { offset: [0, 6] } }, { name: 'flip', options: { fallbackPlacements: ['bottom-start', 'right'] } }]}>
      <Paper elevation={6} data-field-help-tip
        sx={{ maxWidth: 380, px: 1.5, py: 1, bgcolor: '#263238', color: '#fff', display: 'flex', gap: 1 }}>
        <InfoOutlinedIcon sx={{ fontSize: 18, mt: '1px', color: '#80cbc4' }} />
        <Box>
          <Typography variant="caption" sx={{ fontWeight: 700, display: 'block' }}>{tip?.label}</Typography>
          <Typography variant="caption" sx={{ display: 'block', lineHeight: 1.45 }}>{tip?.text}</Typography>
        </Box>
      </Paper>
    </Popper>
  )
}
