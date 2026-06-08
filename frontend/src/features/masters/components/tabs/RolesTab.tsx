import { useMemo, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from '../../../../hooks/useToast';
import { Field, EmptyState, AddButton, Section, Table, EditBtn, LoadingRow, inputCls } from '../MasterSharedUI';
import { roleApi } from '../../../../services/user/userApi';

interface Feature { id: string; featureKey: string; description: string | null; module: string | null }

export default function RolesTab() {
  const qc = useQueryClient();
  const { data: roles = [], isLoading } = useQuery({ queryKey: ['roles'], queryFn: roleApi.getAll });
  const { data: features = [] } = useQuery({ queryKey: ['features', 'all'], queryFn: roleApi.getFeatures });

  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<any>(null);
  const [filter, setFilter] = useState('');
  const blank = { name: '', description: '', featureIds: [] as string[] };
  const [form, setForm] = useState(blank);

  // Group features by module for a readable matrix.
  const grouped = useMemo(() => {
    const map: Record<string, Feature[]> = {};
    (features as Feature[]).forEach(f => {
      const mod = f.module || 'OTHER';
      (map[mod] ||= []).push(f);
    });
    Object.values(map).forEach(arr => arr.sort((a, b) => a.featureKey.localeCompare(b.featureKey)));
    return Object.entries(map).sort(([a], [b]) => a.localeCompare(b));
  }, [features]);

  const selected = new Set(form.featureIds);

  const mut = useMutation({
    mutationFn: () => (editing ? roleApi.update(editing.id, form) : roleApi.create(form)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['roles'] });
      reset();
      toast({ title: editing ? 'Role updated successfully' : 'Role created successfully', variant: 'success' });
    },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  });

  function reset() { setShowForm(false); setEditing(null); setForm(blank); setFilter(''); }

  function startEdit(r: any) {
    setEditing(r);
    setForm({ name: r.name, description: r.description ?? '', featureIds: (r.features || []).map((f: any) => f.id) });
    setShowForm(true);
  }

  function toggle(id: string) {
    setForm(f => ({ ...f, featureIds: selected.has(id) ? f.featureIds.filter(x => x !== id) : [...f.featureIds, id] }));
  }

  function toggleModule(_mod: string, ids: string[], allOn: boolean) {
    setForm(f => {
      const set = new Set(f.featureIds);
      ids.forEach(id => (allOn ? set.delete(id) : set.add(id)));
      return { ...f, featureIds: [...set] };
    });
  }

  const visibleGroups = grouped
    .map(([mod, list]) => [mod, list.filter(f => !filter || f.featureKey.toLowerCase().includes(filter.toLowerCase()))] as [string, Feature[]])
    .filter(([, list]) => list.length > 0);

  return (
    <Section
      title="Roles & Permissions"
      description="Assign feature-level access to each role. Changes apply immediately on the server."
      action={<AddButton label="New Role" onClick={() => { reset(); setShowForm(true); }} />}
    >
      {showForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 animate-in fade-in duration-150 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-4xl overflow-hidden border border-gray-100 flex flex-col max-h-[92vh] animate-in zoom-in-95 duration-150">
            <div className="bg-gradient-to-r from-neutral-600 to-neutral-600 px-6 py-4 flex justify-between items-center text-white">
              <h3 className="text-lg font-bold tracking-tight">{editing ? 'Edit Role' : 'Add Role'}</h3>
              <button onClick={reset} className="text-white/80 hover:text-white hover:bg-white/10 p-1.5 rounded-lg transition-colors">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" /></svg>
              </button>
            </div>

            <div className="p-6 overflow-y-auto space-y-4 flex-1 bg-gray-50/50">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 bg-white p-5 rounded-xl border border-gray-150 shadow-sm">
                <Field label="Name *">
                  <input type="text" className={inputCls} value={form.name}
                    onChange={e => setForm(f => ({ ...f, name: e.target.value }))} required />
                </Field>
                <Field label="Description">
                  <input type="text" className={inputCls} value={form.description}
                    onChange={e => setForm(f => ({ ...f, description: e.target.value }))} />
                </Field>
              </div>

              <div className="bg-white p-5 rounded-xl border border-gray-150 shadow-sm space-y-3">
                <div className="flex items-center justify-between gap-3">
                  <span className="text-sm font-bold text-gray-700">
                    Permissions <span className="text-gray-400 font-medium">({form.featureIds.length} selected)</span>
                  </span>
                  <input type="text" placeholder="Filter features…" className={`${inputCls} max-w-xs`}
                    value={filter} onChange={e => setFilter(e.target.value)} />
                </div>

                <div className="space-y-3 max-h-[46vh] overflow-y-auto pr-1">
                  {visibleGroups.length === 0 && <p className="text-sm text-gray-400 py-4 text-center">No features match.</p>}
                  {visibleGroups.map(([mod, list]) => {
                    const ids = list.map(f => f.id);
                    const allOn = ids.every(id => selected.has(id));
                    return (
                      <div key={mod} className="border border-gray-150 rounded-lg overflow-hidden">
                        <div className="flex items-center justify-between bg-gray-50 px-3 py-2 border-b border-gray-150">
                          <span className="text-xs font-bold tracking-wide text-gray-600">{mod}</span>
                          <button type="button" onClick={() => toggleModule(mod, ids, allOn)}
                            className="text-xs font-semibold text-neutral-600 hover:text-neutral-800">
                            {allOn ? 'Clear all' : 'Select all'}
                          </button>
                        </div>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1 p-3">
                          {list.map(f => (
                            <label key={f.id} className="flex items-center gap-2 py-1 cursor-pointer text-sm text-gray-700 hover:text-gray-900">
                              <input type="checkbox" className="rounded border-gray-300 text-neutral-600 focus:ring-neutral-500"
                                checked={selected.has(f.id)} onChange={() => toggle(f.id)} />
                              <span className="font-mono text-xs">{f.featureKey}</span>
                            </label>
                          ))}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>

            <div className="bg-gray-50 px-6 py-4 flex justify-end gap-3 border-t border-gray-150">
              <button type="button" onClick={reset}
                className="px-4 py-2 text-xs font-bold rounded-lg border border-gray-200 text-gray-700 hover:bg-gray-100 transition-all">Cancel</button>
              <button type="button" onClick={() => mut.mutate()} disabled={mut.isPending || !form.name.trim()}
                className="px-5 py-2 text-xs font-bold rounded-lg bg-neutral-600 hover:bg-neutral-700 text-white shadow-md disabled:opacity-50 disabled:pointer-events-none transition-all">
                {mut.isPending ? (editing ? 'Updating…' : 'Creating…') : (editing ? 'Update Role' : 'Create')}
              </button>
            </div>
          </div>
        </div>
      )}

      <Table headers={['S.NO', 'ROLE NAME', 'DESCRIPTION', 'PERMISSIONS', 'ACTION']}>
        {isLoading ? <LoadingRow /> : roles.length === 0 ? <EmptyState label="roles" /> :
          roles.map((r: any, idx: number) => (
            <tr key={r.id} className="hover:bg-gray-50/70 transition-colors">
              <td className="px-4 py-3 text-xs font-bold text-gray-400">{idx + 1}</td>
              <td className="px-4 py-3 font-mono text-sm font-semibold text-gray-800">{r.name}</td>
              <td className="px-4 py-3 text-gray-600 text-sm">{r.description ?? '—'}</td>
              <td className="px-4 py-3 text-sm text-center">
                <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                  {(r.features?.length ?? 0)} features
                </span>
              </td>
              <td className="px-4 py-3 text-center"><EditBtn onClick={() => startEdit(r)} /></td>
            </tr>
          ))}
      </Table>
    </Section>
  );
}
