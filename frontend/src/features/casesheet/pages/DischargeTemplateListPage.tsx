import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { dischargeTemplateApi } from '../../../services/casesheet/casesheetApi'
import { cn } from '../../../lib/utils'
import { toast } from '../../../hooks/useToast'

export default function DischargeTemplateListPage() {
  const [specFilter, setSpecFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState<'ACTIVE' | 'INACTIVE' | ''>('')
  const [confirmTemplate, setConfirmTemplate] = useState<{ id: string; name: string; status: 'ACTIVE' | 'INACTIVE' } | null>(null)
  const qc = useQueryClient()

  const { data: templates = [], isLoading } = useQuery({
    queryKey: ['discharge-templates', specFilter, statusFilter],
    queryFn:  () => dischargeTemplateApi.list(specFilter || undefined, statusFilter || undefined),
  })

  const toggleStatusMut = useMutation({
    mutationFn: ({ id, status }: { id: string; status: 'ACTIVE' | 'INACTIVE' }) =>
      dischargeTemplateApi.update(id, { status }),
    onSuccess: (_, variables) => {
      qc.invalidateQueries({ queryKey: ['discharge-templates'] })
      qc.invalidateQueries({ queryKey: ['discharge-templates-active'] })
      toast({ title: `Template marked as ${variables.status.toLowerCase()}`, variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Status update failed', description: e.message, variant: 'destructive' }),
  })

  const departments = [...new Set(templates.map(t => t.specialization))].sort()

  return (
    <div className="space-y-6 max-w-6xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Discharge Summary Templates</h1>
          
        </div>
        <Link
          to="/admin/discharge-templates/new"
          className="px-4 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 transition-all shadow-sm"
        >
          + New Template
        </Link>
      </div>

      {/* Filters */}
      <div className="flex gap-3 flex-wrap">
        <select
          value={specFilter}
          onChange={e => setSpecFilter(e.target.value)}
          className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500 min-w-44"
        >
          <option value="">All Departments</option>
          {departments.map(d => <option key={d} value={d}>{d}</option>)}
        </select>
        <select
          value={statusFilter}
          onChange={e => setStatusFilter(e.target.value as 'ACTIVE' | 'INACTIVE' | '')}
          className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500"
        >
          <option value="">All Statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="INACTIVE">Inactive</option>
        </select>
        {(specFilter || statusFilter) && (
          <button
            onClick={() => { setSpecFilter(''); setStatusFilter('') }}
            className="px-3 py-2 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            Clear filters
          </button>
        )}
      </div>

      {/* Table */}
      {isLoading ? (
        <div className="text-sm text-gray-500 py-8 text-center">Loading templates…</div>
      ) : templates.length === 0 ? (
        <div className="text-sm text-gray-400 py-12 text-center border border-dashed border-gray-200 rounded-xl">
          No templates found.{' '}
          <Link to="/admin/discharge-templates/new" className="text-neutral-600 hover:underline">Create one</Link>
        </div>
      ) : (
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
          <table className="w-full text-sm text-left">
            <thead className="bg-gray-50 text-gray-500 font-semibold text-xs uppercase tracking-wider border-b border-gray-200">
              <tr>
                {['Template Name', 'Department', 'Fields', 'Default', 'Status', 'Actions'].map(h => (
                  <th key={h} className="px-6 py-4">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {templates.map(t => (
                <tr key={t.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-6 py-4">
                    <p className="font-semibold text-gray-900">{t.name}</p>
                    {t.description && <p className="text-xs text-gray-400 mt-0.5 line-clamp-1">{t.description}</p>}
                  </td>
                  <td className="px-6 py-4">
                    <span className="inline-block px-2.5 py-1 rounded-full text-xs font-semibold bg-gray-100 text-gray-700">
                      {t.specialization}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-gray-600">{t.fieldCount} fields</td>
                  <td className="px-6 py-4">
                    {t.defaultTemplate ? (
                      <span className="text-emerald-600 text-xs font-semibold">✓ Default</span>
                    ) : (
                      <span className="text-gray-300 text-xs">—</span>
                    )}
                  </td>
                  <td className="px-6 py-4">
                    <span className={cn(
                      'px-2.5 py-1 rounded-full text-xs font-semibold',
                      t.status === 'ACTIVE'
                        ? 'bg-emerald-100 text-emerald-800'
                        : 'bg-gray-100 text-gray-600'
                    )}>
                      {t.status === 'ACTIVE' ? 'Active' : 'Inactive'}
                    </span>
                  </td>
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-2">
                      <Link
                        to={`/admin/discharge-templates/${t.id}`}
                        className="text-neutral-600 hover:text-neutral-900 font-medium text-xs"
                      >
                        Edit
                      </Link>
                      <button
                        onClick={() => {
                          const newStatus = t.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
                          setConfirmTemplate({ id: t.id, name: t.name, status: newStatus })
                        }}
                        disabled={toggleStatusMut.isPending}
                        className={cn(
                          "text-xs font-medium transition-colors disabled:opacity-50",
                          t.status === 'ACTIVE'
                            ? "text-yellow-600 hover:text-yellow-800"
                            : "text-emerald-600 hover:text-emerald-800"
                        )}
                      >
                        {t.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {confirmTemplate && (
        <div
          onClick={() => setConfirmTemplate(null)}
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm animate-in fade-in duration-200"
          style={{ marginTop: 0 }}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            className="bg-white rounded-2xl shadow-2xl p-6 w-full max-w-md mx-4 space-y-5 animate-in zoom-in-95 duration-200 border border-gray-100"
          >
            <div className="flex items-center gap-3 text-black">
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
              <h3 className="text-lg font-bold text-gray-900">Confirm Status Change</h3>
            </div>
            <p className="text-sm text-gray-600 leading-relaxed">
              Are you sure you want to mark template <span className="font-semibold text-gray-900">"{confirmTemplate.name}"</span> as <span className="font-semibold text-gray-900">{confirmTemplate.status.toLowerCase()}</span>?
            </p>
            <div className="flex justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={() => setConfirmTemplate(null)}
                className="px-4 py-2 border border-gray-200 text-sm font-semibold text-gray-600 rounded-lg hover:bg-gray-50 transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => {
                  toggleStatusMut.mutate({ id: confirmTemplate.id, status: confirmTemplate.status })
                  setConfirmTemplate(null)
                }}
                className="px-4 py-2 bg-neutral-600 hover:bg-neutral-700 text-white text-sm font-semibold rounded-lg shadow-sm transition-all"
              >
                Confirm
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
