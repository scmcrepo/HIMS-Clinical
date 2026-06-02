import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { PatientSearchInput } from '../../../components/shared/PatientSearchInput'
import { useCreateBill } from '../../../hooks/billing/useBilling'
import BackButton from '../../../components/shared/BackButton'
import type { Patient } from '../../../types/patient'
import type { BillType, EncounterType } from '../../../types/billing'
import { cn } from '../../../lib/utils'

export default function CreateBillPage() {
  const navigate = useNavigate()
  const [sp] = useSearchParams()
  const typeParam = sp.get('type')?.toLowerCase() // 'op' or 'ip'
  const isTypeLocked = typeParam === 'op' || typeParam === 'ip'
  const defaultEncounterType: EncounterType = typeParam === 'ip' ? 'INPATIENT' : 'OUTPATIENT'

  const createBillMutation = useCreateBill()

  const [patient, setPatient] = useState<Patient | null>(null)
  const [encounterType, setEncounterType] = useState<EncounterType>(defaultEncounterType)
  const [billType, setBillType] = useState<BillType>('CASH')
  const [selectedEncounterId, setSelectedEncounterId] = useState<string | undefined>(undefined)

  const handleEncounterTypeChange = (type: EncounterType) => {
    setEncounterType(type)
    setPatient(null)
    setSelectedEncounterId(undefined)
    if (type === 'OUTPATIENT') {
      setBillType('CASH')
    }
  }

  const handlePatientSelect = (p: Patient | null, encounterId?: string) => {
    setPatient(p)
    setSelectedEncounterId(encounterId)
  }

  const handleCreateBill = () => {
    if (!patient) return
    createBillMutation.mutate(
      { patientId: patient.id, encounterType, billType, encounterId: selectedEncounterId },
      { onSuccess: (bill) => navigate(`/billing/${bill.id}`, { replace: true }) }
    )
  }

  return (
    <div className="max-w-lg mx-auto px-4 py-8 space-y-6 relative overflow-hidden">
      <div className="flex items-center justify-between mb-8 px-4">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 tracking-tight">
            {typeParam === 'op' ? 'Create OP Bill' : typeParam === 'ip' ? 'Create IP Bill' : 'Create New Bill'}
          </h2>
          <p className="text-sm text-gray-500 mt-0.5">Select a patient and bill type to start</p>
        </div>
        <BackButton />
      </div>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-7 space-y-6">
        {/* Encounter & Bill Type */}
        <div className={cn(isTypeLocked ? "space-y-2" : "grid grid-cols-2 gap-4")}>
          {!isTypeLocked && (
            <div className="space-y-2">
              <label className="block text-xs font-bold text-gray-700 uppercase tracking-wider">
                Encounter Type
              </label>
              <select
                value={encounterType}
                onChange={(e) => handleEncounterTypeChange(e.target.value as EncounterType)}
                className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:ring-2 focus:ring-blue-500 focus:bg-white focus:outline-none transition-all font-medium"
              >
                <option value="OUTPATIENT">Outpatient</option>
                <option value="INPATIENT">Inpatient</option>
              </select>
            </div>
          )}

          <div className="space-y-2">
            <label className="block text-xs font-bold text-gray-700 uppercase tracking-wider">
              Bill Type
            </label>
            <select
              value={billType}
              onChange={(e) => setBillType(e.target.value as BillType)}
              className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:ring-2 focus:ring-blue-500 focus:bg-white focus:outline-none transition-all font-medium"
            >
              <option value="CASH">Cash / General</option>
              <option value="CREDIT">Credit</option>
              {encounterType !== 'OUTPATIENT' && <option value="INSURANCE">Insurance</option>}
            </select>
          </div>
        </div>

        {/* Patient Search */}
        <div className="space-y-2">
          <label className="block text-xs font-bold text-gray-700 uppercase tracking-wider">
            Search Patient
          </label>
          <p className="text-[10px] text-gray-400">
            {encounterType === 'OUTPATIENT'
              ? 'Showing only active outpatients for today'
              : 'Showing only active inpatients'}
          </p>
          <PatientSearchInput
            key={encounterType}
            onSelect={handlePatientSelect}
            placeholder={encounterType === 'OUTPATIENT' ? 'Search active outpatient...' : 'Search active inpatient...'}
            encounterFilter={encounterType}
          />
          {patient && (
            <div className="mt-3 p-3.5 bg-blue-50 border border-blue-100 rounded-xl flex items-center justify-between animate-in slide-in-from-top-2 duration-200">
              <div>
                <p className="text-sm font-bold text-blue-900">{patient.fullName}</p>
                <p className="text-[11px] text-blue-700 font-medium">
                  {patient.patientNumber} · {patient.contactNumber ?? 'No contact'}
                </p>
              </div>
              <button
                onClick={() => { setPatient(null); setSelectedEncounterId(undefined) }}
                className="text-[11px] font-bold text-blue-600 hover:text-blue-800 bg-white px-2.5 py-1 rounded-lg border border-blue-200 shadow-sm"
              >
                Change
              </button>
            </div>
          )}
        </div>

        {/* Actions */}
        <div className="pt-2 flex gap-3">
          <button
            onClick={() => navigate(`/billing/${typeParam || 'op'}`)}
            className="flex-1 px-4 py-3 bg-gray-100 text-gray-700 text-sm font-bold rounded-xl hover:bg-gray-200 transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={handleCreateBill}
            disabled={!patient || createBillMutation.isPending}
            className="flex-[2] px-4 py-3 bg-blue-600 text-white text-sm font-bold rounded-xl hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-all shadow-lg shadow-blue-200 active:scale-[0.98]"
          >
            {createBillMutation.isPending ? 'Creating...' : 
             typeParam === 'op' ? 'Create OP Bill' : 
             typeParam === 'ip' ? 'Create IP Bill' : 
             'Create Bill'}
          </button>
        </div>
      </div>

      {/* Decorative element */}
      <div className="absolute -bottom-10 -right-10 w-32 h-32 bg-blue-50 rounded-full blur-3xl opacity-60 -z-10 pointer-events-none" />
    </div>
  )
}
