
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from '../../../../hooks/useToast';
import { inputCls, Field, Section, LoadingSection } from '../MasterSharedUI';
import { configApi } from '../../../../services/config/configApi';

export default function HospitalProfileTab() {
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

  if (isLoading) return <LoadingSection />

  return (
    <Section title="Hospital Profile" description="Displayed on printed bills, reports, and SMS messages">
      <div className="bg-white border border-gray-200 rounded-xl p-6 space-y-6 shadow-sm max-w-2xl">
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

          <Field label="Hospital Name">
            <input className={inputCls} value={name || currentName} onChange={e => setName(e.target.value)} />
          </Field>

          <Field label="Address">
            <textarea rows={3} className={`${inputCls} resize-none`} value={address || currentAddress} onChange={e => setAddress(e.target.value)} />
          </Field>

          <Field label="Contact Number">
            <input type="tel" maxLength={10} className={inputCls} value={phone || currentPhone} onChange={e => setPhone(e.target.value.replace(/\D/g, '').slice(0, 10))} />
          </Field>

          {/* Current values */}
          {(currentName || currentAddress || currentPhone) && (
            <div className="bg-gray-50 border border-gray-100 rounded-lg p-3 text-xs text-gray-500 space-y-1">
              <p className="font-semibold text-gray-600 mb-1">Currently saved:</p>
              {currentName    && <p><span className="font-medium">Name:</span> {currentName}</p>}
              {currentAddress && <p><span className="font-medium">Address:</span> {currentAddress}</p>}
              {currentPhone   && <p><span className="font-medium">Phone:</span> {currentPhone}</p>}
            </div>
          )}

          <button onClick={() => saveMutation.mutate()} disabled={saveMutation.isPending}
            className="px-5 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 disabled:opacity-50 transition-colors">
            {saveMutation.isPending ? 'Saving…' : 'Save Hospital Profile'}
          </button>
        </div>
      </div>
    </Section>
  )
}