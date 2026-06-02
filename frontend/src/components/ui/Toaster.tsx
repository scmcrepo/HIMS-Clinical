import { useToastState } from '../../hooks/useToast'
import { cn } from '../../lib/utils'
export function Toaster() {
  const { toasts } = useToastState()
  if (!toasts.length) return null
  return (
    <div className="fixed bottom-4 right-4 z-[100] flex flex-col gap-2 w-80">
      {toasts.map(t => (
        <div key={t.id} className={cn(
          'rounded-lg border px-4 py-3 shadow-lg text-sm font-medium transition-all',
          t.variant === 'success' && 'bg-green-50 border-green-200 text-green-800',
          t.variant === 'destructive' && 'bg-red-50 border-red-200 text-red-800',
          (!t.variant || t.variant === 'default') && 'bg-white border-gray-200 text-gray-900'
        )}>
          <p className="font-semibold">{t.title}</p>
          {t.description && <p className="text-xs mt-0.5 opacity-80">{t.description}</p>}
        </div>
      ))}
    </div>
  )
}
