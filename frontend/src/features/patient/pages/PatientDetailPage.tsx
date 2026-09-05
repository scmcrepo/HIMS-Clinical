import { lazy, Suspense, useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { usePatient } from '../../../hooks/patient/usePatient'
import { encounterApi } from '../../../services/encounter/encounterApi'
import { billingApi } from '../../../services/billing/billingApi'
import { formatDate, formatDateTime } from '../../../lib/dateUtils'
import { BillStatusBadge } from '../../../components/shared/StatusBadge'
import { AmountDisplay } from '../../../components/shared/AmountDisplay'
import { cn } from '../../../lib/utils'
import BackButton from '../../../components/shared/BackButton'
import { PrintButton } from '../../../components/shared/PrintButton'
import type { EncounterStatus } from '../../../types/encounter'
import CreateEncounterModal from '../../encounter/components/CreateEncounterModal'
import { toast } from '../../../hooks/useToast'
import PatientAvatar from '../components/PatientAvatar'

import { useAuthStore } from '../../../store/authStore'

// Lazy: the ABHA and policy bundles load only for desks that open them.
const AbhaVerifiedBadge = lazy(() => import('../../abha/components/AbhaVerifiedBadge'))
const AbhaVerificationModal = lazy(() => import('../../abha/components/AbhaVerificationModal'))
const PolicyDiscoveryPanel = lazy(() => import('../../policy/components/PolicyDiscoveryPanel'))
const CoveragePanel = lazy(() => import('../../policy/components/CoveragePanel'))
const ExternalRecordsViewer = lazy(() => import('../../abdm/components/ExternalRecordsViewer'))
const PatientConsentPanel = lazy(() => import('../../compliance/components/PatientConsentPanel'))
const PatientDataRightsPanel = lazy(() => import('../../compliance/components/PatientDataRightsPanel'))

const ENCOUNTER_STATUS_STYLES: Record<EncounterStatus, string> = {
  CHECKED_IN: 'bg-blue-50 text-blue-700 border-blue-200',
  CONSULTATION_STARTED: 'bg-purple-50 text-purple-700 border-purple-200',
  CASESHEET_RECORDED: 'bg-amber-50 text-amber-700 border-amber-200',
  BILLING_DONE: 'bg-green-50 text-green-700 border-green-200',
}

const ENCOUNTER_STATUS_LABELS: Record<EncounterStatus, string> = {
  CHECKED_IN: 'Checked In',
  CONSULTATION_STARTED: 'Consultation Started',
  CASESHEET_RECORDED: 'Casesheet Recorded',
  BILLING_DONE: 'Billing Done',
}

type Tab = 'encounters' | 'bills' | 'insurance' | 'externalRecords' | 'consent' | 'dataRights'

export default function PatientDetailPage() {
  const { patientId } = useParams<{ patientId: string }>()
  const navigate = useNavigate()
  const [tab, setTab] = useState<Tab>('encounters')
  const [abhaOpen, setAbhaOpen] = useState(false)
  const [showEncounterModal, setShowEncounterModal] = useState(false)
  const { hasPermission } = useAuthStore()
  const canManageConsent = hasPermission('CONSENT_MANAGE')

  const { data: patient, isLoading: patientLoading, error: patientError } = usePatient(patientId)

  const { data: encounters, isLoading: encLoading } = useQuery({
    queryKey: ['encounters', 'patient', patientId],
    queryFn: () => encounterApi.getByPatient(patientId!, 0),
    enabled: !!patientId && tab === 'encounters',
  })

  // Check if patient has an active IP encounter (not discharged)
  const hasActiveIpEncounter = encounters?.content?.some(
    (e: any) => e.encounterType === 'INPATIENT' && !e.dischargedAt
  ) ?? false

  const { data: bills, isLoading: billsLoading } = useQuery({
    queryKey: ['bills', 'patient', patientId],
    queryFn: () => billingApi.getBillsByPatient(patientId!),
    enabled: !!patientId && tab === 'bills',
  })

  if (patientLoading) {
    return <div className="text-sm text-gray-500 p-6" aria-live="polite">Loading patient…</div>
  }
  if (patientError || !patient) {
    return <div className="text-sm text-red-600 p-6" role="alert">Patient not found</div>
  }

  return (
    <div className="space-y-5 max-w-5xl">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div className="flex items-start gap-4">
          <PatientAvatar
            patientId={patientId!}
            firstName={patient.firstName}
            lastName={patient.lastName}
            editable={false}
          />
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-xl font-bold text-gray-900">{patient.fullName}</h2>
              <span className="px-2 py-0.5 bg-blue-50 text-blue-600 rounded text-[10px] font-mono border border-blue-100">{patient.patientNumber}</span>
            </div>
            <div className="flex items-center gap-3 mt-1 text-sm text-gray-500">
              <span>{patient.gender.toLowerCase()}</span>
              <span aria-hidden="true">·</span>
              <span>{patient.age}</span>
              {patient.bloodGroup && (
                <>
                  <span aria-hidden="true">·</span>
                  <span className="font-bold text-red-600">{patient.bloodGroup}</span>
                </>
              )}
              {/* {patient.contactNumber && (
                <>
                  <span aria-hidden="true">·</span>
                  <span>{patient.contactNumber}</span>
                </>
              )} */}
              {/* Verified-ABHA badge. Renders nothing when the patient has no
                  ABHA, so an unlinked record looks unremarkable rather than
                  deficient. */}
              {patientId && (
                <Suspense fallback={null}>
                  <AbhaVerifiedBadge patientId={patientId} />
                </Suspense>
              )}
              {patient.isClinicalTrial && (
                <span className="inline-flex items-center px-2 py-0.5 rounded bg-purple-100 text-purple-700 text-xs font-medium">
                  Clinical Trial
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Quick action buttons */}
        <div className="flex gap-2">
          {patientId && (
            <PrintButton
              templateType="PATIENT_ID"
              params={{ id: patientId }}
              variant="outline"
              label="ID Card"
            />
          )}
          <button
            onClick={() => setAbhaOpen(true)}
            className="px-3 py-1.5 text-sm border border-gray-300 text-gray-600 rounded-lg hover:bg-gray-50 transition-colors">
            ABHA
          </button>
          <button
            onClick={() => navigate(`/patients/${patientId}/edit`)}
            className="px-3 py-1.5 text-sm border border-gray-300 text-gray-600 rounded-lg hover:bg-gray-50 transition-colors">
            Edit
          </button>
          <button
            onClick={() => {
              if (hasActiveIpEncounter) {
                toast({ title: 'Cannot create encounter', description: 'Patient already has an active Inpatient (IP) encounter. Please discharge the patient first.', variant: 'destructive' })
                return
              }
              setShowEncounterModal(true)
            }}
            className="px-3 py-1.5 text-sm bg-neutral-600 text-white rounded-lg hover:bg-neutral-700 transition-colors">
            + New Encounter
          </button>
          <BackButton to="/patients" />
        </div>
      </div>

      {/* Info cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {[
          { label: 'Contact', value: patient.contactNumber ?? '—' },
          { label: 'Status', value: patient.status },
          { label: 'Address', value: patient.address ?? '—' },
        ].map(({ label, value }) => (
          <div key={label} className="bg-white border border-gray-200 rounded-xl px-4 py-3">
            <p className="text-xs text-gray-500">{label}</p>
            <p className="text-sm font-medium text-gray-800 mt-0.5 truncate">{value}</p>
          </div>
        ))}
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-gray-100 p-1 rounded-lg w-fit" role="tablist">
        {([
          { key: 'encounters', label: 'Encounters' },
          { key: 'bills', label: 'Bills' },
          { key: 'insurance', label: 'Insurance' },
          { key: 'externalRecords', label: 'External Records (ABHA)' },
          { key: 'consent', label: 'Consent' },
          { key: 'dataRights', label: 'Data Rights' },
        ] as const).map(({ key, label }) => (
          <button key={key} role="tab" aria-selected={tab === key}
            onClick={() => setTab(key)}
            className={cn('px-4 py-1.5 text-sm font-medium rounded-md transition-colors',
              tab === key
                ? 'bg-white text-gray-900 shadow-sm'
                : 'text-gray-500 hover:text-gray-700')}>
            {label}
          </button>
        ))}
      </div>

      {/* Consent tab — DPDP Screen WO-023 */}
      {tab === 'consent' && patientId && (
        <div className="bg-white border border-gray-200 rounded-xl p-5">
          <Suspense fallback={<p className="text-sm text-gray-500">Loading consent…</p>}>
            <PatientConsentPanel patientId={patientId} canManage={canManageConsent} />
          </Suspense>
        </div>
      )}

      {/* Data Rights tab — DPDP Screen WO-024 */}
      {tab === 'dataRights' && patientId && (
        <div className="bg-white border border-gray-200 rounded-xl p-5">
          <Suspense fallback={<p className="text-sm text-gray-500">Loading data rights…</p>}>
            <PatientDataRightsPanel patientId={patientId} />
          </Suspense>
        </div>
      )}

      {/* External Records tab — ABDM Screen 3.2 */}
      {tab === 'externalRecords' && patientId && (
        <div className="bg-white border border-gray-200 rounded-xl p-5">
          <Suspense fallback={<p className="text-sm text-gray-500">Loading…</p>}>
            <ExternalRecordsViewer patientId={patientId} />
          </Suspense>
        </div>
      )}

      {/* Insurance tab — Screens 1.2 and 2.1. Policy discovery and the coverage
          breakdown sit on the patient, not on one encounter: a policy is
          discovered once and then relied on across admissions. */}
      {tab === 'insurance' && patientId && (
        <div className="space-y-6">
          <Suspense fallback={<p className="text-sm text-gray-500">Loading…</p>}>
            <section>
              <h3 className="mb-3 text-sm font-semibold text-gray-900">Coverage &amp; benefits</h3>
              <CoveragePanel patientId={patientId} />
            </section>
            <section>
              <h3 className="mb-3 text-sm font-semibold text-gray-900">Find policies</h3>
              <PolicyDiscoveryPanel patientId={patientId} />
            </section>
          </Suspense>
        </div>
      )}

      {tab === 'encounters' && (
        <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
          <div className="px-5 py-4 border-b border-gray-100">
            <h3 className="text-sm font-semibold text-gray-900">Encounter History</h3>
          </div>
          {encLoading && <p className="text-sm text-gray-500 px-5 py-4" aria-live="polite">Loading…</p>}
          <table className="w-full text-sm" aria-label="Encounter history">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-100 text-left text-xs">
                <th className="px-4 py-3 font-semibold text-gray-600 w-12">S.No</th>
                <th className="px-4 py-3 font-semibold text-gray-600">Consultant</th>
                <th className="px-4 py-3 font-semibold text-gray-600">Date & Time</th>
                <th className="px-4 py-3 font-semibold text-gray-600">Type</th>
                <th className="px-4 py-3 font-semibold text-gray-600">Status</th>
                {/* <th className="px-4 py-3 font-semibold text-gray-600">Diagnosis</th> */}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {encounters?.content.map((e, index) => (
                <tr key={e.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-3 text-gray-500 font-medium">{index + 1}</td>
                  <td className="px-4 py-3 text-gray-600 font-medium">{e.providerName ?? '—'}</td>
                  <td className="px-4 py-3 text-gray-600 font-medium whitespace-nowrap">{formatDateTime(e.startedAt)}</td>
                  <td className="px-4 py-3">
                    <span className={cn('inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold border',
                      e.encounterType === 'OUTPATIENT' ? 'bg-blue-50 text-blue-700 border-blue-200' : 'bg-amber-50 text-amber-700 border-amber-200')}>
                      {e.encounterType === 'OUTPATIENT' ? 'OP' : 'IP'}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <span className={cn('inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border',
                      e.encounterType === 'INPATIENT' && e.dischargedAt
                        ? 'bg-neutral-50 text-neutral-700 border-neutral-200'
                        : ENCOUNTER_STATUS_STYLES[e.status]
                    )}>
                      {e.encounterType === 'INPATIENT' && e.dischargedAt
                        ? 'Discharged'
                        : e.encounterType === 'OUTPATIENT' && e.status === 'BILLING_DONE'
                          ? 'Consulted'
                          : ENCOUNTER_STATUS_LABELS[e.status]}
                    </span>
                  </td>
                  {/* <td className="px-4 py-3 text-gray-500 text-xs max-w-40 truncate">
                    {e.diagnosis ?? '—'}
                  </td> */}
                </tr>
              ))}
              {(!encounters?.content || encounters.content.length === 0) && !encLoading && (
                <tr>
                  <td colSpan={6} className="px-4 py-10 text-center text-gray-400 text-sm">
                    No encounters recorded yet
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Bills tab */}
      {tab === 'bills' && (
        <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
          <div className="px-5 py-4 border-b border-gray-100">
            <h3 className="text-sm font-semibold text-gray-900">Billing History</h3>
          </div>
          {billsLoading && <p className="text-sm text-gray-500 px-5 py-4" aria-live="polite">Loading…</p>}
          <table className="w-full text-sm" aria-label="Bill history">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-100 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">
                <th className="px-4 py-3 w-12">S.No</th>
                <th className="px-4 py-3">Bill No.</th>
                <th className="px-4 py-3">Bill Date</th>
                <th className="px-4 py-3">Type</th>
                <th className="px-4 py-3 text-right">Amount</th>
                <th className="px-4 py-3 text-right">Discount</th>
                <th className="px-4 py-3 text-right">Due</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {bills?.map((b, index) => (
                <tr key={b.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-3 text-gray-500 font-medium">{index + 1}</td>
                  <td className="px-4 py-3 font-mono text-[10px] font-bold text-gray-500">{b.billNumber || '—'}</td>
                  <td className="px-4 py-3 text-gray-700">
                    {b.billDate ? formatDate(b.billDate) : (b.createdAt ? formatDate(b.createdAt) : '—')}
                  </td>
                  <td className="px-4 py-3">
                    <span className={cn('inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold tracking-wider uppercase',
                      b.encounterType === 'OUTPATIENT'
                        ? 'bg-blue-50 text-blue-700 border border-blue-100'
                        : 'bg-amber-50 text-amber-700 border border-amber-100')}>
                      {b.encounterType === 'OUTPATIENT' ? 'OP' : 'IP'}
                    </span>
                    <div className="text-[10px] text-gray-400 mt-1 uppercase tracking-tight">{b.billType}</div>
                  </td>
                  <td className="px-4 py-3 text-right font-medium"><AmountDisplay amount={b.billAmount} /></td>
                  <td className="px-4 py-3 text-right text-gray-600 font-medium">
                    {Number(b.discountTotal || 0) > 0 ? <AmountDisplay amount={b.discountTotal} /> : '—'}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <AmountDisplay
                      amount={b.dueAmount}
                      className={b.dueAmount > 0 ? 'text-amber-700 font-bold' : 'text-green-700'}
                    />
                  </td>
                  <td className="px-4 py-3"><BillStatusBadge status={b.status} /></td>
                  <td className="px-4 py-3">
                    <Link to={`/billing/${b.id}`}
                      className="text-xs text-neutral-600 hover:underline font-medium">
                      Open
                    </Link>
                  </td>
                </tr>
              ))}
              {(!bills || bills.length === 0) && !billsLoading && (
                <tr>
                  <td colSpan={9} className="px-4 py-10 text-center text-gray-400 text-sm">
                    No bills found
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {showEncounterModal && patient && (
        <CreateEncounterModal
          initialPatient={patient}
          onClose={() => setShowEncounterModal(false)}
          onSuccess={() => { }}
        />
      )}
    
      {abhaOpen && patientId && (
        <Suspense fallback={null}>
          <AbhaVerificationModal
            patientId={patientId}
            patientName={patient.fullName}
            onClose={() => setAbhaOpen(false)}
          />
        </Suspense>
      )}
    </div>
  )
}
