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
      <div className="flex flex-col max-h-[88vh]">
        {/* Modal Header */}
        <div className="px-6 pt-6 pb-4 border-b border-slate-100 pr-12 shrink-0">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-red-50 border border-red-100 flex items-center justify-center text-red-600 shrink-0">
              <Trash2 className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900 leading-tight">Erasure Receipt</h2>
              <p className="text-xs text-slate-500 mt-0.5">
                What was cleared, what was kept, and why under statutory obligations.
              </p>
            </div>
          </div>
        </div>

        {/* Modal Content */}
        {isLoading || !data ? (
          <div className="flex items-center justify-center gap-2 p-12 text-slate-500">
            <Loader2 className="h-5 w-5 animate-spin text-blue-600" />
            <span className="text-sm font-medium">Loading erasure receipt…</span>
          </div>
        ) : (
          <div className="p-6 space-y-5 overflow-y-auto flex-1">
            {/* Outcome Stats */}
            <div className="grid grid-cols-4 gap-3 text-center">
              {[
                { n: data.erased, label: 'Erased', bg: 'bg-red-50 border-red-200 text-red-900', numColor: 'text-red-700' },
                { n: data.anonymised, label: 'Anonymised', bg: 'bg-amber-50 border-amber-200 text-amber-900', numColor: 'text-amber-700' },
                { n: data.retained, label: 'Retained', bg: 'bg-slate-50 border-slate-200 text-slate-900', numColor: 'text-slate-800' },
                { n: data.failed, label: 'Failed', bg: data.failed > 0 ? 'bg-red-100 border-red-300 text-red-900' : 'bg-slate-50 border-slate-200 text-slate-900', numColor: data.failed > 0 ? 'text-red-700' : 'text-slate-800' },
              ].map(s => (
                <div
                  key={s.label}
                  className={cn(
                    'rounded-xl border p-3.5 transition-all shadow-sm',
                    s.bg,
                  )}
                >
                  <div className={cn("text-2xl font-bold tracking-tight", s.numColor)}>{s.n}</div>
                  <div className="text-xs font-medium text-slate-600 mt-0.5">{s.label}</div>
                </div>
              ))}
            </div>

            {incomplete.length > 0 && (
              <div className="flex items-start gap-3 rounded-xl border border-red-300 bg-red-50 p-4">
                <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-red-600" />
                <div className="text-sm text-red-900">
                  <p className="font-semibold">This erasure is not complete.</p>
                  <p className="mt-0.5">
                    {incomplete.length} store(s) were not processed. Do not tell the
                    patient their data has been erased until this is resolved.
                  </p>
                </div>
              </div>
            )}

            {data.request.retainedReason && (
              <div className="rounded-xl border border-slate-200 bg-slate-50/70 p-4">
                <p className="mb-1 text-xs font-bold uppercase tracking-wider text-slate-500">
                  What was kept, and why — tell the patient this
                </p>
                <p className="text-sm text-slate-800 leading-relaxed">{data.request.retainedReason}</p>
              </div>
            )}

            {/* Target Stores Table */}
            <div className="rounded-xl border border-slate-200 overflow-hidden shadow-sm">
              <div className="max-h-72 overflow-y-auto">
                <table className="w-full text-sm">
                  <thead className="sticky top-0 bg-slate-100/90 backdrop-blur-xs text-left text-xs uppercase text-slate-600 border-b border-slate-200">
                    <tr>
                      <th className="px-4 py-2.5 font-semibold">Store</th>
                      <th className="px-4 py-2.5 font-semibold">Outcome</th>
                      <th className="px-4 py-2.5 font-semibold">Rows</th>
                      <th className="px-4 py-2.5 font-semibold">Detail</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 bg-white">
                    {data.targets.map(t => {
                      const style = OUTCOME_STYLE[t.outcome]
                      return (
                        <tr key={t.store} className="hover:bg-slate-50/60 transition-colors">
                          <td className="px-4 py-2.5 font-mono text-xs font-medium text-slate-700">
                            {t.store}
                          </td>
                          <td className="px-4 py-2.5">
                            <span
                              className={cn(
                                'inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-semibold shadow-2xs',
                                style.className,
                              )}
                            >
                              <style.Icon className="h-3 w-3" />
                              {style.label}
                            </span>
                          </td>
                          <td className="px-4 py-2.5 text-slate-600 font-mono text-xs">
                            {t.rowsAffected ?? '—'}
                          </td>
                          <td className="px-4 py-2.5 text-xs text-slate-500">
                            {t.detail ?? '—'}
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            </div>

            <p className="text-xs text-slate-500">
              Request <span className="font-mono text-slate-700">{data.request.id}</span> · raised{' '}
              {new Date(data.request.requestedAt).toLocaleString()} · verified by{' '}
              <span className="font-medium text-slate-700">{data.request.verificationMethod ?? '—'}</span>
            </p>
          </div>
        )}

        {/* Modal Footer */}
        <div className="px-6 py-4 bg-slate-50 border-t border-slate-100 flex items-center justify-between shrink-0">
          <span className="text-xs text-slate-500 font-medium">DPDP Act 2023 · Right to Erasure Receipt</span>
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 bg-white border border-slate-300 hover:bg-slate-100 text-slate-700 rounded-lg text-sm font-medium transition-colors shadow-2xs cursor-pointer"
          >
            Close Receipt
          </button>
        </div>
      </div>
    </Modal>
  )
}
