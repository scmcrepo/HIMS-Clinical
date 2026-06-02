import { CategoryReportPage } from '../components/CategoryReportPage'
import { InventoryReportsTab } from '../components/InventoryReportsTab'

export default function InventoryReportPage() {
  return (
    <CategoryReportPage title="Inventory Analysis Reports">
      {(onViewReport) => <InventoryReportsTab onViewReport={onViewReport} />}
    </CategoryReportPage>
  )
}
