import { CategoryReportPage } from '../components/CategoryReportPage'
import { StockReportsTab } from '../components/StockReportsTab'

export default function StocksReportPage() {
  return (
    <CategoryReportPage title="Stock Reports">
      {(onViewReport) => <StockReportsTab onViewReport={onViewReport} />}
    </CategoryReportPage>
  )
}
