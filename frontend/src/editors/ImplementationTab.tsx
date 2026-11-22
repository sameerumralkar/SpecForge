import {
  Alert, Card, CardContent, CardHeader, FormControlLabel, Radio, RadioGroup, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography,
} from '@mui/material'
import { useRuleChain } from '../api/hooks'
import type { EndpointKind, ImplementationStyle, MessagingProtocol } from '../api/types'
import { RULE_COLORS, RULE_MAPPING, STYLE_LABEL } from '../lib/rules'

const OUTLINE: Record<ImplementationStyle, Record<EndpointKind, string>> = {
  IMPLEMENTATION_CLASS: {
    REST: 'Controller → Service interface + ServiceImpl (one method per endpoint, each rule an ordered private method) → DTOs → exception handler.',
    ASYNC: '@{listener} → Service interface + ServiceImpl with the rules as ordered private methods → DTOs.',
  },
  PROCESSING_ROUTE: {
    REST: 'Camel REST DSL → one RouteBuilder per endpoint with the rules as EIP steps → processors / beans → DTOs.',
    ASYNC: 'A Camel route from("{endpoint}:…") with the rules as EIP steps → processors / beans → DTOs.',
  },
}

/** Implementation tab: choose class vs route for this endpoint and see how each rule will be generated. */
const LISTENER: Record<MessagingProtocol, [string, string]> = {
  KAFKA: ['KafkaListener', 'kafka'],
  JMS: ['JmsListener', 'jms'],
  AMQP: ['RabbitListener', 'spring-rabbitmq'],
}

export function ImplementationTab({ projectId, endpointId, kind, protocol, value, projectDefault, onChange }: {
  projectId: string
  endpointId: string
  kind: EndpointKind
  protocol?: MessagingProtocol | null
  value: ImplementationStyle | null | undefined
  projectDefault: ImplementationStyle
  onChange: (style: ImplementationStyle | null) => void
}) {
  const chain = useRuleChain(projectId, endpointId)
  const effective = value ?? projectDefault
  const [listener, camel] = LISTENER[protocol ?? 'KAFKA']
  const outline = OUTLINE[effective][kind].replace('{listener}', listener).replace('{endpoint}', camel)
  return (
    <Stack spacing={3}>
      <Card variant="outlined">
        <CardContent>
          <RadioGroup value={value ?? 'DEFAULT'} onChange={(e) => onChange(e.target.value === 'DEFAULT' ? null : (e.target.value as ImplementationStyle))}>
            <FormControlLabel value="DEFAULT" control={<Radio />} label={`Project default (${STYLE_LABEL[projectDefault]})`} />
            <FormControlLabel value="IMPLEMENTATION_CLASS" control={<Radio />} label="Implementation class: plain Spring services" />
            <FormControlLabel value="PROCESSING_ROUTE" control={<Radio />} label="Processing route: Apache Camel routes" />
          </RadioGroup>
          <Alert severity="info" icon={false} sx={{ mt: 1 }}>
            <Typography variant="body2"><b>{STYLE_LABEL[effective]}:</b> {outline}</Typography>
          </Alert>
        </CardContent>
      </Card>
      <Card variant="outlined">
        <CardHeader title="How each rule is generated" slotProps={{ title: { variant: 'subtitle1' } }} />
        <Table>
          <TableHead>
            <TableRow>
              <TableCell sx={{ width: 40 }}>#</TableCell>
              <TableCell>Rule</TableCell>
              <TableCell>{STYLE_LABEL[effective]}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {chain.data?.rules.length === 0 && (
              <TableRow><TableCell colSpan={3}><Typography variant="body2" color="text.secondary">No rules on this endpoint.</Typography></TableCell></TableRow>
            )}
            {chain.data?.rules.map((r, i) => (
              <TableRow key={r.id}>
                <TableCell>{i + 1}</TableCell>
                <TableCell sx={{ borderLeft: 4, borderLeftColor: RULE_COLORS[r.type] }}>
                  {r.name} <Typography component="span" variant="body2" color="text.secondary">({r.typeLabel})</Typography>
                </TableCell>
                <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{RULE_MAPPING[r.type][effective]}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>
    </Stack>
  )
}
