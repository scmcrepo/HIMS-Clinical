import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  insuranceApi,
  type InsuranceRecord,
  type CreateInsuranceCmd,
  type InsurancePreAuthType,
} from '../../../services/insurance/insuranceApi'
import { PatientSearchInput } from '../../../components/shared/PatientSearchInput'
import type { Patient } from '../../../types/patient'
import { toast } from '../../../hooks/useToast'
import { cn } from '../../../lib/utils'
import DatePicker from '../../../components/shared/DatePicker'

const STATUS_STYLES: Record<InsuranceRecord['insuranceStatus'], string> = {
  ACTIVE:              'bg-blue-50 text-blue-700 border-blue-200',
  PRE_AUTH_REQUESTED:  'bg-amber-50 text-amber-700 border-amber-200',
  PRE_AUTH_RECEIVED:   'bg-purple-50 text-purple-700 border-purple-200',
  SETTLED:             'bg-green-50 text-green-700 border-green-200',
  REJECTED:            'bg-red-50 text-red-700 border-red-200',
}

const STATUS_LABELS: Record<InsuranceRecord['insuranceStatus'], string> = {
  ACTIVE:             'Active',
  PRE_AUTH_REQUESTED: 'Pre-Auth Requested',
  PRE_AUTH_RECEIVED:  'Pre-Auth Received',
  SETTLED:            'Settled',
  REJECTED:           'Rejected',
}

