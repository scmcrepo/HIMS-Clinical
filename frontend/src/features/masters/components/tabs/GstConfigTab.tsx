import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Building2,
  KeyRound,
  FileSpreadsheet,
  History,
  Download,
  ChevronDown,
  Save,
  RefreshCw,
} from 'lucide-react'
import { toast } from '../../../../hooks/useToast'
import { Section, Table, LoadingSection } from '../MasterSharedUI'
import { gstApi, type GstConfig, type GstReturnType } from '../../../../services/gst/gstApi'
import { useAuthStore } from '../../../../store/authStore'
import { cn } from '../../../../lib/utils'

const rupees = (n: number) =>
  `₹ ${Number(n || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

const currentPeriod = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

const fieldInputCls =
  'w-full h-10 px-3.5 py-2 border border-gray-200 rounded-xl text-sm bg-gray-50/50 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-neutral-500/20 focus:border-neutral-500 focus:bg-white transition-all text-gray-800 placeholder:text-gray-400 font-medium'
const fieldSelectCls =
  'w-full h-10 pl-3.5 pr-9 py-2 border border-gray-200 rounded-xl text-sm bg-gray-50/50 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-neutral-500/20 focus:border-neutral-500 focus:bg-white transition-all text-gray-800 font-medium appearance-none cursor-pointer'

function FormField({
  label,
  children,
  hint,
  required,
}: {
  label: React.ReactNode
  children: React.ReactNode
  hint?: React.ReactNode
  required?: boolean
}) {
  return (
    <div className="space-y-1.5">
      <label className="block text-xs font-semibold text-gray-700 tracking-tight">
        {label} {required && <span className="text-red-500">*</span>}
      </label>
      {children}
      {hint && <p className="text-[11px] text-gray-500 leading-normal">{hint}</p>}
    </div>
  )
}

/**
 * GstConfigTab — the hospital's GSTIN, GSP credentials, and its filing history.
 */
export default function GstConfigTab() {
  const qc = useQueryClient()
  const { user } = useAuthStore()
  const isSuperAdmin = Boolean(user?.isSuperAdmin)

  const { data: config, isLoading } = useQuery({ queryKey: ['gstConfig'], queryFn: gstApi.getConfig })
  const { data: filings = [] } = useQuery({ queryKey: ['gstFilings'], queryFn: gstApi.history })

  const [form, setForm] = useState({
    gstin: '',
    legalName: '',
    sandboxBaseUrl: '',
    productionBaseUrl: '',
    activeEnvironment: 'SANDBOX' as GstConfig['activeEnvironment'],
    autoSyncFrequency: 'MANUAL' as GstConfig['autoSyncFrequency'],
    apiKey: '',
    apiSecret: '',
  })
  const [returnType, setReturnType] = useState<GstReturnType>('GSTR1')
  const [period, setPeriod] = useState(currentPeriod())

  useEffect(() => {
    if (!config) return
    setForm(f => ({
      ...f,
      gstin: config.gstin ?? '',
      legalName: config.legalName ?? '',
      sandboxBaseUrl: config.sandboxBaseUrl ?? '',
      productionBaseUrl: config.productionBaseUrl ?? '',
      activeEnvironment: config.activeEnvironment,
      autoSyncFrequency: config.autoSyncFrequency,
      apiKey: '',
      apiSecret: '',
    }))
  }, [config])

  const fail = (e: any) =>
    toast({
      title: 'Could not complete',
      description: e?.response?.data?.message || e.message,
      variant: 'destructive',
    })

  const save = useMutation({
    mutationFn: () => {
      const goingLive =
        form.activeEnvironment === 'PRODUCTION' && config?.activeEnvironment !== 'PRODUCTION'
      if (
        goingLive &&
        !window.confirm(
          'Point GST filing at the PRODUCTION environment?\n\nReturns generated from now on will be aimed at the live GST network.'
        )
      ) {
        return Promise.reject(new Error('Cancelled'))
      }
      return gstApi.saveConfig(form, goingLive)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['gstConfig'] })
      toast({ title: 'GST configuration saved', variant: 'success' })
    },
    onError: (e: any) => {
      if (e.message !== 'Cancelled') fail(e)
    },
  })

  const toggleIntegration = useMutation({
    mutationFn: (enabled: boolean) => gstApi.setIntegration(enabled),
    onSuccess: c => {
      qc.invalidateQueries({ queryKey: ['gstConfig'] })
      toast({
        title: `GST integration ${c.integrationEnabled ? 'enabled' : 'disabled'}`,
        variant: 'success',
      })
    },
    onError: fail,
  })

  const generate = useMutation({
    mutationFn: () => gstApi.generate(returnType, period),
    onSuccess: record => {
      qc.invalidateQueries({ queryKey: ['gstFilings'] })
      toast({
        title: record.errors.length
          ? `${returnType} generated with ${record.errors.length} issue(s)`
          : `${returnType} generated — ${record.lineItemCount} lines`,
        variant: record.errors.length ? 'destructive' : 'success',
      })
    },
    onError: fail,
  })

  if (isLoading) return <LoadingSection />

  const statusTone: Record<string, string> = {
    GENERATED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    ERROR: 'bg-red-50 text-red-700 border-red-200',
    FILED: 'bg-blue-50 text-blue-700 border-blue-200',
    SUBMITTED: 'bg-blue-50 text-blue-700 border-blue-200',
    DRAFT: 'bg-gray-50 text-gray-600 border-gray-200',
  }

  return (
    <Section title="GST Filing" description="GSTIN, provider credentials, and return history">
      <div className="space-y-6">
        {/* ── Configuration Card ── */}
        <div className="bg-white border border-gray-200/80 rounded-2xl p-6 sm:p-7 shadow-sm space-y-6">
          {/* Identity Sub-section */}
          <div className="space-y-4">
            <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-gray-500 border-b border-gray-100 pb-2.5">
              <Building2 className="w-4 h-4 text-neutral-600" />
              Hospital GST Details
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
              <FormField
                label="GSTIN"
                hint={
                  config?.stateCode ? (
                    <span>
                      State code <span className="font-mono font-semibold text-gray-700">{config.stateCode}</span> — used as the place of supply for hospital sales.
                    </span>
                  ) : undefined
                }
              >
                <input
                  className={fieldInputCls}
                  value={form.gstin}
                  maxLength={15}
                  placeholder="22AAAAA0000A1Z5"
                  onChange={e =>
                    setForm(f => ({
                      ...f,
                      gstin: e.target.value.toUpperCase().replace(/[^0-9A-Z]/g, ''),
                    }))
                  }
                />
              </FormField>
              <FormField label="Legal Name">
                <input
                  className={fieldInputCls}
                  value={form.legalName}
                  placeholder="Registered Entity / Hospital Name"
                  onChange={e => setForm(f => ({ ...f, legalName: e.target.value }))}
                />
              </FormField>
            </div>
          </div>

          {/* Provider Credentials Sub-section */}
          <div className="space-y-4 pt-2">
            <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-gray-500 border-b border-gray-100 pb-2.5">
              <KeyRound className="w-4 h-4 text-neutral-600" />
              GSP & API Credentials
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
              <FormField
                label={
                  <span>
                    API Key{' '}
                    {config?.apiKeySet && (
                      <span className="text-[11px] font-normal text-emerald-600">(currently set — enter to update)</span>
                    )}
                  </span>
                }
              >
                <input
                  className={fieldInputCls}
                  type="password"
                  autoComplete="new-password"
                  value={form.apiKey}
                  placeholder={config?.apiKeySet ? '••••••••••••••••' : 'Not set'}
                  onChange={e => setForm(f => ({ ...f, apiKey: e.target.value }))}
                />
              </FormField>
              <FormField
                label={
                  <span>
                    API Secret{' '}
                    {config?.apiSecretSet && (
                      <span className="text-[11px] font-normal text-emerald-600">(currently set — enter to update)</span>
                    )}
                  </span>
                }
              >
                <input
                  className={fieldInputCls}
                  type="password"
                  autoComplete="new-password"
                  value={form.apiSecret}
                  placeholder={config?.apiSecretSet ? '••••••••••••••••' : 'Not set'}
                  onChange={e => setForm(f => ({ ...f, apiSecret: e.target.value }))}
                />
              </FormField>
              <FormField label="Sandbox Base URL">
                <div className="relative">
                  <input
                    className={fieldInputCls}
                    value={form.sandboxBaseUrl}
                    placeholder="https://sandbox.gsp.api"
                    onChange={e => setForm(f => ({ ...f, sandboxBaseUrl: e.target.value }))}
                  />
                </div>
              </FormField>
              <FormField label="Production Base URL">
                <div className="relative">
                  <input
                    className={fieldInputCls}
                    value={form.productionBaseUrl}
                    placeholder="https://api.gsp.network"
                    onChange={e => setForm(f => ({ ...f, productionBaseUrl: e.target.value }))}
                  />
                </div>
              </FormField>
              <FormField label="Active Environment">
                <div className="relative">
                  <select
                    className={fieldSelectCls}
                    value={form.activeEnvironment}
                    onChange={e =>
                      setForm(f => ({ ...f, activeEnvironment: e.target.value as any }))
                    }
                  >
                    <option value="SANDBOX">Sandbox (Testing & Simulation)</option>
                    <option value="PRODUCTION">Production (Live GST Network)</option>
                  </select>
                  <ChevronDown className="w-4 h-4 text-gray-400 absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none" />
                </div>
              </FormField>
              <FormField label="Auto-sync Frequency">
                <div className="relative">
                  <select
                    className={fieldSelectCls}
                    value={form.autoSyncFrequency}
                    onChange={e =>
                      setForm(f => ({ ...f, autoSyncFrequency: e.target.value as any }))
                    }
                  >
                    <option value="MANUAL">Manual (On-demand)</option>
                    <option value="DAILY">Daily Automatic Sync</option>
                    <option value="MONTHLY">Monthly Automatic Sync</option>
                  </select>
                  <ChevronDown className="w-4 h-4 text-gray-400 absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none" />
                </div>
              </FormField>
            </div>
          </div>

          {/* Footer: Integration status + Save Button */}
          <div className="pt-5 border-t border-gray-150 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="flex items-center flex-wrap gap-3">
              <div
                className={cn(
                  'inline-flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-semibold border',
                  config?.integrationEnabled
                    ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                    : 'bg-gray-100 text-gray-600 border-gray-200'
                )}
              >
                <span
                  className={cn(
                    'w-2 h-2 rounded-full',
                    config?.integrationEnabled ? 'bg-emerald-500 animate-pulse' : 'bg-gray-400'
                  )}
                />
                Integration: {config?.integrationEnabled ? 'Enabled' : 'Not Enabled'}
              </div>
              {isSuperAdmin && (
                <button
                  type="button"
                  onClick={() => toggleIntegration.mutate(!config?.integrationEnabled)}
                  className="text-xs font-semibold text-neutral-600 hover:text-neutral-800 underline transition-colors"
                >
                  {config?.integrationEnabled ? 'Disable Integration' : 'Enable Integration'}
                </button>
              )}
              {!isSuperAdmin && (
                <span className="text-xs text-gray-400">(controlled by platform admin)</span>
              )}
            </div>
            <button
              type="button"
              onClick={() => save.mutate()}
              disabled={save.isPending}
              className="h-10 px-6 text-sm font-semibold text-white bg-neutral-600 rounded-xl hover:bg-neutral-700 shadow-sm hover:shadow active:scale-[0.98] transition-all flex items-center justify-center gap-2 disabled:opacity-50"
            >
              {save.isPending ? (
                <>
                  <RefreshCw className="w-4 h-4 animate-spin" />
                  Saving…
                </>
              ) : (
                <>
                  <Save className="w-4 h-4" />
                  Save Configuration
                </>
              )}
            </button>
          </div>
        </div>

        {/* ── Generate Return Card ── */}
        <div className="bg-white border border-gray-200/80 rounded-2xl p-6 sm:p-7 shadow-sm space-y-5">
          <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-gray-500 border-b border-gray-100 pb-2.5">
            <FileSpreadsheet className="w-4 h-4 text-neutral-600" />
            Generate a Return
          </div>
          <div className="flex flex-col sm:flex-row sm:items-end gap-4">
            <div className="w-full sm:w-60 space-y-1.5">
              <label className="block text-xs font-semibold text-gray-700">Return Type</label>
              <div className="relative">
                <select
                  className={fieldSelectCls}
                  value={returnType}
                  onChange={e => setReturnType(e.target.value as GstReturnType)}
                >
                  <option value="GSTR1">GSTR-1 (Outward Supplies)</option>
                  <option value="GSTR3B">GSTR-3B (Summary Return)</option>
                </select>
                <ChevronDown className="w-4 h-4 text-gray-400 absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none" />
              </div>
            </div>
            <div className="w-full sm:w-56 space-y-1.5">
              <label className="block text-xs font-semibold text-gray-700">Period (Month / Year)</label>
              <input
                type="month"
                className={fieldInputCls}
                value={period}
                onChange={e => setPeriod(e.target.value)}
              />
            </div>
            <button
              type="button"
              onClick={() => generate.mutate()}
              disabled={generate.isPending}
              className="h-10 px-6 text-sm font-semibold text-white bg-neutral-600 rounded-xl hover:bg-neutral-700 shadow-sm hover:shadow active:scale-[0.98] transition-all flex items-center justify-center gap-2 shrink-0 disabled:opacity-50"
            >
              {generate.isPending ? (
                <>
                  <RefreshCw className="w-4 h-4 animate-spin" />
                  Generating…
                </>
              ) : (
                <>
                  <FileSpreadsheet className="w-4 h-4" />
                  Generate Return
                </>
              )}
            </button>
          </div>
        </div>

        {/* ── Filing History Card ── */}
        <div className="bg-white border border-gray-200/80 rounded-2xl p-6 sm:p-7 shadow-sm space-y-5">
          <div className="flex items-center justify-between border-b border-gray-100 pb-2.5">
            <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-gray-500">
              <History className="w-4 h-4 text-neutral-600" />
              Filing History
            </div>
            {filings.length > 0 && (
              <span className="px-2.5 py-0.5 text-xs font-semibold rounded-full bg-gray-100 text-gray-700 border border-gray-200">
                {filings.length} {filings.length === 1 ? 'record' : 'records'}
              </span>
            )}
          </div>

          {filings.length === 0 ? (
            <div className="border border-dashed border-gray-200 rounded-xl p-10 text-center bg-gray-50/50 flex flex-col items-center justify-center gap-2">
              <div className="w-12 h-12 rounded-full bg-gray-100 flex items-center justify-center text-gray-400 mb-1">
                <History className="w-6 h-6" />
              </div>
              <p className="text-sm font-semibold text-gray-700">No returns generated yet</p>
              <p className="text-xs text-gray-400 max-w-sm">
                Generate your first GSTR-1 or GSTR-3B return using the generator above to review line items and download JSON payloads.
              </p>
            </div>
          ) : (
            <Table headers={['Return', 'Period', 'Status', 'Lines', 'Taxable', 'Tax', '']}>
              {filings.map(f => (
                <tr key={f.id} className="align-top hover:bg-gray-50/60 transition-colors">
                  <td className="px-4 py-3.5 text-sm font-bold text-gray-900">{f.returnType}</td>
                  <td className="px-4 py-3.5 text-sm text-gray-700 font-mono font-medium">{f.returnPeriod}</td>
                  <td className="px-4 py-3.5">
                    <span
                      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-[11px] font-semibold border ${statusTone[f.filingStatus] ?? statusTone.DRAFT}`}
                    >
                      {f.filingStatus}
                    </span>
                    {f.errors.length > 0 && (
                      <ul className="mt-2 space-y-1 max-w-md">
                        {f.errors.map((e, i) => (
                          <li key={i} className="text-[11px] text-red-700 leading-snug flex items-start gap-1">
                            <span className="text-red-400 shrink-0">•</span> {e.message}
                          </li>
                        ))}
                      </ul>
                    )}
                  </td>
                  <td className="px-4 py-3.5 text-sm text-gray-700 tabular-nums">{f.lineItemCount}</td>
                  <td className="px-4 py-3.5 text-sm text-gray-700 tabular-nums whitespace-nowrap font-medium">
                    {rupees(f.totalTaxableValue)}
                  </td>
                  <td className="px-4 py-3.5 text-sm text-gray-700 tabular-nums whitespace-nowrap font-medium">
                    {rupees(Number(f.totalCgst) + Number(f.totalSgst) + Number(f.totalIgst))}
                  </td>
                  <td className="px-4 py-3.5 text-right whitespace-nowrap">
                    <button
                      type="button"
                      onClick={() => gstApi.downloadPayload(f.id, f.returnType, f.returnPeriod)}
                      className="h-8 px-3.5 inline-flex items-center gap-1.5 text-xs font-semibold text-neutral-700 bg-white border border-gray-200 rounded-lg hover:bg-neutral-50 hover:border-neutral-300 transition-all shadow-xs active:scale-[0.98]"
                    >
                      <Download className="w-3.5 h-3.5 text-neutral-600" />
                      Download JSON
                    </button>
                  </td>
                </tr>
              ))}
            </Table>
          )}
        </div>
      </div>
    </Section>
  )
}
