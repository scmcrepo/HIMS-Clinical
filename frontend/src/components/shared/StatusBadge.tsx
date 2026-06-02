import { cn } from '../../lib/utils'
import type { BillStatus } from '../../types/billing'

const STATUS_STYLES: Record<BillStatus, string> = {
  DRAFT:      'bg-blue-50 text-blue-700 border-blue-200',
  SETTLED:    'bg-green-50 text-green-700 border-green-200',
  WITH_DUE:   'bg-amber-50 text-amber-700 border-amber-200',
  REFUNDED:   'bg-pink-50 text-pink-700 border-pink-200',
  CANCELLED:  'bg-gray-100 text-gray-600 border-gray-200',
}

const STATUS_LABELS: Record<BillStatus, string> = {
  DRAFT:      'Draft',
  SETTLED:    'Settled',
  WITH_DUE:   'With Due',
  REFUNDED:   'Refunded',
  CANCELLED:  'Cancelled',
}

export function BillStatusBadge({ status }: { status: BillStatus }) {
  return (
    <span className={cn('inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border', STATUS_STYLES[status])}>
      {STATUS_LABELS[status]}
    </span>
  )
}
