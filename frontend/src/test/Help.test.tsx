import { useState, type ReactNode } from 'react'
import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TextField } from '@mui/material'
import { Route, Routes } from 'react-router'
import { fieldHelp, screenHelp } from '../lib/help'
import { FieldHelpLayer, fieldUnder } from '../components/help/FieldHelpLayer'
import { setFieldHelpOn } from '../components/help/fieldHelpStore'
import { ScreenHelp } from '../components/help/ScreenHelp'
import { EndpointDefinition } from '../editors/EndpointDefinition'
import { RequestResponse } from '../editors/RequestResponse'
import { PayloadEditor } from '../editors/PayloadEditor'
import { FieldConstraints } from '../editors/FieldConstraints'
import { RuleDialog } from '../editors/rules/RuleDialog'
import { ErrorCodeDialog } from '../components/errors/ErrorCodes'
import { ProjectDialog } from '../components/ProjectDialog'
import { ImportDialog } from '../components/ImportDialog'
import { FieldDefinitionDialog } from '../components/inventory/FieldDefinitionDialog'
import { CapabilitiesPage } from '../pages/CapabilitiesPage'
import { defaultConfig } from '../lib/rules'
import type { EndpointRequest, Field, Payload, PayloadRequest, RuleType } from '../api/types'
import { META, renderWithProviders } from './render'

afterEach(() => setFieldHelpOn(true))

/** Every field on screen (text fields, selects, checkboxes, switches, toggle groups) and its label. */
function labelsOnScreen(): string[] {
  const controls = document.querySelectorAll('.MuiFormControl-root, .MuiFormControlLabel-root, .MuiCheckbox-root, .MuiSwitch-root, .MuiToggleButtonGroup-root[aria-label]')
  const labels = new Set<string>()
  controls.forEach((c) => {
    const found = fieldUnder(c.querySelector('input, textarea, [role="combobox"], button, span') ?? c)
    if (found) labels.add(found.label.replace(/\s*\*\s*$/, ''))
  })
  return [...labels]
}

function missingHelp(render: () => void): string[] {
  render()
  const missing = labelsOnScreen().filter((l) => !fieldHelp(l))
  cleanup()
  return missing
}

const order: Payload = {
  id: 'o', projectId: 'p', name: 'Order', role: 'STRUCTURE', fields: [
    { name: 'total', type: 'DECIMAL', required: true, enumValues: [] },
    { name: 'items', type: 'ARRAY', itemType: 'OBJECT', refPayloadId: 'o', required: false, enumValues: [] },
  ],
}

function EditorPair({ initial }: { initial: EndpointRequest }) {
  const [value, setValue] = useState(initial)
  return (
    <>
      <EndpointDefinition value={value} onChange={setValue} allTags={[]} error={null} />
      <RequestResponse value={value} onChange={setValue} payloads={[order]} error={null} />
    </>
  )
}

function Constraints({ field }: { field: Field }) {
  const [f, setF] = useState(field)
  return <FieldConstraints field={f} onChange={(p) => setF({ ...f, ...p })} err={() => undefined} />
}

const routed = (ui: ReactNode, path = '/projects/p') =>
  renderWithProviders(<Routes><Route path="/projects/:projectId/*" element={ui} /></Routes>, [
    [['projects', 'p', 'error-codes'], []], [['projects', 'p', 'field-definitions'], []], [['projects', 'p', 'capabilities'], []],
    [['projects', 'p', 'capabilities', 'levels'], { levels: ['Business domain', 'Capability area', 'Capability', 'Sub-capability', 'Function', 'Process', 'Activity', 'Task'] }],
    [['projects', 'p', 'endpoints'], []], [['projects', 'p', 'payloads'], [order]],
  ], path)

