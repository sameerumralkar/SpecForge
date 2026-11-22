import { Autocomplete, Card, CardContent, Chip, MenuItem, Stack, TextField } from '@mui/material'
import type { AsyncOperation, EndpointRequest, HttpMethod, MessagingProtocol, SecurityScheme } from '../api/types'
import { errorText } from '../lib/errors'
import { syncPathParams } from '../lib/paths'
import { mono } from '../theme'

const METHODS: HttpMethod[] = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']
const SECURITY: SecurityScheme[] = ['BEARER_JWT', 'API_KEY', 'BASIC', 'OAUTH2']


/** Definition tab: name, text, tags and the route (method + path, or channel). */
export function EndpointDefinition({ value, onChange, allTags, error }: {
  value: EndpointRequest
  onChange: (value: EndpointRequest) => void
  allTags: string[]
  error: unknown
}) {
  const err = (f: string) => errorText(error, f)
  const set = <K extends keyof EndpointRequest>(key: K, v: EndpointRequest[K]) => onChange({ ...value, [key]: v })
  // Sync parameters when the user leaves the field, not per keystroke: half-typed paths would drop parameters and lose their settings.
  const syncParams = () => onChange({ ...value, parameters: syncPathParams(value.path, value.parameters) })

  return (
    <Stack spacing={3}>
      <Card variant="outlined">
        <CardContent>
          <Stack spacing={2}>
            <TextField label="Name (operation id)" value={value.name} onChange={(e) => set('name', e.target.value)}
              error={!!err('name')} helperText={err('name') ?? 'lowerCamelCase; becomes the Java method name'} />
            <TextField label="Summary" value={value.summary ?? ''} onChange={(e) => set('summary', e.target.value)}
              error={!!err('summary')} helperText={err('summary')} />
            <TextField label="Description" multiline minRows={3} value={value.description ?? ''} onChange={(e) => set('description', e.target.value)}
              error={!!err('description')} helperText={err('description')} />
            <Autocomplete multiple freeSolo options={allTags} value={value.tags} onChange={(_, tags) => set('tags', tags as string[])}
              renderValue={(tags, getItemProps) => tags.map((t, index) => <Chip label={t} {...getItemProps({ index })} key={t} />)}
              renderInput={(params) => <TextField {...params} label="Tags" helperText="The first tag groups the endpoint in the sidebar and the spec" />} />
          </Stack>
        </CardContent>
      </Card>

      <Card variant="outlined">
        <CardContent>
          {value.kind === 'REST' ? (
            <Stack spacing={2}>
              <Stack direction="row" spacing={2}>
                <TextField select label="Method" value={value.httpMethod ?? ''} sx={{ maxWidth: 150 }}
                  onChange={(e) => set('httpMethod', e.target.value as HttpMethod)} error={!!err('httpMethod')} helperText={err('httpMethod')}>
                  {METHODS.map((m) => <MenuItem key={m} value={m}>{m}</MenuItem>)}
                </TextField>
                <TextField label="Path" value={value.path ?? ''} onChange={(e) => set('path', e.target.value)} onBlur={syncParams}
                  slotProps={{ htmlInput: { style: { fontFamily: mono.fontFamily } } }}
                  error={!!err('path')} helperText={err('path') ?? 'Each {name} becomes a path parameter (see Request/Response)'} />
              </Stack>
              <Autocomplete multiple options={SECURITY} value={value.security} onChange={(_, v) => set('security', v)}
                renderInput={(params) => <TextField {...params} label="Security" helperText="Leave empty for a public endpoint" />} />
            </Stack>
          ) : (
            <Stack direction="row" spacing={2}>
              <TextField select label="Operation" value={value.operation ?? ''} sx={{ maxWidth: 200 }}
                onChange={(e) => set('operation', e.target.value as AsyncOperation)} error={!!err('operation')} helperText={err('operation')}>
                <MenuItem value="RECEIVE">RECEIVE (listen)</MenuItem>
                <MenuItem value="SEND">SEND (publish)</MenuItem>
              </TextField>
              <TextField label="Channel / topic" value={value.channel ?? ''} onChange={(e) => set('channel', e.target.value)}
                slotProps={{ htmlInput: { style: { fontFamily: mono.fontFamily } } }}
                error={!!err('channel')} helperText={err('channel')} />
              <TextField select label="Protocol" value={value.protocol ?? ''} sx={{ maxWidth: 160 }}
                onChange={(e) => set('protocol', e.target.value as MessagingProtocol)} error={!!err('protocol')} helperText={err('protocol')}>
                {(['KAFKA', 'JMS', 'AMQP'] as const).map((p) => <MenuItem key={p} value={p}>{p}</MenuItem>)}
              </TextField>
            </Stack>
          )}
        </CardContent>
      </Card>
    </Stack>
  )
}
