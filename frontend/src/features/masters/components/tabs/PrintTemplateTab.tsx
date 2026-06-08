/**
 * PrintTemplateTab.tsx — Full Print Template Engine admin UI
 * 4-tab form: Basic | Page Setup | Template | Printer
 * Supports all 33 TemplateTypes, HTML and DOT_MATRIX modes
 */
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from '../../../../hooks/useToast'
import { inputCls, Field, FormShell, StatusBadge, EmptyState, AddButton, Section, Table, EditBtn, LoadingRow } from '../MasterSharedUI'

const TEMPLATE_TYPES = [
  { value: 'BILL',                     label: 'OP Bill (BILL)',                   module: 'OP Billing'      },
  { value: 'OP_RECEIPT',               label: 'OP Receipt',                       module: 'OP Billing'      },
  { value: 'IP_RECEIPT',               label: 'IP Receipt',                       module: 'IP Billing'      },
  { value: 'IP_BILL_CONSOLIDATED',     label: 'IP Bill Consolidated',             module: 'IP Billing'      },
  { value: 'IP_BILL_DETAIL',           label: 'IP Bill Detail',                   module: 'IP Billing'      },
  { value: 'PROVISIONAL_BILL',         label: 'Provisional Bill',                 module: 'IP Billing'      },
  { value: 'PATIENT_ID',               label: 'Patient ID Card',                  module: 'Patient Profile' },
  { value: 'PRESCRIPTION',             label: 'Prescription',                     module: 'Medical Record'  },
  { value: 'PRESCRIPTION_NOHEADER',    label: 'Prescription (No Header)',          module: 'Medical Record'  },
  { value: 'PRESCRIPTION_ORDER',       label: 'Prescription Order',               module: 'Medical Record'  },
  { value: 'PRESCRIPTION_ORDER_NOHEADER', label: 'Prescription Order (No Header)', module: 'Medical Record' },
  { value: 'DIAGNOSTIC_ORDER',         label: 'Diagnostic Order',                 module: 'Lab Report'      },
  { value: 'DIAGNOSTIC_NOHEADER',      label: 'Diagnostic (No Header)',           module: 'Medical Record'  },
  { value: 'IP_DIAGNOSTIC_ORDER',      label: 'IP Diagnostic Order',              module: 'Medical Record'  },
  { value: 'PAYMENT',                  label: 'Payment Receipt',                  module: 'Payment'         },
  { value: 'LAB',                      label: 'Lab Report (LAB)',                 module: 'Lab Report'      },
  { value: 'RADIOLOGY',                label: 'Radiology Report',                 module: 'Radiology'       },
  { value: 'DISCHARGE_SUMMARY',        label: 'Discharge Summary',                module: 'Bed Management'  },
  { value: 'CLINICAL',                 label: 'Clinical Record',                  module: 'Medical Record'  },
  { value: 'PURCHASE_ORDER',           label: 'Purchase Order',                   module: 'Purchase'        },
  { value: 'SALES',                    label: 'Pharmacy Sale (SALES)',            module: 'Sales'           },
  { value: 'ADVANCE_REFUND_RECEIPT',   label: 'Advance Refund Receipt',           module: 'OP Billing'      },
  { value: 'REFUND_RECEIPT',           label: 'Refund Receipt',                   module: 'OP Billing'      },
  { value: 'ADMISSION_ADVICE',         label: 'Admission Advice',                 module: 'Medical Record'  },
  { value: 'DONOR_ID',                 label: 'Donor ID',                         module: 'Blood Bank'      },
  { value: 'BAG_LABEL',                label: 'Blood Bag Label',                  module: 'Blood Bank'      },
  { value: 'LETTER_ACCEPTANCE',        label: 'Insurance Acceptance Letter',      module: 'Insurance'       },
  { value: 'ENHANCEMENT_REQUEST',      label: 'Insurance Enhancement Request',    module: 'Insurance'       },
  { value: 'SAMPLE',                   label: 'Sample Barcode Sticker',           module: 'Medical Record'  },
  { value: 'SPECIMEN',                 label: 'Specimen Barcode Label',           module: 'Sample'          },
  { value: 'VEHICLE_MOVEMENT',         label: 'Ambulance Movement Card',          module: 'Ambulance'       },
  { value: 'AMBULANCE_SERVICE_ENTRY',  label: 'Ambulance Service Bill',           module: 'Ambulance'       },
  { value: 'CSSD_BATCH',               label: 'CSSD Batch Label',                 module: 'CSSD'            },
]

const PAGE_SIZES = ['A4', 'A5', 'A6', 'Letter', 'Legal', 'Custom']

