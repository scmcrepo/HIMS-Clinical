import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { templateApi } from '../../../services/casesheet/casesheetApi'
import { cn } from '../../../lib/utils'
import { toast } from '../../../hooks/useToast'
import type { CaseSheetTemplateSummary, CaseSheetVisitType } from '../../../types/casesheet'

const VT_STYLES: Record<string, string> = {
  OP:   'bg-blue-50 text-blue-700 border-blue-200',
  IP:   'bg-purple-50 text-purple-700 border-purple-200',
  BOTH: 'bg-teal-50 text-teal-700 border-teal-200',
}

export default function TemplateListPage() {
  const [specFilter, setSpecFilter] = useState('')
  const [vtFilter, setVtFilter]     = useState<CaseSheetVisitType | ''>('')
  const qc = useQueryClient()

  const { data: templates = [], isLoading } = useQuery({
    queryKey: ['casesheet-templates', specFilter, vtFilter],
    queryFn:  () => templateApi.list(specFilter || undefined, vtFilter || undefined),
  })

  const deleteMut = useMutation({
    mutationFn: (id: string) => templateApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['casesheet-templates'] })
      toast({ title: 'Template deleted', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Delete failed', description: e.message, variant: 'destructive' }),
  })

  const specializations = [...new Set(templates.map(t => t.specialization))].sort()

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-900">Case Sheet Templates</h2>
          <p className="text-sm text-gray-500 mt-0.5">Manage form layouts per specialization and visit type</p>
        </div>
        <Link
          to="/admin/casesheet-templates/new"
          className="px-4 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 transition-colors"
        >
          + New Template
        </Link>
      </div>

      {/* Filters */}
      <div className="flex gap-3 flex-wrap">
        <select
          value={specFilter}
          onChange={e => setSpecFilter(e.target.value)}
          className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 min-w-44"
        >
          <option value="">All Specializations</option>
          {specializations.map(s => <option key={s} value={s}>{s}</option>)}
        </select>
        <select
          value={vtFilter}
          onChange={e => setVtFilter(e.target.value as CaseSheetVisitType | '')}
          className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">All Visit Types</option>
          <option value="OP">OP</option>
          <option value="IP">IP</option>
          <option value="BOTH">Both</option>
        </select>
        {(specFilter || vtFilter) && (
          <button
            onClick={() => { setSpecFilter(''); setVtFilter('') }}
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
          <Link to="/admin/casesheet-templates/new" className="text-blue-600 hover:underline">Create one</Link>
        </div>
      ) : (
        <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                {['Template Name', 'Specialization', 'Visit Type', 'Fields', 'Default', 'Actions'].map(h => (
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
                    <div className="flex items-center gap-2">
                      <Link
                        to={`/admin/casesheet-templates/${t.id}`}
                        className="px-3 py-1.5 text-xs font-semibold bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors"
                      >
                        Edit
                      </Link>
                      <button
                        onClick={() => {
                          if (window.confirm(`Delete template "${t.name}"? This cannot be undone.`)) {
                            deleteMut.mutate(t.id)
                          }
                        }}
                        disabled={deleteMut.isPending}
                        className="px-3 py-1.5 text-xs font-semibold bg-red-50 text-red-600 rounded-lg hover:bg-red-100 transition-colors disabled:opacity-50"
                      >
                        Delete
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
