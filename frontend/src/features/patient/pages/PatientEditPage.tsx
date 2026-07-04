import { useNavigate, useParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import BackButton from '../../../components/shared/BackButton'
import { usePatient, useUpdatePatient } from '../../../hooks/patient/usePatient'
import { PatientForm, PatientFormValues } from '../components/PatientRegistrationForm'
import { attachmentApi } from '../../../services/attachment/attachmentApi'

export default function PatientEditPage() {
  const { patientId } = useParams<{ patientId: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  
  const { data: patient, isLoading: patientLoading } = usePatient(patientId)
  const updatePatient = useUpdatePatient(patientId!)
  
  const handleSubmit = async (data: PatientFormValues, file: File | null, fileRemoved?: boolean) => {
    await updatePatient.mutateAsync(data)
    if (file) {
      try {
        await attachmentApi.upload(file, 'PATIENT_PICTURE', undefined, patientId)
        qc.invalidateQueries({ queryKey: ['patient-picture', patientId] })
      } catch (err) {
        console.error('Failed to upload photo during edit', err)
      }
    } else if (fileRemoved) {
      try {
        const attachments = await attachmentApi.getByPatient(patientId!)
        const pic = attachments.find(a => a.attachmentType === 'PATIENT_PICTURE')
        if (pic) {
          await attachmentApi.delete(pic.id)
          qc.invalidateQueries({ queryKey: ['patient-picture', patientId] })
        }
      } catch (err) {
        console.error('Failed to delete photo', err)
      }
    }
    navigate(`/patients/${patientId}`)
  }

  if (patientLoading) return <div className="p-6 text-gray-500">Loading...</div>
  if (!patient) return <div className="p-6 text-red-500">Patient not found</div>

  const initialValues: Partial<PatientFormValues> = {
    salutation: patient.salutation ?? undefined,
    firstName: patient.firstName,
    lastName: patient.lastName,
    gender: patient.gender as any,
    contactNumber: patient.contactNumber ?? '',
    email: patient.email ?? '',
    bloodGroup: patient.bloodGroup ?? '',
    address: patient.address ?? '',
    estimatedDateOfBirth: patient.estimatedDateOfBirth,
    isClinicalTrial: patient.isClinicalTrial
  }

  return (
    <div className="max-w-2xl mx-auto py-8">
      <div className="flex items-center justify-between mb-8 px-4">
        <h2 className="text-3xl font-bold text-gray-900 tracking-tight">Edit Patient</h2>
        <BackButton />
      </div>

      <PatientForm 
        initialValues={initialValues}
        onSubmit={handleSubmit}
        onCancel={() => navigate(-1)}
        isPending={updatePatient.isPending}
        error={updatePatient.error}
        submitLabel="Save Changes"
        isEdit={true}
        patientId={patientId}
      />
    </div>
  )
}
