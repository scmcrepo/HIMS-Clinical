import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { billingApi } from '../../../services/billing/billingApi'
import { insuranceApi } from '../../../services/insurance/insuranceApi'
import { toast } from '../../../hooks/useToast'
import {
  formatPaise,
  outstandingAgainstLimit,
  type InsuranceDesk,
  type TimelineStepKey,
  type WorkflowStage,
} from '../insuranceDesk'
import { StageTimeline } from './StageTimeline'
import { PreauthApprovalStageForm, PreauthStageForm } from './PreauthStages'
import { EnhancementApprovalStageForm, EnhancementStageForm } from './EnhancementStages'
import { ChecklistStageForm, DispatchStageForm, DisallowanceStageForm } from './SettlementStages'

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
  const navigate = useNavigate()
  const [step, setStep] = useState<TimelineStepKey>('preauth')
  const [hasInitializedStep, setHasInitializedStep] = useState(false)

  const { data: desk, isLoading } = useQuery({
    queryKey: ['insurance', 'desk', insuranceId],
    queryFn: () => insuranceApi.getDesk(insuranceId),
  })

  useEffect(() => {
    if (desk && !hasInitializedStep) {
      if (desk.currentStage) {
        const stageToStep: Partial<Record<WorkflowStage, TimelineStepKey>> = {
          PREAUTHORISATION: 'preauth',
          PREAUTHORISATION_APPROVAL: 'preauthApproval',
          PREAUTHORISATION_REJECTED: 'preauthApproval',
          ENHANCEMENT_REQUEST: 'enhancement',
          ENHANCEMENT_APPROVAL: 'enhancementApproval',
          ENHANCEMENT_REJECTED: 'enhancementApproval',
          CHECK_LIST_ENTRY: 'checkList',
          DISPATCH_ENTRY: 'dispatch',
          DISALLOWANCE_ENTRY: 'disallowance',
        }
        const initial = stageToStep[desk.currentStage]
        if (initial) setStep(initial)
      }
      setHasInitializedStep(true)
    }
  }, [desk, hasInitializedStep])

  const applyUpdate = (updated: InsuranceDesk) => {
    qc.setQueryData(['insurance', 'desk', insuranceId], updated)
    // The landing grid shows the stage column, so it is now stale.
    qc.invalidateQueries({ queryKey: ['insurance', 'search'] })
  }

  const stageMutation = useMutation({
    mutationFn: async ({
      fn,
      nextStep,
    }: {
      fn: () => Promise<InsuranceDesk>
      nextStep?: TimelineStepKey
    }) => {
      const updated = await fn()
      return { updated, nextStep }
    },
    onSuccess: ({ updated, nextStep }) => {
      applyUpdate(updated)
      if (nextStep) {
        setStep(nextStep)
      }
      toast({ title: 'Saved', variant: 'success' })
    },
    onError: (e: Error) =>
      toast({ title: 'Could not save', description: e.message, variant: 'destructive' }),
  })

  const run = (fn: () => Promise<InsuranceDesk>, nextStep?: TimelineStepKey) =>
    stageMutation.mutate({ fn, nextStep })
  const saving = stageMutation.isPending

  // Auto-link: fetch patient's IP bills and link the first one automatically
  const autoLinkMutation = useMutation({
    mutationFn: async () => {
      if (!desk?.patientId) throw new Error('No patient attached to this claim.')
      const bills = await billingApi.getBillsByPatient(desk.patientId)
      const ipBills = bills.filter(b => b.encounterType === 'INPATIENT')
      if (ipBills.length === 0) throw new Error('No inpatient bills found for this patient. Create a credit bill in Billing first.')
      return insuranceApi.linkBill(desk.id, ipBills[0].id)
    },
    onSuccess: (updated) => {
      applyUpdate(updated)
      toast({ title: 'Bill linked', variant: 'success' })
    },
    onError: (e: Error) =>
      toast({ title: 'Could not link bill', description: e.message, variant: 'destructive' }),
  })

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 backdrop-blur-sm p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="desk-title"
    >
      <div className="bg-white rounded-2xl border border-gray-200 shadow-xl w-full max-w-7xl min-h-[85vh] max-h-[92vh] flex flex-col">
        {isLoading || !desk ? (
          <div className="p-10 text-center text-sm text-gray-400" aria-live="polite">
            Loading claim…
          </div>
        ) : (
          <>
            {/* Header: the facts a clerk needs on screen at all times. */}
            <div className="px-6 py-4 border-b border-gray-100 flex items-start justify-between gap-4">
              <div className="min-w-0 flex-1 space-y-2">
                {/* Patient Information */}
                {(desk.patientName || desk.patientNo) && (
                  <div className="flex flex-wrap items-center gap-x-3 gap-y-1 pb-2 border-b border-gray-100/80">
                    <span className="text-xs font-medium text-gray-400 uppercase tracking-wide">
                      Patient
                    </span>
                    <span className="text-sm font-semibold text-gray-900">
                      {desk.patientName ?? '—'}
                    </span>
                    {(desk.patientAge || desk.patientGender) && (
                      <span className="text-xs text-gray-500 font-medium">
                        ({' '}
                        {[
                          desk.patientAge,
                          desk.patientGender
                            ? desk.patientGender.charAt(0) +
                              desk.patientGender.slice(1).toLowerCase()
                            : null,
                        ]
                          .filter(Boolean)
                          .join(' / ')}{' '}
                        )
                      </span>
                    )}
                    {desk.patientNo && (
                      <span className="inline-flex items-center px-2 py-0.5 rounded bg-neutral-100 border border-neutral-300 text-neutral-800 text-xs font-semibold font-mono">
                        Patient ID: {desk.patientNo}
                      </span>
                    )}
                  </div>
                )}

                <div>
                  <h3 id="desk-title" className="font-semibold text-base text-gray-900 truncate">
                    {desk.insurerName}
                    {desk.tpaName && (
                      <span className="text-gray-400 font-normal"> · {desk.tpaName}</span>
                    )}
                  </h3>
                  <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mt-1 text-xs text-gray-500">
                    {desk.claimNo && (
                      <span>
                        Claim <span className="font-medium text-gray-700">{desk.claimNo}</span>
                      </span>
                    )}
                    {desk.policyNumber && (
                      <span>
                        Policy <span className="font-medium text-gray-700">{desk.policyNumber}</span>
                      </span>
                    )}
                    {desk.currentStageLabel && (
                      <span className="px-2 py-0.5 rounded-full bg-neutral-100 text-neutral-700 font-medium">
                        {desk.currentStageLabel}
                      </span>
                    )}
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-2 shrink-0">
                {desk.billLinked ? (
                  <button
                    onClick={() => {
                      if (desk.billId) {
                        onClose()
                        navigate(`/billing/${desk.billId}`, { state: { fromInsuranceClaimId: desk.id } })
                      }
                    }}
                    className="px-3 py-1.5 text-xs font-medium rounded-lg border border-neutral-300 bg-neutral-100 text-neutral-800 hover:bg-neutral-200 transition-colors flex items-center gap-1.5"
                  >
                    <span className="w-1.5 h-1.5 rounded-full bg-neutral-700" />
                    IP Bill
                  </button>
                ) : (
                  <button
                    onClick={() => autoLinkMutation.mutate()}
                    disabled={autoLinkMutation.isPending}
                    className="px-3 py-1.5 text-xs font-medium rounded-lg border border-neutral-300 bg-neutral-100 text-neutral-800 hover:bg-neutral-200 transition-colors flex items-center gap-1.5 disabled:opacity-50"
                  >
                    {autoLinkMutation.isPending ? 'Linking…' : 'Link bill'}
                  </button>
                )}
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
                    onSave={cmd =>
                      run(() => insuranceApi.submitPreauth(desk.id, cmd), 'preauthApproval')
                    }
                  />
                )}
                {step === 'preauthApproval' && (
                  <PreauthApprovalStageForm
                    desk={desk}
                    saving={saving}
                    onSave={cmd => {
                      const nextStep =
                        cmd.approvalStatus === 'REJECTED'
                          ? undefined
                          : desk.billLinked
                            ? 'enhancement'
                            : 'checkList'
                      run(() => insuranceApi.submitPreauthApproval(desk.id, cmd), nextStep)
                    }}
                  />
                )}
                {step === 'enhancement' && (
                  <EnhancementStageForm
                    desk={desk}
                    saving={saving}
                    onGoToBillLink={() => autoLinkMutation.mutate()}
                    onSave={cmd =>
                      run(
                        () => insuranceApi.submitEnhancement(desk.id, cmd),
                        'enhancementApproval',
                      )
                    }
                  />
                )}
                {step === 'enhancementApproval' && (
                  <EnhancementApprovalStageForm
                    desk={desk}
                    saving={saving}
                    onSave={cmd =>
                      run(
                        () => insuranceApi.submitEnhancementApproval(desk.id, cmd),
                        'checkList',
                      )
                    }
                  />
                )}
                {step === 'checkList' && (
                  <ChecklistStageForm
                    desk={desk}
                    saving={saving}
                    onSave={items =>
                      run(() => insuranceApi.submitChecklist(desk.id, items), 'dispatch')
                    }
                  />
                )}
                {step === 'dispatch' && (
                  <DispatchStageForm
                    desk={desk}
                    saving={saving}
                    onSave={cmd =>
                      run(() => insuranceApi.submitDispatch(desk.id, cmd), 'disallowance')
                    }
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


          </>
        )}
      </div>
    </div>
  )
}
