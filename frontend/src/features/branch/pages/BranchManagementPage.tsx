import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { branchApi, type Branch } from '../../../services/branch/branchApi'
import { tenantApi } from '../../../services/tenant/tenantApi'
import { useAuthStore } from '../../../store/authStore'

/**
 * Branch management for a hospital (audit 17.4). Visible to HOSPITAL_ADMIN (their hospital) and
 * SUPERADMIN. The active hospital is resolved server-side from the session, so no tenant is passed.
 * For SUPERADMIN, they can select which hospital's branches to manage via a dropdown.
 */
export default function BranchManagementPage() {
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
  const [editing, setEditing] = useState<Branch | null>(null)
  const [editName, setEditName] = useState('')

  const { data: branches, isLoading } = useQuery({
    queryKey: ['branches', activeTenantId],
    queryFn: () => branchApi.getAll(headers).then(r => r.data ?? []),
    enabled: canManage && (!user?.isSuperAdmin || !!activeTenantId),
  })

  const invalidate = () => qc.invalidateQueries({ queryKey: ['branches', activeTenantId] })

  const createMut = useMutation({
    mutationFn: () => branchApi.create({ code: code.trim(), name: name.trim() }, headers),
    onSuccess: () => { setCode(''); setName(''); invalidate() },
  })
  const renameMut = useMutation({
    mutationFn: (b: Branch) => branchApi.update(b.id, { name: editName.trim() }, headers),
    onSuccess: () => { setEditing(null); setEditName(''); invalidate() },
  })
  const toggleMut = useMutation({
    mutationFn: (b: Branch) => branchApi.update(b.id, { status: b.status === 1 ? 0 : 1 }, headers),
    onSuccess: invalidate,
  })

  if (!canManage) {
    return <div className="p-8 text-sm text-red-600">Forbidden — hospital administrators only.</div>
  }

  return (
    <div className="p-8 max-w-4xl">
      <h1 className="text-2xl font-semibold tracking-tight text-neutral-900 mb-1">Branches</h1>
      <p className="text-sm text-neutral-500 mb-6 font-medium">Manage the locations of your hospital. Every hospital has a default Main Branch.</p>

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
          <div className="mb-8 flex flex-wrap items-end gap-3 rounded-lg border border-neutral-200 bg-white p-4">
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
            <button onClick={() => createMut.mutate()} disabled={!code.trim() || !name.trim() || createMut.isPending}
              className="rounded-lg bg-neutral-900 px-4 py-2 text-sm font-semibold text-white hover:bg-neutral-800 disabled:opacity-50 transition-colors">
              {createMut.isPending ? 'Creating…' : 'Add branch'}
            </button>
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
                    <tr key={b.id}>
                      <td className="px-4 py-2.5 font-mono text-xs text-neutral-700">{b.code}</td>
                      <td className="px-4 py-2.5 text-neutral-900">
                        {editing?.id === b.id ? (
                          <input value={editName} onChange={e => setEditName(e.target.value)}
                            className="rounded border border-neutral-300 px-2 py-1 text-sm focus:border-neutral-900 focus:outline-none" />
                        ) : b.name}
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
                              className="text-xs font-medium text-neutral-900 hover:underline disabled:opacity-40">Save</button>
                            <button onClick={() => { setEditing(null); setEditName('') }}
                              className="text-xs text-neutral-400 hover:text-neutral-700">Cancel</button>
                          </div>
                        ) : (
                          <div className="flex justify-end gap-3">
                            <button onClick={() => { setEditing(b); setEditName(b.name) }}
                              className="text-xs font-medium text-neutral-700 hover:underline">Edit</button>
                            {!b.isDefault && (
                              <button onClick={() => toggleMut.mutate(b)}
                                className="text-xs font-medium text-neutral-500 hover:text-red-600">
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
    </div>
  )
}
