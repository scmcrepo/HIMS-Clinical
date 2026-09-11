import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from '../../../../hooks/useToast'
import { inputCls, Field, Section, Table, LoadingSection } from '../MasterSharedUI'
import { gstApi, type GstConfig, type GstReturnType } from '../../../../services/gst/gstApi'
import { useAuthStore } from '../../../../store/authStore'

const rupees = (n: number) =>
  `₹ ${Number(n || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

const currentPeriod = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

/**
 * GstConfigTab — the hospital's GSTIN, GSP credentials, and its filing history.
 *
 * This build generates and validates returns but does not transmit them, so the screen
 * leads with the JSON download rather than a filing button: downloading and uploading
 * to the GST portal is the path that actually works today, and presenting a prominent
 * "File" action that always fails would be worse than presenting none.
 */
export default function GstConfigTab() {
  const qc = useQueryClient()
  const { user } = useAuthStore()
  // Only a platform super-admin may turn the integration on (D3). The server enforces
  // this; hiding the control just avoids offering an action that will be refused.
  const isSuperAdmin = Boolean(user?.isSuperAdmin)

  const { data: config, isLoading } = useQuery({ queryKey: ['gstConfig'], queryFn: gstApi.getConfig })
  const { data: filings = [] } = useQuery({ queryKey: ['gstFilings'], queryFn: gstApi.history })

  const [form, setForm] = useState({
    gstin: '', legalName: '', sandboxBaseUrl: '', productionBaseUrl: '',
    activeEnvironment: 'SANDBOX' as GstConfig['activeEnvironment'],
    autoSyncFrequency: 'MANUAL' as GstConfig['autoSyncFrequency'],
    apiKey: '', apiSecret: '',
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
      apiKey: '', apiSecret: '',
    }))
  }, [config])

  const fail = (e: any) => toast({
    title: 'Could not complete',
    description: e?.response?.data?.message || e.message,
    variant: 'destructive',
  })

  const save = useMutation({
    mutationFn: () => {
      const goingLive = form.activeEnvironment === 'PRODUCTION' && config?.activeEnvironment !== 'PRODUCTION'
      if (goingLive && !window.confirm(
        'Point GST filing at the PRODUCTION environment?\n\nReturns generated from now on will be aimed at the live GST network.'
      )) {
        return Promise.reject(new Error('Cancelled'))
      }
      return gstApi.saveConfig(form, goingLive)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['gstConfig'] })
      toast({ title: 'GST configuration saved', variant: 'success' })
    },
    onError: (e: any) => { if (e.message !== 'Cancelled') fail(e) },
  })

  const toggleIntegration = useMutation({
    mutationFn: (enabled: boolean) => gstApi.setIntegration(enabled),
    onSuccess: (c) => {
      qc.invalidateQueries({ queryKey: ['gstConfig'] })
      toast({ title: `GST integration ${c.integrationEnabled ? 'enabled' : 'disabled'}`, variant: 'success' })
    },
    onError: fail,
  })

  const generate = useMutation({
    mutationFn: () => gstApi.generate(returnType, period),
    onSuccess: (record) => {
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
    ERROR:     'bg-red-50 text-red-700 border-red-200',
    FILED:     'bg-blue-50 text-blue-700 border-blue-200',
    SUBMITTED: 'bg-blue-50 text-blue-700 border-blue-200',
    DRAFT:     'bg-gray-50 text-gray-600 border-gray-200',
  }

  return (
    <Section title="GST Filing" description="GSTIN, provider credentials, and return history">
      {/* ── Identity ── */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
        <Field label="GSTIN">
          <input
            className={inputCls}
            value={form.gstin}
            maxLength={15}
            placeholder="22AAAAA0000A1Z5"
            onChange={e => setForm(f => ({ ...f, gstin: e.target.value.toUpperCase().replace(/[^0-9A-Z]/g, '') }))}
          />
          {config?.stateCode && (
            <p className="text-xs text-gray-500 mt-1">
              State code <span className="font-mono font-semibold">{config.stateCode}</span> — used as the
              place of supply for anything sold at the hospital.
            </p>
          )}
        </Field>
        <Field label="Legal Name">
          <input
            className={inputCls}
            value={form.legalName}
            onChange={e => setForm(f => ({ ...f, legalName: e.target.value }))}
          />
        </Field>
      </div>

      {/* ── Provider credentials ── */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
        <Field label={`API Key${config?.apiKeySet ? ' (set — type to replace)' : ''}`}>
          <input
            className={inputCls}
            type="password"
            autoComplete="new-password"
            value={form.apiKey}
            placeholder={config?.apiKeySet ? '••••••••' : 'Not set'}
            onChange={e => setForm(f => ({ ...f, apiKey: e.target.value }))}
          />
        </Field>
        <Field label={`API Secret${config?.apiSecretSet ? ' (set — type to replace)' : ''}`}>
          <input
            className={inputCls}
            type="password"
            autoComplete="new-password"
            value={form.apiSecret}
            placeholder={config?.apiSecretSet ? '••••••••' : 'Not set'}
            onChange={e => setForm(f => ({ ...f, apiSecret: e.target.value }))}
          />
        </Field>
        <Field label="Sandbox Base URL">
          <input className={inputCls} value={form.sandboxBaseUrl}
                 onChange={e => setForm(f => ({ ...f, sandboxBaseUrl: e.target.value }))} />
        </Field>
        <Field label="Production Base URL">
          <input className={inputCls} value={form.productionBaseUrl}
                 onChange={e => setForm(f => ({ ...f, productionBaseUrl: e.target.value }))} />
        </Field>
        <Field label="Active Environment">
          <select className={inputCls} value={form.activeEnvironment}
                  onChange={e => setForm(f => ({ ...f, activeEnvironment: e.target.value as any }))}>
            <option value="SANDBOX">Sandbox</option>
            <option value="PRODUCTION">Production</option>
          </select>
        </Field>
        <Field label="Auto-sync Frequency">
          <select className={inputCls} value={form.autoSyncFrequency}
                  onChange={e => setForm(f => ({ ...f, autoSyncFrequency: e.target.value as any }))}>
            <option value="MANUAL">Manual</option>
            <option value="DAILY">Daily</option>
            <option value="MONTHLY">Monthly</option>
          </select>
        </Field>
      </div>

      <div className="flex items-center justify-between gap-4 mb-8 pb-6 border-b border-gray-150">
        <div className="text-sm">
          <span className="text-gray-500">Integration: </span>
          <span className={config?.integrationEnabled ? 'font-semibold text-emerald-700' : 'font-semibold text-gray-600'}>
            {config?.integrationEnabled ? 'Enabled' : 'Not enabled'}
          </span>
          {isSuperAdmin && (
            <button
              type="button"
              onClick={() => toggleIntegration.mutate(!config?.integrationEnabled)}
              className="ml-3 text-xs font-semibold text-neutral-700 underline hover:text-neutral-900"
            >
              {config?.integrationEnabled ? 'Disable' : 'Enable'}
            </button>
          )}
          {!isSuperAdmin && (
            <span className="ml-2 text-xs text-gray-400">(a platform administrator controls this)</span>
          )}
        </div>
        <button
          type="button"
          onClick={() => save.mutate()}
          disabled={save.isPending}
          className="px-5 py-2 text-sm font-semibold text-white bg-neutral-600 rounded-lg hover:bg-neutral-700 disabled:opacity-50"
        >
          {save.isPending ? 'Saving…' : 'Save Configuration'}
        </button>
      </div>

      {/* ── Generate ── */}
      <h4 className="text-sm font-bold text-gray-800 mb-3">Generate a return</h4>
      <div className="flex flex-wrap items-end gap-3 mb-6">
        <div className="w-40">
          <label className="block text-xs font-semibold text-gray-600 mb-1">Return</label>
          <select className={inputCls} value={returnType} onChange={e => setReturnType(e.target.value as GstReturnType)}>
            <option value="GSTR1">GSTR-1</option>
            <option value="GSTR3B">GSTR-3B</option>
          </select>
        </div>
        <div className="w-44">
          <label className="block text-xs font-semibold text-gray-600 mb-1">Period</label>
          <input type="month" className={inputCls} value={period} onChange={e => setPeriod(e.target.value)} />
        </div>
        <button
          type="button"
          onClick={() => generate.mutate()}
          disabled={generate.isPending}
          className="px-5 py-2 text-sm font-semibold text-white bg-neutral-600 rounded-lg hover:bg-neutral-700 disabled:opacity-50"
        >
          {generate.isPending ? 'Generating…' : 'Generate'}
        </button>
      </div>

      {/* ── History ── */}
      <h4 className="text-sm font-bold text-gray-800 mb-3">Filing history</h4>
      {filings.length === 0 ? (
        <div className="bg-gray-50 border border-gray-200 text-gray-600 px-4 py-3 rounded-lg text-sm">
          No returns generated yet.
        </div>
      ) : (
        <Table headers={['Return', 'Period', 'Status', 'Lines', 'Taxable', 'Tax', '']}>
          {filings.map(f => (
            <tr key={f.id} className="align-top">
              <td className="px-4 py-3 text-sm font-semibold text-gray-800">{f.returnType}</td>
              <td className="px-4 py-3 text-sm text-gray-700 font-mono">{f.returnPeriod}</td>
              <td className="px-4 py-3">
                <span className={`inline-block px-2 py-0.5 rounded text-[11px] font-semibold border ${statusTone[f.filingStatus] ?? statusTone.DRAFT}`}>
                  {f.filingStatus}
                </span>
                {f.errors.length > 0 && (
                  <ul className="mt-2 space-y-1 max-w-md">
                    {f.errors.map((e, i) => (
                      <li key={i} className="text-[11px] text-red-700 leading-snug">{e.message}</li>
                    ))}
                  </ul>
                )}
              </td>
              <td className="px-4 py-3 text-sm text-gray-700 tabular-nums">{f.lineItemCount}</td>
              <td className="px-4 py-3 text-sm text-gray-700 tabular-nums whitespace-nowrap">{rupees(f.totalTaxableValue)}</td>
              <td className="px-4 py-3 text-sm text-gray-700 tabular-nums whitespace-nowrap">
                {rupees(Number(f.totalCgst) + Number(f.totalSgst) + Number(f.totalIgst))}
              </td>
              <td className="px-4 py-3 text-right whitespace-nowrap">
                <button
                  type="button"
                  onClick={() => gstApi.downloadPayload(f.id, f.returnType, f.returnPeriod)}
                  className="px-3 py-1.5 text-xs font-semibold text-neutral-700 border border-gray-300 rounded-lg hover:bg-gray-50"
                >
                  Download JSON
                </button>
              </td>
            </tr>
          ))}
        </Table>
      )}
    </Section>
  )
}
