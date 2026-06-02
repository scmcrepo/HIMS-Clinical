import { CategoryReportPage } from '../components/CategoryReportPage'
import { DiagnosticsReportsTab } from '../components/DiagnosticsReportsTab'

export default function DiagnosticsReportPage() {
  return (
    <CategoryReportPage title="Diagnostics Reports">
      {(onViewReport) => <DiagnosticsReportsTab onViewReport={onViewReport} />}
    </CategoryReportPage>
  )
}