const PAGE_PRESETS: Record<string, { width: string; height: string }> = {
  A4: { width: '210mm', height: '297mm' }, A5: { width: '148mm', height: '210mm' },
  A6: { width: '105mm', height: '148mm' }, Letter: { width: '216mm', height: '279mm' },
  Legal: { width: '216mm', height: '356mm' }, Custom: { width: '', height: '' },
}

const PUG_STARTER = `html
  head
    style.
      body { font-family: sans-serif; font-size: 12px; margin: 0; }
      .header { text-align: center; border-bottom: 1px solid #ccc; padding-bottom: 8px; margin-bottom: 12px; }
      table { width: 100%; border-collapse: collapse; }
      th, td { border: 1px solid #ddd; padding: 4px 8px; }
  body
    .header
      h2= profile.name
      p= profile.address
    h3 Document
    if data
      p= JSON.stringify(data)
    else
      p No data found.
`

const BLANK: Record<string, any> = {
  name: '', documentType: 'BILL', printMode: 'HTML', pageSize: 'A4',
  height: '297mm', width: '210mm', marginTop: '10mm', marginBottom: '10mm',
  marginLeft: '10mm', marginRight: '10mm', margin: '', pugTemplate: '',
  content: '', defaultPrinter: '', isDefault: false, status: 1,
}

function PrintModeBadge({ mode }: { mode: string }) {
  return mode === 'DOT_MATRIX' ? (
    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-50 text-amber-700 border border-amber-200">
      <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" /></svg>
      DOT MATRIX
    </span>
  ) : (
    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-blue-50 text-blue-700 border border-blue-200">
      <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" /></svg>
      HTML
    </span>
  )
}

