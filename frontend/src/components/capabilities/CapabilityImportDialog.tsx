import { useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import {
  Alert, Box, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Link, Stack, Table, TableBody, TableCell,
  TableHead, TableRow, Typography,
} from '@mui/material'
import UploadFileIcon from '@mui/icons-material/UploadFile'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import { keys } from '../../api/hooks'
import { ErrorSummary } from '../ErrorSummary'
import { ScreenHelp } from '../help/ScreenHelp'

const CSV_TEMPLATE = [
  'L1,L1 code,L2,L2 code,L3,L3 code,L4,Description,APIs',
  'Payments,PAY,Card payments,PAY.CARD,Authorisation,PAY.CARD.01,,Approve or decline a card payment,authorisePayment',
  ',,,,Refunds,PAY.CARD.02,,Return money for a settled card payment,',
  ',,,,,,Partial refunds,Refund part of the amount,',
  ',,Wallets,PAY.WAL,Top-up,PAY.WAL.01,,Add money to a wallet,',
].join('\n') + '\n'

const JSON_TEMPLATE = JSON.stringify({
  capabilities: [{
    name: 'Payments', code: 'PAY', description: 'Moving money for customers and merchants', children: [
      { name: 'Card payments', code: 'PAY.CARD', children: [
        { name: 'Authorisation', code: 'PAY.CARD.01', apis: ['authorisePayment'] },
        { name: 'Refunds', code: 'PAY.CARD.02', children: [{ name: 'Partial refunds' }] },
      ] },
    ],
  }],
}, null, 2)

function download(name: string, text: string, type: string) {
  const url = URL.createObjectURL(new Blob([text], { type }))
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  URL.revokeObjectURL(url)
}

/** The file as base64, as the import API takes it. */
export async function fileToBase64(file: Blob): Promise<string> {
  const bytes = new Uint8Array(await file.arrayBuffer())
  let binary = ''
  for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000))
  return btoa(binary)
}

/** Choose an Excel, CSV or JSON file of capabilities; it becomes a draft to review before anything changes. */
export function CapabilityImportDialog({ projectId, open, onClose }: { projectId: string; open: boolean; onClose: () => void }) {
  const navigate = useNavigate()
  const client = useQueryClient()
  const input = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)

  const read = async (file: File | undefined) => {
    if (!file) return
    setBusy(true)
    setError(null)
    try {
      const draft = await api.uploadCapabilityImport(projectId, file.name, await fileToBase64(file))
      await client.invalidateQueries({ queryKey: keys.capabilities(projectId) })
      onClose()
      navigate(`/projects/${projectId}/capabilities/imports/${draft.id}`)
    } catch (e) {
      setError(e)
    } finally {
      setBusy(false)
      if (input.current) input.current.value = ''
    }
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>Import capabilities <ScreenHelp screen="capability-import" /></DialogTitle>
      <DialogContent>
        <ErrorSummary error={error} />
        <Typography variant="body2" sx={{ mb: 2 }}>
          The file becomes a <b>draft</b>: you see it next to your capability map, with possible duplicates and overlaps highlighted,
          edit it, and choose what to create, merge or skip. Nothing changes until you apply it.
        </Typography>
        <Typography variant="subtitle2">Files</Typography>
        <Typography variant="body2" sx={{ mb: 1.5 }}>
          Excel workbooks (.xlsx; the first sheet with capability columns), CSV or TSV, and JSON. Older .xls files: save them as .xlsx first.
        </Typography>
        <Typography variant="subtitle2" sx={{ mb: 0.5 }}>Layouts, recognised from the header row</Typography>
        <Table size="small" aria-label="Layouts" sx={{ mb: 2 }}>
          <TableHead><TableRow><TableCell>Layout</TableCell><TableCell>Columns</TableCell></TableRow></TableHead>
          <TableBody>
            <TableRow><TableCell>Level columns</TableCell><TableCell>
              <b>L1, L2 … L8</b>: one row per capability; blank cells take the value above (as with merged cells). Optional <b>L2 code</b>, <b>L2 description</b>…
            </TableCell></TableRow>
            <TableRow><TableCell>Parent column</TableCell><TableCell><b>Name</b> and <b>Parent</b> (the parent's code or name)</TableCell></TableRow>
            <TableRow><TableCell>Level column</TableCell><TableCell><b>Name</b> and <b>Level</b> (1–8 or L1–L8), in outline order</TableCell></TableRow>
            <TableRow><TableCell>JSON tree</TableCell><TableCell><b>name</b>, <b>code</b>, <b>description</b>, <b>apis</b>, <b>children</b>; or a SpecForge project export</TableCell></TableRow>
          </TableBody>
        </Table>
        <Typography variant="body2" color="text.secondary">
          Optional in every layout: <b>Code</b>, <b>Description</b>, and <b>APIs</b> (endpoint names, separated by commas) to link.
          Templates: <Link component="button" onClick={() => download('capabilities-template.csv', CSV_TEMPLATE, 'text/csv')}>CSV (opens in Excel)</Link>
          {' · '}<Link component="button" onClick={() => download('capabilities-template.json', JSON_TEMPLATE, 'application/json')}>JSON</Link>
        </Typography>
        {busy && <Alert severity="info" icon={<CircularProgress size={16} />} sx={{ mt: 2 }}>Reading the file and comparing it with your capability map…</Alert>}
        <Box component="input" ref={input} type="file" accept=".xlsx,.csv,.tsv,.txt,.json" sx={{ display: 'none' }} aria-label="Capability file"
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => read(e.target.files?.[0])} />
      </DialogContent>
      <DialogActions>
        <Stack direction="row" spacing={1}>
          <Button onClick={onClose}>Cancel</Button>
          <Button variant="contained" startIcon={<UploadFileIcon />} disabled={busy} onClick={() => input.current?.click()}>Choose file…</Button>
        </Stack>
      </DialogActions>
    </Dialog>
  )
}
