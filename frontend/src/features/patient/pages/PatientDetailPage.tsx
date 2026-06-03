import { useState } from 'react'
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

const ENCOUNTER_STATUS_STYLES: Record<EncounterStatus, string> = {
  CHECKED_IN: 'bg-blue-50 text-blue-700 border-blue-200',
  CONSULTATION_STARTED: 'bg-purple-50 text-purple-700 border-purple-200',
  CASESHEET_RECORDED: 'bg-amber-50 text-amber-700 border-amber-200',
  BILLING_DONE: 'bg-green-50 text-green-700 border-green-200',
}

const ENCOUNTER_STATUS_LABELS: Record<EncounterStatus, string> = {
  CHECKED_IN: 'Checked In',
  CONSULTATION_STARTED: 'In Consultation',
  CASESHEET_RECORDED: 'Casesheet Done',
  BILLING_DONE: 'Billing Done',
}

type Tab = 'encounters' | 'bills'

export default function PatientDetailPage() {
  const { patientId } = useParams<{ patientId: string }>()
  const navigate = useNavigate()
  const [tab, setTab] = useState<Tab>('encounters')
  const [showEncounterModal, setShowEncounterModal] = useState(false)

  const { data: patient, isLoading: patientLoading, error: patientError } = usePatient(patientId)

  const { data: encounters, isLoading: encLoading } = useQuery({
    queryKey: ['encounters', 'patient', patientId],
    queryFn: () => encounterApi.getByPatient(patientId!, 0),
    enabled: !!patientId && tab === 'encounters',
  })

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
          {/* Avatar */}
          <div className="w-14 h-14 rounded-full bg-blue-100 flex items-center justify-center shrink-0"
            aria-hidden="true">
            <span className="text-blue-700 text-lg font-bold">
              {patient.firstName.charAt(0)}{patient.lastName.charAt(0)}
            </span>
          </div>
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
            onClick={() => navigate(`/patients/${patientId}/edit`)}
            className="px-3 py-1.5 text-sm border border-gray-300 text-gray-600 rounded-lg hover:bg-gray-50 transition-colors">
            Edit
          </button>
          <button
            onClick={() => setShowEncounterModal(true)}
            className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors">
            + New Encounter
          </button>
          <BackButton />
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

      {/* Overview tab */}
      {/* {tab === 'overview' && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
          <div className="bg-white border border-gray-200 rounded-xl p-5 space-y-3">
            <h3 className="text-sm font-semibold text-gray-900">Patient Information</h3>
            <dl className="space-y-2 text-sm">
              {[
                { term: 'Patient ID', def: <span className="font-mono text-blue-600">{patient.patientNumber}</span> },
                { term: 'Full Name', def: patient.fullName },
                { term: 'Salutation', def: patient.salutation ?? '—' },
                { term: 'Gender', def: patient.gender.charAt(0) + patient.gender.slice(1).toLowerCase() },
                { term: 'Age', def: patient.age },
                { term: 'Blood Group', def: patient.bloodGroup ?? '—' },
                // { term: 'Contact', def: patient.contactNumber ?? '—' },
                { term: 'Address', def: patient.address ?? '—' },
              ].map(({ term, def }) => (
                <div key={term} className="flex gap-3">
                  <dt className="text-gray-500 w-28 shrink-0">{term}</dt>
                  <dd className="text-gray-800 font-medium">{def}</dd>
                </div>
              ))}
            </dl>
          </div>

          <div className="bg-white border border-gray-200 rounded-xl p-5 space-y-3">
            <h3 className="text-sm font-semibold text-gray-900">Quick Actions</h3>
            <div className="space-y-2">
              {[
                { label: 'Create New Encounter',     action: () => setShowEncounterModal(true),      icon: '🏥' },
                { label: 'View All Bills',           action: () => setTab('bills'),                  icon: '💳' },
                { label: 'Pharmacy Sale',            href: `/sales?patientId=${patientId}`,          icon: '💊' },
                { label: 'Order Diagnostics',        href: `/diagnostics?patientId=${patientId}`,    icon: '🔬' },
              ].map(({ label, href, action, icon }) => (
                href ? (
                  <Link key={label} to={href}
                    className="flex items-center gap-3 px-4 py-2.5 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors text-sm text-gray-700">
                    <span aria-hidden="true">{icon}</span>
                    {label}
                  </Link>
                ) : (
                  <button key={label} onClick={action}
                    className="w-full flex items-center gap-3 px-4 py-2.5 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors text-sm text-gray-700 text-left">
                    <span aria-hidden="true">{icon}</span>
                    {label}
                  </button>
                )
              ))}
            </div>
          </div>
        </div>
      )} */}

      {/* Encounters tab */}
      {tab === 'encounters' && (
        <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
          <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-gray-900">Encounter History</h3>
            <button onClick={() => setShowEncounterModal(true)}
              className="text-xs text-blue-600 hover:underline font-medium">
              + New Encounter
            </button>
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
                      ENCOUNTER_STATUS_STYLES[e.status])}>
                      {ENCOUNTER_STATUS_LABELS[e.status]}
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
                      className="text-xs text-blue-600 hover:underline font-medium">
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
    </div>
  )
}
