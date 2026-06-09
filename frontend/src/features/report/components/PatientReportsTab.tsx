import { ReportCard, DateRangeType } from './ReportCard'

interface PatientReportsTabProps {
  onViewReport: (reportName: string, params: Record<string, string>) => void
}

export function PatientReportsTab({ onViewReport }: PatientReportsTabProps) {
  const renderWarning = (message: string, rangeType: DateRangeType) => {
    const period = rangeType === 'today' ? 'today' : rangeType === 'current_month' ? 'this month' : 'last month'
    return (
      <div className="flex items-center justify-center bg-gray-50 border border-gray-200 text-gray-600 px-4 py-3 rounded-lg text-sm">
        <svg className="w-4 h-4 mr-2 text-gray-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
        <span>
          {message.replace(' ! ', '. ')} <span className="font-semibold text-gray-800">{period}</span>.
        </span>
      </div>
    )
  }

  return (
    <div className="space-y-1">
      <ReportCard
        title="Registrations"
        reportName="patient_registration_daywise"
        detailReportName="patient_registration_details"
        onViewReport={onViewReport}
        renderSummary={(data, range) => {
          if (!data || data.length === 0) {
            return renderWarning('No patient registered ! There is no patient registered', range)
          }
          const males = data.reduce((sum, r) => sum + (Number(r.male_count ?? r.MALE_COUNT) || 0), 0)
          const females = data.reduce((sum, r) => sum + (Number(r.female_count ?? r.FEMALE_COUNT) || 0), 0)
          return (
            <div className="flex divide-x border rounded-lg bg-gray-50 text-center text-sm">
              <div className="flex-1 p-2">
                <div className="text-gray-500 font-semibold mb-1">Male Patient</div>
                <div className="text-lg font-bold">{males}</div>
              </div>
              <div className="flex-1 p-2">
                <div className="text-gray-500 font-semibold mb-1">Female Patient</div>
                <div className="text-lg font-bold">{females}</div>
              </div>
              <div className="flex-1 p-2 bg-gray-100">
                <div className="text-gray-600 font-semibold mb-1">Total Patients</div>
                <div className="text-lg font-bold text-gray-900">
                  {data.reduce((sum, r) => sum + (Number(r.total_registered ?? r.TOTAL_REGISTERED) || 0), 0) || (males + females)}
                </div>
              </div>
            </div>
          )
        }}
      />

      <ReportCard
        title="Appointment Booked"
        reportName="appointments_daywise"
        detailReportName="appointment_scheduled_details"
        onViewReport={onViewReport}
        renderSummary={(data, range) => {
          if (!data || data.length === 0) {
            return renderWarning('No appointment booked ! There is no appointments booked', range)
          }
          const totalAppointments = data.reduce((sum, r) => sum + (Number(r.total_booked ?? r.TOTAL_BOOKED) || 0), 0);
          return <div className="text-sm font-medium text-gray-700 bg-gray-50 border p-3 rounded-lg">{totalAppointments} appointments booked.</div>
        }}
      />

      <ReportCard
        title="Appointment Cancelled"
        reportName="appointments_cancelled_daywise"
        detailReportName="appointment_cancelled_details"
        onViewReport={onViewReport}
        renderSummary={(data, range) => {
          if (!data || data.length === 0) {
            return renderWarning('No appointment cancelled ! There is no appointments cancelled', range)
          }
          const totalCancelled = data.reduce((sum, r) => sum + (Number(r.cancelled_count ?? r.CANCELLED_COUNT) || 0), 0);
          return <div className="text-sm font-medium text-gray-700 bg-gray-50 border p-3 rounded-lg">{totalCancelled} cancellations found.</div>
        }}
      />

      <ReportCard
        title="Encounter Details Report"
        reportName="encounters_report"
        detailReportName="visit_details"
        showConsultantFilter={true}
        onViewReport={onViewReport}
        renderSummary={(data, range) => {
          if (!data || data.length === 0) {
            return renderWarning('No patient visited ! There is no patient visited', range)
          }
          return <div className="text-sm font-medium text-gray-700 bg-gray-50 border p-3 rounded-lg">{data.length} patient visits found.</div>
        }}
      />

      <ReportCard
        title="Consultant Wise Encounter Report"
        reportName="consultant_wise_visit"
        onViewReport={onViewReport}
        renderSummary={(data, range) => {
          if (!data || data.length === 0) {
            return renderWarning('No patient visited ! There is no patient visited', range)
          }
          return (
            <div className="bg-gray-50 border p-3 rounded-lg max-h-40 overflow-y-auto">
              <ul className="space-y-1">
                {data.filter(r => r.Consultant !== 'Total').map((r, i) => (
                  <li key={i} className="flex justify-between text-sm">
                    <span className="text-gray-600">{r.Consultant}</span>
                    <span className="font-semibold text-gray-900">{r.Total}</span>
                  </li>
                ))}
              </ul>
            </div>
          )
        }}
      />

      <ReportCard
        title="Department Wise Encounter Report"
        reportName="department_wise_visit"
        onViewReport={onViewReport}
        renderSummary={(data, range) => {
          if (!data || data.length === 0) {
            return renderWarning('No patient Encountered ! There is no patient Encountered', range)
          }
          return (
            <div className="bg-gray-50 border p-3 rounded-lg max-h-40 overflow-y-auto">
              <ul className="space-y-1">
                {data.filter(r => r.Consultant !== 'Total' && r.Department !== 'Grand Total').reduce((acc, curr) => {
                  const newVal = Number(curr['New Patients'] ?? curr['New Visit'] ?? 0);
                  const oldVal = Number(curr['Old Patients'] ?? curr['Revisit'] ?? 0);
                  acc[curr.Department] = (acc[curr.Department] || 0) + newVal + oldVal;
                  return acc;
                }, {} as Record<string, number>).map ? null : Object.entries(
                  data.filter(r => r.Consultant !== 'Total' && r.Department !== 'Grand Total').reduce((acc, curr) => {
                    const newVal = Number(curr['New Patients'] ?? curr['New Visit'] ?? 0);
                    const oldVal = Number(curr['Old Patients'] ?? curr['Revisit'] ?? 0);
                    acc[curr.Department] = (acc[curr.Department] || 0) + newVal + oldVal;
                    return acc;
                  }, {} as Record<string, number>)
                ).map(([name, count]) => (
                  <li key={name} className="flex justify-between text-sm">
                    <span className="text-gray-600">{name}</span>
                    <span className="font-semibold text-gray-900">{count as React.ReactNode}</span>
                  </li>
                ))}
              </ul>
            </div>
          )
        }}
      />


      {/* Legacy links that don't have quick filters */}
      {/* <div className="bg-white border border-gray-200 rounded-xl shadow-sm mb-4">
        <table className="w-full text-sm">
          <tbody className="divide-y divide-gray-100">

            <tr>
              <td className="w-8"></td>
              <td className="py-4 font-semibold text-gray-700">Consultation Report</td>
              <td className="py-4 pr-5 text-right">
                <button
                  onClick={() => onViewReport('consultation_summary', todayParams)}
                  className="px-4 py-1.5 bg-neutral-600 text-white text-xs font-semibold rounded-lg hover:bg-neutral-700 transition-colors shadow-sm"
                >
                  VIEW REPORT
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div> */}
    </div>
  )
}
