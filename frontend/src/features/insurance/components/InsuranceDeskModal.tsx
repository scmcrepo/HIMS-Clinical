import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { billingApi } from '../../../services/billing/billingApi'
import { insuranceApi } from '../../../services/insurance/insuranceApi'
import { toast } from '../../../hooks/useToast'
import { cn } from '../../../lib/utils'
import { formatPaise, outstandingAgainstLimit, type InsuranceDesk, type TimelineStepKey } from '../insuranceDesk'
import { StageTimeline } from './StageTimeline'
import { PreauthApprovalStageForm, PreauthStageForm } from './PreauthStages'
import { EnhancementApprovalStageForm, EnhancementStageForm } from './EnhancementStages'
import { ChecklistStageForm, DispatchStageForm, DisallowanceStageForm } from './SettlementStages'
import { Banner } from './formPrimitives'

/**
 * Pick the patient's credit bill to bind to the claim.
 *
 * Lists the patient's bills rather than asking for an id: nobody at a desk knows
 * a bill UUID, and the previous flow's silent failure meant the link never
 * happened at all.
 */
export function LinkBillModal({
  desk,
  onClose,
  onLinked,
}: {
  desk: InsuranceDesk
  onClose: () => void
  onLinked: (updated: InsuranceDesk) => void
}) {
  const { data: bills, isLoading } = useQuery({
    queryKey: ['bills', 'patient', desk.patientId],
    queryFn: () => billingApi.getBillsByPatient(desk.patientId!),
    enabled: Boolean(desk.patientId),
  })

  const link = useMutation({
    mutationFn: (billId: string) => insuranceApi.linkBill(desk.id, billId),
    onSuccess: updated => {
      toast({ title: 'Bill linked', variant: 'success' })
      onLinked(updated)
      onClose()
    },
    onError: (e: Error) =>
      toast({ title: 'Could not link bill', description: e.message, variant: 'destructive' }),
  })

  return (
    <div
      className="fixed inset-0 z-[60] flex items-center justify-center bg-black/30 backdrop-blur-sm p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="link-bill-title"
    >
      <div className="bg-white rounded-2xl border border-gray-200 shadow-xl w-full max-w-2xl max-h-[80vh] flex flex-col">
        <div className="px-6 py-4 border-b border-gray-100">
          <h3 id="link-bill-title" className="font-semibold text-gray-900">
            Link a bill
          </h3>
          <p className="text-xs text-gray-500 mt-0.5">
            The claim needs a bill before an enhancement can be raised or charges deducted.
          </p>
        </div>

        <div className="flex-1 overflow-y-auto px-6 py-4">
          {!desk.patientId && (
            <Banner tone="danger">
              This insurance record has no patient attached, so there are no bills to choose from.
            </Banner>
          )}
          {isLoading && <p className="text-sm text-gray-400">Loading bills…</p>}
          {bills && bills.length === 0 && (
            <p className="text-sm text-gray-400 py-8 text-center">
              This patient has no bills yet. Raise the credit bill in Billing first.
            </p>
          )}
          {bills && bills.length > 0 && (
            <ul className="divide-y divide-gray-100">
              {bills.map(b => (
                <li key={b.id}>
                  <button
                    onClick={() => link.mutate(b.id)}
                    disabled={link.isPending}
                    className={cn(
                      'w-full text-left px-3 py-3 rounded-lg hover:bg-gray-50 transition-colors flex items-center justify-between gap-4',
                      desk.billId === b.id && 'bg-neutral-50',
                    )}
                  >
                    <span>
                      <span className="block text-sm font-medium text-gray-900">
                        {b.billNumber ?? 'Draft bill'}
                      </span>
                      <span className="block text-xs text-gray-500">
                        {b.billDate ?? '—'} · {b.encounterType ?? '—'}
                      </span>
                    </span>
                    <span className="text-sm font-medium text-gray-800 shrink-0">
                      {formatPaise(b.billAmount ?? 0)}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="px-6 py-4 border-t border-gray-100 flex justify-end">
          <button
            onClick={onClose}
            className="px-4 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-gray-50"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  )
}

/**
 * The desk itself — timeline on the left, the selected stage on the right.
 *
 * All seven stages in one modal rather than seven screens: the clerk works a
 * claim in one sitting, moving between stages as the TPA replies, and a
 * page-per-stage flow means losing the claim's context on every hop.
 *
 * Every stage submission returns the whole desk payload, so `desk` is replaced
 * outright on success. Partial merges here would let the timeline disagree with
 * the form that just saved.
 */
export function InsuranceDeskModal({
  insuranceId,
  onClose,
}: {
  insuranceId: string
  onClose: () => void
}) {
  const qc = useQueryClient()
  const [step, setStep] = useState<TimelineStepKey>('preauth')
  const [linkingBill, setLinkingBill] = useState(false)

  const { data: desk, isLoading } = useQuery({
    queryKey: ['insurance', 'desk', insuranceId],
    queryFn: () => insuranceApi.getDesk(insuranceId),
  })

  const applyUpdate = (updated: InsuranceDesk) => {
    qc.setQueryData(['insurance', 'desk', insuranceId], updated)
    // The landing grid shows the stage column, so it is now stale.
    qc.invalidateQueries({ queryKey: ['insurance', 'search'] })
  }

  const stageMutation = useMutation({
    mutationFn: (fn: () => Promise<InsuranceDesk>) => fn(),
    onSuccess: updated => {
      applyUpdate(updated)
      toast({ title: 'Saved', variant: 'success' })
    },
    onError: (e: Error) =>
      toast({ title: 'Could not save', description: e.message, variant: 'destructive' }),
  })

  const run = (fn: () => Promise<InsuranceDesk>) => stageMutation.mutate(fn)
  const saving = stageMutation.isPending

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 backdrop-blur-sm p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="desk-title"
    >
      <div className="bg-white rounded-2xl border border-gray-200 shadow-xl w-full max-w-5xl max-h-[92vh] flex flex-col">
        {isLoading || !desk ? (
          <div className="p-10 text-center text-sm text-gray-400" aria-live="polite">
            Loading claim…
          </div>
        ) : (
          <>
            {/* Header: the facts a clerk needs on screen at all times. */}
            <div className="px-6 py-4 border-b border-gray-100 flex items-start justify-between gap-4">
              <div className="min-w-0">
                <h3 id="desk-title" className="font-semibold text-gray-900 truncate">
                  {desk.insurerName}
                  {desk.tpaName && (
                    <span className="text-gray-400 font-normal"> · {desk.tpaName}</span>
                  )}
                </h3>
                <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mt-1 text-xs text-gray-500">
                  {desk.claimNo && <span>Claim {desk.claimNo}</span>}
                  {desk.policyNumber && <span>Policy {desk.policyNumber}</span>}
                  {desk.currentStageLabel && (
                    <span className="px-2 py-0.5 rounded-full bg-neutral-100 text-neutral-700 font-medium">
                      {desk.currentStageLabel}
                    </span>
                  )}
                </div>
              </div>
              <div className="flex items-center gap-2 shrink-0">
                <button
                  onClick={() => setLinkingBill(true)}
                  className={cn(
                    'px-3 py-1.5 text-xs font-medium rounded-lg border transition-colors',
                    desk.billLinked
                      ? 'border-gray-200 text-gray-600 hover:bg-gray-50'
                      : 'border-amber-300 bg-amber-50 text-amber-800 hover:bg-amber-100',
                  )}
                >
                  {desk.billLinked ? 'Change bill' : 'Link bill'}
                </button>
                <button
                  onClick={onClose}
                  className="px-3 py-1.5 text-xs text-gray-500 rounded-lg hover:bg-gray-50"
                  aria-label="Close claim"
                >
                  Close
                </button>
              </div>
            </div>

            {/* Money summary — the question every desk asks first. */}
            {(desk.effectiveApprovedLimit != null || desk.totalReceived > 0) && (
              <div className="px-6 py-2.5 bg-gray-50 border-b border-gray-100 flex flex-wrap gap-x-8 gap-y-1 text-xs">
                <span className="text-gray-500">
                  Sanctioned:{' '}
                  <span className="font-semibold text-gray-800">
                    {formatPaise(desk.effectiveApprovedLimit)}
                  </span>
                </span>
                <span className="text-gray-500">
                  Received:{' '}
                  <span className="font-semibold text-gray-800">
                    {formatPaise(desk.totalReceived)}
                  </span>
                </span>
                {outstandingAgainstLimit(desk) != null && (
                  <span className="text-gray-500">
                    Outstanding:{' '}
                    <span className="font-semibold text-gray-800">
                      {formatPaise(outstandingAgainstLimit(desk))}
                    </span>
                  </span>
                )}
              </div>
            )}

            <div className="flex-1 overflow-hidden flex">
              <div className="p-4 overflow-y-auto">
                <StageTimeline desk={desk} activeStep={step} onSelect={setStep} />
              </div>

              <div className="flex-1 overflow-y-auto p-6">
                {step === 'preauth' && (
                  <PreauthStageForm
                    desk={desk}
                    saving={saving}
                    onSave={cmd => run(() => insuranceApi.submitPreauth(desk.id, cmd))}
                  />
                )}
                {step === 'preauthApproval' && (
                  <PreauthApprovalStageForm
                    desk={desk}
                    saving={saving}
                    onSave={cmd => run(() => insuranceApi.submitPreauthApproval(desk.id, cmd))}
                  />
                )}
                {step === 'enhancement' && (
                  <EnhancementStageForm
                    desk={desk}
                    saving={saving}
                    onGoToBillLink={() => setLinkingBill(true)}
                    onSave={cmd => run(() => insuranceApi.submitEnhancement(desk.id, cmd))}
                  />
                )}
                {step === 'enhancementApproval' && (
                  <EnhancementApprovalStageForm
                    desk={desk}
                    saving={saving}
                    onSave={cmd => run(() => insuranceApi.submitEnhancementApproval(desk.id, cmd))}
                  />
                )}
                {step === 'checkList' && (
                  <ChecklistStageForm
                    desk={desk}
                    saving={saving}
                    onSave={items => run(() => insuranceApi.submitChecklist(desk.id, items))}
                  />
                )}
                {step === 'dispatch' && (
                  <DispatchStageForm
                    desk={desk}
                    saving={saving}
                    onSave={cmd => run(() => insuranceApi.submitDispatch(desk.id, cmd))}
                  />
                )}
                {step === 'disallowance' && (
                  <DisallowanceStageForm
                    desk={desk}
                    saving={saving}
                    onSave={cmd => run(() => insuranceApi.submitDisallowance(desk.id, cmd))}
                  />
                )}
              </div>
            </div>

            {linkingBill && (
              <LinkBillModal
                desk={desk}
                onClose={() => setLinkingBill(false)}
                onLinked={applyUpdate}
              />
            )}
          </>
        )}
      </div>
    </div>
  )
}
