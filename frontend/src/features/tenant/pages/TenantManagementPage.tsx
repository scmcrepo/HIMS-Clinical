import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { tenantApi, type Tenant } from '../../../services/tenant/tenantApi'
import { useAuthStore } from '../../../store/authStore'

/**
 * Platform tenant management. Rendered only for SUPERADMIN (guard at the route AND here).
 */
export default function TenantManagementPage() {
  const isSuperAdmin = useAuthStore(s => s.user?.isSuperAdmin ?? false)
  const qc = useQueryClient()
  const [newName, setNewName] = useState('')
  const [newDescription, setNewDescription] = useState('')
  const [adminUser, setAdminUser] = useState('')
  const [adminPass, setAdminPass] = useState('')

  const { data: tenants, isLoading } = useQuery({
    queryKey: ['tenants'],
    queryFn: () => tenantApi.getAll().then(r => r.data ?? []),
    enabled: isSuperAdmin,
  })

  const invalidate = () => qc.invalidateQueries({ queryKey: ['tenants'] })

  const createMut = useMutation({
    mutationFn: () => tenantApi.create({ name: newName.trim(), description: newDescription.trim() || undefined,
      adminUsername: adminUser.trim() || undefined, adminPassword: adminPass || undefined }),
    onSuccess: () => { setNewName(''); setNewDescription(''); setAdminUser(''); setAdminPass(''); invalidate() },
  })
  const seedMut = useMutation({
    mutationFn: (id: string) => tenantApi.seedRbac(id),
  })
  const toggleMut = useMutation({
    mutationFn: (t: Tenant) => tenantApi.update(t.id, { status: t.status === 1 ? 0 : 1 }),
    onSuccess: invalidate,
  })

  if (!isSuperAdmin) {
    return <div className="p-8 text-sm text-red-600">Forbidden — platform administrators only.</div>
  }

  return (
    <div className="p-8 max-w-4xl">
      <h1 className="text-2xl font-semibold tracking-tight text-neutral-900 mb-6">Tenants</h1>

      {/* Create */}
      <div className="mb-8 flex flex-wrap items-end gap-3 rounded-lg border border-neutral-200 bg-white p-4">
        <div className="flex-1 min-w-[180px]">
          <label className="block text-xs font-medium text-neutral-600 mb-1">Name</label>
          <input value={newName} onChange={e => setNewName(e.target.value)} placeholder="Apollo Hospital, Chennai"
            className="w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:border-neutral-900 focus:outline-none" />
        </div>
        <div className="flex-1 min-w-[180px]">
          <label className="block text-xs font-medium text-neutral-600 mb-1">Description</label>
          <input value={newDescription} onChange={e => setNewDescription(e.target.value)} placeholder="Main multi-specialty hub"
            className="w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:border-neutral-900 focus:outline-none" />
        </div>
        <div>
          <label className="block text-xs font-medium text-neutral-600 mb-1">Hospital Admin username</label>
          <input value={adminUser} onChange={e => setAdminUser(e.target.value)} placeholder="apollo.admin"
            className="rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:border-neutral-900 focus:outline-none" />
        </div>
        <div>
          <label className="block text-xs font-medium text-neutral-600 mb-1">Hospital Admin password</label>
          <input type="password" value={adminPass} onChange={e => setAdminPass(e.target.value)} placeholder="••••••"
            className="rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:border-neutral-900 focus:outline-none" />
        </div>
        <button onClick={() => createMut.mutate()} disabled={!newName.trim() || createMut.isPending}
          className="rounded-lg bg-neutral-900 px-4 py-2 text-sm font-semibold text-white hover:bg-neutral-800 disabled:opacity-50">
          {createMut.isPending ? 'Onboarding…' : 'Onboard hospital'}
        </button>
      </div>
      {createMut.error && (
        <p className="mb-4 text-xs text-red-600">{(createMut.error as any)?.response?.data?.message ?? 'Create failed'}</p>
      )}

      {/* Table */}
      {isLoading ? (
        <p className="text-sm text-neutral-500">Loading…</p>
      ) : (
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200 text-left text-neutral-500">
              <th className="py-2 font-medium">Name</th>
              <th className="py-2 font-medium">Tenant ID</th>
              <th className="py-2 font-medium">Description</th>
              <th className="py-2 font-medium">Status</th>
              <th className="py-2 font-medium text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {(tenants ?? []).map(t => (
              <tr key={t.id} className="border-b border-neutral-100">
                <td className="py-2.5 text-neutral-900 font-medium">{t.name}</td>
                <td className="py-2.5 text-xs text-neutral-400 font-mono select-all">{t.id}</td>
                <td className="py-2.5 text-neutral-500">{t.description ?? '-'}</td>
                <td className="py-2.5">
                  <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${t.status === 1 ? 'bg-green-50 text-green-700' : 'bg-neutral-100 text-neutral-500'}`}>
                    {t.status === 1 ? 'Active' : 'Inactive'}
                  </span>
                </td>
                <td className="py-2.5 text-right space-x-2">
                  <button onClick={() => seedMut.mutate(t.id)} disabled={seedMut.isPending}
                    className="rounded-md border border-neutral-200 px-2.5 py-1 text-xs hover:bg-neutral-50 disabled:opacity-50">
                    Seed RBAC
                  </button>
                  <button onClick={() => toggleMut.mutate(t)} disabled={toggleMut.isPending}
                    className="rounded-md border border-neutral-200 px-2.5 py-1 text-xs hover:bg-neutral-50 disabled:opacity-50">
                    {t.status === 1 ? 'Deactivate' : 'Activate'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {seedMut.isSuccess && <p className="mt-3 text-xs text-green-700">RBAC seeded.</p>}
    </div>
  )
}
