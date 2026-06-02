import { PatientForm, PatientFormValues } from '../../patient/components/PatientRegistrationForm'
import type { Appointment } from '../../../types/appointment'
import { useRegisterPatient } from '../../../hooks/patient/usePatient'

interface Props {
  appointment: Appointment
  onSuccess: (patientId: string) => void
  onCancel: () => void
}

export function QuickRegistrationModal({ appointment, onSuccess, onCancel }: Props) {
  const registerPatient = useRegisterPatient()
  
  // Split name if possible
  const nameParts = (appointment.tempPatientName || '').split(' ')
  const firstName = nameParts[0] || ''
  const lastName = nameParts.slice(1).join(' ')

  const initialValues: Partial<PatientFormValues> = {
    firstName,
    lastName,
    gender: (appointment.tempPatientGender || 'MALE') as any,
    contactNumber: appointment.tempPatientPhone || '',
  }

  const handleSubmit = async (data: PatientFormValues) => {
    const patient = await registerPatient.mutateAsync(data)
    onSuccess(patient.id)
  }

  return (
    <div 
          className="fixed inset-0 z-50 flex items-center justify-center p-2 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200"
          style={{ marginTop: 0 }}
        >
      <div className="bg-white rounded-3xl shadow-2xl w-full max-w-3xl overflow-hidden animate-in zoom-in-95 duration-200">
        <div className="px-6 py-4 border-b border-gray-100 flex justify-between items-center bg-gray-50/50">
          <h3 className="text-lg font-bold text-gray-900">Patient Registration</h3>
          <button onClick={onCancel} className="p-2 hover:bg-gray-200 rounded-full transition-colors text-gray-400">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
          </button>
        </div>

        <div className="p-6 max-h-[calc(100vh-120px)] overflow-y-auto custom-scrollbar">
          <p className="text-xs text-gray-500 mb-6 italic">Complete full registration to check-in for this appointment.</p>
          <PatientForm 
            initialValues={initialValues}
            onSubmit={handleSubmit}
            onCancel={onCancel}
            isModal
            hideEncounterFields={true}
            isPending={registerPatient.isPending}
            error={registerPatient.error}
          />
        </div>
      </div>
    </div>
  )
}
