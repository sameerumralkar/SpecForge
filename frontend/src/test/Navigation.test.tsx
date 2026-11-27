import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes, useLocation } from 'react-router'
import { Sidebar } from '../components/Sidebar'
import type { Project } from '../api/types'
import { renderWithProviders } from './render'

const project = { id: 'p', name: 'Shop', basePackage: 'com.shop', version: '1.0.0' } as Project

function Where() {
  return <div aria-label="Location">{useLocation().pathname}</div>
}

describe('the left menu', () => {
  it('leads to the overview, object inventory, API spec, generated code and diagrams', async () => {
    const user = userEvent.setup()
    renderWithProviders(<Routes><Route path="*" element={<><Sidebar projectId="p" /><Where /></>} /></Routes>, [
      [['projects', 'p'], project], [['projects', 'p', 'endpoints'], []], [['projects', 'p', 'payloads'], []],
    ])
    const menu = screen.getByRole('list', { name: 'Project menu' })
    expect(within(menu).getAllByRole('button').map((b) => b.textContent)).toEqual(['Overview & settings', 'Capabilities', 'Field inventory', 'Object inventory', 'Error codes', 'API spec', 'Generated code', 'Diagrams'])
    await user.click(within(menu).getByRole('button', { name: 'Diagrams' }))
    expect(screen.getByLabelText('Location')).toHaveTextContent('/projects/p/diagrams')
    expect(within(menu).getByRole('button', { name: 'Diagrams' })).toHaveClass('Mui-selected')
    await user.click(within(menu).getByRole('button', { name: 'Generated code' }))
    expect(screen.getByLabelText('Location')).toHaveTextContent('/projects/p/code')
    await user.click(within(menu).getByRole('button', { name: 'Object inventory' }))
    expect(screen.getByLabelText('Location')).toHaveTextContent('/projects/p/inventory')
  })
})
