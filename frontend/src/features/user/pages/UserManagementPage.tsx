import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { userApi, roleApi, type CreateUserCmd, type CreateRoleCmd } from '../../../services/user/userApi'
import { toast } from '../../../hooks/useToast'
import { cn } from '../../../lib/utils'

type Tab = 'users' | 'roles'

export default function UserManagementPage() {
  const [tab, setTab] = useState<Tab>('users')
  return (
    <div className="space-y-5">
      <h2 className="text-xl font-bold text-gray-900">User Management</h2>
      <div className="flex gap-1 bg-gray-100 p-1 rounded-lg w-fit" role="tablist">
        {(['users', 'roles'] as const).map(t => (
          <button key={t} role="tab" aria-selected={tab === t}
            onClick={() => setTab(t)}
            className={cn('px-4 py-1.5 text-sm font-medium rounded-md transition-colors capitalize',
              tab === t ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700')}>
            {t === 'users' ? 'Users' : 'Roles & Permissions'}
          </button>
        ))}
      </div>
      {tab === 'users' ? <UsersTab /> : <RolesTab />}
    </div>
  )
}

function UsersTab() {
  const qc = useQueryClient()
  const { data: users, isLoading } = useQuery({ queryKey: ['users'], queryFn: () => userApi.getAll() })
  const { data: roles } = useQuery({ queryKey: ['roles'], queryFn: () => roleApi.getAll() })
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState<CreateUserCmd>({ username: '', password: '', firstName: '', lastName: '', email: '', roleIds: [], showCasesheet: false, speechLanguage: 'en-IN' })

  const createMutation = useMutation({
    mutationFn: (cmd: CreateUserCmd) => userApi.create(cmd),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['users'] }); setShowForm(false); toast({ title: 'User created', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Create failed', description: e.message, variant: 'destructive' }),
  })

  const inputCls = "w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500"

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <button onClick={() => setShowForm(v => !v)}
          className="px-4 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 transition-colors">
          + Add User
        </button>
      </div>

      {showForm && (
        <div className="bg-gray-50 border border-gray-200 rounded-xl p-5 space-y-4">
          <h3 className="text-sm font-semibold text-gray-900">New User</h3>
          <div className="grid grid-cols-2 gap-4">
            <div><label className="block text-xs font-medium text-gray-700 mb-1">Username *</label>
              <input value={form.username} onChange={e => setForm(f => ({...f, username: e.target.value}))} className={inputCls} placeholder="min 3 chars" /></div>
            <div><label className="block text-xs font-medium text-gray-700 mb-1">Password *</label>
              <input type="password" value={form.password} onChange={e => setForm(f => ({...f, password: e.target.value}))} className={inputCls} placeholder="min 6 chars" /></div>
            <div><label className="block text-xs font-medium text-gray-700 mb-1">First Name *</label>
              <input value={form.firstName} onChange={e => setForm(f => ({...f, firstName: e.target.value}))} className={inputCls} /></div>
            <div><label className="block text-xs font-medium text-gray-700 mb-1">Last Name *</label>
              <input value={form.lastName} onChange={e => setForm(f => ({...f, lastName: e.target.value}))} className={inputCls} /></div>
            <div><label className="block text-xs font-medium text-gray-700 mb-1">Email</label>
              <input type="email" value={form.email} onChange={e => setForm(f => ({...f, email: e.target.value}))} className={inputCls} /></div>
            <div><label className="block text-xs font-medium text-gray-700 mb-1">Roles *</label>
              <select multiple value={form.roleIds} onChange={e => setForm(f => ({...f, roleIds: Array.from(e.target.selectedOptions, o => o.value)}))}
                className={`${inputCls} h-24`} aria-label="Assign roles">
                {roles?.filter((r: any) => r.status !== 'INACTIVE' && r.status !== 0).map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
              </select>
              <p className="text-xs text-gray-400 mt-0.5">Hold Ctrl/Cmd to select multiple</p>
            </div>
          </div>
          <div className="flex gap-3">
            <button onClick={() => createMutation.mutate(form)}
              disabled={!form.username || !form.password || !form.firstName || !form.lastName || createMutation.isPending}
              className="px-5 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 disabled:opacity-50 transition-colors">
              {createMutation.isPending ? 'Creating…' : 'Create User'}
            </button>
            <button onClick={() => setShowForm(false)}
              className="px-4 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-gray-50 transition-colors">
              Cancel
            </button>
          </div>
        </div>
      )}

      {isLoading && <p className="text-sm text-gray-500" aria-live="polite">Loading users…</p>}
      {users && (
        <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
          <table className="w-full text-sm" aria-label="Users list">
            <thead><tr className="bg-gray-50 border-b border-gray-100 text-left text-xs">
              <th className="px-4 py-3 font-semibold text-gray-600">Username</th>
              <th className="px-4 py-3 font-semibold text-gray-600">Full Name</th>
              <th className="px-4 py-3 font-semibold text-gray-600">Email</th>
              <th className="px-4 py-3 font-semibold text-gray-600">Roles</th>
              <th className="px-4 py-3 font-semibold text-gray-600">Status</th>
            </tr></thead>
            <tbody className="divide-y divide-gray-100">
              {users.map(u => (
                <tr key={u.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-3 font-mono text-sm text-gray-800">{u.username}</td>
                  <td className="px-4 py-3 font-medium text-gray-900">{u.fullName}</td>
                  <td className="px-4 py-3 text-gray-500">{u.email ?? '—'}</td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-1">
                      {u.roles.map(r => (
                        <span key={r.id} className="inline-flex items-center px-2 py-0.5 rounded bg-blue-50 text-blue-700 text-xs font-medium">{r.name}</span>
                      ))}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <span className={cn('inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border',
                      u.status === 'ACTIVE' ? 'bg-green-50 text-green-700 border-green-200' : 'bg-gray-100 text-gray-600 border-gray-200')}>
                      {u.status}
                    </span>
                  </td>
                </tr>
              ))}
              {users.length === 0 && <tr><td colSpan={5} className="px-4 py-10 text-center text-gray-400 text-sm">No users found</td></tr>}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function RolesTab() {
  const qc = useQueryClient()
  const { data: roles, isLoading } = useQuery({ queryKey: ['roles'], queryFn: () => roleApi.getAll() })
  const { data: features } = useQuery({ queryKey: ['features', 'all'], queryFn: () => roleApi.getFeatures() })
  const [showForm, setShowForm] = useState(false)
  const [roleName, setRoleName]     = useState('')
  const [roleDesc, setRoleDesc]     = useState('')
  const [selectedFeatureIds, setSelectedFeatureIds] = useState<Set<string>>(new Set())

  const createMutation = useMutation({
    mutationFn: (cmd: CreateRoleCmd) => roleApi.create(cmd),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['roles'] }); setShowForm(false); setRoleName(''); setSelectedFeatureIds(new Set()); toast({ title: 'Role created — permission cache rebuilt', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Create failed', description: e.message, variant: 'destructive' }),
  })

  const toggleFeature = (id: string) => setSelectedFeatureIds(prev => { const s = new Set(prev); s.has(id) ? s.delete(id) : s.add(id); return s })

  // Group features by module
  const featuresByModule: Record<string, typeof features> = {}
  features?.forEach(f => {
    const mod = f.module ?? 'OTHER'
    if (!featuresByModule[mod]) featuresByModule[mod] = []
    featuresByModule[mod]!.push(f)
  })

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <button onClick={() => setShowForm(v => !v)}
          className="px-4 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 transition-colors">
          + New Role
        </button>
      </div>

      {showForm && (
        <div className="bg-gray-50 border border-gray-200 rounded-xl p-5 space-y-4">
          <h3 className="text-sm font-semibold text-gray-900">New Role</h3>
          <div className="grid grid-cols-2 gap-4">
            <div><label className="block text-xs font-medium text-gray-700 mb-1">Role Name *</label>
              <input value={roleName} onChange={e => setRoleName(e.target.value)} placeholder="e.g. ROLE_DOCTOR"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500" /></div>
            <div><label className="block text-xs font-medium text-gray-700 mb-1">Description</label>
              <input value={roleDesc} onChange={e => setRoleDesc(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500" /></div>
          </div>
          <div>
            <p className="text-xs font-medium text-gray-700 mb-2">Permissions ({selectedFeatureIds.size} selected)</p>
            <div className="max-h-72 overflow-y-auto border border-gray-200 rounded-lg bg-white p-3 space-y-3">
              {Object.entries(featuresByModule).map(([mod, fts]) => (
                <div key={mod}>
                  <p className="text-xs font-bold text-gray-500 uppercase tracking-wide mb-1">{mod}</p>
                  <div className="grid grid-cols-2 gap-1">
                    {fts?.map(f => (
                      <label key={f.id} className="flex items-center gap-1.5 cursor-pointer hover:bg-gray-50 rounded px-1 py-0.5">
                        <input type="checkbox" checked={selectedFeatureIds.has(f.id)} onChange={() => toggleFeature(f.id)}
                          className="w-3.5 h-3.5 rounded border-gray-300 text-neutral-600" />
                        <span className="text-xs text-gray-700 font-mono">{f.featureKey}</span>
                      </label>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>
          <div className="flex gap-3">
            <button onClick={() => createMutation.mutate({ name: roleName, description: roleDesc, featureIds: Array.from(selectedFeatureIds) })}
              disabled={!roleName.trim() || createMutation.isPending}
              className="px-5 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 disabled:opacity-50 transition-colors">
              {createMutation.isPending ? 'Creating…' : 'Create Role'}
            </button>
            <button onClick={() => setShowForm(false)}
              className="px-4 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-gray-50 transition-colors">
              Cancel
            </button>
          </div>
        </div>
      )}

      {isLoading && <p className="text-sm text-gray-500" aria-live="polite">Loading…</p>}
      {roles && (
        <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
          <table className="w-full text-sm" aria-label="Roles list">
            <thead><tr className="bg-gray-50 border-b border-gray-100 text-left text-xs">
              <th className="px-4 py-3 font-semibold text-gray-600">Role Name</th>
              <th className="px-4 py-3 font-semibold text-gray-600">Description</th>
              <th className="px-4 py-3 font-semibold text-gray-600">Permissions</th>
            </tr></thead>
            <tbody className="divide-y divide-gray-100">
              {roles.map(r => (
                <tr key={r.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-3 font-mono text-sm font-semibold text-gray-800">{r.name}</td>
                  <td className="px-4 py-3 text-gray-500 text-xs">{r.description ?? '—'}</td>
                  <td className="px-4 py-3">
                    <span className="text-xs text-gray-600">{r.features.length} permission{r.features.length !== 1 ? 's' : ''}</span>
                  </td>
                </tr>
              ))}
              {roles.length === 0 && <tr><td colSpan={3} className="px-4 py-10 text-center text-gray-400 text-sm">No roles configured yet</td></tr>}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
