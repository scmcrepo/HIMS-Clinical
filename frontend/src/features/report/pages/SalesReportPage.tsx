import { CategoryReportPage } from '../components/CategoryReportPage'
import { SalesReportsTab } from '../components/SalesReportsTab'

export default function SalesReportPage() {
  return (
    <CategoryReportPage title="Sales Reports">
      {(onViewReport) => <SalesReportsTab onViewReport={onViewReport} />}
    </CategoryReportPage>
  )
}
