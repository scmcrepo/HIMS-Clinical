import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, Archive, Loader2, ShieldOff, Trash2 } from 'lucide-react'

import { Modal } from '../../../components/ui/Modal'
import { cn } from '../../../lib/utils'
import { complianceApi } from '../../../services/compliance/complianceApi'
import type { TargetOutcome } from '../../../types/compliance'

interface Props {
  requestId: string
  onClose: () => void
}

const OUTCOME_STYLE: Record<
  TargetOutcome['outcome'],
  { label: string; className: string; Icon: typeof Trash2 }
> = {
  ERASED: {
    label: 'Erased',
    className: 'bg-red-100 text-red-800',
    Icon: Trash2,
  },
  ANONYMISED: {
    label: 'Anonymised',
    className: 'bg-amber-100 text-amber-800',
    Icon: ShieldOff,
  },
  RETAINED: {
    label: 'Retained',
    className: 'bg-slate-100 text-slate-700',
    Icon: Archive,
  },
  FAILED: {
    label: 'Failed',
    className: 'bg-red-200 text-red-900',
    Icon: AlertTriangle,
  },
  PENDING: {
    label: 'Not processed',
    className: 'bg-yellow-100 text-yellow-800',
    Icon: AlertTriangle,
  },
}

/**
 * The per-store outcome of an erasure.
 *
 * <p>This is the evidence the erasure was real, and the account of anything kept.
 * A refusal to erase a clinical record is only lawful if the patient is told it
 * happened and why, so `retainedReason` is shown rather than tucked into a log —
 * the operator reading this screen is the one who will relay it.
 *
 * <p>FAILED and PENDING targets are surfaced prominently. A partially completed
 * erasure that looks complete is the worst outcome available here: the hospital
 * would tell the patient their data was cleared while copies remained.
 */
export default function ErasureReceiptModal({ requestId, onClose }: Props) {
  const { data, isLoading } = useQuery({
    queryKey: ['rights-receipt', requestId],
    queryFn: () => complianceApi.get(requestId),
  })

  const incomplete = data
    ? data.targets.filter(t => t.outcome === 'FAILED' || t.outcome === 'PENDING')
    : []

  return (
    <Modal
      isOpen
      onClose={onClose}
      size="2xl"
      title="Erasure receipt"
      description="What was cleared, what was kept, and why."
    >
      {isLoading || !data ? (
        <div className="flex items-center gap-2 p-8 text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading…
        </div>
      ) : (
        <div className="space-y-4">
          <div className="grid grid-cols-4 gap-2 text-center">
            {[
              { n: data.erased, label: 'Erased' },
              { n: data.anonymised, label: 'Anonymised' },
              { n: data.retained, label: 'Retained' },
              { n: data.failed, label: 'Failed' },
            ].map(s => (
              <div
                key={s.label}
                className={cn(
                  'rounded-md border p-3',
                  s.label === 'Failed' && s.n > 0
                    ? 'border-red-300 bg-red-50'
                    : 'border-slate-200 bg-slate-50',
                )}
              >
                <div className="text-2xl font-semibold text-slate-900">{s.n}</div>
                <div className="text-xs text-slate-600">{s.label}</div>
              </div>
            ))}
          </div>

          {incomplete.length > 0 && (
            <div className="flex items-start gap-3 rounded-md border border-red-300 bg-red-50 p-3">
              <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-red-600" />
              <div className="text-sm text-red-900">
                <p className="font-medium">This erasure is not complete.</p>
                <p>
                  {incomplete.length} store(s) were not processed. Do not tell the
                  patient their data has been erased until this is resolved.
                </p>
              </div>
            </div>
          )}

          {data.request.retainedReason && (
            <div className="rounded-md border border-slate-200 bg-slate-50 p-3">
              <p className="mb-1 text-xs font-medium uppercase text-slate-500">
                What was kept, and why — tell the patient this
              </p>
              <p className="text-sm text-slate-800">{data.request.retainedReason}</p>
            </div>
          )}

          <div className="max-h-80 overflow-y-auto rounded-md border border-slate-200">
            <table className="w-full text-sm">
              <thead className="sticky top-0 bg-slate-50 text-left text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-3 py-2">Store</th>
                  <th className="px-3 py-2">Outcome</th>
                  <th className="px-3 py-2">Rows</th>
                  <th className="px-3 py-2">Detail</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.targets.map(t => {
                  const style = OUTCOME_STYLE[t.outcome]
                  return (
                    <tr key={t.store}>
                      <td className="px-3 py-2 font-mono text-xs text-slate-700">
                        {t.store}
                      </td>
                      <td className="px-3 py-2">
                        <span
                          className={cn(
                            'inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium',
                            style.className,
                          )}
                        >
                          <style.Icon className="h-3 w-3" />
                          {style.label}
                        </span>
                      </td>
                      <td className="px-3 py-2 text-slate-600">
                        {t.rowsAffected ?? '—'}
                      </td>
                      <td className="px-3 py-2 text-xs text-slate-500">
                        {t.detail ?? '—'}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          <p className="text-xs text-slate-500">
            Request {data.request.id} · raised{' '}
            {new Date(data.request.requestedAt).toLocaleString()} · verified by{' '}
            {data.request.verificationMethod ?? '—'}
          </p>
        </div>
      )}
    </Modal>
  )
}
