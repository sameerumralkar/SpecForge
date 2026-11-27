/** A user guide shown in the app: its Markdown is bundled from the repository's docs folder. */
export interface Guide {
  id: string
  title: string
  /** The Markdown file name, as other guides link to it (e.g. "USER_GUIDE.md"). */
  file: string
  load: () => Promise<string>
  /** Where the file lives on GitHub, for links that leave the guide (README, source files). */
  repoUrl: string
}

export const GUIDES: Guide[] = [
  {
    id: 'specforge', title: 'SpecForge user guide', file: 'USER_GUIDE.md', repoUrl: 'https://github.com/sameerumralkar/SpecForge',
    load: () => import('../../../../docs/USER_GUIDE.md?raw').then((m) => m.default),
  },
  {
    id: 'diagrams', title: 'Diagrams', file: 'DIAGRAMS.md', repoUrl: 'https://github.com/sameerumralkar/SpecForge',
    load: () => import('../../../../docs/DIAGRAMS.md?raw').then((m) => m.default),
  },
]

export const guide = (id: string | undefined) => GUIDES.find((g) => g.id === id) ?? GUIDES[0]

/** The guide help panels point at unless they name another: the SpecForge guide. */
const helpGuide = () => GUIDES.find((g) => g.id === 'specforge') ?? GUIDES[0]

/** GitHub's heading anchors: "8. Map capabilities" → "8-map-capabilities". */
export function slug(text: string): string {
  return text.trim().toLowerCase().replace(/[^\p{L}\p{N}\s-]/gu, '').replace(/\s/g, '-')
}

export interface GuideLink {
  label: string
  href: string
}

/**
 * The guide references in a help text ("§ 3 Create a project, § 14 Import and export") as links to those
 * sections. A reference starting with another guide's title ("SpecForge Agents user guide § 4 …") points
 * at that guide.
 */
export function guideLinks(reference: string): GuideLink[] {
  const target = GUIDES.find((g) => reference.trim().toLowerCase().startsWith(g.title.toLowerCase())) ?? helpGuide()
  const links: GuideLink[] = []
  for (const m of reference.matchAll(/§\s*(\d+)\s+([^,§]+)/g)) {
    const title = m[2].trim()
    links.push({ label: `§ ${m[1]} ${title}`, href: `/guide/${target.id}?section=${m[1]}` })
  }
  return links.length ? links : [{ label: reference, href: `/guide/${target.id}` }]
}
