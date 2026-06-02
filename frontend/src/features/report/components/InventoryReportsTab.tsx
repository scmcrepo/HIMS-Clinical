import { useState } from 'react'
import { ReportCard, DateRangeType } from './ReportCard'
import { cn } from '../../../lib/utils'

interface InventoryReportsTabProps {
  onViewReport: (reportName: string, params: Record<string, string>) => void
}

export function InventoryReportsTab({ onViewReport }: InventoryReportsTabProps) {
  const [view, setView] = useState<'grid' | 'table'>('grid')
  
  const [reports, setReports] = useState([
    { id: '1', name: 'product_wise_return', title: 'Product Wise Return', description: 'Date / Product-wise returns analysis' },
    { id: '2', name: 'department_wise_return', title: 'Department Wise Return', description: 'Date / Department-wise returns analysis' },
    { id: '3', name: 'indent_report', title: 'Indent', description: 'Date-wise indents analysis' },
    { id: '4', name: 'indent_issue_report', title: 'Indent Issue', description: 'Date-wise indent issues analysis' },
  ])

  const [editingId, setEditingId] = useState<string | null>(null)
  const [formData, setFormData] = useState<any>({})

  const startEdit = (report: any) => {
    setEditingId(report.id)
    setFormData({ ...report })
  }

  const cancelEdit = () => {
    setEditingId(null)
    setFormData({})
  }

  const handleSave = () => {
    if (!formData.title) return
    setReports(prev => prev.map(r => r.id === formData.id ? { ...r, title: formData.title, description: formData.description } : r))
    cancelEdit()
  }

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
    <div className="space-y-6">
      {/* Header and Toggle switcher */}
      <div className="flex justify-between items-center bg-white border border-gray-200 rounded-xl p-4 shadow-sm">
        <div>
          <h3 className="text-base font-bold text-gray-800">Inventory Analysis</h3>
          <p className="text-xs text-gray-500 mt-0.5">Analyze indents, issues, and product/department wise returns.</p>
        </div>
        <div className="flex items-center gap-1 bg-gray-100 p-1 rounded-xl border border-gray-200/80">
          <button
            onClick={() => setView('grid')}
            className={cn(
              'flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-lg transition-all duration-150',
              view === 'grid'
                ? 'bg-white text-blue-600 shadow-sm border border-gray-150 font-bold'
                : 'text-gray-500 hover:text-gray-900'
            )}
          >
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z" />
            </svg>
            Grid
          </button>
          <button
            onClick={() => setView('table')}
            className={cn(
              'flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-lg transition-all duration-150',
              view === 'table'
                ? 'bg-white text-blue-600 shadow-sm border border-gray-150 font-bold'
                : 'text-gray-500 hover:text-gray-900'
            )}
          >
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M4 6h16M4 12h16M4 18h16" />
            </svg>
            Table
          </button>
        </div>
      </div>

      {view === 'grid' ? (
        <div className="space-y-1">
          {reports.map((r) => (
            <ReportCard
              key={r.id}
              title={r.title}
              reportName={r.name}
              onViewReport={onViewReport}
              renderSummary={(data, range) => {
                if (!data || data.length === 0) {
                  return renderWarning(`No records. There are no returns/indents`, range)
                }
                return <div className="text-sm font-medium text-gray-700 bg-gray-50 border p-3 rounded-lg">{data.length} records.</div>
              }}
            />
          ))}
        </div>
      ) : (
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
          <table className="w-full text-sm text-left">
            <thead className="bg-gray-50 text-gray-500 font-semibold text-xs uppercase tracking-wider">
              <tr>
                <th className="px-6 py-4 w-20">S.NO</th>
                <th className="px-6 py-4">REPORT NAME</th>
                <th className="px-6 py-4">DESCRIPTION</th>
                <th className="px-6 py-4 text-right">ACTIONS</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {reports.map((r, index) => (
                editingId === r.id ? (
                  <tr key={r.id} className="bg-blue-50/30">
                    <td className="px-6 py-4 font-semibold text-gray-500">{index + 1}</td>
                    <td className="px-6 py-4">
                      <input
                        type="text"
                        value={formData.title || ''}
                        onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                        className="w-full px-3 py-1.5 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 bg-white"
                      />
                    </td>
                    <td className="px-6 py-4">
                      <input
                        type="text"
                        value={formData.description || ''}
                        onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                        className="w-full px-3 py-1.5 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 bg-white"
                      />
                    </td>
                    <td className="px-6 py-4 text-right space-x-2">
                      <button
                        onClick={cancelEdit}
                        className="px-3 py-1.5 text-xs bg-gray-100 hover:bg-gray-200 text-gray-700 font-semibold rounded-lg transition-colors border"
                      >
                        Cancel
                      </button>
                      <button
                        onClick={handleSave}
                        disabled={!formData.title}
                        className="px-3 py-1.5 text-xs bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-lg transition-colors disabled:opacity-50"
                      >
                        Save
                      </button>
                    </td>
                  </tr>
                ) : (
                  <tr key={r.id} className="hover:bg-gray-50/55 transition-colors">
                    <td className="px-6 py-4 font-semibold text-gray-500">{index + 1}</td>
                    <td className="px-6 py-4 font-bold text-gray-900">{r.title}</td>
                    <td className="px-6 py-4 text-gray-500">{r.description}</td>
                    <td className="px-6 py-4 text-right space-x-2">
                      <button
                        onClick={() => startEdit(r)}
                        className="inline-flex items-center justify-center p-1.5 text-blue-600 hover:bg-blue-50 rounded-lg border border-transparent hover:border-blue-100 transition-all"
                        title="Edit Report Details"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
                        </svg>
                      </button>
                      <button
                        onClick={() => onViewReport(r.name, {})}
                        className="px-3 py-1.5 bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold rounded-lg transition-colors shadow-sm"
                      >
                        View Report
                      </button>
                    </td>
                  </tr>
                )
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
