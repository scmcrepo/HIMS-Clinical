import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { templateApi } from '../../../services/casesheet/casesheetApi'
import { cn } from '../../../lib/utils'
import { toast } from '../../../hooks/useToast'
import type { CaseSheetVisitType } from '../../../types/casesheet'

const VT_STYLES: Record<string, string> = {
  OP:   'bg-blue-50 text-blue-700 border-blue-200',
  IP:   'bg-purple-50 text-purple-700 border-purple-200',
  BOTH: 'bg-teal-50 text-teal-700 border-teal-200',
}

export default function TemplateListPage() {
  const [specFilter, setSpecFilter] = useState('')
  const [vtFilter, setVtFilter]     = useState<CaseSheetVisitType | ''>('')
  const [statusFilter, setStatusFilter] = useState<'ACTIVE' | 'INACTIVE' | ''>('')
  const qc = useQueryClient()

  const { data: templates = [], isLoading } = useQuery({
    queryKey: ['casesheet-templates', specFilter, vtFilter, statusFilter],
    queryFn:  () => templateApi.list(specFilter || undefined, vtFilter || undefined, statusFilter || undefined),
  })

  const toggleStatusMut = useMutation({
    mutationFn: ({ id, status }: { id: string; status: 'ACTIVE' | 'INACTIVE' }) =>
      templateApi.update(id, { status }),
    onSuccess: (_, variables) => {
      qc.invalidateQueries({ queryKey: ['casesheet-templates'] })
      toast({ title: `Template marked as ${variables.status.toLowerCase()}`, variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Status update failed', description: e.message, variant: 'destructive' }),
  })

  const departments = [...new Set(templates.map(t => t.specialization))].sort()

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-900">Case Sheet Templates</h2>
          <p className="text-sm text-gray-500 mt-0.5">Manage form layouts per department and encounter type</p>
        </div>
        <Link
          to="/admin/casesheet-templates/new"
          className="px-4 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 transition-colors"
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
          value={vtFilter}
          onChange={e => setVtFilter(e.target.value as CaseSheetVisitType | '')}
          className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500"
        >
          <option value="">All Encounter Types</option>
          <option value="OP">OP</option>
          <option value="IP">IP</option>
          <option value="BOTH">Both</option>
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
        {(specFilter || vtFilter || statusFilter) && (
          <button
            onClick={() => { setSpecFilter(''); setVtFilter(''); setStatusFilter('') }}
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
          <Link to="/admin/casesheet-templates/new" className="text-neutral-600 hover:underline">Create one</Link>
        </div>
      ) : (
        <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                {['Template Name', 'Department', 'Encounter Type', 'Fields', 'Default', 'Status', 'Actions'].map(h => (
                  <th key={h} className="text-left px-4 py-3 text-xs font-semibold text-gray-600 uppercase tracking-wide">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {templates.map(t => (
                <tr key={t.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-3">
                    <p className="font-semibold text-gray-900">{t.name}</p>
                    {t.description && <p className="text-xs text-gray-400 mt-0.5 line-clamp-1">{t.description}</p>}
                  </td>
                  <td className="px-4 py-3">
                    <span className="inline-block px-2 py-0.5 rounded text-xs font-semibold bg-gray-100 text-gray-700">
                      {t.specialization}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <span className={cn('inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border', VT_STYLES[t.visitType])}>
                      {t.visitType}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-gray-600 text-xs">{t.fieldCount} fields</td>
                  <td className="px-4 py-3">
                    {t.defaultTemplate ? (
                      <span className="text-green-600 text-xs font-semibold">✓ Default</span>
                    ) : (
                      <span className="text-gray-300 text-xs">—</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <span className={cn(
                      'inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold border',
                      t.status === 'ACTIVE'
                        ? 'bg-green-50 text-green-700 border-green-200'
                        : 'bg-yellow-50 text-yellow-700 border-yellow-200'
                    )}>
                      {t.status}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      <Link
                        to={`/admin/casesheet-templates/${t.id}`}
                        className="px-3 py-1.5 text-xs font-semibold bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors"
                      >
                        Edit
                      </Link>
                      <button
                        onClick={() => {
                          const newStatus = t.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
                          if (window.confirm(`Mark template "${t.name}" as ${newStatus.toLowerCase()}?`)) {
                            toggleStatusMut.mutate({ id: t.id, status: newStatus })
                          }
                        }}
                        disabled={toggleStatusMut.isPending}
                        className={cn(
                          "px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors disabled:opacity-50",
                          t.status === 'ACTIVE'
                            ? "bg-yellow-50 text-yellow-700 hover:bg-yellow-100"
                            : "bg-green-50 text-green-700 hover:bg-green-100"
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
    </div>
  )
}
