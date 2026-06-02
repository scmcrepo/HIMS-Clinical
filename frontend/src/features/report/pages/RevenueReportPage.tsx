import { CategoryReportPage } from '../components/CategoryReportPage'
import { RevenueReportsTab } from '../components/RevenueReportsTab'

export default function RevenueReportPage() {
  return (
    <CategoryReportPage title="Revenue Analysis Reports">
      {(onViewReport) => <RevenueReportsTab onViewReport={onViewReport} />}
    </CategoryReportPage>
  )
}
