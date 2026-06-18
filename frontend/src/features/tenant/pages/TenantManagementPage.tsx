import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { tenantApi, type Tenant } from '../../../services/tenant/tenantApi'
import { useAuthStore } from '../../../store/authStore'
import { configApi } from '../../../services/config/configApi'
import { Eye, X, Copy, Check, MapPin, Phone, Key, Plus, Building2, Users, ShieldCheck, Sparkles } from 'lucide-react'
import { toast } from '../../../hooks/useToast'
import { Modal } from '../../../components/ui/Modal'

/**
 * Platform tenant management. Rendered only for SUPERADMIN (guard at the route AND here).
 */
export default function TenantManagementPage() {
  const isSuperAdmin = useAuthStore(s => s.user?.isSuperAdmin ?? false)
  const qc = useQueryClient()
  const [newName, setNewName] = useState('')
  const [newDescription, setNewDescription] = useState('')
  const [newAddress, setNewAddress] = useState('')
  const [newContactNumber, setNewContactNumber] = useState('')
  const [adminUser, setAdminUser] = useState('')
  const [adminPass, setAdminPass] = useState('')
  const [logoFile, setLogoFile] = useState<File | null>(null)
  const [logoVersion, setLogoVersion] = useState(() => Date.now())
  const [uploadingTenantId, setUploadingTenantId] = useState<string | null>(null)
  const [viewingTenant, setViewingTenant] = useState<Tenant | null>(null)
  const [previewTenant, setPreviewTenant] = useState<Tenant | null>(null)
  const [copied, setCopied] = useState(false)
  const [showOnboardCard, setShowOnboardCard] = useState(false)

  // Edit tenant state
  const [isEditing, setIsEditing] = useState(false)
  const [editName, setEditName] = useState('')
  const [editDescription, setEditDescription] = useState('')
  const [editAddress, setEditAddress] = useState('')
  const [editContactNumber, setEditContactNumber] = useState('')

  const updateMut = useMutation({
    mutationFn: (body: { name: string; description?: string; address?: string; contactNumber?: string }) =>
      tenantApi.update(viewingTenant!.id, body),
    onSuccess: (res) => {
      invalidate()
      setViewingTenant(res.data ?? null)
      setIsEditing(false)
      toast({ title: 'Hospital details updated successfully', variant: 'success' })
    },
    onError: (e: any) => {
      toast({
        title: 'Error updating hospital',
        description: e.response?.data?.message || e.message,
        variant: 'destructive'
      })
    }
  })

  // Password reset state
  const [resetTenant, setResetTenant] = useState<Tenant | null>(null)
  const [newAdminPass, setNewAdminPass] = useState('')

  const resetPassMut = useMutation({
    mutationFn: () => tenantApi.resetAdminPassword(resetTenant!.id, newAdminPass),
    onSuccess: () => {
      setResetTenant(null)
      setNewAdminPass('')
      toast({ title: 'Hospital admin password updated successfully', variant: 'success' })
    },
    onError: (e: any) => {
      toast({
        title: 'Error updating password',
        description: e.response?.data?.message || e.message,
        variant: 'destructive'
      })
    }
  })

  const { data: tenants = [], isLoading } = useQuery({
    queryKey: ['tenants'],
    queryFn: () => tenantApi.getAll().then(r => r.data ?? []),
    enabled: isSuperAdmin,
  })

  const invalidate = () => qc.invalidateQueries({ queryKey: ['tenants'] })

  const createMut = useMutation({
    mutationFn: async () => {
      const res = await tenantApi.create({
        name: newName.trim(),
        description: newDescription.trim() || undefined,
        address: newAddress.trim() || undefined,
        contactNumber: newContactNumber.trim() || undefined,
        adminUsername: adminUser.trim() || undefined,
        adminPassword: adminPass || undefined
      })
      const tenantId = res.data?.id
      if (tenantId && logoFile) {
        await configApi.uploadLogo(logoFile, tenantId)
      }
      return res
    },
    onSuccess: () => {
      setNewName('')
      setNewDescription('')
      setNewAddress('')
      setNewContactNumber('')
      setAdminUser('')
      setAdminPass('')
      setLogoFile(null)
      setLogoVersion(Date.now())
      setShowOnboardCard(false)
      invalidate()
      toast({ title: 'Hospital onboarded successfully', variant: 'success' })
    },
    onError: (e: any) => {
      toast({
        title: 'Error onboarding hospital',
        description: e.response?.data?.message || e.message,
        variant: 'destructive'
      })
    }
  })

  const seedMut = useMutation({
    mutationFn: (id: string) => tenantApi.seedRbac(id),
    onSuccess: () => {
      toast({ title: 'RBAC permissions seeded successfully', variant: 'success' })
    },
    onError: (e: any) => {
      toast({
        title: 'Error seeding RBAC',
        description: e.response?.data?.message || e.message,
        variant: 'destructive'
      })
    }
  })

  const toggleMut = useMutation({
    mutationFn: (t: Tenant) => tenantApi.update(t.id, { status: t.status === 1 ? 0 : 1 }),
    onSuccess: () => {
      invalidate()
      toast({ title: 'Hospital status updated', variant: 'success' })
    },
    onError: (e: any) => {
      toast({
        title: 'Error updating hospital status',
        description: e.response?.data?.message || e.message,
        variant: 'destructive'
      })
    }
  })

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  if (!isSuperAdmin) {
    return <div className="p-8 text-sm text-red-600">Forbidden — platform administrators only.</div>
  }

  const activeCount = tenants.filter(t => t.status === 1).length
  const inactiveCount = tenants.filter(t => t.status !== 1).length

  return (
    <div className="p-8 max-w-7xl mx-auto space-y-8 animate-fadeIn">
      
      {/* Header Panel */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-neutral-150 pb-4">
        <div>
          <h1 className="text-xl font-bold tracking-tight text-neutral-900 flex items-center gap-2">
            <Building2 className="text-neutral-600 w-6 h-6" />
            Platform Administration
          </h1>
          <p className="text-xs text-neutral-500 mt-0.5">
            Overview, monitor, and provision tenant medical organizations on the platform.
          </p>
        </div>

        <button
          onClick={() => setShowOnboardCard(true)}
          className="px-4 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 transition-colors flex items-center gap-1.5 cursor-pointer"
        >
          <Plus className="w-4 h-4" />
          Register New Hospital
        </button>
      </div>

      {/* Statistics Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-white border border-neutral-200/80 rounded-xl p-3 flex items-center gap-3 shadow-sm hover:shadow-md transition-all duration-200">
          <div className="bg-neutral-100 text-neutral-600 p-2.5 rounded-lg shrink-0">
            <Building2 className="w-5 h-5" />
          </div>
          <div>
            <span className="block text-[10px] font-bold text-neutral-400 uppercase tracking-wider">Total Hospitals</span>
            <span className="text-lg font-bold text-neutral-800">{tenants.length}</span>
          </div>
        </div>

        <div className="bg-white border border-neutral-200/80 rounded-xl p-3 flex items-center gap-3 shadow-sm hover:shadow-md transition-all duration-200">
          <div className="bg-emerald-50 text-emerald-700 p-2.5 rounded-lg shrink-0">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <div>
            <span className="block text-[10px] font-bold text-neutral-400 uppercase tracking-wider">Active Tenants</span>
            <span className="text-lg font-bold text-neutral-800">{activeCount}</span>
          </div>
        </div>

        <div className="bg-white border border-neutral-200/80 rounded-xl p-3 flex items-center gap-3 shadow-sm hover:shadow-md transition-all duration-200">
          <div className="bg-amber-50/50 text-amber-700 p-2.5 rounded-lg shrink-0">
            <Users className="w-5 h-5" />
          </div>
          <div>
            <span className="block text-[10px] font-bold text-neutral-400 uppercase tracking-wider">Inactive / Suspended</span>
            <span className="text-lg font-bold text-neutral-800">{inactiveCount}</span>
          </div>
        </div>
      </div>

      {/* Onboarding Form Modal */}
      <Modal
        isOpen={showOnboardCard}
        onClose={() => setShowOnboardCard(false)}
        size="4xl"
        showCloseButton={false}
        title="Hospital Registration Portal"
      >
        {/* Modal Header */}
        <div className="bg-gradient-to-r from-neutral-600 to-neutral-600 px-6 py-4 flex justify-between items-center text-white shrink-0">
          <div className="flex items-center gap-2">
            <Sparkles className="text-white w-5 h-5" />
            <h3 className="text-lg font-bold tracking-tight">Hospital Registration Portal</h3>
          </div>
          <button
            onClick={() => setShowOnboardCard(false)}
            className="text-white/80 hover:text-white hover:bg-white/10 p-1.5 rounded-lg transition-colors focus:outline-none cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>
        
        {/* Modal Body */}
        <div className="p-6 overflow-y-auto space-y-4 flex-1 bg-gray-50/50">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 bg-white p-6 rounded-xl border border-gray-150 shadow-sm">
            
            {/* Left Column: Hospital Profile */}
            <div className="space-y-4">
              <h3 className="text-xs font-bold text-neutral-400 uppercase tracking-wider border-b border-neutral-100 pb-1.5">Hospital Profile</h3>
              
              <div>
                <label className="block text-xs font-semibold text-neutral-600 mb-1.5">Hospital Name <span className="text-red-500">*</span></label>
                <input
                  value={newName}
                  onChange={e => setNewName(e.target.value)}
                  placeholder="e.g., Apollo Specialty Hospital"
                  className="w-full rounded-lg border border-neutral-200 px-3.5 py-2.5 text-sm focus:border-neutral-900 focus:ring-2 focus:ring-neutral-200 focus:outline-none transition-all placeholder:text-neutral-300 font-medium"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-neutral-600 mb-1.5">Description</label>
                <input
                  value={newDescription}
                  onChange={e => setNewDescription(e.target.value)}
                  placeholder="e.g., Multi-specialty tertiary care hub"
                  className="w-full rounded-lg border border-neutral-200 px-3.5 py-2.5 text-sm focus:border-neutral-900 focus:ring-2 focus:ring-neutral-200 focus:outline-none transition-all placeholder:text-neutral-300 font-medium"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-neutral-600 mb-1.5">Address</label>
                <input
                  value={newAddress}
                  onChange={e => setNewAddress(e.target.value)}
                  placeholder="e.g., Vadapalani, Chennai"
                  className="w-full rounded-lg border border-neutral-200 px-3.5 py-2.5 text-sm focus:border-neutral-900 focus:ring-2 focus:ring-neutral-200 focus:outline-none transition-all placeholder:text-neutral-300 font-medium"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-neutral-600 mb-1.5">Hospital Logo</label>
                <div className="flex items-center gap-3">
                  <div className="w-12 h-12 rounded-lg bg-neutral-50 border border-neutral-200 flex items-center justify-center overflow-hidden shadow-sm shrink-0">
                    {logoFile ? (
                      <img
                        src={URL.createObjectURL(logoFile)}
                        className="w-full h-full object-contain p-1"
                        alt="Preview"
                      />
                    ) : (
                      <Building2 className="w-6 h-6 text-neutral-400" />
                    )}
                  </div>
                  <label className="inline-flex items-center justify-center bg-white hover:bg-neutral-50 text-neutral-700 border border-neutral-200 hover:border-gray-300 font-semibold text-xs px-3 py-1.5 rounded-lg cursor-pointer transition-all shadow-sm active:scale-[0.98]">
                    <span>Choose Logo</span>
                    <input
                      type="file"
                      accept="image/*"
                      className="hidden"
                      onChange={(e) => {
                        const file = e.target.files?.[0] || null
                        setLogoFile(file)
                      }}
                    />
                  </label>
                  {logoFile && (
                    <button
                      type="button"
                      onClick={() => setLogoFile(null)}
                      className="text-xs text-rose-600 hover:text-rose-800 font-semibold cursor-pointer"
                    >
                      Remove
                    </button>
                  )}
                </div>
              </div>
            </div>

            {/* Right Column: Hospital Admin Credentials */}
            <div className="space-y-4">
              <h3 className="text-xs font-bold text-neutral-400 uppercase tracking-wider border-b border-neutral-100 pb-1.5">Admin Account credentials</h3>

              <div>
                <label className="block text-xs font-semibold text-neutral-600 mb-1.5">Admin Username <span className="text-red-500">*</span></label>
                <input
                  value={adminUser}
                  onChange={e => setAdminUser(e.target.value)}
                  placeholder="e.g., apollo.admin"
                  className="w-full rounded-lg border border-neutral-200 px-3.5 py-2.5 text-sm focus:border-neutral-900 focus:ring-2 focus:ring-neutral-200 focus:outline-none transition-all placeholder:text-neutral-300 font-medium"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-neutral-600 mb-1.5">Admin Password <span className="text-red-500">*</span></label>
                <input
                  type="password"
                  value={adminPass}
                  onChange={e => setAdminPass(e.target.value)}
                  placeholder="••••••••"
                  className="w-full rounded-lg border border-neutral-200 px-3.5 py-2.5 text-sm focus:border-neutral-900 focus:ring-2 focus:ring-neutral-200 focus:outline-none transition-all placeholder:text-neutral-300 font-medium"
                />
              </div>
            </div>

          </div>
        </div>

        {/* Modal Footer */}
        <div className="px-6 py-4 border-t bg-white flex justify-end gap-3 shrink-0">
          <button
            type="button"
            onClick={() => setShowOnboardCard(false)}
            className="px-4 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-white transition-colors cursor-pointer"
          >
            Cancel
          </button>
          <button
            onClick={() => createMut.mutate()}
            disabled={!newName.trim() || !adminUser.trim() || !adminPass.trim() || createMut.isPending}
            className="px-5 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 disabled:opacity-50 transition-colors cursor-pointer"
          >
            {createMut.isPending ? 'Onboarding Hospital…' : 'Onboard & Provision'}
          </button>
        </div>
      </Modal>

      {/* Tenants Directory Card */}
      <div className="bg-white border border-neutral-200 rounded-xl shadow-sm overflow-hidden">
        <div className="bg-neutral-50 px-4 py-2.5 border-b border-neutral-150 flex items-center justify-between">
          <h2 className="text-sm font-bold text-neutral-800">Hospital Tenants Directory</h2>
          <span className="text-xs text-neutral-400 font-mono">Total records: {tenants.length}</span>
        </div>

        <div className="overflow-x-auto">
          {isLoading ? (
            <div className="p-10 text-center text-sm text-neutral-400">Loading hospitals registry…</div>
          ) : tenants.length === 0 ? (
            <div className="p-10 text-center text-sm text-neutral-400">No medical tenants registered on the platform.</div>
          ) : (
            <table className="w-full text-sm text-left border-collapse">
              <thead>
                <tr className="bg-neutral-50/50 border-b border-neutral-200 text-xs font-bold text-neutral-500 uppercase tracking-wider">
                  <th className="px-4 py-2.5">Hospital Info</th>
                  <th className="px-4 py-2.5">Status</th>
                  <th className="px-4 py-2.5 text-center">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-neutral-100">
                {tenants.map(t => (
                  <tr key={t.id} className="hover:bg-neutral-50/40 transition-colors">
                    <td className="px-4 py-2">
                      <div className="flex items-center gap-2.5">
                        <div className="relative group w-9 h-9 rounded-lg bg-neutral-50 border border-neutral-200 flex items-center justify-center overflow-hidden shrink-0 shadow-sm hover:border-neutral-400 transition-colors"
                             title="Click to preview logo">
                          <img
                            src={`/api/hospitalProfile/logo?tenantId=${t.id}&t=${logoVersion}`}
                            onError={(e) => {
                              e.currentTarget.src = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 24 24' stroke-width='1.5' stroke='%23a3a3a3' class='w-5 h-5'%3E%3Cpath stroke-linecap='round' stroke-linejoin='round' d='M2.25 21h19.5m-18-18v18m10.5-18v18m6-13.5V21M6.75 6.75h.75m-.75 3h.75m-.75 3h.75m3-6h.75m-.75 3h.75m-.75 3h.75M6.75 21v-3.375c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21M3 3h12v18H3V3z' /%3E%3C/svg%3E"
                            }}
                            className="w-full h-full object-contain p-1 cursor-zoom-in"
                            alt="Logo"
                            onClick={() => setPreviewTenant(t)}
                          />
                          {uploadingTenantId === t.id && (
                            <div className="absolute inset-0 bg-white/75 flex items-center justify-center">
                              <div className="w-3.5 h-3.5 border-2 border-neutral-600 border-t-transparent rounded-full animate-spin" />
                            </div>
                          )}
                        </div>
                        <div>
                          <span className="block font-semibold text-neutral-800 text-sm">{t.name}</span>
                          <span className="block text-xs text-neutral-400 max-w-sm truncate">{t.description || 'No description provided'}</span>
                        </div>
                      </div>
                    </td>

                    <td className="px-4 py-2">
                      <span className={`inline-flex rounded-full px-2 py-0.5 text-[10px] font-semibold tracking-wide uppercase ${t.status === 1 ? 'bg-emerald-50 text-emerald-700 border border-emerald-200/50' : 'bg-neutral-100 text-neutral-500 border border-neutral-200/50'}`}>
                        {t.status === 1 ? 'Active' : 'Inactive'}
                      </span>
                    </td>

                    <td className="px-4 py-2 text-right">
                      <div className="inline-flex gap-1.5">
                        <button
                          onClick={() => setViewingTenant(t)}
                          className="px-2 py-1 border border-gray-300 bg-white rounded hover:bg-gray-100 active:bg-gray-200 text-gray-700 text-xs font-semibold inline-flex items-center gap-1 transition-colors cursor-pointer"
                          title="View Details"
                        >
                          <Eye size={12} />
                          Details
                        </button>
                        
                        <button
                          onClick={() => seedMut.mutate(t.id)}
                          disabled={seedMut.isPending}
                          className="px-2.5 py-1 border border-gray-300 bg-white rounded hover:bg-gray-100 active:bg-gray-200 text-gray-700 text-xs font-semibold transition-colors disabled:opacity-50 cursor-pointer"
                          title="Seed RBAC Roles"
                        >
                          Seed RBAC
                        </button>

                        <button
                          onClick={() => setResetTenant(t)}
                          className="px-2 py-1 border border-gray-300 bg-white rounded hover:bg-gray-100 active:bg-gray-200 text-gray-700 text-xs font-semibold inline-flex items-center gap-1 transition-colors cursor-pointer"
                          title="Reset Password"
                        >
                          <Key size={12} />
                          Reset PW
                        </button>

                        <button
                          onClick={() => toggleMut.mutate(t)}
                          disabled={toggleMut.isPending}
                          className={`px-2.5 py-1 border rounded text-xs font-semibold transition-colors cursor-pointer ${t.status === 1 ? 'border-rose-300 bg-white text-rose-600 hover:bg-rose-50' : 'border-emerald-300 bg-white text-emerald-600 hover:bg-emerald-50'}`}
                        >
                          {t.status === 1 ? 'Deactivate' : 'Activate'}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {/* Details Modal */}
      <Modal
        isOpen={!!viewingTenant}
        onClose={() => {
          setViewingTenant(null)
          setIsEditing(false)
        }}
        size="md"
        title={isEditing ? `Edit ${viewingTenant?.name}` : (viewingTenant?.name || 'Hospital Profile Details')}
      >
        {viewingTenant && (
          <div className="p-6 flex flex-col">
            {isEditing ? (
              <div className="space-y-4 text-sm text-neutral-600">
                <div>
                  <label className="block text-xs font-semibold text-neutral-500 uppercase tracking-wider mb-1">Hospital Name</label>
                  <input
                    value={editName}
                    onChange={e => setEditName(e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500"
                    placeholder="e.g. City General Hospital"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-neutral-500 uppercase tracking-wider mb-1">Description</label>
                  <textarea
                    value={editDescription}
                    onChange={e => setEditDescription(e.target.value)}
                    rows={3}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500 resize-none"
                    placeholder="Brief description"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-neutral-500 uppercase tracking-wider mb-1">Address</label>
                  <textarea
                    value={editAddress}
                    onChange={e => setEditAddress(e.target.value)}
                    rows={2}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500 resize-none"
                    placeholder="Full address"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-neutral-500 uppercase tracking-wider mb-1">Contact Number</label>
                  <input
                    type="tel"
                    maxLength={10}
                    value={editContactNumber}
                    onChange={e => setEditContactNumber(e.target.value.replace(/\D/g, '').slice(0, 10))}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500"
                    placeholder="10-digit mobile number"
                  />
                </div>
                <div className="flex gap-3 mt-6">
                  <button
                    onClick={() => setIsEditing(false)}
                    className="flex-1 px-4 py-2 border border-gray-300 text-gray-750 text-sm font-semibold rounded-lg hover:bg-gray-50 transition-colors cursor-pointer"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={() => {
                      if (!editName.trim()) {
                        toast({ title: 'Name is required', variant: 'destructive' })
                        return
                      }
                      updateMut.mutate({
                        name: editName.trim(),
                        description: editDescription.trim(),
                        address: editAddress.trim(),
                        contactNumber: editContactNumber.trim()
                      })
                    }}
                    disabled={updateMut.isPending}
                    className="flex-1 px-4 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 transition-colors disabled:opacity-50 cursor-pointer"
                  >
                    {updateMut.isPending ? 'Saving...' : 'Save Changes'}
                  </button>
                </div>
              </div>
            ) : (
              <>
                <div className="flex flex-col items-center text-center pb-5 border-b border-neutral-100">
                  <div className="w-20 h-20 rounded-2xl bg-neutral-50 border border-neutral-200 flex items-center justify-center overflow-hidden shadow-sm mb-2 cursor-zoom-in hover:border-neutral-400 transition-colors"
                       onClick={() => setPreviewTenant(viewingTenant)}
                       title="Click to preview logo">
                    <img
                      src={`/api/hospitalProfile/logo?tenantId=${viewingTenant.id}&t=${logoVersion}`}
                      onError={(e) => {
                        e.currentTarget.src = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 24 24' stroke-width='1.5' stroke='%23a3a3a3' class='w-8 h-8'%3E%3Cpath stroke-linecap='round' stroke-linejoin='round' d='M2.25 21h19.5m-18-18v18m10.5-18v18m6-13.5V21M6.75 6.75h.75m-.75 3h.75m-.75 3h.75m3-6h.75m-.75 3h.75m-.75 3h.75M6.75 21v-3.375c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21M3 3h12v18H3V3z' /%3E%3C/svg%3E"
                      }}
                      className="w-full h-full object-contain p-2"
                      alt="Logo"
                    />
                  </div>
                  <label className="relative inline-flex items-center justify-center bg-neutral-50 hover:bg-neutral-100 text-neutral-600 border border-neutral-200 hover:border-neutral-300 font-semibold text-[11px] px-2.5 py-1 rounded-md cursor-pointer transition-all shadow-sm active:scale-[0.98] mb-3">
                    <span>{uploadingTenantId === viewingTenant.id ? 'Uploading...' : 'Change Logo'}</span>
                    <input
                      type="file"
                      accept="image/*"
                      className="hidden"
                      disabled={uploadingTenantId === viewingTenant.id}
                      onChange={async (e) => {
                        const file = e.target.files?.[0]
                        if (!file) return
                        setUploadingTenantId(viewingTenant.id)
                        try {
                          await configApi.uploadLogo(file, viewingTenant.id)
                          setLogoVersion(Date.now())
                          toast({ title: 'Hospital logo updated', variant: 'success' })
                        } catch (err) {
                          console.error(err)
                        } finally {
                          setUploadingTenantId(null)
                        }
                      }}
                    />
                  </label>
                  <h3 className="text-xl font-extrabold text-neutral-800">{viewingTenant.name}</h3>
                  <span className={`mt-2 inline-flex rounded-full px-2.5 py-0.5 text-xs font-semibold uppercase ${viewingTenant.status === 1 ? 'bg-emerald-50 text-emerald-700 border border-emerald-200/50' : 'bg-neutral-100 text-neutral-500 border border-neutral-200/50'}`}>
                    {viewingTenant.status === 1 ? 'Active' : 'Inactive'}
                  </span>
                </div>

                <div className="mt-5 space-y-4 text-sm text-neutral-600">
                  <div>
                    <span className="block text-xs font-bold text-neutral-400 uppercase tracking-wider mb-1">Tenant ID</span>
                    <div className="flex items-center justify-between gap-2 bg-neutral-50 border border-neutral-200 rounded-xl p-2.5 font-mono text-xs text-neutral-700">
                      <span className="truncate select-all">{viewingTenant.id}</span>
                      <button
                        onClick={() => handleCopy(viewingTenant.id)}
                        className="text-neutral-400 hover:text-neutral-900 p-1 hover:bg-white rounded-lg border border-transparent hover:border-neutral-200 transition-all shrink-0 cursor-pointer"
                        title="Copy to clipboard"
                      >
                        {copied ? <Check size={14} className="text-green-600 animate-scaleIn" /> : <Copy size={14} />}
                      </button>
                    </div>
                  </div>

                  {viewingTenant.description && (
                    <div>
                      <span className="block text-xs font-bold text-neutral-400 uppercase tracking-wider mb-1">Description</span>
                      <p className="text-neutral-800 leading-relaxed bg-neutral-50/50 p-2.5 rounded-xl border border-neutral-100">{viewingTenant.description}</p>
                    </div>
                  )}

                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <span className="block text-xs font-bold text-neutral-400 uppercase tracking-wider mb-1">Address</span>
                      <div className="flex items-start gap-1.5 text-neutral-800 bg-neutral-50/50 p-2.5 rounded-xl border border-neutral-100 h-full">
                        <MapPin size={16} className="text-neutral-400 shrink-0 mt-0.5" />
                        <span className="text-xs leading-normal">{viewingTenant.address || '—'}</span>
                      </div>
                    </div>

                    <div>
                      <span className="block text-xs font-bold text-neutral-400 uppercase tracking-wider mb-1">Contact Number</span>
                      <div className="flex items-start gap-1.5 text-neutral-800 bg-neutral-50/50 p-2.5 rounded-xl border border-neutral-100 h-full">
                        <Phone size={16} className="text-neutral-400 shrink-0 mt-0.5" />
                        <span className="text-xs leading-normal">{viewingTenant.contactNumber || '—'}</span>
                      </div>
                    </div>
                  </div>
                </div>

                <div className="mt-6 flex gap-3">
                  <button
                    onClick={() => {
                      setEditName(viewingTenant.name)
                      setEditDescription(viewingTenant.description || '')
                      setEditAddress(viewingTenant.address || '')
                      setEditContactNumber(viewingTenant.contactNumber || '')
                      setIsEditing(true)
                    }}
                    className="flex-1 px-4 py-2 border border-gray-300 text-gray-750 text-sm font-semibold rounded-lg hover:bg-gray-50 transition-colors cursor-pointer"
                  >
                    Edit Profile
                  </button>
                  <button
                    onClick={() => setViewingTenant(null)}
                    className="flex-1 px-4 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 transition-colors cursor-pointer"
                  >
                    Close Details
                  </button>
                </div>
              </>
            )}
          </div>
        )}
      </Modal>

      {/* Reset Password Modal */}
      <Modal
        isOpen={!!resetTenant}
        onClose={() => { setResetTenant(null); setNewAdminPass(''); }}
        size="md"
        title="Reset Admin Password"
      >
        {resetTenant && (
          <div className="p-6">
            <div className="pb-4 border-b border-neutral-100">
              <h3 className="text-lg font-extrabold text-neutral-800 flex items-center gap-2">
                <Key size={20} className="text-neutral-500" />
                Reset Admin Password
              </h3>
              <p className="text-xs text-neutral-500 mt-1">
                For hospital: <span className="font-bold text-neutral-700">{resetTenant.name}</span>
              </p>
            </div>

            <div className="mt-5 space-y-4">
              <div>
                <label className="block text-xs font-bold text-neutral-400 uppercase tracking-wider mb-2">New Password</label>
                <input 
                  type="password"
                  value={newAdminPass}
                  onChange={e => setNewAdminPass(e.target.value)}
                  placeholder="Enter new administrator password"
                  className="w-full rounded-xl border border-neutral-200 px-3.5 py-2.5 text-sm focus:border-neutral-900 focus:ring-2 focus:ring-neutral-200 focus:outline-none transition-all placeholder:text-neutral-300"
                />
              </div>
            </div>

            <div className="mt-6 flex justify-end gap-2.5">
              <button
                onClick={() => { setResetTenant(null); setNewAdminPass(''); }}
                className="px-4 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-white transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                onClick={() => resetPassMut.mutate()}
                disabled={!newAdminPass.trim() || resetPassMut.isPending}
                className="px-5 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 disabled:opacity-50 transition-colors cursor-pointer"
              >
                {resetPassMut.isPending ? 'Updating...' : 'Update Password'}
              </button>
            </div>
          </div>
        )}
      </Modal>

      {/* Logo Preview Lightbox Modal */}
      <Modal
        isOpen={!!previewTenant}
        onClose={() => setPreviewTenant(null)}
        size="xl"
        title={previewTenant?.name || 'Hospital Logo Preview'}
      >
        {previewTenant && (
          <div className="p-6 flex flex-col items-center">
            <div className="pb-3 w-full border-b border-neutral-100 text-center mb-5">
              <h3 className="text-base font-extrabold text-neutral-800">{previewTenant.name}</h3>
              <p className="text-xs text-neutral-400 mt-0.5">Hospital Logo Preview</p>
            </div>

            <div className="w-full aspect-square max-h-[320px] rounded-xl bg-neutral-50 border border-neutral-200 flex items-center justify-center overflow-hidden p-6 mb-6">
              <img
                src={`/api/hospitalProfile/logo?tenantId=${previewTenant.id}&t=${logoVersion}`}
                onError={(e) => {
                  e.currentTarget.src = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 24 24' stroke-width='1.5' stroke='%23a3a3a3' class='w-16 h-16'%3E%3Cpath stroke-linecap='round' stroke-linejoin='round' d='M2.25 21h19.5m-18-18v18m10.5-18v18m6-13.5V21M6.75 6.75h.75m-.75 3h.75m-.75 3h.75m3-6h.75m-.75 3h.75m-.75 3h.75M6.75 21v-3.375c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21M3 3h12v18H3V3z' /%3E%3C/svg%3E"
                }}
                className="max-w-full max-h-full object-contain"
                alt="Hospital Logo"
              />
            </div>

            <div className="flex gap-3 w-full justify-center">
              <label className="relative inline-flex items-center justify-center bg-neutral-600 hover:bg-neutral-700 text-white font-semibold text-xs px-4 py-2.5 rounded-lg cursor-pointer transition-all shadow-sm active:scale-[0.98]">
                <span>{uploadingTenantId === previewTenant.id ? 'Uploading Logo...' : 'Upload New Logo'}</span>
                <input
                  type="file"
                  accept="image/*"
                  className="hidden"
                  disabled={uploadingTenantId === previewTenant.id}
                  onChange={async (e) => {
                    const file = e.target.files?.[0]
                    if (!file) return
                    setUploadingTenantId(previewTenant.id)
                    try {
                      await configApi.uploadLogo(file, previewTenant.id)
                      setLogoVersion(Date.now())
                      toast({ title: 'Hospital logo updated', variant: 'success' })
                    } catch (err) {
                      console.error(err)
                    } finally {
                      setUploadingTenantId(null)
                    }
                  }}
                />
              </label>
              <button
                onClick={() => setPreviewTenant(null)}
                className="px-4 py-2.5 border border-neutral-200 text-neutral-600 text-xs font-semibold rounded-lg hover:bg-neutral-50 transition-colors cursor-pointer"
              >
                Close Preview
              </button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}
