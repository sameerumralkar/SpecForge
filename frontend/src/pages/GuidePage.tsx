import { useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router'
import { Box, Card, CircularProgress, InputAdornment, Link, List, ListItemButton, ListItemText, Stack, Tab, Tabs, TextField, Typography } from '@mui/material'
import OpenInNewIcon from '@mui/icons-material/OpenInNew'
import SearchIcon from '@mui/icons-material/Search'
import { Marked, type Tokens } from 'marked'
import { GUIDES, guide as findGuide, slug } from '../lib/guides'

interface Section {
  id: string
  title: string
  depth: number
  text: string
}

/** Markdown to HTML, with GitHub-style ids on headings so links between sections work. */
function render(markdown: string): string {
  const md = new Marked({ gfm: true })
  md.use({
    renderer: {
      heading(this: { parser: { parseInline: (t: Tokens.Heading['tokens']) => string } }, token: Tokens.Heading) {
        return `<h${token.depth} id="${slug(token.text)}">${this.parser.parseInline(token.tokens)}</h${token.depth}>\n`
      },
    },
  })
  return md.parse(markdown, { async: false }) as string
}

/** The guide's sections (level 2 and 3 headings), each with its own text, for the contents and search. */
function sections(markdown: string): Section[] {
  const out: Section[] = []
  for (const line of markdown.split('\n')) {
    const m = /^(#{2,3})\s+(.*)$/.exec(line)
    if (m) out.push({ id: slug(m[2].replace(/[*_`]/g, '')), title: m[2].replace(/[*_`]/g, ''), depth: m[1].length, text: '' })
    else if (out.length) out[out.length - 1].text += line + '\n'
  }
  return out
}

/**
 * The user guide inside the app: contents on the left (filtered by a search), the guide on the right.
 * {@code ?section=8} opens it at section 8.
 */
export function GuidePage() {
  const { guideId } = useParams()
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const guide = findGuide(guideId)
  const [markdown, setMarkdown] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const body = useRef<HTMLDivElement>(null)

  useEffect(() => {
    let cancelled = false
    setMarkdown(null)
    guide.load().then((text) => { if (!cancelled) setMarkdown(text) })
    return () => { cancelled = true }
  }, [guide])

  const html = useMemo(() => (markdown ? render(markdown) : ''), [markdown])
  const all = useMemo(() => (markdown ? sections(markdown) : []), [markdown])
  const q = query.trim().toLowerCase()
  const shown = q ? all.filter((s) => s.title.toLowerCase().includes(q) || s.text.toLowerCase().includes(q)) : all

  const scrollTo = (id: string) => {
    const el = body.current?.querySelector(`[id="${id.replace(/"/g, '')}"]`)
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  // Open at #anchor (links from another guide).
  const { hash } = useLocation()
  useEffect(() => {
    if (html && hash) body.current?.querySelector(`[id="${decodeURIComponent(hash.slice(1)).replace(/"/g, '')}"]`)?.scrollIntoView({ block: 'start' })
  }, [html, hash])

  // Open at ?section=N: the heading "N. …".
  const section = params.get('section')
  useEffect(() => {
    if (!html || !section) return
    const heading = [...(body.current?.querySelectorAll('h2') ?? [])].find((h) => h.textContent?.trim().startsWith(`${section}.`))
    heading?.scrollIntoView({ block: 'start' })
  }, [html, section])

  // Links inside the guide: sections scroll, other guides open here, anything else opens on GitHub.
  const click = (e: React.MouseEvent) => {
    const a = (e.target as HTMLElement).closest('a')
    const href = a?.getAttribute('href')
    if (!a || !href) return
    if (href.startsWith('#')) {
      e.preventDefault()
      scrollTo(decodeURIComponent(href.slice(1)))
      return
    }
    if (/^[a-z]+:/i.test(href)) {
      a.setAttribute('target', '_blank')
      a.setAttribute('rel', 'noreferrer')
      return
    }
    e.preventDefault()
    const [file, anchor] = href.split('#')
    const other = GUIDES.find((g) => file.endsWith(g.file))
    if (other) navigate(`/guide/${other.id}${anchor ? `#${anchor}` : ''}`)
    else window.open(new URL(file, `${guide.repoUrl}/blob/main/docs/`).href, '_blank', 'noreferrer')
  }

  return (
    <Box sx={{ flex: 1, overflow: 'auto', bgcolor: 'background.default' }}>
      <Box sx={{ p: 3, maxWidth: 1400, mx: 'auto' }}>
        <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 2 }}>
          <Typography variant="h5" sx={{ flex: 1 }}>User guide</Typography>
          <Link href={`${guide.repoUrl}/blob/main/docs/${guide.file}`} target="_blank" rel="noreferrer" variant="body2"
            sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
            On GitHub <OpenInNewIcon sx={{ fontSize: 14 }} />
          </Link>
        </Stack>
        {GUIDES.length > 1 && (
          <Tabs value={guide.id} onChange={(_, id) => navigate(`/guide/${id}`)} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}>
            {GUIDES.map((g) => <Tab key={g.id} value={g.id} label={g.title} />)}
          </Tabs>
        )}
        {!markdown ? <CircularProgress /> : (
          <Box sx={{ display: 'grid', gridTemplateColumns: '280px minmax(0, 1fr)', gap: 3, alignItems: 'start' }}>
            <Card variant="outlined" sx={{ position: 'sticky', top: 16, maxHeight: 'calc(100vh - 140px)', display: 'flex', flexDirection: 'column' }}>
              <Box sx={{ p: 1.5 }}>
                <TextField size="small" fullWidth placeholder="Find in the guide" value={query} onChange={(e) => setQuery(e.target.value)}
                  slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
              </Box>
              <List dense aria-label="Contents" sx={{ overflow: 'auto', pt: 0 }}>
                {shown.length === 0 && <Typography variant="body2" color="text.secondary" sx={{ px: 2 }}>Nothing matches.</Typography>}
                {shown.map((s) => (
                  <ListItemButton key={s.id + s.title} onClick={() => scrollTo(s.id)} sx={{ pl: s.depth === 2 ? 2 : 4 }}>
                    <ListItemText primary={s.title} slotProps={{ primary: { variant: 'body2', sx: { fontWeight: s.depth === 2 ? 500 : 400 } } }} />
                  </ListItemButton>
                ))}
              </List>
            </Card>
            <Card variant="outlined" sx={{ px: 5, py: 3 }}>
              <Box ref={body} onClick={click} className="guide" aria-label={guide.title} sx={{
                fontSize: 15, lineHeight: 1.65,
                '& h1': { fontSize: 30, fontWeight: 600, mt: 0 },
                '& h2': { fontSize: 22, fontWeight: 600, mt: 5, pb: 0.5, borderBottom: 1, borderColor: 'divider', scrollMarginTop: 16 },
                '& h3': { fontSize: 18, fontWeight: 600, mt: 3, scrollMarginTop: 16 },
                '& a': { color: 'primary.main' },
                '& code': { fontFamily: 'monospace', fontSize: 13, bgcolor: 'grey.100', px: 0.5, borderRadius: 0.5 },
                '& pre': { bgcolor: 'grey.100', p: 2, borderRadius: 1, overflow: 'auto', '& code': { bgcolor: 'transparent', px: 0 } },
                '& table': { borderCollapse: 'collapse', my: 2, width: '100%' },
                '& th, & td': { border: 1, borderColor: 'divider', px: 1.5, py: 0.75, textAlign: 'left', verticalAlign: 'top' },
                '& th': { bgcolor: 'grey.50', fontWeight: 600 },
                '& blockquote': { borderLeft: 4, borderColor: 'primary.light', m: 0, pl: 2, color: 'text.secondary' },
                '& li': { mb: 0.5 },
              }} dangerouslySetInnerHTML={{ __html: html }} />
            </Card>
          </Box>
        )}
      </Box>
    </Box>
  )
}
