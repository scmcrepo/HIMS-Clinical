import { PatientForm, PatientFormValues } from '../../patient/components/PatientRegistrationForm'
import { Modal } from '../../../components/ui/Modal'
import type { Appointment } from '../../../types/appointment'
import { useRegisterPatient } from '../../../hooks/patient/usePatient'
import { attachmentApi } from '../../../services/attachment/attachmentApi'

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

  let estimatedDateOfBirth = ''
  if (appointment.tempPatientAge !== null && appointment.tempPatientAge !== undefined) {
    const age = appointment.tempPatientAge
    const date = new Date()
    date.setFullYear(date.getFullYear() - age)
    const yyyy = date.getFullYear()
    const mm = String(date.getMonth() + 1).padStart(2, '0')
    const dd = String(date.getDate()).padStart(2, '0')
    estimatedDateOfBirth = `${yyyy}-${mm}-${dd}`
  }

  const initialValues: Partial<PatientFormValues> = {
    salutation: appointment.tempPatientSalutation || '',
    firstName,
    lastName,
    gender: (appointment.tempPatientGender || 'MALE') as any,
    contactNumber: appointment.tempPatientPhone || '',
    estimatedDateOfBirth,
  }

  const handleSubmit = async (data: PatientFormValues, file: File | null) => {
    const patient = await registerPatient.mutateAsync(data)
    if (file) {
      try {
        await attachmentApi.upload(file, 'PATIENT_PICTURE', undefined, patient.id)
      } catch (err) {
        console.error('Failed to upload photo during quick registration', err)
      }
    }
    onSuccess(patient.id)
  }

  return (
    <Modal
      isOpen={true}
      onClose={onCancel}
      title="Patient Registration"
      description="Complete registration for walk-in appointment"
      size="3xl"
      showCloseButton={true}
    >
      <div className="flex flex-col max-h-[85vh] overflow-hidden">
        <div className="px-6 py-4 border-b border-gray-100 flex justify-between items-center bg-gray-50/50 pr-12">
          <h3 className="text-base font-bold text-gray-900">Patient Registration</h3>
        </div>

        <div className="p-6 overflow-y-auto custom-scrollbar flex-1">
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
    </Modal>
  )
}
