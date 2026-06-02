
import { useState, useEffect, Fragment } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { cn } from '../../../../lib/utils';
import { toast } from '../../../../hooks/useToast';
import { inputCls, Field, EmptyState, Section, LoadingRow, AddButton } from '../MasterSharedUI';
import { specimenApi } from '../../../../services/diagnostic/specimenApi';
import { resultTemplateApi } from '../../../../services/masters/masterApi';

export default function ResultTemplateTab() {
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [query, setQuery] = useState('')
  const [expandedRows, setExpandedRows] = useState<Record<string, boolean>>({})
  const toggleRow = (id: string) => {
    setExpandedRows(prev => ({ ...prev, [id]: !prev[id] }))
  }
  useEffect(() => { const t = setTimeout(() => { setQuery(search); setPage(0) }, 400); return () => clearTimeout(t) }, [search])
  useEffect(() => {
    setExpandedRows({})
  }, [page, query])
  const { data: pageData, isLoading } = useQuery({
    queryKey: ['diagTemplates', 'page', page, query],
    queryFn: () => resultTemplateApi.getPaginated({ start: page * 10, limit: 10, ...(query ? { value: query } : {}) })
  })
  const items = pageData?.content ?? []
  const totalPages = pageData?.totalPages ?? 0
  const totalElements = pageData?.totalElements ?? 0
  const { data: departments = [] } = useQuery({ queryKey: ['diagDepartments'], queryFn: resultTemplateApi.getDepartments })
  const { data: specimenList = [] } = useQuery({ queryKey: ['specimens'], queryFn: specimenApi.getAll })
  const { data: printTemplates = [] } = useQuery({
    queryKey: ['printTemplatesList'],
    queryFn: () => import('../../../../services/masters/masterApi').then(m => m.printTemplateApi.getAll())
  })



  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<any>(null)

  const blank = {
    name: '',
    diagnosticType: 'LAB',
    format: 'LAB_TEMPLATE',
    departmentId: '',
    specimenId: '',
    unit: '',
    referenceRange: '',
    chargeId: '',
    orderNumber: 0,
    header: '',
    method: '',
    templateHtml: '',
    labTemplateType: '',
    labTemplateDetails: [] as Array<{
      id?: string;
      resultName: string;
      normalRange?: string;
      normalRangeExp?: string;
      unit?: string;
      labType: string;
      orderNumber: number;
      rowCount?: number;
      status?: number;
    }>
  }
  const [form, setForm] = useState(blank)

  // Inner parameter row editing states
  const [editingRowIdx, setEditingRowIdx] = useState<number | null>(null)
  const [editRowData, setEditRowData] = useState<any>(null)
  const [newParam, setNewParam] = useState({
    labType: 'NUMERIC',
    orderNumber: 1,
    resultName: '',
    normalRange: '',
    normalRangeExp: '',
    unit: '',
    rowCount: 1
  })

  const handleStartEditRow = (idx: number, row: any) => {
    setEditingRowIdx(idx)
    setEditRowData({ ...row })
  }

  const handleSaveEditRow = (idx: number) => {
    if (!editRowData.resultName.trim()) return
    setForm(f => ({
      ...f,
      labTemplateDetails: f.labTemplateDetails.map((row, i) => i === idx ? editRowData : row)
    }))
    setEditingRowIdx(null)
    setEditRowData(null)
  }

  const handleCancelEditRow = () => {
    setEditingRowIdx(null)
    setEditRowData(null)
  }

  const handleAddParam = () => {
    if (!newParam.resultName.trim()) return
    setForm(f => ({
      ...f,
      labTemplateDetails: [
        ...f.labTemplateDetails,
        {
          resultName: newParam.resultName,
          normalRange: newParam.normalRange,
          normalRangeExp: newParam.normalRangeExp,
          unit: newParam.unit,
          labType: newParam.labType,
          orderNumber: newParam.orderNumber,
          rowCount: newParam.rowCount,
          status: 1
        }
      ]
    }))
    setNewParam(prev => ({
      ...prev,
      resultName: '',
      normalRange: '',
      normalRangeExp: '',
      unit: '',
      rowCount: 1,
      orderNumber: prev.orderNumber + 1
    }))
  }

  const mut = useMutation({
    mutationFn: async () => {
      const m = await import('../../../../services/masters/masterApi')
      return editing ? m.resultTemplateApi.update(editing.id, form) : m.resultTemplateApi.create(form)
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['diagTemplates'] }); reset(); toast({ title: editing ? 'Result template updated successfully' : 'Result template saved successfully', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  function reset() {
    setShowForm(false)
    setEditing(null)
    setForm(blank)
    setEditingRowIdx(null)
    setEditRowData(null)
    setNewParam({
      labType: 'NUMERIC',
      orderNumber: 1,
      resultName: '',
      normalRange: '',
      normalRangeExp: '',
      unit: '',
      rowCount: 1
    })
  }

  function startEdit(r: any) {
    setEditing(r)
    setForm({
      name: r.name,
      diagnosticType: r.diagnosticType,
      format: r.format,
      departmentId: r.department?.id ?? '',
      specimenId: r.specimenId ?? '',
      unit: r.unit ?? '',
      referenceRange: r.referenceRange ?? '',
      chargeId: r.chargeId ?? '',
      orderNumber: r.orderNumber ?? 0,
      header: r.header ?? '',
      method: r.method ?? '',
      templateHtml: r.templateHtml ?? '',
      labTemplateType: r.labTemplateType ?? '',
      labTemplateDetails: r.labTemplateDetails ?? []
    })
    setNewParam(prev => ({ ...prev, orderNumber: (r.labTemplateDetails?.length ?? 0) + 1 }))
    setShowForm(true)
  }

  const removeDetailRow = (index: number) => {
    setForm(f => ({
      ...f,
      labTemplateDetails: f.labTemplateDetails.filter((_, i) => i !== index)
    }))
  }

  return (
    <Section
      title="Result Templates"
      description="Master templates for lab and radiology parameters"
      action={
        <div className="flex gap-4 items-center">
          <input
            type="text"
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0); }}
            className="px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all w-64"
          />
          <AddButton label="Add Result Template" onClick={() => { reset(); setShowForm(true) }} />
        </div>
      }
    >
      {showForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 animate-in fade-in duration-150 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-4xl overflow-hidden border border-gray-100 flex flex-col max-h-[90vh] animate-in zoom-in-95 duration-150">

            {/* Modal Header */}
            <div className="border-b border-gray-100 px-6 py-4 flex justify-between items-center text-gray-800 bg-gray-50/50">
              <h3 className="text-sm font-bold uppercase tracking-wider text-gray-700">
                {editing ? `${form.name || 'Edit Template'} - ${departments.find(d => d.id === form.departmentId)?.name || 'NO DEPARTMENT'}` : 'New Result Template'}
              </h3>
              <button onClick={reset} className="text-gray-400 hover:text-gray-600 p-1.5 rounded-lg transition-colors focus:outline-none">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" /></svg>
              </button>
            </div>

            {/* Modal Content */}
            <div className="p-6 overflow-y-auto space-y-5 flex-1 bg-white">

              {/* Core fields */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-x-12 gap-y-6 bg-white p-5 rounded-xl">

                {/* Name & Format row */}
                {!editing && (
                  <Field label="Template Name *">
                    <input className={inputCls} value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} />
                  </Field>
                )}
                <div className={!editing ? 'hidden md:block' : 'hidden'}></div>

                <Field label="Format">
                  <select className={inputCls} value={form.format} onChange={e => setForm(f => ({ ...f, format: e.target.value }))}>
                    <option value="LAB_TEMPLATE">LAB TEMPLATE</option>
                    <option value="CUSTOM_TEMPLATE">CUSTOM TEMPLATE</option>
                  </select>
                </Field>
                {form.format === 'CUSTOM_TEMPLATE' ? (
                  <Field label="Diagnostic Templates">
                    <select
                      className={inputCls}
                      value={form.labTemplateType ?? ''}
                      onChange={e => {
                        const val = e.target.value;
                        const selectedPrintTemplate = printTemplates.find((pt: any) => pt.id === val || pt.name === val);
                        setForm(f => ({
                          ...f,
                          labTemplateType: val,
                          templateHtml: selectedPrintTemplate ? selectedPrintTemplate.content : f.templateHtml
                        }));
                      }}
                    >
                      <option value="">Select diagnostic template...</option>
                      {printTemplates
                        .filter((pt: any) => pt.documentType === 'LAB_REPORT' || pt.documentType === 'RADIOLOGY_REPORT')
                        .map((pt: any) => (
                          <option key={pt.id} value={pt.name}>{pt.name}</option>
                        ))}
                      <option value="DIAGNOSTIC_TEMPLATE">DIAGNOSTIC_TEMPLATE</option>
                    </select>
                  </Field>
                ) : (
                  <div className="hidden md:block"></div>
                )}

                {/* Department Row */}
                <Field label="Department">
                  <select className={inputCls} value={form.departmentId} onChange={e => setForm(f => ({ ...f, departmentId: e.target.value }))}>
                    <option value="">Select…</option>
                    {departments.filter((d: any) => d.status !== 'INACTIVE' && d.status !== 0).map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
                  </select>
                </Field>

                {form.format === 'LAB_TEMPLATE' && (
                  <Field label="Method">
                    <input className={inputCls} value={form.method} onChange={e => setForm(f => ({ ...f, method: e.target.value }))} />
                  </Field>
                )}

                {/* Specimen Row */}
                {form.format === 'LAB_TEMPLATE' && (
                  <>
                    <Field label="Specimen">
                      <select className={inputCls} value={form.specimenId} onChange={e => setForm(f => ({ ...f, specimenId: e.target.value }))}>
                        <option value="">Select…</option>
                        {specimenList.filter((s: any) => s.status !== 'INACTIVE' && s.status !== 0).map((s: any) => <option key={s.id} value={s.id}>{s.name}</option>)}
                      </select>
                    </Field>
                    <Field label="Order No">
                      <input type="number" className={inputCls} value={form.orderNumber} onChange={e => setForm(f => ({ ...f, orderNumber: parseInt(e.target.value) || 0 }))} />
                    </Field>
                  </>
                )}
              </div>

              {/* Lab Parameters Table */}
              {form.format === 'LAB_TEMPLATE' && (
                <div className="border border-gray-150 rounded-xl overflow-hidden shadow-sm">
                  <div className="px-4 py-3 bg-gray-50/80 border-b border-gray-150 flex justify-between items-center">
                    <h4 className="text-xs font-bold text-gray-700 uppercase tracking-wider">Lab Parameters (Structured Details)</h4>
                  </div>

                  <div className="overflow-x-auto">
                    <table className="w-full text-left border-collapse text-xs">
                      <thead>
                        <tr className="bg-gray-100 border-b border-gray-200 text-gray-700 uppercase font-bold tracking-wider">
                          <th className="px-3 py-2 w-28">TYPE</th>
                          <th className="px-3 py-2 w-16 text-center">ROWS</th>
                          <th className="px-3 py-2 w-16 text-center">ORDER</th>
                          <th className="px-3 py-2">RESULT NAME</th>
                          <th className="px-3 py-2">NORMAL RANGE</th>
                          <th className="px-3 py-2 font-mono">EXPRESSION</th>
                          <th className="px-3 py-2 w-20">UNIT</th>
                          <th className="px-3 py-2 w-20 text-center">ACTIONS</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100 bg-white">
                        {form.labTemplateDetails.map((row, idx) => {
                          const isEditingRow = editingRowIdx === idx;
                          const displayType = row.labType === 'HEADER' ? 'Heading' :
                            row.labType === 'FORMULA' ? 'Formula' :
                              row.labType === 'TEXT' ? 'Text' : 'Numeric';
                          if (isEditingRow) {
                            return (
                              <tr key={idx} className="bg-blue-50/40">
                                <td className="px-2 py-1">
                                  <select
                                    className="w-full px-2 py-1 border border-gray-300 rounded text-xs bg-white focus:ring-1 focus:ring-blue-500 focus:outline-none"
                                    value={editRowData.labType}
                                    onChange={e => setEditRowData({ ...editRowData, labType: e.target.value })}
                                  >
                                    <option value="TEXT">Text</option>
                                    <option value="NUMERIC">Numeric</option>
                                    <option value="FORMULA">Formula</option>
                                    <option value="HEADER">Heading</option>
                                  </select>
                                </td>
                                <td className="px-2 py-1 text-center">
                                  <input
                                    type="number"
                                    className="w-12 px-1 py-1 border border-gray-300 rounded text-xs text-center"
                                    value={editRowData.rowCount ?? 1}
                                    onChange={e => setEditRowData({ ...editRowData, rowCount: parseInt(e.target.value) || 1 })}
                                  />
                                </td>
                                <td className="px-2 py-1 text-center">
                                  <input
                                    type="number"
                                    className="w-12 px-1 py-1 border border-gray-300 rounded text-xs text-center"
                                    value={editRowData.orderNumber}
                                    onChange={e => setEditRowData({ ...editRowData, orderNumber: parseInt(e.target.value) || 0 })}
                                  />
                                </td>
                                <td className="px-2 py-1" colSpan={editRowData.labType === 'HEADER' ? 4 : 1}>
                                  <input
                                    type="text"
                                    className="w-full px-2 py-1 border border-gray-300 rounded text-xs"
                                    value={editRowData.resultName}
                                    onChange={e => setEditRowData({ ...editRowData, resultName: e.target.value })}
                                  />
                                </td>
                                {editRowData.labType !== 'HEADER' && (
                                  <>
                                    <td className="px-2 py-1">
                                      <input
                                        type="text"
                                        className="w-full px-2 py-1 border border-gray-300 rounded text-xs"
                                        value={editRowData.normalRange ?? ''}
                                        onChange={e => setEditRowData({ ...editRowData, normalRange: e.target.value })}
                                      />
                                    </td>
                                    <td className="px-2 py-1">
                                      <input
                                        type="text"
                                        className="w-full px-2 py-1 border border-gray-300 rounded text-xs font-mono"
                                        value={editRowData.normalRangeExp ?? ''}
                                        onChange={e => setEditRowData({ ...editRowData, normalRangeExp: e.target.value })}
                                      />
                                    </td>
                                    <td className="px-2 py-1">
                                      <input
                                        type="text"
                                        className="w-full px-2 py-1 border border-gray-300 rounded text-xs"
                                        value={editRowData.unit ?? ''}
                                        onChange={e => setEditRowData({ ...editRowData, unit: e.target.value })}
                                      />
                                    </td>
                                  </>
                                )}
                                <td className="px-2 py-1 text-center">
                                  <div className="flex items-center justify-center gap-1.5">
                                    <button
                                      type="button"
                                      onClick={() => handleSaveEditRow(idx)}
                                      className="p-1 text-green-600 hover:text-green-800 hover:bg-green-50 rounded"
                                      title="Save Row"
                                    >
                                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M5 13l4 4L19 7" /></svg>
                                    </button>
                                    <button
                                      type="button"
                                      onClick={handleCancelEditRow}
                                      className="p-1 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded"
                                      title="Cancel"
                                    >
                                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" /></svg>
                                    </button>
                                  </div>
                                </td>
                              </tr>
                            )
                          }

                          return (
                            <tr key={idx} className="hover:bg-gray-50/50">
                              <td className="px-3 py-2.5 font-medium text-gray-700">{displayType}</td>
                              <td className="px-3 py-2.5 text-center text-gray-600">{row.rowCount ?? 1}</td>
                              <td className="px-3 py-2.5 text-center text-gray-600">{row.orderNumber}</td>
                              <td className="px-3 py-2.5 font-semibold text-gray-900" colSpan={row.labType === 'HEADER' ? 4 : 1}>{row.resultName}</td>
                              {row.labType !== 'HEADER' && (
                                <>
                                  <td className="px-3 py-2.5 text-gray-500 whitespace-pre-wrap">{row.normalRange ?? '—'}</td>
                                  <td className="px-3 py-2.5 font-mono text-xs text-gray-500 max-w-[200px] overflow-hidden text-ellipsis">{row.normalRangeExp ?? '—'}</td>
                                  <td className="px-3 py-2.5 text-gray-500">{row.unit ?? '—'}</td>
                                </>
                              )}
                              <td className="px-3 py-2.5 text-center">
                                <div className="flex items-center justify-center gap-1">
                                  <button
                                    type="button"
                                    onClick={() => handleStartEditRow(idx, row)}
                                    className="p-1 text-blue-600 hover:text-blue-800 hover:bg-blue-50 rounded"
                                    title="Edit Parameter"
                                  >
                                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" /></svg>
                                  </button>
                                  <button
                                    type="button"
                                    onClick={() => removeDetailRow(idx)}
                                    className="p-1 text-red-500 hover:text-red-700 hover:bg-red-50 rounded"
                                    title="Remove Parameter"
                                  >
                                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" /></svg>
                                  </button>
                                </div>
                              </td>
                            </tr>
                          )
                        })}

                        {/* Add Row form row matching SCMC */}
                        <tr className="bg-gray-50/70 border-t border-gray-200">
                          <td className="px-2 py-2">
                            <select
                              className="w-full px-2 py-1.5 border border-gray-300 rounded text-xs bg-white focus:outline-none focus:ring-1 focus:ring-blue-500"
                              value={newParam.labType}
                              onChange={e => setNewParam({ ...newParam, labType: e.target.value })}
                            >
                              <option value="TEXT">Text</option>
                              <option value="NUMERIC">Numeric</option>
                              <option value="FORMULA">Formula</option>
                              <option value="HEADER">Heading</option>
                            </select>
                          </td>
                          <td className="px-2 py-2 text-center">
                            <input
                              type="number"
                              className="w-12 px-1 py-1 border border-gray-300 rounded text-xs text-center bg-white"
                              value={newParam.rowCount}
                              onChange={e => setNewParam({ ...newParam, rowCount: parseInt(e.target.value) || 1 })}
                            />
                          </td>
                          <td className="px-2 py-2 text-center">
                            <input
                              type="number"
                              className="w-12 px-1 py-1 border border-gray-300 rounded text-xs text-center bg-white"
                              value={newParam.orderNumber}
                              onChange={e => setNewParam({ ...newParam, orderNumber: parseInt(e.target.value) || 1 })}
                            />
                          </td>
                          <td className="px-2 py-2" colSpan={newParam.labType === 'HEADER' ? 4 : 1}>
                            <input
                              type="text"
                              className="w-full px-2 py-1.5 border border-gray-300 rounded text-xs bg-white"
                              value={newParam.resultName}
                              onChange={e => setNewParam({ ...newParam, resultName: e.target.value })}
                            />
                          </td>
                          {newParam.labType !== 'HEADER' && (
                            <>
                              <td className="px-2 py-2">
                                <input
                                  type="text"
                                  className="w-full px-2 py-1.5 border border-gray-300 rounded text-xs bg-white"
                                  value={newParam.normalRange}
                                  onChange={e => setNewParam({ ...newParam, normalRange: e.target.value })}
                                />
                              </td>
                              <td className="px-2 py-2">
                                <input
                                  type="text"
                                  className="w-full px-2 py-1.5 border border-gray-300 rounded text-xs font-mono bg-white"
                                  value={newParam.normalRangeExp}
                                  onChange={e => setNewParam({ ...newParam, normalRangeExp: e.target.value })}
                                />
                              </td>
                              <td className="px-2 py-2">
                                <input
                                  type="text"
                                  className="w-full px-2 py-1.5 border border-gray-300 rounded text-xs bg-white"
                                  value={newParam.unit}
                                  onChange={e => setNewParam({ ...newParam, unit: e.target.value })}
                                />
                              </td>
                            </>
                          )}
                          <td className="px-2 py-2 text-center">
                            <button
                              type="button"
                              onClick={handleAddParam}
                              className="px-3.5 py-1.5 bg-blue-600 text-white font-bold rounded-lg hover:bg-blue-700 transition-colors shadow-sm text-sm"
                            >
                              +
                            </button>
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>
              )}



            </div>

            {/* Modal Footer */}
            <div className="px-6 py-4 border-t border-gray-100 flex justify-end gap-3 bg-gray-50">
              <button
                type="button"
                onClick={reset}
                className="px-5 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
              >Cancel</button>
              <button
                type="button"
                onClick={() => mut.mutate()}
                disabled={mut.isPending || !form.name}
                className="px-6 py-2 text-sm font-bold text-white bg-[#e03a4f] rounded-lg hover:bg-[#d02f43] disabled:opacity-50 transition-all shadow-sm"
              >
                {mut.isPending ? (editing ? 'Updating…' : 'Creating…') : (editing ? 'Update Result Template' : 'Create')}
              </button>
            </div>

          </div>
        </div>
      )}



      <div className="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-gray-50 border-b border-gray-100">
              <th className="px-4 py-3 w-12 text-center"></th>
              <th className="px-4 py-3 text-[11px] font-bold text-gray-500 uppercase tracking-wider text-center w-16">S.NO</th>
              <th className="px-4 py-3 text-[11px] font-bold text-gray-500 uppercase tracking-wider !text-left">TEST NAME</th>
              <th className="px-4 py-3 text-[11px] font-bold text-gray-500 uppercase tracking-wider !text-left">CATEGORY</th>
              <th className="px-4 py-3 text-[11px] font-bold text-gray-500 uppercase tracking-wider text-center w-36">REPORT TEMPLATE</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
        {isLoading ? <LoadingRow /> : items.length === 0 ? <EmptyState label="templates" /> :
          items.map((t: any, idx: number) => {
            const isExpanded = !!expandedRows[t.id];
            return (
              <Fragment key={t.id}>
                <tr className="hover:bg-gray-50/80 border-b border-gray-100 transition-colors">
                  <td className="px-2 py-3 text-center w-12">
                    <button
                      type="button"
                      onClick={() => toggleRow(t.id)}
                      className="p-1 hover:bg-gray-100 rounded text-gray-400 hover:text-gray-600 transition-colors cursor-pointer"
                      title={isExpanded ? "Collapse details" : "Expand details"}
                    >
                      <svg
                        className={cn("w-4 h-4 transform transition-transform duration-200", isExpanded ? "rotate-90 text-blue-600" : "")}
                        fill="none"
                        stroke="currentColor"
                        viewBox="0 0 24 24"
                      >
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M9 5l7 7-7 7" />
                      </svg>
                    </button>
                  </td>
                  <td className="px-4 py-3 text-gray-500 font-medium text-center w-16">{(page * 10) + idx + 1}</td>
                  <td className="px-4 py-3 font-medium text-gray-800 !text-left">{t.name}</td>
                  <td className="px-4 py-3 text-gray-600 text-xs font-semibold uppercase !text-left">{t.department?.name ?? '—'}</td>
                  <td className="px-4 py-3 text-center w-36">
                    <button
                      onClick={() => startEdit(t)}
                      className="text-gray-500 hover:text-blue-600 p-1.5 hover:bg-blue-50 rounded-lg transition-colors border border-gray-200 cursor-pointer shadow-sm inline-flex items-center justify-center bg-white"
                      title="Edit Report Template"
                    >
                      <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M3 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1z" clipRule="evenodd" />
                      </svg>
                    </button>
                  </td>
                </tr>
                {isExpanded && (
                  <tr className="bg-gray-55/40">
                    <td colSpan={5} className="px-6 py-4">
                      {t.labTemplateDetails && t.labTemplateDetails.length > 0 ? (
                        <div className="border border-gray-200 rounded-xl overflow-hidden bg-white shadow-inner ml-12 mr-12 max-w-4xl">
                          <table className="w-full text-left border-collapse text-xs">
                            <thead>
                              <tr className="bg-gray-100 border-b border-gray-200 text-gray-600 font-bold">
                                <th className="px-4 py-2.5 w-16 text-center text-[10px] uppercase">Order</th>
                                <th className="px-4 py-2.5 text-[10px] uppercase">Result Name</th>
                                <th className="px-4 py-2.5 w-32 text-[10px] uppercase">Type</th>
                                <th className="px-4 py-2.5 w-40 text-[10px] uppercase">Normal Range</th>
                                <th className="px-4 py-2.5 w-24 text-[10px] uppercase">Unit</th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-150">
                              {t.labTemplateDetails
                                .slice()
                                .sort((a: any, b: any) => (a.orderNumber ?? 0) - (b.orderNumber ?? 0))
                                .map((detail: any, dIdx: number) => (
                                  <tr key={detail.id || dIdx} className="hover:bg-gray-50/85 transition-colors">
                                    <td className="px-4 py-2 text-center font-medium text-gray-500">{detail.orderNumber}</td>
                                    <td className="px-4 py-2 font-medium text-gray-800">{detail.resultName}</td>
                                    <td className="px-4 py-2">
                                      <span className={cn(
                                        "px-2 py-0.5 rounded text-[9px] font-bold uppercase tracking-wider",
                                        detail.labType === 'HEADER' 
                                          ? "bg-purple-50 text-purple-700 border border-purple-100" 
                                          : "bg-blue-50 text-blue-700 border border-blue-100"
                                      )}>
                                        {detail.labType}
                                      </span>
                                    </td>
                                    <td className="px-4 py-2 text-gray-600 font-medium">{detail.normalRange || '—'}</td>
                                    <td className="px-4 py-2 text-gray-500 font-medium font-mono">{detail.unit || '—'}</td>
                                  </tr>
                                ))}
                            </tbody>
                          </table>
                        </div>
                      ) : (
                        <div className="text-gray-400 italic text-xs ml-12 py-2">No result parameters defined for this template.</div>
                      )}
                    </td>
                  </tr>
                )}
              </Fragment>
            )
          })}
          </tbody>
        </table>
      </div>

      {!isLoading && items.length > 0 && (
        <div className="flex items-center justify-between px-6 py-4 bg-gray-50 border border-t-0 border-gray-100 rounded-b-lg text-xs font-bold text-gray-500 mt-2 shadow-sm">
          <div className="text-xs text-gray-500 font-medium normal-case">
            Page <span className="font-semibold text-gray-900">{page + 1}</span> of{' '}
            <span className="font-semibold text-gray-900">{totalPages || 1}</span>
            {totalElements !== undefined && (
              <span className="ml-2">· {totalElements} total items</span>
            )}
          </div>
          <div className="flex items-center gap-1">
            <button
              onClick={() => setPage((p: number) => Math.max(0, p - 1))}
              disabled={page === 0 || isLoading}
              className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7" />
              </svg>
            </button>

            {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
              let pageNum = i
              if (totalPages > 5 && page > 2) {
                pageNum = Math.min(page - 2 + i, totalPages - 5 + i)
              }
              return (
                <button
                  key={pageNum}
                  onClick={() => setPage(pageNum)}
                  className={cn(
                    'min-w-[28px] h-7 flex items-center justify-center rounded text-xs font-semibold transition-all',
                    page === pageNum ? 'bg-blue-600 text-white shadow-sm' : 'text-gray-600 hover:bg-gray-100'
                  )}
                >
                  {pageNum + 1}
                </button>
              )
            })}

            <button
              onClick={() => setPage((p: number) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1 || isLoading}
              className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7" />
              </svg>
            </button>
          </div>
        </div>
      )}
    </Section>
  )
}