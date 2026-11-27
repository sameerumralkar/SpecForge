/** Help for a whole screen: what it is for, what you can do there, and tips. */
export interface ScreenHelpEntry {
  title: string
  /** One or two sentences: shown on hover, and at the top of the help panel. */
  summary: string
  sections: { heading: string; items: string[] }[]
  tips?: string[]
  /** The user guide section that covers the screen, e.g. "§ 8 Manage error codes". */
  guide?: string
}

/**
 * Help for fields, found by the label you see. {@code fields} are exact labels (case and a trailing "*" are
 * ignored); {@code patterns} cover numbered or generated labels such as "Field 3 name".
 */
export interface HelpBundle {
  screens?: Record<string, ScreenHelpEntry>
  fields?: Record<string, string>
  patterns?: [RegExp, string][]
}
