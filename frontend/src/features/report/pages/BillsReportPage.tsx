import { CategoryReportPage } from '../components/CategoryReportPage'
import { BillsReportsTab } from '../components/BillsReportsTab'

export default function BillsReportPage() {
  return (
    <CategoryReportPage title="Bills Reports">
      {(onViewReport) => <BillsReportsTab onViewReport={onViewReport} />}
    </CategoryReportPage>
  )
}