describe('Context help', () => {
  it('finds field help by label, ignoring case and the required star, and by pattern', () => {
    expect(fieldHelp('Pattern (regex) *')).toMatch(/regular expression/)
    expect(fieldHelp('PARENT CAPABILITY')).toMatch(/8 levels/)
    expect(fieldHelp('Field 12 name')).toMatch(/lowerCamelCase/)
    expect(fieldHelp('L3 name')).toMatch(/capability model/)
    expect(fieldHelp('Nothing like this')).toBeUndefined()
    expect(screenHelp('capabilities')?.summary).toMatch(/L1/)
  })

  it('shows what a field is for on hover and focus, unless field tips are off', async () => {
    const user = userEvent.setup()
    renderWithProviders(<><TextField label="Pattern (regex)" /><TextField label="Unknown thing" /><FieldHelpLayer /></>)
    await user.hover(screen.getByLabelText('Pattern (regex)'))
    const tip = await screen.findByRole('tooltip', { name: 'Field help' })
    expect(tip).toHaveTextContent('Pattern (regex)')
    expect(tip).toHaveTextContent('regular expression')
    await user.unhover(screen.getByLabelText('Pattern (regex)'))
    expect(screen.queryByRole('tooltip', { name: 'Field help' })).not.toBeInTheDocument()

    await user.hover(screen.getByLabelText('Unknown thing'))
    await new Promise((r) => setTimeout(r, 600))
    expect(screen.queryByRole('tooltip', { name: 'Field help' })).not.toBeInTheDocument()

    setFieldHelpOn(false)
    await user.click(screen.getByLabelText('Pattern (regex)'))
    await new Promise((r) => setTimeout(r, 600))
    expect(screen.queryByRole('tooltip', { name: 'Field help' })).not.toBeInTheDocument()
  })

  it('explains a screen: a tooltip on hover, and a panel on click', async () => {
    const user = userEvent.setup()
    renderWithProviders(<ScreenHelp screen="capabilities" />)
    await user.hover(screen.getByRole('button', { name: 'Help: Capabilities' }))
    expect(await screen.findByRole('tooltip')).toHaveTextContent('functional capability model')
    await user.click(screen.getByRole('button', { name: 'Help: Capabilities' }))
    expect(await screen.findByText('What you can do')).toBeInTheDocument()
    expect(screen.getByText(/Link APIs… to choose the APIs/)).toBeInTheDocument()
    await user.click(screen.getByText('Turn field tips off'))
    expect(screen.getByText('Turn field tips on')).toBeInTheDocument()
  })

  it('has a help page for every screen', () => {
    for (const id of ['home', 'overview', 'capabilities', 'fields', 'inventory', 'errors', 'spec', 'code', 'diagrams', 'payload',
      'endpoint-definition', 'endpoint-io', 'endpoint-rules', 'endpoint-implementation', 'endpoint-diagrams']) {
      expect(screenHelp(id), id).toBeDefined()
    }
  })

  it('has help for every field of the editors and dialogs', async () => {
    const missing = new Set<string>()
    const add = (labels: string[]) => labels.forEach((l) => missing.add(l))

    add(missingHelp(() => renderWithProviders(<EditorPair initial={{
      kind: 'REST', name: 'getOrder', tags: [], httpMethod: 'GET', path: '/orders/{orderId}', requestPayloadId: 'o',
      parameters: [{ name: 'orderId', location: 'PATH', type: 'UUID', required: true }],
      responses: [{ statusCode: '200', description: 'OK', payloadId: 'o' }], security: [],
    }} />)))
    add(missingHelp(() => renderWithProviders(<EditorPair initial={{
      kind: 'ASYNC', name: 'orderCreated', tags: [], channel: 'orders.created', operation: 'SEND', protocol: 'KAFKA', messagePayloadId: 'o',
      parameters: [{ name: 'traceId', location: 'HEADER', type: 'STRING', required: false }], responses: [], security: [],
    }} />)))
    add(missingHelp(() => routed(<PayloadEditor value={{ name: 'Order', fields: order.fields, role: 'STRUCTURE' } as PayloadRequest}
      onChange={() => {}} payloads={[order]} selfId="o" error={null} />)))
    for (const field of [
      { name: 's', type: 'STRING', required: false, enumValues: [] },
      { name: 'n', type: 'DECIMAL', required: false, enumValues: [] },
      { name: 'a', type: 'ARRAY', itemType: 'STRING', required: false, enumValues: [] },
    ] as Field[]) add(missingHelp(() => routed(<Constraints field={field} />)))
    for (const type of META.ruleTypes.map((r) => r.type) as RuleType[]) {
      add(missingHelp(() => renderWithProviders(<RuleDialog open title="Add rule" onSubmit={async () => {}} onClose={() => {}}
        ctx={{ payloads: [order], current: order, otherRules: ['Enrich customer'], parameters: [], meta: META }}
        initial={{ type, name: '', condition: '', config: defaultConfig(type) }} />)))
    }
    add(missingHelp(() => routed(<ErrorCodeDialog projectId="p" open onClose={() => {}} />)))
    add(missingHelp(() => renderWithProviders(<ProjectDialog open onClose={() => {}} onCreated={() => {}} />)))
    add(missingHelp(() => renderWithProviders(<ImportDialog open onClose={() => {}} onImported={() => {}} />)))
    add(missingHelp(() => routed(<FieldDefinitionDialog projectId="p" open onClose={() => {}} />)))

    const user = userEvent.setup()
    routed(<CapabilitiesPage />, '/projects/p/capabilities')
    await user.click(screen.getByRole('button', { name: 'New L1 capability' }))
    add(labelsOnScreen().filter((l) => !fieldHelp(l)))
    cleanup()
    routed(<CapabilitiesPage />, '/projects/p/capabilities')
    await user.click(screen.getByRole('button', { name: 'Level names' }))
    add(labelsOnScreen().filter((l) => !fieldHelp(l)))

    expect([...missing].sort()).toEqual([])
  }, 30000)
})
