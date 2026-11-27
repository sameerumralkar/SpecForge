import { beforeAll, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { GuidePage } from '../pages/GuidePage'
import { ScreenHelp } from '../components/help/ScreenHelp'
import { guideLinks, slug } from '../lib/guides'
import { renderWithProviders } from './render'

const scrolled: string[] = []
beforeAll(() => {
  Element.prototype.scrollIntoView = vi.fn(function (this: Element) { scrolled.push(this.id || this.textContent || '') })
})

const page = (path: string) => renderWithProviders(<Routes><Route path="/guide/:guideId?" element={<GuidePage />} /></Routes>, [], path)

describe('User guide in the app', () => {
  it('makes GitHub-style anchors and links help references to sections', () => {
    expect(slug('8. Map capabilities')).toBe('8-map-capabilities')
    expect(slug('Help on every screen')).toBe('help-on-every-screen')
    expect(guideLinks('§ 3 Create a project, § 14 Import and export')).toEqual([
      { label: '§ 3 Create a project', href: '/guide/specforge?section=3' },
      { label: '§ 14 Import and export', href: '/guide/specforge?section=14' },
    ])
  })

  it('shows the guide with its contents, opens at a section, and searches', async () => {
    const user = userEvent.setup()
    page('/guide/specforge?section=8')
    const guide = await screen.findByLabelText('SpecForge user guide')
    expect(within(guide).getByRole('heading', { name: '8. Map capabilities' })).toHaveAttribute('id', '8-map-capabilities')
    expect(within(guide).getByRole('heading', { name: 'Import capabilities from Excel, CSV or JSON' })).toBeInTheDocument()
    await waitFor(() => expect(scrolled).toContain('8-map-capabilities'))

    const contents = screen.getByRole('list', { name: 'Contents' })
    expect(within(contents).getByText('9. Manage error codes')).toBeInTheDocument()
    await user.type(screen.getByPlaceholderText('Find in the guide'), 'xlsx')
    expect(within(contents).queryByText('9. Manage error codes')).not.toBeInTheDocument()
    expect(within(contents).getByText('Import capabilities from Excel, CSV or JSON')).toBeInTheDocument()

    // Links between sections stay in the page.
    scrolled.length = 0
    await user.click(within(guide).getAllByRole('link', { name: 'Map capabilities' })[0])
    expect(scrolled).toContain('8-map-capabilities')
  })

  it('links a screen\'s help panel to its guide section', async () => {
    const user = userEvent.setup()
    renderWithProviders(<ScreenHelp screen="capabilities" />)
    await user.click(screen.getByRole('button', { name: 'Help: Capabilities' }))
    const link = await screen.findByRole('link', { name: '§ 8 Map capabilities' })
    expect(link).toHaveAttribute('href', '/guide/specforge?section=8')
    expect(link).toHaveAttribute('target', '_blank')
  })
})
