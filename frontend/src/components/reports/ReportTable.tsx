import { useMemo, useState, type ReactNode } from 'react'
import {
  Box, Button, Card, InputAdornment, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow,
  TableSortLabel, TextField, Tooltip, Typography,
} from '@mui/material'
import DownloadOutlinedIcon from '@mui/icons-material/DownloadOutlined'
import SearchIcon from '@mui/icons-material/Search'
import { saveBlob } from '../../lib/diagrams'
import { toCsv } from '../../lib/csv'

export type CellValue = string | number | boolean | null | undefined | string[]

export interface Column<T> {
  id: string
  label: string
  /** What the column sorts, searches and exports by. */
  value: (row: T) => CellValue
  /** How the cell is drawn; the value, as text, when left out. */
  render?: (row: T) => ReactNode
  numeric?: boolean
  width?: number | string
}

const text = (v: CellValue) => (v === null || v === undefined ? '' : Array.isArray(v) ? v.join(', ') : typeof v === 'boolean' ? (v ? 'yes' : 'no') : String(v))

function compare(a: CellValue, b: CellValue): number {
  if (typeof a === 'number' && typeof b === 'number') return a - b
  if (typeof a === 'boolean' && typeof b === 'boolean') return Number(a) - Number(b)
  return text(a).localeCompare(text(b), undefined, { numeric: true, sensitivity: 'base' })
}

/**
 * A report table: click a heading to sort, type to filter, page through long lists, and download what is shown
 * (filtered and sorted, every page) as CSV.
 */
export function ReportTable<T>({ title, columns, rows, rowKey, fileName, empty, toolbar }: {
  title: string
  columns: Column<T>[]
  rows: T[]
  rowKey: (row: T) => string
  fileName: string
  empty?: string
  /** Extra controls beside the search, such as a filter. */
  toolbar?: ReactNode
}) {
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState<{ id: string; dir: 'asc' | 'desc' } | null>(null)
  const [page, setPage] = useState(0)
  const [perPage, setPerPage] = useState(25)

  const shown = useMemo(() => {
    const q = query.trim().toLowerCase()
    const filtered = q ? rows.filter((r) => columns.some((c) => text(c.value(r)).toLowerCase().includes(q))) : rows
    const col = sort && columns.find((c) => c.id === sort.id)
    if (!col) return filtered
    const sign = sort!.dir === 'asc' ? 1 : -1
    return [...filtered].sort((a, b) => sign * compare(col.value(a), col.value(b)))
  }, [rows, columns, query, sort])

  const lastPage = Math.max(0, Math.ceil(shown.length / (perPage < 0 ? Math.max(shown.length, 1) : perPage)) - 1)
  const current = Math.min(page, lastPage)
  const visible = perPage < 0 ? shown : shown.slice(current * perPage, current * perPage + perPage)

  const download = () => {
    const csv = toCsv(columns.map((c) => c.label), shown.map((r) => columns.map((c) => {
      const v = c.value(r)
      return typeof v === 'boolean' ? (v ? 'yes' : 'no') : v
    })))
    saveBlob(new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' }), `${fileName}.csv`)
  }

  const toggle = (id: string) => {
    setSort((s) => (s?.id !== id ? { id, dir: 'asc' } : s.dir === 'asc' ? { id, dir: 'desc' } : null))
    setPage(0)
  }

  return (
    <Card variant="outlined" sx={{ mb: 3 }}>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center', px: 2, py: 1.5, flexWrap: 'wrap', rowGap: 1 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 600, flex: 1, minWidth: 200 }}>
          {title} <Typography component="span" variant="body2" color="text.secondary">
            {shown.length === rows.length ? `(${rows.length})` : `(${shown.length} of ${rows.length})`}
          </Typography>
        </Typography>
        {toolbar}
        <TextField size="small" sx={{ width: 240 }} placeholder="Find in the table" value={query}
          onChange={(e) => { setQuery(e.target.value); setPage(0) }}
          slotProps={{ htmlInput: { 'aria-label': `Find in ${title.toLowerCase()}` }, input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
        <Tooltip title="Download the rows shown (filtered and sorted, every page) as a CSV file for Excel">
          <span>
            <Button size="small" variant="outlined" startIcon={<DownloadOutlinedIcon />} onClick={download} disabled={shown.length === 0}>CSV</Button>
          </span>
        </Tooltip>
      </Stack>
      <TableContainer>
        <Table size="small" aria-label={title}>
          <TableHead>
            <TableRow>
              {columns.map((c) => (
                <TableCell key={c.id} align={c.numeric ? 'right' : 'left'} sx={{ width: c.width, fontWeight: 600, whiteSpace: 'nowrap', bgcolor: 'grey.50' }}
                  sortDirection={sort?.id === c.id ? sort.dir : false}>
                  <TableSortLabel active={sort?.id === c.id} direction={sort?.id === c.id ? sort.dir : 'asc'} onClick={() => toggle(c.id)}>
                    {c.label}
                  </TableSortLabel>
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {visible.length === 0 && (
              <TableRow><TableCell colSpan={columns.length}>
                <Typography variant="body2" color="text.secondary">{query ? 'Nothing matches.' : empty ?? 'Nothing to report yet.'}</Typography>
              </TableCell></TableRow>
            )}
            {visible.map((r) => (
              <TableRow key={rowKey(r)} hover sx={{ '& td': { verticalAlign: 'top' } }}>
                {columns.map((c) => (
                  <TableCell key={c.id} align={c.numeric ? 'right' : 'left'}>
                    {c.render ? c.render(r) : <Box component="span" sx={{ wordBreak: 'break-word' }}>{text(c.value(r))}</Box>}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      {shown.length > 25 && (
        <TablePagination component="div" count={shown.length} page={current} rowsPerPage={perPage}
          rowsPerPageOptions={[25, 50, 100, { label: 'All', value: -1 }]}
          onPageChange={(_, p) => setPage(p)} onRowsPerPageChange={(e) => { setPerPage(Number(e.target.value)); setPage(0) }} />
      )}
    </Card>
  )
}

/** The headline numbers of a report, as a row of tiles. */
export function StatTiles({ label, stats }: { label: string; stats: { label: string; value: number; hint?: string }[] }) {
  return (
    <Box role="list" aria-label={label} sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: 1.5, mb: 3 }}>
      {stats.map((s) => (
        <Tooltip key={s.label} title={s.hint ?? ''} disableHoverListener={!s.hint}>
          <Card role="listitem" variant="outlined" sx={{ px: 2, py: 1.5 }} aria-label={`${s.label}: ${s.value}`}>
            <Typography sx={{ fontSize: 26, fontWeight: 600, lineHeight: 1.2 }}>{s.value.toLocaleString()}</Typography>
            <Typography variant="body2" color="text.secondary">{s.label}</Typography>
          </Card>
        </Tooltip>
      ))}
    </Box>
  )
}
