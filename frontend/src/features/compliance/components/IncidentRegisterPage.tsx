import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Loader2, Plus, ShieldAlert } from 'lucide-react'

import { Modal } from '../../../components/ui/Modal'
import { toast } from '../../../hooks/useToast'
import { cn } from '../../../lib/utils'
import { incidentApi } from '../../../services/compliance/complianceApi'
import type { IncidentState, SecurityIncident } from '../../../types/compliance'

const STATES: { value: IncidentState; label: string }[] = [
  { value: 'OPEN', label: 'Open' },
  { value: 'CONTAINED', label: 'Contained' },
  { value: 'NOTIFIED', label: 'Notified' },
  { value: 'CLOSED', label: 'Closed' },
  { value: 'DISMISSED', label: 'Dismissed' },
]

const SEVERITY_STYLE: Record<string, string> = {
  CRITICAL: 'bg-red-600 text-white',
  HIGH: 'bg-red-100 text-red-800',
  MEDIUM: 'bg-amber-100 text-amber-800',
  LOW: 'bg-slate-100 text-slate-700',
}

/**
 * The breach register — WO-026.
 *
 * <p>Until this screen existed the incident API was unreachable by staff, which
 * defeated the point of granting {@code INCIDENT_RAISE} to clinical and
 * reception roles: a near-miss nobody can file is a near-miss nobody learns
 * from. Reporting is therefore the primary action here, not a buried one.
 *
 * <p>The two Rule 7 clocks are shown separately, because the initial intimation
 * and the 72-hour report are missed independently and a single "notified" badge
 * cannot tell an operator which obligation is about to lapse.
 */
