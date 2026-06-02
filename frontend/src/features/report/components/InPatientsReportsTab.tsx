import { ReportCard } from './ReportCard'

interface InPatientsReportsTabProps {
  onViewReport: (reportName: string, params: Record<string, string>) => void
}

export function InPatientsReportsTab({ onViewReport }: InPatientsReportsTabProps) {
  const today = new Date()
  const y = today.getFullYear()
  const m = String(today.getMonth() + 1).padStart(2, '0')
  const d = String(today.getDate()).padStart(2, '0')
  const td = `${y}-${m}-${d}`

  return (
    <div className="space-y-1">
      {/* 1. Admission Report */}
      <ReportCard 
        title="Admission" 
        reportName="admissions_report" 
        onViewReport={(name, params) => onViewReport(name, { ...params, report_type: 'DETAIL' })}
        renderSummary={(data = []) => {
          let maleCount = 0
          let femaleCount = 0
          let totalCount = 0

          if (data.length > 0) {
            const firstRow = data[0]
            if ('male' in firstRow || 'female' in firstRow) {
              maleCount = data.reduce((sum: number, x: any) => sum + (Number(x.male) || 0), 0)
              femaleCount = data.reduce((sum: number, x: any) => sum + (Number(x.female) || 0), 0)
              totalCount = data.reduce((sum: number, x: any) => sum + (Number(x.total) || 0), 0)
            } else {
              maleCount = data.filter((x: any) => {
                const g = String(x.gender || x.Gender || '').toUpperCase()
                return g === '0' || g === 'MALE' || g === 'M'
              }).length
              femaleCount = data.filter((x: any) => {
                const g = String(x.gender || x.Gender || '').toUpperCase()
                return g === '1' || g === 'FEMALE' || g === 'F'
              }).length
              totalCount = data.length
            }
          }

          return (
            <div className="flex items-center gap-12 text-center py-2 max-w-xl">
              <div className="flex-1">
                <div className="text-[13px] font-semibold text-gray-500 mb-1">Male Patient</div>
                <div className="text-3xl font-normal text-blue-900">{maleCount}</div>
              </div>
              <div className="h-10 w-px bg-gray-200" />
              <div className="flex-1">
                <div className="text-[13px] font-semibold text-gray-500 mb-1">Female Patient</div>
                <div className="text-3xl font-normal text-blue-900">{femaleCount}</div>
              </div>
              <div className="h-10 w-px bg-gray-200" />
              <div className="flex-1">
                <div className="text-[13px] font-semibold text-gray-500 mb-1">Total No of Patients</div>
                <div className="text-3xl font-normal text-blue-900">{totalCount}</div>
              </div>
            </div>
          )
        }} 
      />

      {/* 2. Discharge Report */}
      <ReportCard 
        title="Discharge" 
        reportName="discharges_report" 
        onViewReport={onViewReport}
        renderSummary={(data = []) => {
          let maleCount = 0
          let femaleCount = 0
          let totalCount = 0

          if (data.length > 0) {
            const firstRow = data[0]
            if ('male' in firstRow || 'female' in firstRow) {
              maleCount = data.reduce((sum: number, x: any) => sum + (Number(x.male) || 0), 0)
              femaleCount = data.reduce((sum: number, x: any) => sum + (Number(x.female) || 0), 0)
              totalCount = data.reduce((sum: number, x: any) => sum + (Number(x.total) || 0), 0)
            } else {
              maleCount = data.filter((x: any) => {
                const g = String(x.gender || x.Gender || '').toUpperCase()
                return g === '0' || g === 'MALE' || g === 'M'
              }).length
              femaleCount = data.filter((x: any) => {
                const g = String(x.gender || x.Gender || '').toUpperCase()
                return g === '1' || g === 'FEMALE' || g === 'F'
              }).length
              totalCount = data.length
            }
          }

          return (
            <div className="flex items-center gap-12 text-center py-2 max-w-xl">
              <div className="flex-1">
                <div className="text-[13px] font-semibold text-gray-500 mb-1">Male Patient</div>
                <div className="text-3xl font-normal text-blue-900">{maleCount}</div>
              </div>
              <div className="h-10 w-px bg-gray-200" />
              <div className="flex-1">
                <div className="text-[13px] font-semibold text-gray-500 mb-1">Female Patient</div>
                <div className="text-3xl font-normal text-blue-900">{femaleCount}</div>
              </div>
              <div className="h-10 w-px bg-gray-200" />
              <div className="flex-1">
                <div className="text-[13px] font-semibold text-gray-500 mb-1">Total No of Patients</div>
                <div className="text-3xl font-normal text-blue-900">{totalCount}</div>
              </div>
            </div>
          )
        }} 
      />

      {/* 3. Bed Occupancy Rate Report */}
      <ReportCard 
        title="Bed Occupancy Rate" 
        reportName="bed_occupancy_period" 
        hideFilters={true}
        onViewReport={onViewReport}
        renderSummary={(data = []) => {
          // Group by ward to avoid duplicate rows and aggregate occupancy pct
          const wardMap: Record<string, { ward: string, occupied: number, total: number, pct: number, numDays: number }> = {}
          
          data.forEach((row: any) => {
            const wardName = row.ward || 'Unknown'
            if (!wardMap[wardName]) {
              wardMap[wardName] = { ward: wardName, occupied: 0, total: 0, pct: 0, numDays: 30 }
            }
            wardMap[wardName].occupied += Number(row.occupied_days) || 0
            wardMap[wardName].total += Number(row.total_beds) || 0
            wardMap[wardName].pct = Number(row.occupancy_pct) || 0
            wardMap[wardName].numDays = Number(row.num_days) || 30
          })

          const uniqueWards = Object.values(wardMap)
          const displayWards = uniqueWards.length > 0 ? uniqueWards : [
            { ward: 'AC ROOM', pct: 0, occupied: 0, total: 0, numDays: 30 },
            { ward: 'SEMI PRIVATE ROOM', pct: 0, occupied: 0, total: 0, numDays: 30 }
          ]

          const totalOccupied = displayWards.reduce((sum, w) => sum + w.occupied, 0)
          const totalBeds = displayWards.reduce((sum, w) => sum + w.total, 0)
          const periodDays = displayWards[0]?.numDays || 30
          // BOR formula: occupied_days * 100 / (total_beds * periodDays)
          const totalPct = totalBeds > 0 ? (totalOccupied * 100) / (totalBeds * periodDays) : 0

          return (
            <div className="max-w-2xl border border-gray-200 rounded-lg overflow-hidden bg-white">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="bg-gray-50 border-b border-gray-200">
                    <th className="px-4 py-2 font-bold text-gray-700">Bed Type</th>
                    <th className="px-4 py-2 font-bold text-gray-700 text-right">BOR</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {displayWards.map((w: any, idx) => (
                    <tr key={idx} className="hover:bg-gray-50/55 transition-colors">
                      <td className="px-4 py-2 font-medium text-gray-600">{w.ward}</td>
                      <td className="px-4 py-2 text-gray-600 text-right">{Number(w.pct).toFixed(2)} %</td>
                    </tr>
                  ))}
                  <tr className="bg-gray-50 font-bold text-gray-900 border-t border-gray-200">
                    <td className="px-4 py-2 text-gray-900">Total</td>
                    <td className="px-4 py-2 text-gray-900 text-right">
                      {(data.length > 0 ? totalPct : 0).toFixed(2)}%
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          )
        }} 
      />

      {/* 4. Beds Transferred Report */}
      <div className="bg-white border border-gray-200 rounded-xl shadow-sm mb-4">
        <table className="w-full text-sm">
          <tbody className="divide-y divide-gray-100">
            <tr>
              <td className="w-8"></td>
              <td className="py-4 font-semibold text-gray-700">Beds Transferred</td>
              <td className="py-4 pr-5 text-right w-48">
                <button
                  type="button"
                  onClick={() => onViewReport('beds_transferred', { from_date: td, to_date: td })}
                  className="px-4 py-1.5 bg-blue-600 text-white text-xs font-semibold rounded-lg hover:bg-blue-700 transition-colors shadow-sm"
                >
                  VIEW DETAIL REPORT
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  )
}
