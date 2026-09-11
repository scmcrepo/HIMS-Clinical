import { CategoryReportPage } from '../components/CategoryReportPage'
import { GstReportsTab } from '../components/GstReportsTab'

export default function GstReportPage() {
  return (
    <CategoryReportPage title="GST Reports">
      {(onViewReport) => <GstReportsTab onViewReport={onViewReport} />}
    </CategoryReportPage>
  )
}
