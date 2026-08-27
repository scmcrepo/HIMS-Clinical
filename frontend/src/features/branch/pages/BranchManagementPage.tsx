import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { branchApi, type Branch } from '../../../services/branch/branchApi'
import { tenantApi } from '../../../services/tenant/tenantApi'
import { useAuthStore } from '../../../store/authStore'
import { toast } from '../../../hooks/useToast'
import { Eye, EyeOff, X, Copy, Check, MapPin, Phone } from 'lucide-react'

/**
 * Branch management for a hospital (audit 17.4). Visible to HOSPITAL_ADMIN (their hospital) and
 * SUPERADMIN. The active hospital is resolved server-side from the session, so no tenant is passed.
 * For SUPERADMIN, they can select which hospital's branches to manage via a dropdown.
 */
export default function BranchManagementPage({ isTab = false }: { isTab?: boolean }) {
  const user = useAuthStore(s => s.user)
  const canManage = (user?.isHospitalAdmin ?? false) || (user?.isSuperAdmin ?? false)
  const qc = useQueryClient()
  
  // Superadmin tenant selection
  const [selectedTenantId, setSelectedTenantId] = useState<string>('')

  // Fetch all tenants for superadmin to choose from
  const { data: tenants } = useQuery({
    queryKey: ['tenants'],
    queryFn: () => tenantApi.getAll().then(r => r.data ?? []),
    enabled: !!user?.isSuperAdmin,
  })

  // Determine the active tenant
  const activeTenantId = user?.isSuperAdmin ? selectedTenantId : (user?.tenantId ?? '')
  const headers = user?.isSuperAdmin && activeTenantId ? { 'X-Tenant-Id': activeTenantId } : undefined

  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [address, setAddress] = useState('')
  const [contactNumber, setContactNumber] = useState('')
  const [adminUsername, setAdminUsername] = useState('')
  const [adminPassword, setAdminPassword] = useState('')
  const [showAdminPassword, setShowAdminPassword] = useState(false)
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const [logoVersion] = useState(() => Date.now())
  const [editing, setEditing] = useState<Branch | null>(null)
  const [editName, setEditName] = useState('')
  const [editAddress, setEditAddress] = useState('')
  const [editContactNumber, setEditContactNumber] = useState('')
  const [viewingBranch, setViewingBranch] = useState<Branch | null>(null)
  const [copied, setCopied] = useState(false)

  const { data: rawBranches, isLoading } = useQuery({
    queryKey: ['branches', activeTenantId],
    queryFn: () => branchApi.getAll(headers).then(r => r.data ?? []),
    enabled: canManage && (!user?.isSuperAdmin || !!activeTenantId),
  })

  // Filter out the auto-created "Main Branch" (isDefault=true) for Hospital Admin.
  // Super Admin can still see all branches.
  const branches = user?.isHospitalAdmin
    ? rawBranches?.filter(b => !b.isDefault)
    : rawBranches

  const invalidate = () => qc.invalidateQueries({ queryKey: ['branches', activeTenantId] })

  const createMut = useMutation({
    mutationFn: async () => {
      return branchApi.create({
        code: code.trim(),
        name: name.trim(),
        address: address.trim() || undefined,
        contactNumber: contactNumber.trim() || undefined,
        adminUsername: adminUsername.trim() || undefined,
        adminPassword: adminPassword || undefined
      }, headers)
    },
    onSuccess: () => {
      setCode('')
      setName('')
      setAddress('')
      setContactNumber('')
      setAdminUsername('')
      setAdminPassword('')
      setConfirmPassword('')
      invalidate()
      toast({ title: 'Branch created successfully', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Failed to create branch', description: (e as any)?.response?.data?.message || e.message, variant: 'destructive' }),
  })
  const renameMut = useMutation({
    mutationFn: (b: Branch) => branchApi.update(b.id, {
      name: editName.trim(),
      address: editAddress.trim() || undefined,
      contactNumber: editContactNumber.trim() || undefined
    }, headers),
    onSuccess: () => { setEditing(null); setEditName(''); setEditAddress(''); setEditContactNumber(''); invalidate(); toast({ title: 'Branch updated successfully', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Failed to update branch', description: (e as any)?.response?.data?.message || e.message, variant: 'destructive' }),
  })
  const toggleMut = useMutation({
    mutationFn: (b: Branch) => branchApi.update(b.id, { status: b.status === 1 ? 0 : 1 }, headers),
    onSuccess: (_, b) => { invalidate(); toast({ title: `Branch ${b.status === 1 ? 'deactivated' : 'activated'} successfully`, variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Failed to update branch status', description: (e as any)?.response?.data?.message || e.message, variant: 'destructive' }),
  })

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  if (!canManage) {
    return <div className="p-8 text-sm text-red-600">Forbidden — hospital administrators only.</div>
  }

  return (
    <div className={isTab ? "space-y-5 max-w-7xl" : "p-8 max-w-7xl"}>
      {!isTab && (
        <>
          <h1 className="text-2xl font-semibold tracking-tight text-neutral-900 mb-1">Branches</h1>
          <p className="text-sm text-neutral-500 mb-6 font-medium">Manage the locations of your hospital.</p>
        </>
      )}

      {/* Hospital selector for superadmin */}
      {user?.isSuperAdmin && (
        <div className="mb-6 max-w-sm rounded-lg border border-neutral-200 bg-white p-4">
          <label className="block text-xs font-semibold text-neutral-600 uppercase tracking-wider mb-2">Select Hospital</label>
          <select
            value={selectedTenantId}
            onChange={e => {
              setSelectedTenantId(e.target.value)
              setEditing(null)
            }}
            className="w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm bg-neutral-50 focus:bg-white focus:border-neutral-900 focus:outline-none transition-colors"
          >
            <option value="">-- Choose a Hospital --</option>
            {(tenants ?? []).map(t => (
              <option key={t.id} value={t.id}>{t.name}</option>
            ))}
          </select>
        </div>
      )}

      {user?.isSuperAdmin && !selectedTenantId ? (
        <div className="rounded-lg border border-dashed border-neutral-200 p-8 text-center text-sm text-neutral-400 bg-white font-medium">
          Please select a hospital from the list above to manage its branches.
        </div>
      ) : (
        <>
          {/* Create */}
          <div className="mb-8 rounded-lg border border-neutral-200 bg-white p-4 space-y-4">
            <div className="flex flex-wrap items-end gap-3">
              <div>
                <label className="block text-xs font-medium text-neutral-600 mb-1">Code</label>
                <input value={code} onChange={e => setCode(e.target.value.toUpperCase())} placeholder="CHENNAI"
                  className="rounded-lg border border-neutral-200 px-3 py-2 text-sm uppercase focus:border-neutral-900 focus:outline-none" />
              </div>
              <div className="flex-1 min-w-[180px]">
                <label className="block text-xs font-medium text-neutral-600 mb-1">Name</label>
                <input value={name} onChange={e => setName(e.target.value)} placeholder="Chennai Branch"
                  className="w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:border-neutral-900 focus:outline-none" />
              </div>
              <div className="flex-1 min-w-[180px]">
                <label className="block text-xs font-medium text-neutral-600 mb-1">Address</label>
                <input value={address} onChange={e => setAddress(e.target.value)} placeholder="Branch Address"
                  className="w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:border-neutral-900 focus:outline-none" />
              </div>
              <div className="flex-1 min-w-[180px]">
                <label className="block text-xs font-medium text-neutral-600 mb-1">Contact Number</label>
                <input value={contactNumber} onChange={e => setContactNumber(e.target.value)} placeholder="Branch Contact"
                  className="w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:border-neutral-900 focus:outline-none" />
              </div>
            </div>
            {/* Branch Admin Credentials */}
            <div className="border-t border-neutral-100 pt-3">
              <p className="text-xs font-semibold text-neutral-400 uppercase tracking-wider mb-2">Branch Admin Account (optional)</p>
              <div className="flex flex-wrap items-end gap-3">
                <div className="flex-1 min-w-[180px]">
                  <label className="block text-xs font-medium text-neutral-600 mb-1">Admin Username</label>
                  <input value={adminUsername} onChange={e => setAdminUsername(e.target.value)} placeholder="e.g. chennai.admin"
                    className="w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:border-neutral-900 focus:outline-none" />
                </div>
                <div className="flex-1 min-w-[180px]">
                  <label className="block text-xs font-medium text-neutral-600 mb-1">Admin Password</label>
                  <div className="relative">
                    <input
                      type={showAdminPassword ? "text" : "password"}
                      value={adminPassword}
                      onChange={e => setAdminPassword(e.target.value)}
                      placeholder="••••••••"
                      className="w-full rounded-lg border border-neutral-200 pl-3 pr-10 py-2 text-sm focus:border-neutral-900 focus:outline-none"
                    />
                    <button
                      type="button"
                      tabIndex={-1}
                      onClick={() => setShowAdminPassword(!showAdminPassword)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-neutral-400 hover:text-neutral-600 focus:outline-none cursor-pointer"
                    >
                      {showAdminPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                    </button>
                  </div>
                </div>
                <div className="flex-1 min-w-[180px]">
                  <label className="block text-xs font-medium text-neutral-600 mb-1">Confirm Admin Password</label>
                  <div className="relative">
                    <input
                      type={showConfirmPassword ? "text" : "password"}
                      value={confirmPassword}
                      onChange={e => setConfirmPassword(e.target.value)}
                      placeholder="••••••••"
                      className={`w-full rounded-lg border pl-3 pr-10 py-2 text-sm focus:outline-none ${
                        confirmPassword && adminPassword !== confirmPassword
                          ? 'border-red-500 focus:border-red-500'
                          : 'border-neutral-200 focus:border-neutral-900'
                      }`}
                    />
                    <button
                      type="button"
                      tabIndex={-1}
                      onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-neutral-400 hover:text-neutral-600 focus:outline-none cursor-pointer"
                    >
                      {showConfirmPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                    </button>
                  </div>
                </div>
                <button onClick={() => createMut.mutate()} disabled={!code.trim() || !name.trim() || createMut.isPending || (adminPassword !== confirmPassword)}
                  className="rounded-lg bg-neutral-900 px-4 py-2 text-sm font-semibold text-white hover:bg-neutral-800 disabled:opacity-50 transition-colors cursor-pointer">
                  {createMut.isPending ? 'Creating…' : 'Add branch'}
                </button>
              </div>
              {confirmPassword && adminPassword !== confirmPassword && (
                <p className="text-[11px] text-red-500 font-medium mt-1.5 animate-fadeIn">Passwords do not match.</p>
              )}
            </div>
          </div>
          {createMut.error && (
            <p className="mb-4 text-sm text-red-600">
              {(createMut.error as any)?.response?.data?.message || 'Could not create branch.'}
            </p>
          )}

          {/* List */}
          {isLoading ? (
            <p className="text-sm text-neutral-500">Loading…</p>
          ) : (
            <div className="overflow-hidden rounded-lg border border-neutral-200 bg-white">
              <table className="w-full text-sm">
                <thead className="bg-neutral-50 text-left text-xs font-medium text-neutral-500">
                  <tr>
                    <th className="px-4 py-2.5">Code</th>
                    <th className="px-4 py-2.5">Name</th>
                    <th className="px-4 py-2.5">Default</th>
                    <th className="px-4 py-2.5">Status</th>
                    <th className="px-4 py-2.5 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-neutral-100">
                  {(branches ?? []).map(b => (
                    <tr key={b.id} className="animate-fadeIn">
                      <td className="px-4 py-2.5 font-mono text-xs text-neutral-700">{b.code}</td>
                      <td className="px-4 py-2.5 text-neutral-900 font-medium">
                        {editing?.id === b.id ? (
                          <input value={editName} onChange={e => setEditName(e.target.value)} placeholder="Branch Name"
                            className="rounded border border-neutral-300 px-2 py-1 text-sm focus:border-neutral-900 focus:outline-none" />
                        ) : (
                          <div className="flex items-center gap-3">
                            <div className="w-8 h-8 rounded bg-neutral-50 border border-neutral-200 flex items-center justify-center overflow-hidden shrink-0 shadow-sm">
                              <img
                                src={`/api/hospitalProfile/logo?tenantId=${activeTenantId}&t=${logoVersion}`}
                                onError={(e) => {
                                  e.currentTarget.src = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 24 24' stroke-width='1.5' stroke='%23a3a3a3' class='w-4 h-4'%3E%3Cpath stroke-linecap='round' stroke-linejoin='round' d='M2.25 21h19.5m-18-18v18m10.5-18v18m6-13.5V21M6.75 6.75h.75m-.75 3h.75m-.75 3h.75m3-6h.75m-.75 3h.75m-.75 3h.75M6.75 21v-3.375c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21M3 3h12v18H3V3z' /%3E%3C/svg%3E"
                                }}
                                className="w-full h-full object-contain p-1"
                                alt="Hospital Logo"
                              />
                            </div>
                            <span>{b.name}</span>
                          </div>
                        )}
                      </td>
                      <td className="px-4 py-2.5">
                        {b.isDefault && <span className="rounded bg-neutral-100 px-1.5 py-0.5 text-[11px] font-medium text-neutral-500">Default</span>}
                      </td>
                      <td className="px-4 py-2.5">
                        <span className={b.status === 1 ? 'text-green-600' : 'text-neutral-400'}>
                          {b.status === 1 ? 'Active' : 'Inactive'}
                        </span>
                      </td>
                      <td className="px-4 py-2.5 text-right">
                        {editing?.id === b.id ? (
                          <div className="flex justify-end gap-2">
                            <button onClick={() => renameMut.mutate(b)} disabled={!editName.trim()}
                              className="text-xs font-medium text-neutral-900 hover:underline disabled:opacity-40 cursor-pointer">Save</button>
                            <button onClick={() => { setEditing(null); setEditName(''); setEditAddress(''); setEditContactNumber('') }}
                              className="text-xs text-neutral-400 hover:text-neutral-700 cursor-pointer">Cancel</button>
                          </div>
                        ) : (
                          <div className="flex justify-end gap-3">
                            <button onClick={() => setViewingBranch(b)}
                              className="inline-flex items-center gap-1 text-xs font-medium text-neutral-700 hover:underline cursor-pointer">
                              <Eye size={12} />
                              View
                            </button>
                            <button onClick={() => { 
                              setEditing(b); 
                              setEditName(b.name); 
                              setEditAddress(b.address ?? ''); 
                              setEditContactNumber(b.contactNumber ?? '') 
                            }}
                              className="text-xs font-medium text-neutral-700 hover:underline cursor-pointer">Edit</button>
                            {!b.isDefault && (
                              <button onClick={() => toggleMut.mutate(b)}
                                className="text-xs font-medium text-neutral-500 hover:text-red-600 cursor-pointer">
                                {b.status === 1 ? 'Deactivate' : 'Activate'}
                              </button>
                            )}
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                  {(branches ?? []).length === 0 && (
                    <tr><td colSpan={5} className="px-4 py-6 text-center text-sm text-neutral-400">No branches yet.</td></tr>
                  )}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {/* Details Modal */}
      {viewingBranch && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-neutral-900/40 backdrop-blur-sm animate-fadeIn">
          <div className="bg-white rounded-xl shadow-2xl p-6 border border-neutral-100 max-w-md w-full relative animate-scaleIn">
            <button 
              onClick={() => setViewingBranch(null)}
              className="absolute top-4 right-4 text-neutral-400 hover:text-neutral-600 transition-colors cursor-pointer"
              title="Close"
            >
              <X size={18} />
            </button>

            <div className="flex flex-col items-center text-center pb-4 border-b border-neutral-100">
              <div className="w-20 h-20 rounded-xl bg-neutral-50 border border-neutral-200 flex items-center justify-center overflow-hidden shadow-sm mb-3">
                <img
                  src={`/api/hospitalProfile/logo?tenantId=${activeTenantId}&t=${logoVersion}`}
                  onError={(e) => {
                    e.currentTarget.src = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 24 24' stroke-width='1.5' stroke='%23a3a3a3' class='w-8 h-8'%3E%3Cpath stroke-linecap='round' stroke-linejoin='round' d='M2.25 21h19.5m-18-18v18m10.5-18v18m6-13.5V21M6.75 6.75h.75m-.75 3h.75m-.75 3h.75m3-6h.75m-.75 3h.75m-.75 3h.75M6.75 21v-3.375c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21M3 3h12v18H3V3z' /%3E%3C/svg%3E"
                  }}
                  className="w-full h-full object-contain p-2"
                  alt="Hospital Logo"
                />
              </div>
              <h3 className="text-lg font-bold text-neutral-900">{viewingBranch.name}</h3>
              <div className="flex gap-2 mt-1.5">
                {viewingBranch.isDefault && (
                  <span className="inline-flex rounded-full px-2.5 py-0.5 text-xs font-semibold bg-neutral-100 text-neutral-600">
                    Default Branch
                  </span>
                )}
                <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-semibold ${viewingBranch.status === 1 ? 'bg-green-50 text-green-700' : 'bg-neutral-100 text-neutral-500'}`}>
                  {viewingBranch.status === 1 ? 'Active' : 'Inactive'}
                </span>
              </div>
            </div>

            <div className="mt-4 space-y-3.5 text-sm text-neutral-600">
              <div>
                <span className="block text-xs font-semibold text-neutral-400 uppercase tracking-wider mb-1">Branch ID</span>
                <div className="flex items-center justify-between gap-2 bg-neutral-50 border border-neutral-200 rounded-lg p-2 font-mono text-xs text-neutral-700">
                  <span className="truncate select-all">{viewingBranch.id}</span>
                  <button
                    onClick={() => handleCopy(viewingBranch.id)}
                    className="text-neutral-400 hover:text-neutral-900 transition-colors shrink-0 cursor-pointer"
                    title="Copy to clipboard"
                  >
                    {copied ? <Check size={14} className="text-green-600" /> : <Copy size={14} />}
                  </button>
                </div>
              </div>

              <div>
                <span className="block text-xs font-semibold text-neutral-400 uppercase tracking-wider mb-1">Branch Code</span>
                <p className="text-neutral-800 font-mono font-medium">{viewingBranch.code}</p>
              </div>

              <div>
                <span className="block text-xs font-semibold text-neutral-400 uppercase tracking-wider mb-1">Address</span>
                <div className="flex items-start gap-2 text-neutral-800">
                  <MapPin size={16} className="text-neutral-400 shrink-0 mt-0.5" />
                  <span>{viewingBranch.address || '—'}</span>
                </div>
              </div>

              <div>
                <span className="block text-xs font-semibold text-neutral-400 uppercase tracking-wider mb-1">Contact Number</span>
                <div className="flex items-center gap-2 text-neutral-800">
                  <Phone size={16} className="text-neutral-400 shrink-0" />
                  <span>{viewingBranch.contactNumber || '—'}</span>
                </div>
              </div>
            </div>

            <div className="mt-6 flex justify-end">
              <button
                onClick={() => setViewingBranch(null)}
                className="rounded-lg bg-neutral-900 hover:bg-neutral-800 text-white font-semibold text-sm px-4 py-2 transition-colors w-full sm:w-auto text-center cursor-pointer"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
