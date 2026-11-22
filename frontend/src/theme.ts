import { createTheme } from '@mui/material'

export const theme = createTheme({
  palette: {
    primary: { main: '#3949ab' },
    background: { default: '#f6f7fb' },
  },
  shape: { borderRadius: 6 },
  typography: { fontSize: 13 },
  components: {
    MuiTextField: { defaultProps: { size: 'small', fullWidth: true } },
    MuiSelect: { defaultProps: { size: 'small' } },
    MuiButton: { defaultProps: { size: 'small' } },
    MuiIconButton: { defaultProps: { size: 'small' } },
    MuiChip: { defaultProps: { size: 'small' } },
    MuiTable: { defaultProps: { size: 'small' } },
  },
})

export const mono = { fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace' }
