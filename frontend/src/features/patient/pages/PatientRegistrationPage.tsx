import { useNavigate, useSearchParams } from 'react-router-dom'
import { PatientForm, PatientFormValues } from '../components/PatientRegistrationForm'
import BackButton from '../../../components/shared/BackButton'
import { useRegisterPatient } from '../../../hooks/patient/usePatient'

export default function PatientRegistrationPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const returnTo = searchParams.get('returnTo')
  const registerPatient = useRegisterPatient()
  
  const handleSubmit = async (data: PatientFormValues) => {
    const patient = await registerPatient.mutateAsync(data)
    if (returnTo) {
      navigate(`${returnTo}?patientId=${patient.id}`)
    } else {
      navigate(`/patients/${patient.id}`)
    }
  }

  return (
    <div className="max-w-3xl mx-auto py-8">
      <div className="flex items-center justify-between mb-8 px-4">
        <div>
          <h2 className="text-3xl font-bold text-gray-900 tracking-tight">Register Patient</h2>
          <p className="text-gray-500 mt-1">Create a new patient record in the system</p>
        </div>
        <BackButton />
      </div>

      <PatientForm 
        onSubmit={handleSubmit}
        onCancel={() => navigate(-1)}
        isPending={registerPatient.isPending}
        error={registerPatient.error}
        submitLabel="Register Patient"
      />
    </div>
  )
}
