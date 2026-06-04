import { useState } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { userApi } from '../../../services/user/userApi'
import { consultantApi } from '../../../services/consultant/consultantApi'
import { encounterApi, type CreateEncounterCmd } from '../../../services/encounter/encounterApi'
import { patientApi } from '../../../services/patient/patientApi'
import { toast } from '../../../hooks/useToast'
import type { Patient } from '../../../types/patient'
import type { VisitMode } from '../../../types/encounter'
import { cn } from '../../../lib/utils'
import { ConsultantSearchInput } from '../../../components/shared/ConsultantSearchInput'

const newEncounterSchema = z.object({
  primaryProviderId: z.string().min(1, 'Required'),
  encounterType: z.enum(['OUTPATIENT', 'INPATIENT']),
  visitMode: z.enum(['WALK_IN', 'APPOINTMENT', 'TELE_CONSULT']).optional()
})
type NewEncounterValues = z.infer<typeof newEncounterSchema>

interface Props {
  initialPatient?: Patient | null
  onClose: () => void
  onSuccess?: (encounterId: string) => void
}

export default function CreateEncounterModal({ initialPatient, onClose, onSuccess }: Props) {
  const queryClient = useQueryClient()
  const [selectedPatient, setSelectedPatient] = useState<Patient | null>(initialPatient || null)
  const [patientSearch, setPatientSearch] = useState('')
  const [isSearching, setIsSearching] = useState(false)

  // Step state is derived from selectedPatient
  const step = selectedPatient ? 'ENCOUNTER_DETAILS' : 'SELECT_PATIENT'

  const { data: searchResults = [] } = useQuery({
    queryKey: ['patientSearch', patientSearch],
    queryFn: () => patientApi.search(patientSearch, 0, 5).then(res => res.content),
    enabled: step === 'SELECT_PATIENT' && patientSearch.length >= 2,
  })

  const { data: users, isLoading: usersLoading } = useQuery({
    queryKey: ['users'],
    queryFn: userApi.getAll,
    enabled: !!selectedPatient
  })

  const { data: consultants, isLoading: consultantsLoading } = useQuery({
    queryKey: ['consultants'],
    queryFn: consultantApi.getAll,
    enabled: !!selectedPatient
  })

  const doctors = users?.filter(u => u.roles.some(r => r.name.includes('DOCTOR') || r.name.includes('PHYSICIAN'))) || []
  const providerList = [
    ...(consultants?.map(c => ({
      id: c.id,
      fullName: (c.salutation ? c.salutation + ' ' : '') + c.firstName + (c.lastName && c.lastName !== '.' ? ' ' + c.lastName : '')
    })) || []),
    ...(doctors.length > 0 ? doctors : (users?.filter(u => u.status === 'ACTIVE') || []))
  ]

  const form = useForm<NewEncounterValues>({
    resolver: zodResolver(newEncounterSchema),
    defaultValues: { encounterType: 'OUTPATIENT', visitMode: 'WALK_IN' }
  })
  const encounterType = form.watch('encounterType')

  const createOp = useMutation({
    mutationFn: encounterApi.createOutpatient,
    onSuccess: (data) => {
      toast({ title: 'Encounter created', variant: 'success' })
      queryClient.invalidateQueries({ queryKey: ['encounters'] })
      onSuccess?.(data.id)
      onClose()
    },
    onError: (e: any) => {
      const msg = e.response?.data?.message || e.message || 'Failed to create encounter'
      toast({ title: 'Error', description: msg, variant: 'destructive' })
    }
  })

  const createIp = useMutation({
    mutationFn: encounterApi.createInpatient,
    onSuccess: (data) => {
      toast({ title: 'Encounter created', variant: 'success' })
      queryClient.invalidateQueries({ queryKey: ['encounters'] })
      onSuccess?.(data.id)
      onClose()
    },
    onError: (e: any) => {
      const msg = e.response?.data?.message || e.message || 'Failed to create encounter'
      toast({ title: 'Error', description: msg, variant: 'destructive' })
    }
  })

  const onSubmit = (data: NewEncounterValues) => {
    if (!selectedPatient) return
    const payload: CreateEncounterCmd = {
      patientId: selectedPatient.id,
      primaryProviderId: data.primaryProviderId,
    }

    if (data.encounterType === 'OUTPATIENT' && data.visitMode) {
      payload.visitMode = data.visitMode as VisitMode
    }

    if (data.encounterType === 'OUTPATIENT') {
      createOp.mutate(payload)
    } else {
      createIp.mutate(payload)
    }
  }

  const inputCls = "w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
  const labelCls = "block text-xs font-medium text-gray-700 mb-1"

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200" style={{marginTop: '0px'}}>
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md animate-in zoom-in-95 duration-200 relative">
        <div className="px-6 py-4 border-b border-gray-100 flex justify-between items-center bg-gray-50/50 rounded-t-2xl">
          <div>
            <h3 className="text-lg font-bold text-gray-900">Create Encounter</h3>
            <p className="text-xs text-gray-500">
              {step === 'SELECT_PATIENT' ? 'Search and select a patient to continue' : `For ${String(selectedPatient?.firstName ?? '')} ${String(selectedPatient?.lastName ?? '')}`}
            </p>
          </div>
          <button onClick={onClose} className="p-2 hover:bg-gray-200 rounded-full transition-colors text-gray-400">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
          </button>
        </div>

        <div className="p-6">
          {step === 'SELECT_PATIENT' ? (
            <div className="space-y-4">
              <div className="relative">
                <label className={labelCls}>Search Patient</label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                    <svg className="h-4 w-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" /></svg>
                  </div>
                  <input
                    type="text"
                    placeholder="Search by name or phone..."
                    value={patientSearch}
                    onChange={e => { setPatientSearch(e.target.value); setIsSearching(true) }}
                    onFocus={() => setIsSearching(true)}
                    className={cn(inputCls, "pl-10")}
                    autoFocus
                  />
                </div>

                {isSearching && searchResults.length > 0 && (
                  <div className="absolute top-full left-0 right-0 mt-1 bg-white border border-gray-200 shadow-xl rounded-xl overflow-hidden z-30 max-h-60 overflow-y-auto">
                    {searchResults.map((p: Patient) => (
                      <button
                        key={p.id}
                        onClick={() => { setSelectedPatient(p); setIsSearching(false) }}
                        className="w-full text-left px-4 py-3 hover:bg-blue-50 border-b border-gray-50 last:border-0 transition-colors group"
                      >
                        <div className="flex justify-between items-center">
                          <div>
                            <span className="font-semibold text-gray-900 block group-hover:text-blue-700 transition-colors">{String(p.firstName ?? '')} {String(p.lastName ?? '')}</span>
                            <span className="text-xs text-gray-500">{String(p.patientNumber || 'No ID')} • {String(p.contactNumber || 'No Phone')}</span>
                          </div>
                          <span className="text-blue-500 opacity-0 group-hover:opacity-100 transition-all text-xs font-bold">Select →</span>
                        </div>
                      </button>
                    ))}
                  </div>
                )}
                {isSearching && patientSearch.length >= 2 && searchResults.length === 0 && (
                  <div className="absolute top-full left-0 right-0 mt-1 bg-white border border-gray-200 shadow-xl rounded-xl p-4 z-30 text-center text-sm text-gray-500">
                    No patients found
                  </div>
                )}
              </div>
              <div className="pt-2">
                <p className="text-[10px] text-gray-400 text-center leading-relaxed italic">
                  Search by name, patient ID, or contact number.
                  <br />Once selected, you can specify encounter details.
                </p>
              </div>
            </div>
          ) : (
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4" noValidate>
              <div className="bg-blue-50/50 p-3 rounded-xl border border-blue-100 mb-2 flex items-center justify-between">
                <div>
                  <p className="text-[10px] uppercase tracking-wider font-bold text-blue-500">Selected Patient</p>
                  <p className="text-sm font-bold text-blue-900">{String(selectedPatient?.fullName ?? '')}</p>
                  <p className="text-[11px] text-blue-700 font-medium">
                    {String(selectedPatient?.patientNumber || 'No ID')} • {String(selectedPatient?.contactNumber || 'No Phone')}
                  </p>
                </div>
                {!initialPatient && (
                  <button
                    type="button"
                    onClick={() => setSelectedPatient(null)}
                    className="text-xs font-bold text-blue-600 hover:underline"
                  >
                    Change
                  </button>
                )}
              </div>

              <div>
                <label className={labelCls}>Encounter Type</label>
                <select {...form.register('encounterType')} className={inputCls}>
                  <option value="OUTPATIENT">Outpatient (OP)</option>
                  <option value="INPATIENT">Inpatient (IP)</option>
                </select>
              </div>

              {encounterType === 'OUTPATIENT' && (
                <div>
                  <label className={labelCls}>Encounter Mode</label>
                  <select {...form.register('visitMode')} className={inputCls}>
                    <option value="WALK_IN">Walk In</option>
                    <option value="APPOINTMENT">Appointment</option>
                    <option value="TELE_CONSULT">Tele Consult</option>
                  </select>
                </div>
              )}

              <div>
                <label className={labelCls}>Primary Consultant</label>
                <Controller
                  name="primaryProviderId"
                  control={form.control}
                  render={({ field }) => (
                    <ConsultantSearchInput
                      consultants={providerList.map((p: any) => ({
                        id: p.id,
                        firstName: p.fullName || p.firstName || '',
                        lastName: '',
                        status: 'ACTIVE'
                      })) as any}
                      value={field.value ?? ''}
                      onChange={field.onChange}
                    />
                  )}
                />
                {form.formState.errors.primaryProviderId && (
                  <p className="text-xs text-red-600 mt-0.5">{form.formState.errors.primaryProviderId.message}</p>
                )}
              </div>

              <div className="pt-4 flex gap-3">
                <button
                  type="submit"
                  disabled={createOp.isPending || createIp.isPending || usersLoading || consultantsLoading}
                  className="flex-1 py-2.5 bg-blue-600 text-white text-sm font-bold rounded-xl hover:bg-blue-700 disabled:opacity-50 transition-all shadow-lg shadow-blue-100"
                >
                  {(createOp.isPending || createIp.isPending) ? 'Creating...' : 'Create Encounter'}
                </button>
                <button
                  type="button"
                  onClick={onClose}
                  className="px-6 py-2.5 border border-gray-200 text-gray-600 text-sm font-bold rounded-xl hover:bg-gray-50 transition-all"
                >
                  Cancel
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  )
}
