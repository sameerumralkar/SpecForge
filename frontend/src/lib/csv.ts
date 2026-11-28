/** A CSV cell: quoted when it holds a comma, quote or line break; a leading = + - @ is defused for spreadsheets. */
export function csvCell(value: unknown): string {
  if (value === null || value === undefined) return ''
  let s = Array.isArray(value) ? value.join('; ') : String(value)
  if (/^[=+\-@\t\r]/.test(s) && !/^-?\d+(\.\d+)?$/.test(s)) s = `'${s}`
  return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}

/** Rows as CSV text, with a header line and Windows line endings (what Excel expects). */
export function toCsv(header: string[], rows: unknown[][]): string {
  return [header, ...rows].map((r) => r.map(csvCell).join(',')).join('\r\n') + '\r\n'
}
