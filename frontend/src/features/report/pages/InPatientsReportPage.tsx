import { CategoryReportPage } from '../components/CategoryReportPage'
import { InPatientsReportsTab } from '../components/InPatientsReportsTab'

export default function InPatientsReportPage() {
  return (
    <CategoryReportPage title="In Patients Reports">
      {(onViewReport) => <InPatientsReportsTab onViewReport={onViewReport} />}
    </CategoryReportPage>
  )
}
