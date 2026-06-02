import { CategoryReportPage } from '../components/CategoryReportPage'
import { CollectionsReportsTab } from '../components/CollectionsReportsTab'

export default function CollectionsReportPage() {
  return (
    <CategoryReportPage title="Collections Reports">
      {(onViewReport) => <CollectionsReportsTab onViewReport={onViewReport} />}
    </CategoryReportPage>
  )
}
