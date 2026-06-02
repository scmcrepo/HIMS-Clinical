import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { cn } from '../../../../lib/utils';
import { toast } from '../../../../hooks/useToast';
import { inputCls, Field, EmptyState, AddButton, Section, Table, EditBtn, LoadingRow } from '../MasterSharedUI';
import { roleApi } from '../../../../services/user/userApi';

export default function RolesTab() {
  const qc = useQueryClient()
  const { data: roles = [], isLoading } = useQuery({ queryKey: ['roles'], queryFn: roleApi.getAll })
  const { data: features = [] } = useQuery({ queryKey: ['features', 'all'], queryFn: roleApi.getFeatures })
  
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<any>(null)
  
  const blank = {
    name: '',
    description: '',
    featureIds: [] as string[]
  }
  const [form, setForm] = useState(blank)

  const sortedFeatures = [...features].sort((a, b) => (a.featureKey || '').localeCompare(b.featureKey || ''))

  const mut = useMutation({
    mutationFn: () => editing ? roleApi.update(editing.id, form) : roleApi.create(form),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['roles'] })
      reset()
      toast({ title: editing ? 'Role updated successfully' : 'Role created successfully', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  function reset() {
    setShowForm(false)
    setEditing(null)
    setForm(blank)
  }

  function startEdit(r: any) {
    setEditing(r)
    setForm({
      name: r.name,
      description: r.description ?? '',
      featureIds: (r.features || []).map((f: any) => f.id)
    })
    setShowForm(true)
  }

  return (
    <Section
      title="Roles & Permissions"
      description="User roles with feature-level access control"
      action={<AddButton label="New Role" onClick={() => { reset(); setShowForm(true) }} />}
    >
      {showForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 animate-in fade-in duration-150 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-4xl overflow-hidden border border-gray-100 flex flex-col max-h-[90vh] animate-in zoom-in-95 duration-150">
            
            {/* Modal Header */}
            <div className="bg-gradient-to-r from-blue-600 to-indigo-600 px-6 py-4 flex justify-between items-center text-white">
              <h3 className="text-lg font-bold tracking-tight">
                {editing ? 'Edit Role' : 'Add Role'}
              </h3>
              <button
                onClick={reset}
                className="text-white/80 hover:text-white hover:bg-white/10 p-1.5 rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-white/20"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            {/* Modal Body */}
            <div className="p-6 overflow-y-auto space-y-4 flex-1 bg-gray-50/50">
              <div className="space-y-4 bg-white p-5 rounded-xl border border-gray-150 shadow-sm">
                
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <Field label="Description">
                    <input
                      type="text"
                      className={inputCls}
                      value={form.description}
                      onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
                    />
                  </Field>

                  <Field label="Name *">
                    <input
                      type="text"
                      className={inputCls}
                      value={form.name}
                      onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
                      required
                    />
                  </Field>
                </div>

                <Field label="Features">
                  <select
                    multiple
                    className={cn(inputCls, "h-64")}
                    value={form.featureIds}
                    onChange={e => {
                      const selectedOptions = Array.from(e.target.selectedOptions, option => option.value);
                      setForm(f => ({ ...f, featureIds: selectedOptions }));
                    }}
                  >
                    {sortedFeatures.map((f: any) => (
                      <option key={f.id} value={f.id}>
                        {f.featureKey}
                      </option>
                    ))}
                  </select>
                </Field>

              </div>
            </div>

            {/* Modal Footer */}
            <div className="bg-gray-50 px-6 py-4 flex justify-end gap-3 border-t border-gray-150">
              <button
                type="button"
                onClick={reset}
                className="px-4 py-2 text-xs font-bold rounded-lg border border-gray-200 text-gray-700 hover:bg-gray-100 active:bg-gray-200 transition-all focus:outline-none"
              >Cancel</button>
              <button
                type="button"
                onClick={() => mut.mutate()}
                disabled={mut.isPending || !form.name.trim()}
                className="px-5 py-2 text-xs font-bold rounded-lg bg-blue-600 hover:bg-blue-700 text-white shadow-md active:scale-[0.98] transition-all disabled:opacity-50 disabled:pointer-events-none focus:outline-none"
              >
                {mut.isPending ? (editing ? 'Updating…' : 'Creating…') : (editing ? 'Update Role' : 'Create')}
              </button>
            </div>

          </div>
        </div>
      )}

      <Table headers={['S.NO', 'ROLE NAME', 'DESCRIPTION', 'STATUS', 'ACTION']}>
        {isLoading ? <LoadingRow /> : roles.length === 0 ? <EmptyState label="roles" /> :
          roles.map((r, idx) => (
            <tr key={r.id} className="hover:bg-gray-50/70 transition-colors">
              <td className="px-4 py-3 text-xs font-bold text-gray-400">{idx + 1}</td>
              <td className="px-4 py-3 font-mono text-sm font-semibold text-gray-800">{r.name}</td>
              <td className="px-4 py-3 text-gray-600 text-sm">{r.description ?? '—'}</td>
              <td className="px-4 py-3 text-sm text-center">
                <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
                  Active
                </span>
              </td>
              <td className="px-4 py-3 text-center">
                <EditBtn onClick={() => startEdit(r)} />
              </td>
            </tr>
          ))}
      </Table>
    </Section>
  )
}