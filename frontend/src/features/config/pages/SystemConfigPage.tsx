import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { configApi } from '../../../services/config/configApi'
import { toast } from '../../../hooks/useToast'
import { cn } from '../../../lib/utils'
import { useAuthStore } from '../../../store/authStore'
import BackButton from '../../../components/shared/BackButton'

type Tab = 'app' | 'hospital'

const APP_CONFIG_KEYS = [
  { key: 'bed.type.calculation',    label: 'Automated Bed Charge Calculation', description: 'Set to 1 to auto-compute bed charges on every getBill() call. 0 = manual.', type: 'toggle' },
  { key: 'prefix.patient.multiple', label: 'Multiple Patient Number Prefixes',  description: 'Set to 1 to allow more than one active patient number prefix.', type: 'toggle' },
  { key: 'max.inactive.time',        label: 'Session Timeout (minutes)',         description: 'Idle session timeout in minutes. Default: 15.', type: 'number' },
  { key: 'consultation.fee.auto',   label: 'Auto-add Consultation Fee',          description: 'Set to 1 to auto-add consultation charge on encounter creation.', type: 'toggle' },
  { key: 'sms.enabled',              label: 'SMS Notifications',                 description: 'Set to 1 to enable SMS on patient registration and billing events.', type: 'toggle' },
  { key: 'ip.package.enabled',       label: 'IP Package Billing',                description: 'Set to 1 to enable IP package charge absorption.', type: 'toggle' },
]