export default function InsurancePage() {
  const qc = useQueryClient()
  const { data: pending, isLoading } = useQuery({
    queryKey: ['insurance', 'pending'],
    queryFn:  () => insuranceApi.getPending(),
    refetchInterval: 60_000,
  })

  const [showForm, setShowForm] = useState(false)
  const [selectedPatient, setSelectedPatient] = useState<Patient | null>(null)
  const [form, setForm] = useState<{
    insurerName: string
    policyNumber: string
    preAuthType: InsurancePreAuthType | ''
    communication: string
  }>({
    insurerName: '', policyNumber: '', preAuthType: '', communication: '',
  })

  // Pre-auth modal state
  const [preAuthModal, setPreAuthModal] = useState<InsuranceRecord | null>(null)
  const [paNumber, setPaNumber]   = useState('')
  const [paAmount, setPaAmount]   = useState('')
  const [paDate, setPaDate]       = useState(new Date().toISOString().split('T')[0])

  const invalidate = () => qc.invalidateQueries({ queryKey: ['insurance'] })

  const createMutation = useMutation({
    mutationFn: (cmd: CreateInsuranceCmd) => insuranceApi.create(cmd),
    onSuccess: () => {
      invalidate(); setShowForm(false); setSelectedPatient(null)
      setForm({ insurerName: '', policyNumber: '', preAuthType: '', communication: '' })
      toast({ title: 'Insurance record created', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  const preAuthMutation = useMutation({
    mutationFn: () => insuranceApi.receivePreAuth(preAuthModal!.id, paNumber, parseInt(paAmount) * 100, paDate),
    onSuccess: () => {
      invalidate(); setPreAuthModal(null)
      toast({ title: 'Pre-auth recorded', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  const settleMutation = useMutation({
    mutationFn: (id: string) => insuranceApi.settle(id),
    onSuccess: () => { invalidate(); toast({ title: 'Insurance settled', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  const rejectMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => insuranceApi.reject(id, reason),
    onSuccess: () => { invalidate(); toast({ title: 'Insurance rejected', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  const inputCls = "w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
  const labelCls = "block text-xs font-medium text-gray-700 mb-1"

  return (
    <div className="space-y-5 max-w-5xl">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-900">Insurance</h2>
          <p className="text-sm text-gray-500 mt-0.5">Manage insurance records, pre-authorisations, and settlements.</p>
        </div>
        <button onClick={() => setShowForm(v => !v)}
          className="px-4 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 transition-colors">
          + New Record
        </button>
      </div>

      {/* Create form */}
      {showForm && (
        <div className="bg-blue-50 border border-blue-200 rounded-xl p-5 space-y-4"
          role="region" aria-label="Create insurance record">
          <h3 className="text-sm font-semibold text-blue-900">New Insurance Record</h3>

          <div>
            <label className={labelCls}>Patient (optional)</label>
            <PatientSearchInput selectedPatient={selectedPatient} onSelect={setSelectedPatient} placeholder="Search patient…" className="max-w-sm" />
            {selectedPatient && (
              <p className="text-xs text-blue-600 mt-1 font-medium">Patient: {selectedPatient.fullName}</p>
            )}
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className={labelCls}>Insurer Name *</label>
              <input value={form.insurerName}
                onChange={e => setForm(f => ({ ...f, insurerName: e.target.value }))}
                placeholder="e.g. Star Health Insurance" className={inputCls}
                aria-label="Insurer name" />
            </div>
            <div>
              <label className={labelCls}>Policy Number</label>
              <input value={form.policyNumber ?? ''}
                onChange={e => setForm(f => ({ ...f, policyNumber: e.target.value }))}
                placeholder="Policy / TPA number" className={inputCls}
                aria-label="Policy number" />
            </div>
            <div>
              <label className={labelCls}>Pre-Auth Type</label>
              <select value={form.preAuthType}
                onChange={e => setForm(f => ({ ...f, preAuthType: e.target.value as InsurancePreAuthType | '' }))}
                className={inputCls} aria-label="Pre-auth type">
                <option value="">Not required</option>
                <option value="PLANNED">Planned Admission</option>
                <option value="EMERGENCY">Emergency</option>
                <option value="DAY_CARE">Day Care</option>
                <option value="OPD">OPD</option>
                <option value="MATERNITY">Maternity</option>
              </select>
            </div>
            <div>
              <label className={labelCls}>Communication Mode</label>
              <select value={form.communication ?? ''}
                onChange={e => setForm(f => ({ ...f, communication: e.target.value }))}
                className={inputCls} aria-label="Communication mode">
                <option value="">Select…</option>
                <option value="EMAIL">Email</option>
                <option value="PHONE">Phone</option>
                <option value="PORTAL">TPA Portal</option>
                <option value="LETTER">Letter</option>
              </select>
            </div>
          </div>

          <div className="flex gap-3">
            <button onClick={() => {
              const cmd: CreateInsuranceCmd = { insurerName: form.insurerName }
              if (selectedPatient?.id) cmd.patientId = selectedPatient.id
              if (form.policyNumber) cmd.policyNumber = form.policyNumber
              if (form.preAuthType) cmd.preAuthType = form.preAuthType
              if (form.communication) cmd.communication = form.communication
              createMutation.mutate(cmd)
            }}
              disabled={!form.insurerName.trim() || createMutation.isPending}
              className="px-5 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors">
              {createMutation.isPending ? 'Creating…' : 'Create Record'}
            </button>
            <button onClick={() => setShowForm(false)}
              className="px-4 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-gray-50 transition-colors">
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Pending pre-auths list */}
      {isLoading && <p className="text-sm text-gray-500" aria-live="polite">Loading…</p>}

      {pending && (
        <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
          <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-gray-900">Pending Pre-Authorisations</h3>
            <span className="text-xs text-gray-400">{pending.length} pending</span>
          </div>
          <table className="w-full text-sm" aria-label="Insurance records">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-100 text-left text-xs">
                <th className="px-4 py-2.5 font-semibold text-gray-600">Insurer</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600">Policy No.</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600">Pre-Auth Type</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600">Status</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600">Pre-Auth #</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600 text-right">Amount</th>
                <th className="px-4 py-2.5" />
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {pending.map(ins => (
                <tr key={ins.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-3 font-medium text-gray-900">{ins.insurerName}</td>
                  <td className="px-4 py-3 text-gray-600 font-mono text-xs">{ins.policyNumber ?? '—'}</td>
                  <td className="px-4 py-3 text-gray-500 text-xs capitalize">
                    {ins.preAuthType?.toLowerCase().replace('_', ' ') ?? '—'}
                  </td>
                  <td className="px-4 py-3">
                    <span className={cn('inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border',
                      STATUS_STYLES[ins.insuranceStatus])}>
                      {STATUS_LABELS[ins.insuranceStatus]}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-gray-600 text-xs">{ins.preAuthNumber ?? '—'}</td>
                  <td className="px-4 py-3 text-right font-medium text-gray-800">
                    {ins.preAuthAmount ? `₹${(ins.preAuthAmount / 100).toFixed(2)}` : '—'}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex gap-2 justify-end">
                      {ins.insuranceStatus === 'PRE_AUTH_REQUESTED' && (
                        <button onClick={() => setPreAuthModal(ins)}
                          className="text-xs text-purple-600 hover:text-purple-800 font-medium">
                          Record Pre-Auth
                        </button>
                      )}
                      {(ins.insuranceStatus === 'PRE_AUTH_RECEIVED' || ins.insuranceStatus === 'ACTIVE') && (
                        <button onClick={() => settleMutation.mutate(ins.id)}
                          disabled={settleMutation.isPending}
                          className="text-xs text-green-600 hover:text-green-800 font-medium disabled:opacity-40">
                          Settle
                        </button>
                      )}
                      {ins.insuranceStatus !== 'REJECTED' && ins.insuranceStatus !== 'SETTLED' && (
                        <button
                          onClick={() => {
                            const reason = prompt('Rejection reason:')
                            if (reason) rejectMutation.mutate({ id: ins.id, reason })
                          }}
                          disabled={rejectMutation.isPending}
                          className="text-xs text-red-500 hover:text-red-700 disabled:opacity-40">
                          Reject
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
              {pending.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-4 py-10 text-center text-gray-400 text-sm">
                    No pending pre-authorisations
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Pre-auth modal */}
      {preAuthModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 backdrop-blur-sm"
          role="dialog" aria-modal="true" aria-labelledby="preauth-title">
          <div className="bg-white rounded-2xl border border-gray-200 shadow-xl p-6 w-full max-w-md space-y-4">
            <h3 id="preauth-title" className="font-bold text-gray-900">Record Pre-Authorisation</h3>
            <p className="text-xs text-gray-500">{preAuthModal.insurerName} — {preAuthModal.policyNumber ?? 'No policy number'}</p>
            <div>
              <label className={labelCls}>Pre-Auth Number *</label>
              <input value={paNumber} onChange={e => setPaNumber(e.target.value)}
                placeholder="TPA pre-auth reference number"
                className={inputCls} aria-label="Pre-auth number" />
            </div>
            <div>
              <label className={labelCls}>Pre-Auth Amount (₹)</label>
              <input type="number" step="0.01" min={0} value={paAmount}
                onChange={e => setPaAmount(e.target.value)}
                placeholder="0.00" className={inputCls} aria-label="Pre-auth amount" />
            </div>
            <div>
              <label className={labelCls}>Received Date</label>
              <DatePicker 
                value={paDate} 
                onChange={setPaDate}
                size="sm"
              />
            </div>
            <div className="flex gap-3">
              <button onClick={() => preAuthMutation.mutate()}
                disabled={!paNumber.trim() || preAuthMutation.isPending}
                className="flex-1 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors">
                {preAuthMutation.isPending ? 'Saving…' : 'Save Pre-Auth'}
              </button>
              <button onClick={() => setPreAuthModal(null)}
                className="flex-1 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-gray-50 transition-colors">
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
