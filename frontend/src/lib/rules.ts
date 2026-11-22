import type { ImplementationStyle, RuleConfig, RuleType } from '../api/types'

/** A config that the backend accepts once the user fills in the blanks the form highlights. */
export function defaultConfig(type: RuleType): RuleConfig {
  switch (type) {
    case 'VALIDATE':
      return { checks: [{ field: '', kind: 'REQUIRED' }], failureStatus: 400 }
    case 'TRANSFORM':
      return { targetPayloadId: null, mappings: [] }
    case 'ENRICH':
      return { serviceName: '', httpMethod: 'GET', url: '', responsePayloadId: null, targetField: '', timeoutMs: 2000, mode: 'ENRICH' }
    case 'CONDITIONAL_ROUTE':
      return { branches: [{ name: '', condition: '', description: '' }], otherwise: '' }
    case 'PERSIST':
      return { entityName: '', operation: 'SAVE', idField: '' }
    case 'PUBLISH_EVENT':
      return { protocol: 'KAFKA', destination: '', eventPayloadId: null, keyField: '' }
    case 'SPLIT_AGGREGATE':
      return { collectionField: '', parallel: false, aggregation: 'COLLECT_LIST', sumField: '' }
    case 'ERROR_HANDLING':
      return { on: ['ANY'], targetRule: null, retry: { maxAttempts: 3, backoffMs: 500, multiplier: 2 }, fallback: null, deadLetterDestination: null }
  }
}

/** Drops empty strings so optional settings are sent as "not set". */
export function cleanConfig(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(cleanConfig)
  if (value && typeof value === 'object') {
    const out: Record<string, unknown> = {}
    for (const [k, v] of Object.entries(value)) {
      if (v === '' || v === undefined) continue
      out[k] = cleanConfig(v)
    }
    return out
  }
  return value
}

export const RULE_COLORS: Record<RuleType, string> = {
  VALIDATE: '#2e7d32',
  TRANSFORM: '#6a1b9a',
  ENRICH: '#0277bd',
  CONDITIONAL_ROUTE: '#ef6c00',
  PERSIST: '#4e342e',
  PUBLISH_EVENT: '#c62828',
  SPLIT_AGGREGATE: '#00838f',
  ERROR_HANDLING: '#546e7a',
}

/** How each rule type turns into code in each implementation style (the mapping Phase 4 generates). */
export const RULE_MAPPING: Record<RuleType, Record<ImplementationStyle, string>> = {
  VALIDATE: {
    IMPLEMENTATION_CLASS: 'Bean Validation on the DTO + a validator method',
    PROCESSING_ROUTE: '.validate() / bean-validator component',
  },
  TRANSFORM: {
    IMPLEMENTATION_CLASS: 'MapStruct mapper method',
    PROCESSING_ROUTE: '.process() / .bean(mapper) / .marshal()',
  },
  ENRICH: {
    IMPLEMENTATION_CLASS: 'RestClient call',
    PROCESSING_ROUTE: '.enrich() / .pollEnrich()',
  },
  CONDITIONAL_ROUTE: {
    IMPLEMENTATION_CLASS: 'if / else in the service method',
    PROCESSING_ROUTE: '.choice().when().otherwise()',
  },
  PERSIST: {
    IMPLEMENTATION_CLASS: 'Spring Data repository call',
    PROCESSING_ROUTE: '.to("jpa:…") or a bean call',
  },
  PUBLISH_EVENT: {
    IMPLEMENTATION_CLASS: 'KafkaTemplate / JmsTemplate / RabbitTemplate',
    PROCESSING_ROUTE: '.to("kafka:…") / .to("jms:…") / .to("spring-rabbitmq:…")',
  },
  SPLIT_AGGREGATE: {
    IMPLEMENTATION_CLASS: 'stream processing in the service',
    PROCESSING_ROUTE: '.split() / .aggregate()',
  },
  ERROR_HANDLING: {
    IMPLEMENTATION_CLASS: '@ControllerAdvice + Resilience4j @Retry',
    PROCESSING_ROUTE: 'onException() / errorHandler() / dead letter channel',
  },
}

export const STYLE_LABEL: Record<ImplementationStyle, string> = {
  IMPLEMENTATION_CLASS: 'Implementation class',
  PROCESSING_ROUTE: 'Processing route',
}