export default function SystemConfigPage() {
  const [tab, setTab] = useState<Tab>('hospital')
  return (
    <div className="space-y-5 max-w-3xl">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-900">System Configuration</h2>
          <p className="text-sm text-gray-500 mt-0.5">Changes take effect immediately — no restart required.</p>
        </div>
        <BackButton />
      </div>
      <div className="flex gap-1 bg-gray-100 p-1 rounded-lg w-fit" role="tablist">
        {(['hospital', 'app'] as const).map(t => (
          <button key={t} role="tab" aria-selected={tab === t} onClick={() => setTab(t)}
            className={cn('px-4 py-1.5 text-sm font-medium rounded-md transition-colors',
              tab === t ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700')}>
            {t === 'app' ? 'App Configuration' : 'Hospital Profile'}
          </button>
        ))}
      </div>
      {tab === 'hospital' && <HospitalProfileTab />}
      {tab === 'app'      && <AppConfigTab />}
    </div>
  )
}

function AppConfigTab() {
  const qc = useQueryClient()
  const { data: values, isLoading } = useQuery({
    queryKey: ['config', 'values'],
    queryFn:  () => configApi.getValues(),
  })

  const saveMutation = useMutation({
    mutationFn: ({ key, value }: { key: string; value: string }) =>
      configApi.save('APP_CONFIGURATION', key, value),
    onSuccess: (_, variables) => {
      qc.invalidateQueries({ queryKey: ['config'] })
      if (variables.key === 'max.inactive.time') {
        const minutes = parseInt(variables.value)
        if (!isNaN(minutes)) useAuthStore.getState().setSessionTimeout(minutes)
      }
      toast({ title: 'Configuration saved', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Save failed', description: e.message, variant: 'destructive' }),
  })

  if (isLoading) return <p className="text-sm text-gray-500" aria-live="polite">Loading…</p>

  return (
    <div className="bg-white border border-gray-200 rounded-xl divide-y divide-gray-100">
      {APP_CONFIG_KEYS.map(({ key, label, description, type }) => {
        const currentValue = values?.[key] ?? '0'
        const isEnabled = currentValue === '1'

        return (
          <div key={key} className="flex items-start justify-between px-5 py-4 gap-4">
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-gray-900">{label}</p>
              <p className="text-xs text-gray-500 mt-0.5">{description}</p>
              <p className="text-xs font-mono text-gray-400 mt-1">{key}</p>
            </div>
            <div className="shrink-0">
              {type === 'toggle' ? (
                <button
                  onClick={() => saveMutation.mutate({ key, value: isEnabled ? '0' : '1' })}
                  disabled={saveMutation.isPending}
                  className={cn(
                    'relative inline-flex h-6 w-11 items-center rounded-full transition-colors',
                    isEnabled ? 'bg-neutral-600' : 'bg-gray-200'
                  )}
                  role="switch"
                  aria-checked={isEnabled}
                  aria-label={label}>
                  <span className={cn(
                    'inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform',
                    isEnabled ? 'translate-x-6' : 'translate-x-1'
                  )} />
                </button>
              ) : (
                <NumberConfigField
                  value={currentValue}
                  onSave={value => saveMutation.mutate({ key, value })}
                  isLoading={saveMutation.isPending}
                  label={label}
                />
              )}
            </div>
          </div>
        )
      })}
    </div>
  )
}

function NumberConfigField({ value, onSave, isLoading, label }: {
  value: string; onSave: (v: string) => void; isLoading: boolean; label: string
}) {
  const [local, setLocal] = useState(value)
  return (
    <div className="flex items-center gap-2">
      <input type="number" value={local} onChange={e => setLocal(e.target.value)}
        className="w-20 px-2 py-1 border border-gray-300 rounded text-sm text-right focus:outline-none focus:ring-1 focus:ring-neutral-500"
        aria-label={`${label} value`} />
      <button onClick={() => onSave(local)} disabled={isLoading || local === value}
        className="px-3 py-1 text-xs bg-neutral-600 text-white rounded hover:bg-neutral-700 disabled:opacity-40 transition-colors">
        Save
      </button>
    </div>
  )
}

function HospitalProfileTab() {
  const qc = useQueryClient()
  const { data: profile, isLoading } = useQuery({
    queryKey: ['config', 'hospital'],
    queryFn:  () => configApi.getHospital(),
  })

  const [name, setName]       = useState('')
  const [address, setAddress] = useState('')
  const [phone, setPhone]     = useState('')

  const [logoVersion, setLogoVersion] = useState(() => Date.now())
  const [uploadingLogo, setUploadingLogo] = useState(false)

  const saveMutation = useMutation({
    mutationFn: () => {
      const data: { name?: string; address?: string; phone?: string } = {}
      if (name)    data.name    = name
      if (address) data.address = address
      if (phone)   data.phone   = phone
      return configApi.saveHospital(data)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['config', 'hospital'] })
      toast({ title: 'Hospital profile updated', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Save failed', description: e.message, variant: 'destructive' }),
  })

  const handleLogoUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    if (!file.type.startsWith('image/')) {
      toast({ title: 'Invalid file type', description: 'Please select an image file (PNG/JPG/SVG)', variant: 'destructive' })
      return
    }

    setUploadingLogo(true)
    try {
      await configApi.uploadLogo(file)
      toast({ title: 'Hospital logo uploaded successfully', variant: 'success' })
      const newVersion = Date.now()
      setLogoVersion(newVersion)
      
      // Dispatch a global custom event to notify Sidebar and other components to reload the logo
      window.dispatchEvent(new CustomEvent('hospital-logo-changed', { detail: { version: newVersion } }))

      qc.invalidateQueries({ queryKey: ['config', 'hospital'] })
    } catch (err: any) {
      toast({ title: 'Logo upload failed', description: err.message, variant: 'destructive' })
    } finally {
      setUploadingLogo(false)
    }
  }

  // Populate from loaded data
  const currentName    = profile?.['hospital.name.param']    ?? ''
  const currentAddress = profile?.['hospital.address.param'] ?? ''
  const currentPhone   = profile?.['hospital.contactNo.param'] ?? ''

  if (isLoading) return <p className="text-sm text-gray-500" aria-live="polite">Loading…</p>

  const inputCls = "w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500"
  const labelCls = "block text-xs font-medium text-gray-700 mb-1"

  return (
    <div className="bg-white border border-gray-200 rounded-xl p-6 space-y-6 shadow-sm">
      {/* Logo Section */}
      <div className="border-b border-gray-100 pb-6 flex flex-col sm:flex-row items-center gap-6">
        <div className="relative group w-24 h-24 bg-gray-50 border border-gray-200 rounded-xl flex items-center justify-center overflow-hidden shadow-sm shrink-0">
          <img
            src={`/api/hospitalProfile/logo?t=${logoVersion}`}
            onError={(e) => {
              e.currentTarget.src = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 24 24' stroke-width='1.5' stroke='%233b82f6' class='w-12 h-12'%3E%3Cpath stroke-linecap='round' stroke-linejoin='round' d='M2.25 21h19.5m-18-18v18m10.5-18v18m6-13.5V21M6.75 6.75h.75m-.75 3h.75m-.75 3h.75m3-6h.75m-.75 3h.75m-.75 3h.75M6.75 21v-3.375c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21M3 3h12v18H3V3z' /%3E%3C/svg%3E"
            }}
            className="w-full h-full object-contain p-2"
            alt="Hospital Logo"
          />
          {uploadingLogo && (
            <div className="absolute inset-0 bg-white/70 flex items-center justify-center">
              <div className="w-5 h-5 border-2 border-neutral-600 border-t-transparent rounded-full animate-spin" />
            </div>
          )}
        </div>
        <div className="flex-1 text-center sm:text-left space-y-2">
          <h3 className="text-sm font-bold text-gray-800">Hospital Logo</h3>
          <p className="text-xs text-gray-500 max-w-sm">
            Upload your logo in JPG or PNG format. This logo will appear dynamically in the sidebar, reports, and headers.
          </p>
          <label className="inline-flex items-center justify-center bg-gray-50 hover:bg-gray-100 text-gray-700 border border-gray-200 hover:border-gray-300 font-semibold text-xs px-4 py-2 rounded-lg cursor-pointer transition-all shadow-sm hover:shadow active:scale-[0.98]">
            <span>{uploadingLogo ? 'Uploading...' : 'Choose Image'}</span>
            <input type="file" accept="image/*" onChange={handleLogoUpload} disabled={uploadingLogo} className="hidden" />
          </label>
        </div>
      </div>

      <div className="space-y-4">
        <p className="text-xs text-gray-500">
          These values appear on printed bills, reports, and in SMS messages as <code className="bg-gray-100 px-1 rounded">$hospitalName$</code>.
        </p>

        <div>
          <label className={labelCls}>Hospital Name</label>
          <input value={name || currentName} onChange={e => setName(e.target.value)}
            placeholder="e.g. City General Hospital"
            className={inputCls} aria-label="Hospital name" />
        </div>

        <div>
          <label className={labelCls}>Address</label>
          <textarea value={address || currentAddress} onChange={e => setAddress(e.target.value)}
            rows={3} placeholder="Full hospital address"
            className={`${inputCls} resize-none`} aria-label="Hospital address" />
        </div>

        <div>
          <label className={labelCls}>Contact Number</label>
          <input 
            type="tel"
            maxLength={10}
            value={phone || currentPhone} 
            onChange={e => setPhone(e.target.value.replace(/\D/g, '').slice(0, 10))}
            placeholder="10-digit mobile number"
            className={inputCls} 
            aria-label="Hospital contact number" 
          />
        </div>

        {/* Current values */}
        {(currentName || currentAddress || currentPhone) && (
          <div className="bg-gray-50 border border-gray-100 rounded-lg p-3 text-xs text-gray-500 space-y-1">
            <p className="font-semibold text-gray-600 mb-1">Currently saved:</p>
            {currentName    && <p><span className="font-medium">Name:</span> {currentName}</p>}
            {currentAddress && <p><span className="font-medium">Address:</span> {currentAddress}</p>}
            {currentPhone   && <p><span className="font-medium">Phone:</span> {currentPhone}</p>}
          </div>
        )}

        <button onClick={() => saveMutation.mutate()} disabled={saveMutation.isPending || (phone ? phone.length !== 10 : false)}
          className="px-5 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 disabled:opacity-50 transition-colors">
          {saveMutation.isPending ? 'Saving…' : 'Save Hospital Profile'}
        </button>
      </div>
    </div>
  )
}
