import type { ReactNode } from 'react'
import type { RuleType } from '../../api/types'
import type { RuleFormProps } from './inputs'
import { ConditionalForm, EnrichForm, ErrorHandlingForm, PersistForm, PublishForm, SplitForm, TransformForm, ValidateForm } from './forms'

/** The form component for each rule type. */
export const RULE_FORMS: Record<RuleType, (props: RuleFormProps) => ReactNode> = {
  VALIDATE: ValidateForm,
  TRANSFORM: TransformForm,
  ENRICH: EnrichForm,
  CONDITIONAL_ROUTE: ConditionalForm,
  PERSIST: PersistForm,
  PUBLISH_EVENT: PublishForm,
  SPLIT_AGGREGATE: SplitForm,
  ERROR_HANDLING: ErrorHandlingForm,
}
