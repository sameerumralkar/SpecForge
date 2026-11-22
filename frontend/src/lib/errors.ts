import { ApiError } from '../api/client'

/** The server's message(s) for one form field, if any. */
export function errorText(error: unknown, field: string): string | undefined {
  return error instanceof ApiError ? error.errorFor(field) : undefined
}
