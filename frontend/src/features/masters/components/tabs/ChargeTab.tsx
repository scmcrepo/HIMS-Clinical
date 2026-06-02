
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { cn } from '../../../../lib/utils';
import { toast } from '../../../../hooks/useToast';
import { inputCls, labelCls, Field, EmptyState, AddButton, Section, Table, LoadingRow, ChargeAutocomplete, StatusBadge } from '../MasterSharedUI';
import { payerApi, categoryMasterApi, chargeApi, PackageCharge } from '../../../../services/masters/masterApi';

export default function ChargeTab() {
  const qc = useQueryClient()
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<any>(null)
  const [itemToDelete, setItemToDelete] = useState<{ id: string; name: string } | null>(null)

  const { data: pageData, isLoading } = useQuery({
    queryKey: ['charges', page, search],
    queryFn: () => chargeApi.getPaginated({ start: page * 10, limit: 10, ...(search ? { value: search } : {}) })
  })
  const { data: cats = [] } = useQuery({ queryKey: ['masterCategories'], queryFn: () => categoryMasterApi.getAll('CHARGE') })
  const { data: payers = [] } = useQuery({ queryKey: ['payers'], queryFn: payerApi.getAll })

  const items = pageData?.content ?? []
  const totalPages = pageData?.totalPages ?? 0
  const totalElements = pageData?.totalElements ?? 0

  const blank = {
    name: '',
    categoryId: '',
    chargeType: 'CHARGE' as 'CHARGE' | 'PACKAGE' | 'IP',
    quantitative: false,
    cashRate: '',
    creditRate: '',
    status: 'ACTIVE' as 'ACTIVE' | 'INACTIVE',
    tariffs: [] as { payorId: string; payorName: string; rate: string }[],
    packageCharges: [] as PackageCharge[]
  }
  const [form, setForm] = useState(blank)
  const [selectedPayorId, setSelectedPayorId] = useState('')
  const [enteredPayorAmount, setEnteredPayorAmount] = useState('')

  // Sub-tab states for package mapping UI
  const [activeSubTab, setActiveSubTab] = useState<'main' | 'subcharges'>('main')
  const [ipComponentType, setIpComponentType] = useState<'category' | 'charge'>('category')
  const [ipSelectedCatId, setIpSelectedCatId] = useState('')
  const [ipSelectedCharge, setIpSelectedCharge] = useState<any>(null)
  const [ipMode, setIpMode] = useState<boolean>(true)
  const [ipAmount, setIpAmount] = useState('')
  const [ipQty, setIpQty] = useState('1')

  const mut = useMutation({
    mutationFn: () => {
      const tariffsPayload: any[] = [
        { billType: 'CASH', payorId: null, rate: Math.round(parseFloat(form.cashRate || '0') * 100) },
        { billType: 'CREDIT', payorId: null, rate: Math.round(parseFloat(form.creditRate || '0') * 100) },
        ...form.tariffs.map(t => ({
          billType: 'INSURANCE',
          payorId: t.payorId,
          rate: Math.round(parseFloat(t.rate) * 100)
        }))
      ]

      const payload: any = {
        name: form.name,
        categoryId: form.categoryId,
        chargeType: form.chargeType,
        quantitative: form.quantitative,
        tariffs: tariffsPayload,
        packageCharges: (form.chargeType === 'PACKAGE' || form.chargeType === 'IP')
          ? form.packageCharges.map(pc => ({
            ...pc,
            amount: Math.round(pc.amount * 100)
          }))
          : [],
        endDate: form.status === 'INACTIVE' ? new Date().toISOString().split('T')[0] : null
      }

      return editing ? chargeApi.update({ ...payload, id: editing.id }) : chargeApi.create(payload)
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['charges'] }); reset(); toast({ title: editing ? 'Charge updated successfully' : 'Charge saved successfully', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error saving charge', description: e.message, variant: 'destructive' }),
  })

  const deleteMut = useMutation({
    mutationFn: (id: string) => chargeApi.remove(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['charges'] }); toast({ title: 'Charge deleted successfully', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error deleting charge', description: e.message, variant: 'destructive' }),
  })

  function reset() {
    setShowForm(false)
    setEditing(null)
    setForm(blank)
    setSelectedPayorId('')
    setEnteredPayorAmount('')
    setActiveSubTab('main')
    setIpComponentType('category')
    setIpSelectedCatId('')
    setIpSelectedCharge(null)
    setIpMode(true)
    setIpAmount('')
    setIpQty('1')
  }

  function startEdit(item: any) {
    const cashTariff = item.tariffs?.find((t: any) => t.billType === 'CASH' && !t.payorId)
    const creditTariff = item.tariffs?.find((t: any) => t.billType === 'CREDIT' && !t.payorId)
    const payorTariffs = item.tariffs?.filter((t: any) => t.billType === 'INSURANCE' && t.payorId) || []

    setEditing(item)
    setForm({
      name: item.name,
      categoryId: item.categoryId ?? '',
      chargeType: item.chargeType ?? 'CHARGE',
      quantitative: item.quantitative ?? false,
      cashRate: cashTariff ? (cashTariff.rate / 100).toString() : '',
      creditRate: creditTariff ? (creditTariff.rate / 100).toString() : '',
      status: item.endDate ? 'INACTIVE' : 'ACTIVE',
      tariffs: payorTariffs.map((t: any) => {
        const payorObj = payers.find((p: any) => p.id === t.payorId)
        return {
          payorId: t.payorId,
          payorName: payorObj?.name ?? 'Unknown Payor',
          rate: (t.rate / 100).toString()
        }
      }),
      packageCharges: (item.packageCharges ?? []).map((pc: any) => ({
        ...pc,
        amount: pc.amount / 100
      }))
    })
    setShowForm(true)
  }

  const handleAddPayorTariff = () => {
    if (!selectedPayorId || !enteredPayorAmount) return
    const payorObj = payers.find((p: any) => p.id === selectedPayorId)
    if (!payorObj) return

    if (form.tariffs.some(t => t.payorId === selectedPayorId)) {
      toast({ title: 'Payor rate already added', variant: 'destructive' })
      return
    }

    setForm(f => ({
      ...f,
      tariffs: [
        ...f.tariffs,
        {
          payorId: selectedPayorId,
          payorName: payorObj.name,
          rate: enteredPayorAmount
        }
      ]
    }))

    setSelectedPayorId('')
    setEnteredPayorAmount('')
  }

  const handleRemovePayorTariff = (index: number) => {
    setForm(f => ({
      ...f,
      tariffs: f.tariffs.filter((_, idx) => idx !== index)
    }))
  }

  const onAddSubCharge = (charge: any) => {
    if (form.packageCharges.some((pc: any) => pc.subCharge?.id === charge.id)) {
      toast({ title: 'Sub-charge already added', variant: 'destructive' })
      return
    }
    const newPc = {
      subCharge: { id: charge.id, name: charge.name, categoryId: charge.categoryId },
      quantity: 1,
      amount: 0,
      mode: true
    }
    setForm(f => ({
      ...f,
      packageCharges: [...f.packageCharges, newPc]
    }))
  }

  const onRemoveSubCharge = (index: number) => {
    setForm(f => ({
      ...f,
      packageCharges: f.packageCharges.filter((_, idx) => idx !== index)
    }))
  }

  const onAddIpComponent = () => {
    if (ipComponentType === 'category') {
      if (!ipSelectedCatId) {
        toast({ title: 'Please select a category', variant: 'destructive' })
        return
      }
      if (form.packageCharges.some((pc: any) => pc.categoryId === ipSelectedCatId && !pc.subCharge)) {
        toast({ title: 'Category already added', variant: 'destructive' })
        return
      }
      const newPc = {
        categoryId: ipSelectedCatId,
        quantity: 1,
        amount: ipMode ? parseFloat(ipAmount || '0') : 0,
        mode: ipMode
      }
      setForm(f => ({ ...f, packageCharges: [...f.packageCharges, newPc] }))
      // reset inputs
      setIpSelectedCatId('')
      setIpAmount('')
    } else {
      if (!ipSelectedCharge) {
        toast({ title: 'Please search and select a charge', variant: 'destructive' })
        return
      }
      if (form.packageCharges.some((pc: any) => pc.subCharge?.id === ipSelectedCharge.id)) {
        toast({ title: 'Charge already added', variant: 'destructive' })
        return
      }
      const newPc = {
        subCharge: { id: ipSelectedCharge.id, name: ipSelectedCharge.name, categoryId: ipSelectedCharge.categoryId },
        categoryId: ipSelectedCharge.categoryId,
        quantity: parseInt(ipQty || '1'),
        amount: ipMode ? parseFloat(ipAmount || '0') : 0,
        mode: ipMode
      }
      setForm(f => ({ ...f, packageCharges: [...f.packageCharges, newPc] }))
      // reset inputs
      setIpSelectedCharge(null)
      setIpAmount('')
      setIpQty('1')
    }
  }

  const handleDelete = async (item: any) => {
    try {
      const msg = await chargeApi.validateDelete(item.id)
      if (msg) {
        toast({ title: 'Cannot Delete', description: msg, variant: 'destructive' })
        return
      }
      setItemToDelete({ id: item.id, name: item.name })
    } catch (err: any) {
      toast({ title: 'Error checking delete validation', description: err.message, variant: 'destructive' })
    }
  }

  const formatDate = (isoString?: string) => {
    if (!isoString) return '—'
    try {
      const date = new Date(isoString)
      if (isNaN(date.getTime())) return isoString
      return date.toLocaleString('en-IN', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        hour12: true
      })
    } catch (e) {
      return isoString
    }
  }

  return (
    <Section
      title="Charges"
      description="Configure standard service catalog items, default rates, and payor tariffs"
      action={
        <div className="flex gap-4 items-center">
          <input
            type="text"
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0); }}
            className="px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all w-64"
          />
          <AddButton label="ADD CHARGE" onClick={() => { reset(); setShowForm(true) }} />
        </div>
      }
    >
      {showForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 animate-in fade-in duration-150 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-4xl overflow-hidden border border-gray-100 flex flex-col max-h-[90vh] animate-in zoom-in-95 duration-150">

            {/* Modal Header */}
            <div className="bg-gradient-to-r from-blue-600 to-indigo-600 px-6 py-4 flex justify-between items-center text-white">
              <h3 className="text-lg font-bold tracking-tight">
                {editing ? 'Update Charge' : 'Add Charge'}
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

            {/* Sub-tab Selection Bar */}
            {(form.chargeType === 'PACKAGE' || form.chargeType === 'IP') && (
              <div className="flex border-b border-gray-200 bg-white">
                <button
                  type="button"
                  onClick={() => setActiveSubTab('main')}
                  className={cn(
                    "px-6 py-3 text-xs font-bold uppercase tracking-wider border-b-2 transition-all",
                    activeSubTab === 'main'
                      ? "border-blue-600 text-blue-600"
                      : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
                  )}
                >
                  {form.chargeType === 'PACKAGE' ? 'Fixed Package' : 'Dynamic Package'}
                </button>
                <button
                  type="button"
                  onClick={() => setActiveSubTab('subcharges')}
                  className={cn(
                    "px-6 py-3 text-xs font-bold uppercase tracking-wider border-b-2 transition-all",
                    activeSubTab === 'subcharges'
                      ? "border-blue-600 text-blue-600"
                      : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
                  )}
                >
                  Sub-Charges
                </button>
              </div>
            )}

            {/* Modal Body */}
            <div className="p-6 overflow-y-auto space-y-6 flex-1 bg-gray-50/50">
              {activeSubTab === 'main' ? (
                <>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-4 bg-white p-5 rounded-xl border border-gray-150 shadow-sm">

                    <Field label="Category *">
                      <select
                        className={inputCls}
                        value={form.categoryId}
                        onChange={e => setForm(f => ({ ...f, categoryId: e.target.value }))}
                      >
                        <option value="">Select category…</option>
                        {cats.filter((c: any) => c.status === 'ACTIVE' || c.status === 1).map((c: any) => (
                          <option key={c.id} value={c.id}>{c.name}</option>
                        ))}
                      </select>
                    </Field>

                    <Field label="Name *">
                      <input
                        type="text"
                        className={inputCls}
                        value={form.name}
                        onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
                      />
                    </Field>

                    <Field label="CASH (₹) *">
                      <input
                        type="number"
                        min="0"
                        step="0.01"
                        className={inputCls}
                        value={form.cashRate}
                        onChange={e => setForm(f => ({ ...f, cashRate: e.target.value }))}
                      />
                    </Field>

                    <Field label="CREDIT (₹) *">
                      <input
                        type="number"
                        min="0"
                        step="0.01"
                        className={inputCls}
                        value={form.creditRate}
                        onChange={e => setForm(f => ({ ...f, creditRate: e.target.value }))}
                      />
                    </Field>

                    <Field label="Charge Type *">
                      <select
                        className={inputCls}
                        value={form.chargeType}
                        onChange={e => {
                          const newType = e.target.value as any;
                          setForm(f => ({ ...f, chargeType: newType }));
                          if (newType === 'CHARGE') setActiveSubTab('main');
                        }}
                      >
                        <option value="CHARGE">Charge</option>
                        <option value="PACKAGE">Fixed Package</option>
                        <option value="IP">Dynamic Package</option>
                      </select>
                    </Field>

                    <div className="flex items-center gap-2 pt-5">
                      <input
                        type="checkbox"
                        id="editableQty"
                        checked={form.quantitative}
                        onChange={e => setForm(f => ({ ...f, quantitative: e.target.checked }))}
                        className="w-4.5 h-4.5 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                      />
                      <label htmlFor="editableQty" className="text-sm font-semibold text-gray-700 select-none">
                        Editable Quantity
                      </label>
                    </div>

                    {editing && (
                      <div className="col-span-full border-t border-gray-100 pt-4 mt-2">
                        <span className={labelCls}>Status</span>
                        <div className="flex gap-2 mt-1">
                          <button
                            type="button"
                            onClick={() => setForm(f => ({ ...f, status: 'ACTIVE' }))}
                            className={cn(
                              "px-4 py-2 text-xs font-bold rounded-lg border transition-all duration-150",
                              form.status === 'ACTIVE'
                                ? "bg-blue-600 text-white border-blue-600 shadow-sm"
                                : "bg-white text-gray-700 border-gray-200 hover:bg-gray-50"
                            )}
                          >
                            Active
                          </button>
                          <button
                            type="button"
                            onClick={() => setForm(f => ({ ...f, status: 'INACTIVE' }))}
                            className={cn(
                              "px-4 py-2 text-xs font-bold rounded-lg border transition-all duration-150",
                              form.status === 'INACTIVE'
                                ? "bg-red-600 text-white border-red-600 shadow-sm"
                                : "bg-white text-gray-700 border-gray-200 hover:bg-gray-50"
                            )}
                          >
                            InActive
                          </button>
                        </div>
                      </div>
                    )}

                  </div>

                  <div className="bg-white p-5 rounded-xl border border-gray-150 shadow-sm space-y-4">
                    <div className="border-b border-gray-100 pb-2">
                      <h4 className="text-xs font-bold text-gray-700 tracking-wider uppercase">Payor Rates Mapping</h4>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-3 items-end bg-gray-50 p-3.5 rounded-lg border border-gray-200">
                      <div>
                        <label className="block text-[11px] font-bold text-gray-600 mb-1">PAYOR</label>
                        <select
                          className={`${inputCls} bg-white`}
                          value={selectedPayorId}
                          onChange={e => setSelectedPayorId(e.target.value)}
                        >
                          <option value="">Select Payor</option>
                          {payers
                            .filter((p: any) => (p.status === 1 || p.status === 'ACTIVE') && !form.tariffs.some(t => t.payorId === p.id))
                            .map((p: any) => (
                              <option key={p.id} value={p.id}>{p.name}</option>
                            ))}
                        </select>
                      </div>
                      <div>
                        <label className="block text-[11px] font-bold text-gray-600 mb-1">AMOUNT (₹)</label>
                        <input
                          type="number"
                          min="0"
                          step="0.01"
                          className={`${inputCls} bg-white`}
                          value={enteredPayorAmount}
                          onChange={e => setEnteredPayorAmount(e.target.value)}
                          onKeyDown={e => e.key === 'Enter' && (e.preventDefault(), handleAddPayorTariff())}
                        />
                      </div>
                      <div>
                        <button
                          type="button"
                          onClick={handleAddPayorTariff}
                          className="w-full py-2 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-lg transition-colors shadow-sm text-sm"
                        >
                          + Add Payor Rate
                        </button>
                      </div>
                    </div>

                    {form.tariffs.length > 0 && (
                      <div className="border border-gray-200 rounded-lg overflow-hidden shadow-sm bg-white">
                        <table className="w-full text-xs">
                          <thead className="bg-gray-50 border-b border-gray-150 text-gray-600 font-bold uppercase tracking-wider">
                            <tr>
                              <th className="px-4 py-2.5 text-left w-16">S.NO.</th>
                              <th className="px-4 py-2.5 text-left">PAYOR NAME</th>
                              <th className="px-4 py-2.5 text-right w-40">AMOUNT (₹)</th>
                              <th className="px-4 py-2.5 text-center w-24">ACTION</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-gray-100 font-medium text-gray-700">
                            {form.tariffs.map((t, idx) => (
                              <tr key={t.payorId} className="hover:bg-blue-50/20 transition-colors">
                                <td className="px-4 py-2 text-gray-500 font-mono">{idx + 1}</td>
                                <td className="px-4 py-2 font-semibold text-gray-900">{t.payorName}</td>
                                <td className="px-4 py-2 text-right font-mono font-bold text-blue-600">₹{parseFloat(t.rate).toFixed(2)}</td>
                                <td className="px-4 py-2 text-center">
                                  <button
                                    type="button"
                                    onClick={() => handleRemovePayorTariff(idx)}
                                    className="p-1 rounded-md text-red-500 hover:text-red-700 hover:bg-red-50 transition-all"
                                  >
                                    <svg className="w-4 h-4 mx-auto" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" />
                                    </svg>
                                  </button>
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    )}

                  </div>
                </>
              ) : (
                /* Sub-Charges Management Tab */
                <div className="space-y-6">
                  {form.chargeType === 'PACKAGE' ? (
                    <div className="bg-white p-5 rounded-xl border border-gray-150 shadow-sm space-y-4">
                      <div className="border-b border-gray-100 pb-2 flex justify-between items-center">
                        <h4 className="text-xs font-bold text-gray-700 tracking-wider uppercase">Fixed Package Sub-Charges</h4>
                        <span className="text-[10px] bg-blue-50 text-blue-700 px-2 py-0.5 rounded-full font-bold">
                          {form.packageCharges.length} Charges
                        </span>
                      </div>

                      {/* Search & Add Sub-charge */}
                      <div className="bg-gray-50 p-4 rounded-lg border border-gray-200 space-y-2">
                        <label className="block text-[11px] font-bold text-gray-600">ENTER THE CHARGE NAME</label>
                        <ChargeAutocomplete
                          cats={cats}
                          onSelect={onAddSubCharge}
                        />
                      </div>

                      {/* Sub-charges Table */}
                      {form.packageCharges.length > 0 ? (
                        <div className="border border-gray-200 rounded-lg overflow-hidden shadow-sm bg-white">
                          <table className="w-full text-xs">
                            <thead className="bg-gray-50 border-b border-gray-150 text-gray-600 font-bold uppercase tracking-wider">
                              <tr>
                                <th className="px-4 py-2.5 text-left w-16">S.NO.</th>
                                <th className="px-4 py-2.5 text-left">CHARGE NAME</th>
                                <th className="px-4 py-2.5 text-left">CATEGORY</th>
                                <th className="px-4 py-2.5 text-center w-24">ACTION</th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100 font-medium text-gray-700">
                              {form.packageCharges.map((pc, idx) => (
                                <tr key={pc.subCharge?.id || idx} className="hover:bg-blue-50/20 transition-colors">
                                  <td className="px-4 py-2 text-gray-500 font-mono">{idx + 1}</td>
                                  <td className="px-4 py-2 font-semibold text-gray-900">{pc.subCharge?.name}</td>
                                  <td className="px-4 py-2 text-gray-500">
                                    {cats.find(c => c.id === pc.subCharge?.categoryId)?.name ?? ''}
                                  </td>
                                  <td className="px-4 py-2 text-center">
                                    <button
                                      type="button"
                                      onClick={() => onRemoveSubCharge(idx)}
                                      className="p-1 rounded-md text-red-500 hover:text-red-700 hover:bg-red-50 transition-all"
                                    >
                                      <svg className="w-4 h-4 mx-auto" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" />
                                      </svg>
                                    </button>
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      ) : (
                        <div className="text-center py-6 text-gray-400 text-xs bg-white rounded-lg border border-dashed border-gray-200">
                          No sub-charges added yet. Use the search input above to add charges to this fixed package.
                        </div>
                      )}
                    </div>
                  ) : (
                    /* Dynamic Package (IP) components */
                    <div className="bg-white p-5 rounded-xl border border-gray-150 shadow-sm space-y-4">
                      <div className="border-b border-gray-100 pb-2 flex justify-between items-center">
                        <h4 className="text-xs font-bold text-gray-700 tracking-wider uppercase">Dynamic Package Components</h4>
                        <span className="text-[10px] bg-indigo-50 text-indigo-700 px-2 py-0.5 rounded-full font-bold">
                          {form.packageCharges.length} Rules
                        </span>
                      </div>

                      {/* Add Dynamic Component rule form */}
                      <div className="bg-gray-50 p-4 rounded-lg border border-gray-200 space-y-4">
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3 items-end">
                          <div>
                            <label className="block text-[11px] font-bold text-gray-600 mb-1">TYPE</label>
                            <select
                              className={`${inputCls} bg-white`}
                              value={ipComponentType}
                              onChange={e => {
                                setIpComponentType(e.target.value as any);
                                setIpSelectedCatId('');
                                setIpSelectedCharge(null);
                              }}
                            >
                              <option value="category">Charge Category</option>
                              <option value="charge">Charge</option>
                            </select>
                          </div>

                          <div className="md:col-span-1 lg:col-span-2">
                            {ipComponentType === 'category' ? (
                              <>
                                <label className="block text-[11px] font-bold text-gray-600 mb-1">CHARGE CATEGORY</label>
                                <select
                                  className={`${inputCls} bg-white`}
                                  value={ipSelectedCatId}
                                  onChange={e => setIpSelectedCatId(e.target.value)}
                                >
                                  <option value="">Select Category</option>
                                  {cats.filter((c: any) => c.status === 'ACTIVE' || c.status === 1).map((c: any) => (
                                    <option key={c.id} value={c.id}>{c.name}</option>
                                  ))}
                                </select>
                              </>
                            ) : (
                              <>
                                <label className="block text-[11px] font-bold text-gray-600 mb-1">CHARGE NAME</label>
                                <ChargeAutocomplete
                                  cats={cats}
                                  onSelect={setIpSelectedCharge}
                                />
                                {ipSelectedCharge && (
                                  <div className="text-[10px] text-green-600 mt-1 font-semibold">
                                    Selected: {ipSelectedCharge.name}
                                  </div>
                                )}
                              </>
                            )}
                          </div>

                          <div>
                            <label className="block text-[11px] font-bold text-gray-600 mb-1 font-sans">ABSORPTION MODE</label>
                            <select
                              className={`${inputCls} bg-white`}
                              value={ipMode ? 'true' : 'false'}
                              onChange={e => setIpMode(e.target.value === 'true')}
                            >
                              <option value="true">Include</option>
                              <option value="false">Exclude</option>
                            </select>
                          </div>
                        </div>

                        {ipMode && (
                          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3 items-end pt-2 border-t border-gray-200/50">
                            <div>
                              <label className="block text-[11px] font-bold text-gray-600 mb-1">AMOUNT (₹)</label>
                              <input
                                type="number"
                                min="0"
                                step="0.01"
                                className={`${inputCls} bg-white`}
                                value={ipAmount}
                                onChange={e => setIpAmount(e.target.value)}
                              />
                            </div>

                            {ipComponentType === 'charge' && (
                              <div>
                                <label className="block text-[11px] font-bold text-gray-600 mb-1">QUANTITY</label>
                                <input
                                  type="number"
                                  min="1"
                                  className={`${inputCls} bg-white`}
                                  value={ipQty}
                                  onChange={e => setIpQty(e.target.value)}
                                />
                              </div>
                            )}

                            <div className="lg:col-start-4">
                              <button
                                type="button"
                                onClick={onAddIpComponent}
                                className="w-full py-2 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-lg transition-colors shadow-sm text-xs uppercase tracking-wider font-sans"
                              >
                                + Add Rule
                              </button>
                            </div>
                          </div>
                        )}

                        {!ipMode && (
                          <div className="flex justify-end pt-2 border-t border-gray-200/50">
                            <button
                              type="button"
                              onClick={onAddIpComponent}
                              className="py-2 px-6 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-lg transition-colors shadow-sm text-xs uppercase tracking-wider font-sans"
                            >
                              + Add Rule (Exclude)
                            </button>
                          </div>
                        )}
                      </div>

                      {/* Rules table */}
                      {form.packageCharges.length > 0 ? (
                        <div className="border border-gray-200 rounded-lg overflow-hidden shadow-sm bg-white">
                          <table className="w-full text-xs">
                            <thead className="bg-gray-50 border-b border-gray-150 text-gray-600 font-bold uppercase tracking-wider">
                              <tr>
                                <th className="px-4 py-2.5 text-left w-16">S.NO.</th>
                                <th className="px-4 py-2.5 text-left">TYPE</th>
                                <th className="px-4 py-2.5 text-left">NAME / CATEGORY</th>
                                <th className="px-4 py-2.5 text-center">MODE</th>
                                <th className="px-4 py-2.5 text-right">AMOUNT (₹)</th>
                                <th className="px-4 py-2.5 text-center">QUANTITY</th>
                                <th className="px-4 py-2.5 text-center w-24">ACTION</th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100 font-medium text-gray-700">
                              {form.packageCharges.map((pc, idx) => (
                                <tr key={idx} className="hover:bg-blue-50/20 transition-colors">
                                  <td className="px-4 py-2 text-gray-500 font-mono">{idx + 1}</td>
                                  <td className="px-4 py-2 text-gray-900 font-semibold uppercase">
                                    {pc.subCharge ? 'Charge' : 'Category'}
                                  </td>
                                  <td className="px-4 py-2">
                                    {pc.subCharge ? (
                                      <div>
                                        <div className="font-semibold text-gray-900">{pc.subCharge.name}</div>
                                        <div className="text-[9px] text-gray-400">
                                          Category: {cats.find(c => c.id === pc.subCharge?.categoryId)?.name ?? ''}
                                        </div>
                                      </div>
                                    ) : (
                                      <span className="font-semibold text-indigo-900">
                                        {cats.find(c => c.id === pc.categoryId)?.name ?? ''}
                                      </span>
                                    )}
                                  </td>
                                  <td className="px-4 py-2 text-center">
                                    <span className={cn(
                                      "px-2 py-0.5 rounded text-[10px] font-bold uppercase",
                                      pc.mode ? "bg-green-50 text-green-700 border border-green-200" : "bg-red-50 text-red-700 border border-red-200"
                                    )}>
                                      {pc.mode ? 'Include' : 'Exclude'}
                                    </span>
                                  </td>
                                  <td className="px-4 py-2 text-right font-mono font-bold text-gray-950">
                                    {pc.mode ? `₹${(pc.amount ?? 0).toFixed(2)}` : '—'}
                                  </td>
                                  <td className="px-4 py-2 text-center font-mono font-bold">
                                    {pc.subCharge && pc.mode ? pc.quantity : '—'}
                                  </td>
                                  <td className="px-4 py-2 text-center">
                                    <button
                                      type="button"
                                      onClick={() => onRemoveSubCharge(idx)}
                                      className="p-1 rounded-md text-red-500 hover:text-red-700 hover:bg-red-50 transition-all"
                                    >
                                      <svg className="w-4 h-4 mx-auto" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" />
                                      </svg>
                                    </button>
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      ) : (
                        <div className="text-center py-6 text-gray-400 text-xs bg-white rounded-lg border border-dashed border-gray-200 font-sans">
                          No absorption rules added yet. Use the form above to add categories or specific charges to this dynamic package.
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* Modal Footer */}
            <div className="bg-gray-50 border-t border-gray-100 px-6 py-4 flex justify-end gap-3">
              <button
                type="button"
                onClick={reset}
                className="px-5 py-2.5 bg-white border border-gray-200 text-gray-700 text-sm font-bold rounded-lg hover:bg-gray-50 active:bg-gray-100 transition-colors shadow-sm"
              >
                CANCEL
              </button>
              <button
                type="button"
                onClick={() => mut.mutate()}
                disabled={mut.isPending || !form.name || !form.categoryId || !form.cashRate || !form.creditRate}
                className="px-6 py-2.5 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white text-sm font-bold rounded-lg transition-colors shadow-sm uppercase"
              >
                {editing ? 'UPDATE CHARGE' : 'ADD CHARGE'}
              </button>
            </div>

          </div>
        </div>
      )}



      {/* List Table */}
      <Table headers={['S.NO.', 'CHARGE', 'CATEGORY', 'CREATED DATE', 'STATUS', 'ACTION']}>
        {isLoading ? (
          <LoadingRow />
        ) : items.length === 0 ? (
          <EmptyState label="charges" />
        ) : (
          items.map((item: any, idx: number) => (
            <tr key={item.id} className="hover:bg-gray-50">
              <td className="px-4 py-3 font-mono text-xs text-gray-500">{(page * 10) + idx + 1}</td>
              <td className="px-4 py-3 font-medium text-gray-900">{item.name}</td>
              <td className="px-4 py-3 text-gray-600">
                {cats.find((c: any) => c.id === item.categoryId)?.name || '—'}
              </td>
              <td className="px-4 py-3 text-gray-500 text-xs">{formatDate(item.createdAt)}</td>
              <td className="px-4 py-3 text-center">
                <StatusBadge active={item.status === 'ACTIVE' || (!item.status && !item.endDate)} />
              </td>
              <td className="px-4 py-3 text-center">
                <div className="flex gap-2 items-center justify-center">
                  <button
                    onClick={() => startEdit(item)}
                    className="p-1 rounded-md text-blue-600 hover:text-blue-800 hover:bg-blue-50 transition-all duration-150"
                    title="Edit"
                  >
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
                    </svg>
                  </button>
                  <button
                    onClick={() => handleDelete(item)}
                    className="p-1 rounded-md text-red-500 hover:text-red-700 hover:bg-red-50 transition-all duration-150"
                    title="Delete"
                  >
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </div>
              </td>
            </tr>
          ))
        )}
      </Table>

      {/* Pagination */}
      {!isLoading && items.length > 0 && (
        <div className="flex items-center justify-between px-6 py-4 bg-gray-50 border border-t-0 border-gray-100 rounded-b-lg text-xs font-bold text-gray-500 mt-2">
          <div className="text-xs text-gray-500 font-medium normal-case">
            Page <span className="font-semibold text-gray-900">{page + 1}</span> of{' '}
            <span className="font-semibold text-gray-900">{totalPages || 1}</span>
            {totalElements !== undefined && (
              <span className="ml-2">· {totalElements} total items</span>
            )}
          </div>
          <div className="flex items-center gap-1">
            <button
              onClick={() => setPage((p: number) => Math.max(0, p - 1))}
              disabled={page === 0 || isLoading}
              className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7" />
              </svg>
            </button>

            {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
              let pageNum = i
              if (totalPages > 5 && page > 2) {
                pageNum = Math.min(page - 2 + i, totalPages - 5 + i)
              }
              return (
                <button
                  key={pageNum}
                  onClick={() => setPage(pageNum)}
                  className={cn(
                    'min-w-[28px] h-7 flex items-center justify-center rounded text-xs font-semibold transition-all',
                    page === pageNum ? 'bg-blue-600 text-white shadow-sm' : 'text-gray-600 hover:bg-gray-100'
                  )}
                >
                  {pageNum + 1}
                </button>
              )
            })}

            <button
              onClick={() => setPage((p: number) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1 || isLoading}
              className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7" />
              </svg>
            </button>
          </div>
        </div>
      )}

      {/* Delete Charge confirmation modal */}
      {itemToDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm">
          <div className="bg-white rounded-2xl shadow-2xl p-6 max-w-sm w-full text-center space-y-4 animate-in zoom-in-95 duration-150">
            <div className="w-14 h-14 rounded-full bg-red-100 flex items-center justify-center mx-auto">
              <span className="text-2xl text-red-600">⚠</span>
            </div>
            <h4 className="text-lg font-bold text-gray-900">Delete Charge</h4>
            <p className="text-sm text-gray-500">
              Are you sure you want to delete{' '}
              <span className="font-semibold text-gray-900">{itemToDelete.name}</span>?
              This action cannot be undone.
            </p>
            <div className="flex gap-3 pt-2">
              <button
                onClick={() => setItemToDelete(null)}
                disabled={deleteMut.isPending}
                className="flex-1 px-4 py-2.5 bg-gray-100 text-gray-700 text-sm font-bold rounded-xl hover:bg-gray-200 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={() => deleteMut.mutate(itemToDelete.id, { onSuccess: () => setItemToDelete(null) })}
                disabled={deleteMut.isPending}
                className="flex-1 px-4 py-2.5 bg-red-600 text-white text-sm font-bold rounded-xl hover:bg-red-700 disabled:opacity-50 transition-all"
              >
                {deleteMut.isPending ? 'Deleting…' : 'Delete'}
              </button>
            </div>
          </div>
        </div>
      )}
    </Section>
  )
}