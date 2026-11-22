import { createContext, useContext } from 'react'

/** Whether the right-hand spec preview is open; shared by the top bar and the project layout. */
export const PreviewContext = createContext<{ open: boolean; setOpen: (open: boolean) => void }>({ open: false, setOpen: () => {} })

export const usePreview = () => useContext(PreviewContext)