export default function PrintTemplateTab() {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<any>(null)
  const [form, setForm] = useState<Record<string, any>>(BLANK)
  const [tab, setTab] = useState<'basic' | 'page' | 'template' | 'printer'>('basic')
  const [showPreview, setShowPreview] = useState(false)

  const { data = [], isLoading } = useQuery({
    queryKey: ['printTemplates'],
    queryFn: () => import('../../../../services/masters/masterApi').then(m => m.printTemplateApi.getAll()),
  })

  const saveMut = useMutation({
    mutationFn: async () => {
      const m = await import('../../../../services/masters/masterApi')
      return editing ? m.printTemplateApi.update(editing.id, form) : m.printTemplateApi.create(form as any)
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['printTemplates'] }); reset(); toast({ title: editing ? 'Template updated' : 'Template saved', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  const deleteMut = useMutation({
    mutationFn: async (id: string) => { const m = await import('../../../../services/masters/masterApi'); return m.printTemplateApi.remove(id) },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['printTemplates'] }); toast({ title: 'Template deleted', variant: 'success' }) },
  })

  function reset() { setShowForm(false); setEditing(null); setForm(BLANK); setTab('basic'); setShowPreview(false) }
  function startEdit(r: any) { setEditing(r); setForm({ name: r.name ?? '', documentType: r.documentType ?? 'BILL', printMode: r.printMode ?? 'HTML', pageSize: r.pageSize ?? 'A4', height: r.height ?? '297mm', width: r.width ?? '210mm', marginTop: r.marginTop ?? '10mm', marginBottom: r.marginBottom ?? '10mm', marginLeft: r.marginLeft ?? '10mm', marginRight: r.marginRight ?? '10mm', margin: r.margin ?? '', pugTemplate: r.pugTemplate ?? '', content: r.content ?? '', defaultPrinter: r.defaultPrinter ?? '', isDefault: r.isDefault ?? false, status: r.status ?? 1 }); setShowForm(true); setTab('basic') }
  const set = (k: string, v: any) => setForm(f => ({ ...f, [k]: v }))
  function onPageSize(ps: string) { const p = PAGE_PRESETS[ps]; setForm(f => ({ ...f, pageSize: ps, ...(p ? { width: p.width, height: p.height } : {}) })) }

  const tabCls = (t: typeof tab) => `px-4 py-2 text-xs font-semibold border-b-2 transition-colors ${tab === t ? 'border-neutral-600 text-neutral-700' : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'}`

  return (
    <Section title="Print Templates" description="Configure HTML and dot-matrix print layouts for all document types" action={<AddButton label="Add Template" onClick={() => { reset(); setShowForm(true) }} />}>

      {showForm && (
        <FormShell title={editing ? 'Update Print Template' : 'Create Print Template'} onCancel={reset} onSave={() => saveMut.mutate()} saving={saveMut.isPending} canSave={!!form.name}>
          <div className="col-span-full border-b border-gray-200 -mt-2 mb-1">
            <nav className="flex gap-0">
              {(['basic', 'page', 'template', 'printer'] as const).map(t => (
                <button key={t} type="button" onClick={() => setTab(t)} className={tabCls(t)}>
                  {t === 'basic' && '① Basic'}{t === 'page' && '② Page Setup'}{t === 'template' && '③ Template'}{t === 'printer' && '④ Printer'}
                </button>
              ))}
            </nav>
          </div>

          {tab === 'basic' && (<>
            <Field label="Template Name *"><input className={inputCls} value={form.name} onChange={e => set('name', e.target.value)} placeholder="e.g. Standard OP Bill" /></Field>
            <Field label="Document Type *">
              <select className={inputCls} value={form.documentType} onChange={e => set('documentType', e.target.value)}>
                {TEMPLATE_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
              </select>
            </Field>
            <div className="col-span-full">
              <label className="block text-xs font-semibold text-gray-600 mb-2">Print Mode *</label>
              <div className="flex gap-3">
                {(['HTML', 'DOT_MATRIX'] as const).map(mode => (
                  <button key={mode} type="button" onClick={() => set('printMode', mode)}
                    className={`flex items-center gap-2 px-4 py-2 rounded-lg border text-sm font-semibold transition-all ${form.printMode === mode ? (mode === 'HTML' ? 'bg-neutral-600 text-white border-neutral-600' : 'bg-amber-500 text-white border-amber-500') : 'bg-white text-gray-600 border-gray-200 hover:border-gray-300'}`}>
                    {mode === 'HTML' ? (<><svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" /></svg>HTML / PDF</>) : (<><svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" /></svg>Dot Matrix / ESC-POS</>)}
                  </button>
                ))}
              </div>
              {form.printMode === 'DOT_MATRIX' && <p className="mt-2 text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded px-3 py-2">DOT_MATRIX sends raw ESC/POS commands via QZ Tray. Set the default printer name in the Printer tab.</p>}
            </div>
            <div className="col-span-full flex items-center gap-2">
              <input type="checkbox" id="isDefault" checked={form.isDefault} onChange={e => set('isDefault', e.target.checked)} className="w-4 h-4 rounded border-gray-300 text-neutral-600" />
              <label htmlFor="isDefault" className="text-sm text-gray-700">Set as default template for this document type</label>
            </div>
            <Field label="Status">
              <select className={inputCls} value={form.status} onChange={e => set('status', Number(e.target.value))}>
                <option value={1}>Active</option><option value={2}>Inactive</option>
              </select>
            </Field>
          </>)}

          {tab === 'page' && (<>
            <Field label="Page Size"><select className={inputCls} value={form.pageSize} onChange={e => onPageSize(e.target.value)}>{PAGE_SIZES.map(s => <option key={s} value={s}>{s}</option>)}</select></Field>
            <Field label="Width"><input className={inputCls} placeholder="210mm" value={form.width} onChange={e => set('width', e.target.value)} /></Field>
            <Field label="Height"><input className={inputCls} placeholder="297mm" value={form.height} onChange={e => set('height', e.target.value)} /></Field>
            <div className="col-span-full"><p className="text-xs text-gray-500">Margins injected as CSS <code className="bg-gray-100 px-1 rounded">@page</code> rules at print time.</p></div>
            <Field label="Margin Top"><input className={inputCls} placeholder="10mm" value={form.marginTop} onChange={e => set('marginTop', e.target.value)} /></Field>
            <Field label="Margin Bottom"><input className={inputCls} placeholder="10mm" value={form.marginBottom} onChange={e => set('marginBottom', e.target.value)} /></Field>
            <Field label="Margin Left"><input className={inputCls} placeholder="10mm" value={form.marginLeft} onChange={e => set('marginLeft', e.target.value)} /></Field>
            <Field label="Margin Right"><input className={inputCls} placeholder="10mm" value={form.marginRight} onChange={e => set('marginRight', e.target.value)} /></Field>
            <Field label="Margin Shorthand (optional)"><input className={inputCls} placeholder="e.g. 10mm 15mm" value={form.margin} onChange={e => set('margin', e.target.value)} /></Field>
          </>)}

          {tab === 'template' && (<>
            <div className="col-span-full">
              <div className="flex items-center justify-between mb-1">
                <label className="text-xs font-semibold text-gray-600">Pug/Jade Template Source <span className="text-[10px] text-gray-400 font-normal ml-2">Variables: #{'{'}profile.name{'}'} #{'{'}data{'}'}</span></label>
                <div className="flex gap-2">
                  <button type="button" onClick={() => set('pugTemplate', PUG_STARTER)} className="text-xs text-neutral-600 hover:text-neutral-800 transition-colors">Load starter</button>
                  <button type="button" onClick={() => setShowPreview(v => !v)} className="text-xs text-gray-500 hover:text-gray-700 transition-colors">{showPreview ? 'Hide preview' : 'Show preview'}</button>
                </div>
              </div>
              <textarea rows={14} className={`${inputCls} font-mono text-xs resize-y leading-relaxed`} placeholder="Enter Pug/Jade template…" value={form.pugTemplate} onChange={e => set('pugTemplate', e.target.value)} />
              <p className="mt-1 text-[10px] text-gray-400">Compiled server-side. Context: <code>data</code> (SQL result), <code>profile</code> (hospital), <code>date</code>.</p>
            </div>
            {showPreview && form.pugTemplate && (
              <div className="col-span-full">
                <label className="block text-xs font-semibold text-gray-600 mb-1">Raw Source Preview</label>
                <div className="bg-gray-900 text-green-400 rounded-lg p-4 font-mono text-xs overflow-auto max-h-48 whitespace-pre">{form.pugTemplate}</div>
              </div>
            )}
            <div className="col-span-full border-t border-gray-100 pt-3">
              <label className="block text-xs font-semibold text-gray-600 mb-1">Legacy HTML Content <span className="text-[10px] text-gray-400 font-normal ml-2">Used if Pug template is empty</span></label>
              <textarea rows={6} className={`${inputCls} font-mono text-xs resize-y`} placeholder="<div>…legacy HTML…</div>" value={form.content} onChange={e => set('content', e.target.value)} />
            </div>
          </>)}

          {tab === 'printer' && (<>
            <div className="col-span-full">
              <div className={`rounded-lg p-4 mb-4 border ${form.printMode === 'DOT_MATRIX' ? 'bg-amber-50 border-amber-200' : 'bg-neutral-50 border-neutral-200'}`}>
                <h4 className={`text-sm font-semibold mb-2 ${form.printMode === 'DOT_MATRIX' ? 'text-amber-900' : 'text-neutral-900'}`}>Printer Configuration</h4>
                {form.printMode === 'HTML' ? (
                  <p className="text-xs text-neutral-700">HTML mode uses the browser's native print dialog. No printer name needed — page geometry from tab ② is injected via @page CSS.</p>
                ) : (<>
                  <p className="text-xs text-amber-700">Enter the exact system printer name registered in QZ Tray. Must match the printer on the client machine.</p>
                  <p className="text-xs text-amber-700 mt-1 font-medium">QZ Tray download: <a href="https://qz.io/download" target="_blank" rel="noopener noreferrer" className="underline">qz.io/download</a></p>
                </>)}
              </div>
            </div>
            <Field label="Default Printer Name">
              <input className={inputCls} placeholder={form.printMode === 'DOT_MATRIX' ? 'e.g. EPSON LX-350' : 'Not required for HTML mode'} value={form.defaultPrinter} disabled={form.printMode === 'HTML'} onChange={e => set('defaultPrinter', e.target.value)} />
            </Field>
          </>)}
        </FormShell>
      )}

      <Table headers={['Name', 'Document Type', 'Module', 'Mode', 'Page', 'Default', 'Status', '']}>
        {isLoading ? <LoadingRow /> : (data as any[]).length === 0 ? <EmptyState label="print templates" /> : (data as any[]).map((r: any) => {
          const typeInfo = TEMPLATE_TYPES.find(t => t.value === r.documentType)
          return (
            <tr key={r.id} className="hover:bg-gray-50">
              <td className="px-4 py-3 font-medium text-gray-900">{r.name}</td>
              <td className="px-4 py-3 text-gray-600 font-mono text-xs">{r.documentType}</td>
              <td className="px-4 py-3 text-gray-500 text-xs">{typeInfo?.module ?? '—'}</td>
              <td className="px-4 py-3"><PrintModeBadge mode={r.printMode ?? 'HTML'} /></td>
              <td className="px-4 py-3 text-gray-500 text-xs">{r.pageSize ?? 'A4'}</td>
              <td className="px-4 py-3">{r.isDefault ? <span className="text-xs text-neutral-600 font-bold">✓ Default</span> : <span className="text-gray-400">—</span>}</td>
              <td className="px-4 py-3"><StatusBadge active={r.status === 1} /></td>
              <td className="px-4 py-3 text-right flex items-center justify-end gap-2">
                <EditBtn onClick={() => startEdit(r)} />
                <button onClick={() => { if (window.confirm('Delete this template?')) deleteMut.mutate(r.id) }} className="p-1.5 text-red-400 hover:text-red-600 hover:bg-red-50 rounded transition-colors" title="Delete">
                  <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg>
                </button>
              </td>
            </tr>
          )
        })}
      </Table>
    </Section>
  )
}
