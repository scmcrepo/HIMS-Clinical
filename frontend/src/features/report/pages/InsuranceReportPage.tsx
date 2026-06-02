import { CategoryReportPage } from '../components/CategoryReportPage'
import { InsuranceReportsTab } from '../components/InsuranceReportsTab'

export default function InsuranceReportPage() {
  return (
    <CategoryReportPage title="Insurance Reports">
      {(onViewReport) => <InsuranceReportsTab onViewReport={onViewReport} />}
    </CategoryReportPage>
  )
}
