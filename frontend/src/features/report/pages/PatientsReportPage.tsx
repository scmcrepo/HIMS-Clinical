import { CategoryReportPage } from '../components/CategoryReportPage'
import { PatientReportsTab } from '../components/PatientReportsTab'

export default function PatientsReportPage() {
  return (
    <CategoryReportPage title="Encounter Reports">
      {(onViewReport) => <PatientReportsTab onViewReport={onViewReport} />}
    </CategoryReportPage>
  )
}
