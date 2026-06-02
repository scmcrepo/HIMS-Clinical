import { cn } from '../../lib/utils'

interface Props { 
  amount: number; 
  className?: string; 
  negative?: boolean | undefined;
  hideDecimals?: boolean;
}

export function AmountDisplay({ amount, className, negative, hideDecimals = true }: Props) {
  const displayValue = new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: hideDecimals ? 0 : 2,
    minimumFractionDigits: hideDecimals ? 0 : 2,
  }).format(Math.round(amount / 100))

  return (
    <span className={cn('font-mono tabular-nums', negative && amount > 0 && 'text-red-600', className)}>
      {displayValue}
    </span>
  )
}
