import { CategoryReportPage } from '../components/CategoryReportPage'
import { PurchaseReportsTab } from '../components/PurchaseReportsTab'

export default function PurchaseReportPage() {
  return (
    <CategoryReportPage title="Purchase Reports">
      {(onViewReport) => <PurchaseReportsTab onViewReport={onViewReport} />}
    </CategoryReportPage>
  )
}