export default function IncidentRegisterPage() {
  const queryClient = useQueryClient()
  const [state, setState] = useState<IncidentState>('OPEN')
  const [reporting, setReporting] = useState(false)
  const [noticeFor, setNoticeFor] = useState<SecurityIncident | null>(null)
  const [noticeText, setNoticeText] = useState('')

  const { data: incidents = [], isLoading } = useQuery({
    queryKey: ['incidents', state],
    queryFn: () => incidentApi.queue(state),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['incidents'] })

  const raise = useMutation({
    mutationFn: incidentApi.raise,
    onSuccess: i => {
      toast({ title: `Incident ${i.incidentRef} recorded` })
      setReporting(false)
      invalidate()
    },
    onError: (e: Error) => toast({ title: 'Could not record', description: e.message }),
  })

  const openNotice = async (incident: SecurityIncident) => {
    try {
      setNoticeFor(incident)
      setNoticeText(await incidentApi.draftNotice(incident.id))
    } catch (e) {
      toast({ title: 'Could not draft the notice', description: (e as Error).message })
      setNoticeFor(null)
    }
  }

  return (
    <div className="space-y-4 p-6">
      <header className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Security incidents</h1>
          <p className="text-sm text-slate-600">
            Personal data breaches and near-misses. If you think something went
            wrong, record it — an over-reported incident costs a conversation.
          </p>
        </div>
        <button
          onClick={() => setReporting(true)}
          className="inline-flex shrink-0 items-center gap-2 rounded-md bg-red-600 px-3 py-2 text-sm font-medium text-white hover:bg-red-700"
        >
          <Plus className="h-4 w-4" />
          Report an incident
        </button>
      </header>

      <div className="flex flex-wrap gap-2">
        {STATES.map(s => (
          <button
            key={s.value}
            onClick={() => setState(s.value)}
            className={cn(
              'rounded-full px-3 py-1 text-sm',
              state === s.value
                ? 'bg-slate-800 text-white'
                : 'bg-slate-100 text-slate-700 hover:bg-slate-200',
            )}
          >
            {s.label}
          </button>
        ))}
      </div>

      {isLoading ? (
        <div className="flex items-center gap-2 p-8 text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading…
        </div>
      ) : incidents.length === 0 ? (
        <p className="rounded-md border border-slate-200 bg-slate-50 p-8 text-center text-sm text-slate-500">
          Nothing in this state.
        </p>
      ) : (
        <div className="space-y-3">
          {incidents.map(i => (
            <div key={i.id} className="rounded-md border border-slate-200 p-4">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono text-sm text-slate-700">
                      {i.incidentRef}
                    </span>
                    <span
                      className={cn(
                        'rounded px-2 py-0.5 text-xs font-medium',
                        SEVERITY_STYLE[i.severity] ?? SEVERITY_STYLE.LOW,
                      )}
                    >
                      {i.severity}
                    </span>
                    <span className="text-xs text-slate-500">{i.category}</span>
                    {i.detectionSource === 'AUTOMATED_DETECTION' && (
                      <span className="rounded bg-blue-100 px-2 py-0.5 text-xs text-blue-800">
                        Auto-detected
                      </span>
                    )}
                  </div>
                  <p className="mt-1 text-sm text-slate-800">{i.summary}</p>

                  <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs">
                    <span className="text-slate-500">
                      Detected {new Date(i.detectedAt).toLocaleString()}
                    </span>
                    <span className={i.scopeUncertain ? 'text-amber-700' : 'text-slate-600'}>
                      {i.scopeUncertain
                        ? 'Scope not yet established'
                        : `${i.affectedPrincipalCount} affected`}
                    </span>
                  </div>

                  {/* Two clocks, shown apart. They lapse independently. */}
                  <div className="mt-2 flex flex-wrap gap-2 text-xs">
                    <span
                      className={cn(
                        'rounded px-2 py-0.5',
                        i.boardNotifiedAt
                          ? 'bg-emerald-100 text-emerald-800'
                          : 'bg-red-100 text-red-800',
                      )}
                    >
                      {i.boardNotifiedAt ? 'Board notified' : 'Board NOT notified'}
                    </span>
                    <span
                      className={cn(
                        'rounded px-2 py-0.5',
                        i.boardDetailReportAt
                          ? 'bg-emerald-100 text-emerald-800'
                          : 'bg-amber-100 text-amber-800',
                      )}
                    >
                      {i.boardDetailReportAt ? '72h report filed' : '72h report outstanding'}
                    </span>
                    {i.affectedPrincipalCount > 0 && (
                      <span
                        className={cn(
                          'rounded px-2 py-0.5',
                          i.principalsNotifiedAt
                            ? 'bg-emerald-100 text-emerald-800'
                            : 'bg-red-100 text-red-800',
                        )}
                      >
                        {i.principalsNotifiedAt
                          ? 'People notified'
                          : 'People NOT notified'}
                      </span>
                    )}
                  </div>
                </div>

                <div className="flex shrink-0 flex-col gap-2">
                  {i.affectedPrincipalCount > 0 && (
                    <button
                      onClick={() => openNotice(i)}
                      className="rounded border border-slate-300 px-2 py-1 text-xs hover:bg-slate-50"
                    >
                      Draft notice
                    </button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {reporting && (
        <ReportIncidentModal
          onClose={() => setReporting(false)}
          onSubmit={body => raise.mutate(body)}
          submitting={raise.isPending}
        />
      )}

      {noticeFor && (
        <Modal
          isOpen
          onClose={() => setNoticeFor(null)}
          size="2xl"
          title={`Notice for ${noticeFor.incidentRef}`}
          description="Rule 7 requires nature, likely consequences, remedial steps and a contact. Review before sending."
        >
          <div className="flex flex-col max-h-[85vh]">
            <div className="px-6 pt-6 pb-4 border-b border-slate-100 pr-12 shrink-0">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-xl bg-amber-50 border border-amber-200 flex items-center justify-center text-amber-600 shrink-0">
                  <ShieldAlert className="w-4 h-4" />
                </div>
                <div>
                  <h2 className="text-lg font-bold text-slate-900 leading-tight">Notice for {noticeFor.incidentRef}</h2>
                  <p className="text-xs text-slate-500 mt-0.5">
                    DPDP Rule 7 notice: details nature, consequences, and remedial steps.
                  </p>
                </div>
              </div>
            </div>

            <div className="p-6 space-y-3 overflow-y-auto flex-1">
              <textarea
                readOnly
                value={noticeText}
                rows={16}
                className="w-full rounded-xl border border-slate-300 p-3.5 font-mono text-xs bg-slate-50 focus:outline-none focus:ring-1 focus:ring-slate-400 leading-relaxed"
              />
              <p className="text-xs text-slate-500">
                Generated from the incident record. Edit before sending if the
                circumstances need it.
              </p>
            </div>

            <div className="px-6 py-4 bg-slate-50 border-t border-slate-100 flex justify-end shrink-0">
              <button
                type="button"
                onClick={() => setNoticeFor(null)}
                className="px-4 py-2 bg-white border border-slate-300 hover:bg-slate-100 text-slate-700 rounded-lg text-sm font-medium transition-colors shadow-2xs cursor-pointer"
              >
                Close Notice
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}

interface ReportProps {
  onClose: () => void
  onSubmit: (body: {
    category: string
    severity: string
    summary: string
    detail?: string
    dataCategories?: string
    scopeUncertain: boolean
  }) => void
  submitting: boolean
}

/**
 * Reporting form.
 *
 * <p>{@code scopeUncertain} defaults to true. Scope is almost never known at the
 * moment of discovery, and a form that defaults to "0 affected" produces a
 * confident zero where an honest "not yet known" belongs — which is the number
 * that would then be reported to the Board.
 */
function ReportIncidentModal({ onClose, onSubmit, submitting }: ReportProps) {
  const [category, setCategory] = useState('UNAUTHORISED_ACCESS')
  const [severity, setSeverity] = useState('MEDIUM')
  const [summary, setSummary] = useState('')
  const [detail, setDetail] = useState('')
  const [dataCategories, setDataCategories] = useState('')
  const [scopeUncertain, setScopeUncertain] = useState(true)

  return (
    <Modal
      isOpen
      onClose={onClose}
      size="lg"
      title="Report an incident"
      description="Better reported and dismissed than unreported."
    >
      <div className="flex flex-col max-h-[85vh]">
        <div className="px-6 pt-6 pb-4 border-b border-slate-100 pr-12 shrink-0">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-red-50 border border-red-200 flex items-center justify-center text-red-600 shrink-0">
              <AlertTriangle className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900 leading-tight">Report a Data Security Incident</h2>
              <p className="text-xs text-slate-500 mt-0.5">
                DPDP statutory obligation: report immediately upon discovery.
              </p>
            </div>
          </div>
        </div>

        <div className="p-6 space-y-4 overflow-y-auto flex-1">
          <div className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50/80 p-3.5">
            <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" />
            <p className="text-sm text-amber-900">
              Do not put patient names or contact details in the description. Who
              was affected is recorded separately, by patient id.
            </p>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">What happened</label>
            <input
              value={summary}
              onChange={e => setSummary(e.target.value)}
              maxLength={500}
              className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm bg-white focus:outline-none focus:ring-1 focus:ring-red-600"
              placeholder="A short factual summary"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Category</label>
              <select
                value={category}
                onChange={e => setCategory(e.target.value)}
                className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm bg-white focus:outline-none focus:ring-1 focus:ring-red-600"
              >
                {['CROSS_TENANT_ACCESS', 'UNAUTHORISED_ACCESS', 'DATA_LOSS',
                  'DATA_EXPOSURE', 'CREDENTIAL_COMPROMISE', 'INTEGRITY_COMPROMISE',
                  'AVAILABILITY', 'OTHER'].map(c => (
                  <option key={c} value={c}>{c.replace(/_/g, ' ').toLowerCase()}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Severity</label>
              <select
                value={severity}
                onChange={e => setSeverity(e.target.value)}
                className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm bg-white focus:outline-none focus:ring-1 focus:ring-red-600"
              >
                {['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map(s => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">What kind of information was involved</label>
            <input
              value={dataCategories}
              onChange={e => setDataCategories(e.target.value)}
              maxLength={300}
              className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm bg-white focus:outline-none focus:ring-1 focus:ring-red-600"
              placeholder="e.g. names and contact numbers"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">More detail</label>
            <textarea
              value={detail}
              onChange={e => setDetail(e.target.value)}
              rows={4}
              className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm bg-white focus:outline-none focus:ring-1 focus:ring-red-600"
              placeholder="Factual chronology, how it was noticed, initial containment actions taken..."
            />
          </div>

          <label className="flex cursor-pointer items-start gap-3 rounded-xl border border-slate-200 p-3 bg-slate-50/50">
            <input
              type="checkbox"
              checked={scopeUncertain}
              onChange={e => setScopeUncertain(e.target.checked)}
              className="mt-1 h-4 w-4 rounded border-slate-300 text-red-600"
            />
            <span className="text-sm text-slate-800">
              We do not yet know how many people are affected.
              <span className="block text-xs text-slate-500 mt-0.5">
                Leave this ticked unless you are sure. An unknown scope recorded as
                zero looks like a contained incident.
              </span>
            </span>
          </label>
        </div>

        <div className="px-6 py-4 bg-slate-50 border-t border-slate-100 flex justify-end gap-2.5 shrink-0">
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 transition-colors cursor-pointer"
          >
            Cancel
          </button>
          <button
            type="button"
            disabled={!summary.trim() || submitting}
            onClick={() =>
              onSubmit({
                category,
                severity,
                summary: summary.trim(),
                detail: detail.trim() || undefined,
                dataCategories: dataCategories.trim() || undefined,
                scopeUncertain,
              })
            }
            className={cn(
              'inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium text-white transition-colors cursor-pointer shadow-2xs',
              summary.trim() && !submitting
                ? 'bg-red-600 hover:bg-red-700'
                : 'cursor-not-allowed bg-slate-300',
            )}
          >
            {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
            <AlertTriangle className="h-4 w-4" />
            Record incident
          </button>
        </div>
      </div>
    </Modal>
  )
}
