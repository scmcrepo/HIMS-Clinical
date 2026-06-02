import { format, parseISO } from 'date-fns'

export const DATE_FORMAT     = 'dd-MM-yyyy'
export const DATETIME_FORMAT = 'dd/MM/yyyy hh:mm a'

export function formatDate(value: string | Date | null | undefined): string {
  if (!value) return '—'
  try {
    const date = typeof value === 'string' ? parseISO(value) : value
    return format(date, DATE_FORMAT)
  } catch { return '—' }
}

export function formatDateTime(value: string | Date | null | undefined): string {
  if (!value) return '—'
  try {
    const date = typeof value === 'string' ? parseISO(value) : value
    return format(date, DATETIME_FORMAT)
  } catch { return '—' }
}
