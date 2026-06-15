import { useMemo, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from '../../../../hooks/useToast';
import { Field, EmptyState, AddButton, Section, Table, EditBtn, LoadingRow, inputCls } from '../MasterSharedUI';
import { roleApi } from '../../../../services/user/userApi';

interface Feature { id: string; featureKey: string; description: string | null; module: string | null }

const PERMISSION_SECTIONS = [
  {
    name: 'Front desk',
    items: [
      { label: 'Appointments', featureKey: 'APPOINTMENT' },
      { label: 'Registration', featureKey: 'REGISTRATION' },
      { label: 'Encounters', featureKey: 'OUT_PATIENT' },
    ]
  },
  {
    name: 'Consultant',
    items: [
      { label: 'OP Queue', featureKey: 'OP_QUEUE' },
    ]
  },
  {
    name: 'Inpatient',
    items: [
      { label: 'In Patient List', featureKey: 'IN_PATIENT' },
      { label: 'Bed Management', featureKey: 'BEDMANAGEMENT' },
      { label: 'Admission Requests', featureKey: 'ADMISSION_REQUEST' },
    ]
  },
  {
    name: 'Billing',
    items: [
      { label: 'OP Billing', featureKey: 'OP_BILLING' },
      { label: 'IP Billing', featureKey: 'IP_BILLING' },
    ]
  },
  {
    name: 'Diagnostics',
    items: [
      { label: 'Laboratory', featureKey: 'LAB_REPORT' },
      { label: 'Radiology', featureKey: 'RADIOLOGY' },
    ]
  },
  {
    name: 'Pharmacy',
    items: [
      { label: 'Sales', featureKey: 'PHARMACY_SALES' },
      { label: 'Prescribed Orders', featureKey: 'PRESCRIBED_ORDERS' },
      { label: 'Sales History', featureKey: 'PHARMACY_SALES_HISTORY' },
      { label: 'Sales Return', featureKey: 'SALES_RETURN' },
      { label: 'Purchase Order', featureKey: 'PURCHASE_ORDER' },
      { label: 'GRN', featureKey: 'INVENTORY_GRN' },
      { label: 'GRN Return', featureKey: 'INVENTORY_GOODS_RETURN' },
      { label: 'Stock Adjustment', featureKey: 'STOCK_ADJUSTMENT' },
    ]
  },
  {
    name: 'Reports',
    items: [
      { label: 'Encounter Report', featureKey: 'REPORT_ENCOUNTER' },
      { label: 'Bills Report', featureKey: 'REPORT_BILLING' },
      { label: 'Collections Report', featureKey: 'REPORT_COLLECTION' },
      { label: 'Diagnostics Report', featureKey: 'REPORT_DIAGNOSTICS' },
      { label: 'Revenue Analysis', featureKey: 'REPORT_REVENUE' },
      { label: 'In Patients Report', featureKey: 'REPORT_INPATIENT' },
      { label: 'Purchase Report', featureKey: 'REPORT_PROCUREMENT' },
      { label: 'Stocks Report', featureKey: 'REPORT_INVENTORY' },
      { label: 'Sales Report', featureKey: 'REPORT_PHARMACY' },
    ]
  },
  {
    name: 'Settings',
    items: [
      { label: 'Bed', featureKey: 'SETTINGS_BED' },
      { label: 'Bed Type', featureKey: 'SETTINGS_BEDTYPE' },
      { label: 'Case Sheet Templates', featureKey: 'SETTINGS_CASESHEET_TEMPLATE' },
      { label: 'Discharge Templates', featureKey: 'SETTINGS_DISCHARGE_TEMPLATE' },
      { label: 'Category', featureKey: 'SETTINGS_CATEGORY' },
      { label: 'Charge', featureKey: 'SETTINGS_CHARGES' },
      { label: 'Consultant', featureKey: 'SETTINGS_CONSULTANT' },
      { label: 'Data Import', featureKey: 'DATA_IMPORT' },
      { label: 'Department', featureKey: 'SETTINGS_DEPARTMENT' },
      { label: 'Favorites', featureKey: 'SETTINGS_FAVORITES' },
      { label: 'Frequency', featureKey: 'SETTINGS_FREQUENCY' },
      { label: 'Hospital Profile', featureKey: 'SETTINGS_HOSPITALPROFILE' },
      { label: 'Item', featureKey: 'SETTINGS_ITEM' },
      { label: 'Order Sets', featureKey: 'SETTINGS_ORDERSET' },
      { label: 'Payers', featureKey: 'SETTINGS_PAYERTYPE' },
      { label: 'Prefix', featureKey: 'SETTINGS_PREFIX' },
      { label: 'Scheduled Drug', featureKey: 'SETTINGS_SCHEDULEDDRUG' },
      { label: 'Print Template', featureKey: 'SETTINGS_PRINT_TEMPLATE' },
      { label: 'Result Template', featureKey: 'SETTINGS_RESULT_TEMPLATE' },
      { label: 'Roles', featureKey: 'SETTINGS_ROLE' },
      { label: 'Specimen', featureKey: 'SETTINGS_SPECIMEN' },
      { label: 'Staff', featureKey: 'SETTINGS_STAFF' },
      { label: 'Supplier', featureKey: 'SETTINGS_SUPPLIER' },
      { label: 'Tax', featureKey: 'SETTINGS_TAX' },
      { label: 'Users', featureKey: 'SETTINGS_USERS' },
    ]
  }
];

export default function RolesTab() {
  const qc = useQueryClient();
  const { data: roles = [], isLoading } = useQuery({ queryKey: ['roles'], queryFn: roleApi.getAll });
  const { data: features = [] } = useQuery({ queryKey: ['features', 'all'], queryFn: roleApi.getFeatures });

  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<any>(null);
  const [filter, setFilter] = useState('');
  const blank = { name: '', description: '', featureIds: [] as string[] };
  const [form, setForm] = useState(blank);

  // Filter features to exclude MARKETING, MRD, and OTSCHEDULE modules.
  const filteredFeatures = useMemo(() => {
    const excludedModules = ['MARKETING', 'MRD', 'OTSCHEDULE'];
    return (features as Feature[]).filter(f => !excludedModules.includes(f.module || ''));
  }, [features]);

  // Create mapping of featureKey to full feature object
  const keyToFeatureMap = useMemo(() => {
    const map: Record<string, Feature> = {};
    filteredFeatures.forEach(f => {
      map[f.featureKey] = f;
    });
    return map;
  }, [filteredFeatures]);

  // Compute other permissions that are in DB but not mapped in standard sections
  const otherFeaturesList = useMemo(() => {
    const mappedKeys = new Set(PERMISSION_SECTIONS.flatMap(s => s.items.map(i => i.featureKey)));
    return filteredFeatures.filter(f => !mappedKeys.has(f.featureKey));
  }, [filteredFeatures]);

  // Combined list of all sections
  const allSections = useMemo(() => {
    const list = [...PERMISSION_SECTIONS];
    if (otherFeaturesList.length > 0) {
      list.push({
        name: 'Other permissions',
        items: otherFeaturesList.map(f => ({
          label: f.description || f.featureKey,
          featureKey: f.featureKey
        }))
      });
    }
    return list;
  }, [otherFeaturesList]);

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

  function reset() {
    setShowForm(false);
    setEditing(null);
    setForm(blank);
    setFilter('');
  }

  function startEdit(r: any) {
    setEditing(r);
    setForm({ name: r.name, description: r.description ?? '', featureIds: (r.features || []).map((f: any) => f.id) });
    setShowForm(true);
  }

  function toggle(id: string) {
    setForm(f => ({ ...f, featureIds: selected.has(id) ? f.featureIds.filter(x => x !== id) : [...f.featureIds, id] }));
  }

  function toggleSection(sectionItems: { label: string; featureKey: string }[], allOn: boolean) {
    setForm(f => {
      const set = new Set(f.featureIds);
      sectionItems.forEach(item => {
        const feat = keyToFeatureMap[item.featureKey];
        if (feat) {
          if (allOn) {
            set.delete(feat.id);
          } else {
            set.add(feat.id);
          }
        }
      });
      return { ...f, featureIds: [...set] };
    });
  }

  // Filter groups of sections for the checklist rendering
  const visibleGroups = useMemo(() => {
    return allSections
      .map(section => {
        const filteredItems = section.items.map(item => {
          const feat = keyToFeatureMap[item.featureKey];
          if (!feat) return null;
          const text = (item.label + ' ' + item.featureKey + ' ' + (feat.description || '')).toLowerCase();
          if (filter && !text.includes(filter.toLowerCase())) return null;
          return { item, feat };
        }).filter(Boolean) as { item: typeof section.items[0]; feat: Feature }[];

        return {
          name: section.name,
          items: filteredItems,
          rawItems: section.items
        };
      }).filter(g => g.items.length > 0);
  }, [allSections, keyToFeatureMap, filter]);

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
                  {visibleGroups.map(group => {
                    const availableFeatures = group.rawItems
                      .map(i => keyToFeatureMap[i.featureKey])
                      .filter(Boolean) as Feature[];
                    const allOn = availableFeatures.length > 0 && availableFeatures.every(f => selected.has(f.id));

                    return (
                      <div key={group.name} className="border border-gray-150 rounded-lg overflow-hidden">
                        <div className="flex items-center justify-between bg-gray-50 px-3 py-2 border-b border-gray-150">
                          <span className="text-xs font-bold tracking-wide text-gray-600 uppercase">{group.name}</span>
                          <button type="button" onClick={() => toggleSection(group.rawItems, allOn)}
                            className="text-xs font-semibold text-neutral-600 hover:text-neutral-800">
                            {allOn ? 'Clear all' : 'Select all'}
                          </button>
                        </div>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1 p-3">
                          {group.items.map(({ item, feat }) => (
                            <label key={item.label + '-' + feat.id} className="flex items-center gap-2 py-1 cursor-pointer text-sm text-gray-700 hover:text-gray-900">
                              <input type="checkbox" className="rounded border-gray-300 text-neutral-600 focus:ring-neutral-500"
                                checked={selected.has(feat.id)} onChange={() => toggle(feat.id)} />
                              <span className="font-semibold text-gray-800">{item.label}</span>
                              <span className="font-mono text-[10px] text-gray-400">({feat.featureKey})</span>
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
