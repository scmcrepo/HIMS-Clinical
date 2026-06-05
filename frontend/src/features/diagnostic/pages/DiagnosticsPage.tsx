import { useState, useMemo } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { cn } from '../../../lib/utils'
import DatePicker from '../../../components/shared/DatePicker'
import {
  FlaskConical,
  Activity,
  CheckCircle2,
  X
} from 'lucide-react'
import { diagnosticApi, type PlaceOrderCmd } from '../../../services/diagnostic/diagnosticApi'
import { useDiagnosticMutations, useTemplates, useSpecimenCollections } from '../../../hooks/diagnostic/useDiagnostic'
import type { DiagnosticType, DiagnosticTemplate } from '../../../types/diagnostic'
import { encounterApi } from '../../../services/encounter/encounterApi'

/* ─── helpers ────────────────────────────────────────────── */
const today = () => new Date().toISOString().slice(0, 10)

const formatPatientInfo = (order: any) => {
  const name = order.patientName || ''
  const gender = order.patientGender
    ? order.patientGender.charAt(0) + order.patientGender.slice(1).toLowerCase()
    : ''
  let age = order.patientAge || ''
  if (age) {
    age = age
      .replace(/\s*yrs?/gi, ' Y')
      .replace(/\s*months?/gi, ' M')
      .replace(/\s*days?/gi, ' D')
  }

  if (!gender && !age) return name

  const details = [gender, age].filter(Boolean).join('\\')
  return `${name} (${details})`
}

const STATUS_DOT: Record<string, string> = {
  ORDERED: 'bg-amber-400', BILLED: 'bg-blue-400', RESULTED: 'bg-emerald-400',
  CANCELLED: 'bg-red-400', PART_PAID: 'bg-orange-400',
  PENDING: 'bg-amber-400', RECORDED: 'bg-teal-400'
}

function SpecimenStatus({ order, onClick }: { order: any; onClick: () => void }) {
  const { data: collections = [], isLoading } = useSpecimenCollections(order.id)

  if (isLoading) {
    return (
      <button disabled className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-gray-400 bg-gray-50 rounded-lg border border-gray-200 transition-colors">
        Loading...
      </button>
    )
  }

  const allCollected = order.lines.length > 0 && order.lines.every((line: any) => {
    const exactMatch = collections.find((c: any) => c.orderLineId === line.id)
    if (exactMatch) return true
    return collections.find((c: any) => c.specimenId === line.specimenId && !c.orderLineId)
  })

  if (allCollected) {
    return (
      <span onClick={onClick} className="cursor-pointer inline-flex items-center gap-1 px-3 py-1.5 text-xs font-semibold text-emerald-700 bg-emerald-50 hover:bg-emerald-100 rounded-lg border border-emerald-200 transition-all">
        <CheckCircle2 className="w-3.5 h-3.5" />
        Collected
      </span>
    )
  }

  const someCollected = collections.length > 0

  return (
    <button onClick={onClick}
      className={cn(
        "inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium rounded-lg border transition-colors",
        someCollected
          ? "text-amber-700 bg-amber-50 hover:bg-amber-100 border-amber-200"
          : "text-teal-700 bg-teal-50 hover:bg-teal-100 border-teal-200"
      )}
      title="Specimen Collection">
      {someCollected ? 'Partially Collected' : 'Collect'}
    </button>
  )
}

/* ═══════════════════════════════════════════════════════════
   DIAGNOSTICS PAGE
   ═══════════════════════════════════════════════════════════ */
export default function DiagnosticsPage() {
  const [sp] = useSearchParams()
  const tabParam = sp.get('tab')
  const tab = (tabParam === 'radiology' || tabParam === 'lab') ? tabParam : 'lab'
  const [searchDate, setSearchDate] = useState(today())

  return (
    <div className="space-y-4">
      {/* ── Page Header ─────────────────────────────────────── */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
            {tab === 'lab' ? 'Laboratory' : 'Radiology'}
          </h1>
          <p className="text-sm text-gray-500 mt-0.5">
            {tab === 'lab' ? 'Laboratory Management' : 'Radiology Management'}
          </p>
        </div>
      </div>

      {/* ── Tab Content ─────────────────────────────────────── */}
      {tab === 'lab' && <LabSection searchDate={searchDate} setSearchDate={setSearchDate} />}
      {tab === 'radiology' && <RadiologySection searchDate={searchDate} setSearchDate={setSearchDate} />}
    </div>
  )
}

/* ═══════════════════════════════════════════════════════════
   LAB SECTION
   ═══════════════════════════════════════════════════════════ */
function LabSection({ searchDate, setSearchDate }: { searchDate: string; setSearchDate: (d: string) => void }) {
  const [search, setSearch] = useState('')
  const navigate = useNavigate()

  const { data: orders = [], isLoading } = useQuery({
    queryKey: ['diagnostics', 'pending', 'LAB', searchDate],
    queryFn: () => diagnosticApi.getPending('LAB', searchDate, searchDate),
    staleTime: 0,
  })

  const filtered = useMemo(() => {
    if (!search.trim()) return orders
    const q = search.toLowerCase()
    return orders.filter(o =>
      (o.sequenceNumber?.toLowerCase() ?? '').includes(q) ||
      (o.patientNumber?.toLowerCase() ?? '').includes(q) ||
      (o.patientName?.toLowerCase() ?? '').includes(q) ||
      (o.patientGender?.toLowerCase() ?? '').includes(q) ||
      (o.patientAge?.toLowerCase() ?? '').includes(q) ||
      o.lines.some(l => (l.itemName?.toLowerCase() ?? '').includes(q))
    )
  }, [orders, search])

  return (
    <>
      <div className="bg-white border border-gray-200 rounded-2xl overflow-hidden shadow-sm">
        {/* search bar */}
        <div className="flex items-center gap-3 px-5 py-3 border-b border-gray-100 bg-gradient-to-r from-emerald-50/50 to-white">
          <div className="flex items-center gap-2">
            <label className="text-xs font-medium text-gray-600">Date</label>
            <DatePicker
              value={searchDate}
              onChange={setSearchDate}
              size="sm"
            />
          </div>
          <input type="text" value={search} onChange={e => setSearch(e.target.value)}
            placeholder="Search Laboratory…" className="flex-1 max-w-xs px-3 py-1.5 border border-gray-200 rounded-lg text-sm focus:ring-2 focus:ring-emerald-400" />
          <span className="text-xs text-gray-400 ml-auto">{filtered.length} orders</span>
        </div>

        {/* table */}
        <div className="overflow-x-auto">
          <table className="w-full text-sm table-fixed">
            <colgroup>
              <col className="w-[4%]" />
              <col className="w-[12%]" />
              <col className="w-[14%]" />
              <col className="w-[26%]" />
              <col className="w-[12%]" />
              <col className="w-[12%]" />
              <col className="w-[10%]" />
              <col className="w-[10%]" />
            </colgroup>
            <thead>
              <tr className="bg-gray-50/80 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                <th className="px-3 py-3 text-center">S.No</th>
                <th className="px-3 py-3 text-left">Lab No</th>
                <th className="px-3 py-3 text-left">Patient</th>
                <th className="px-3 py-3 text-left">Tests</th>
                <th className="px-3 py-3 text-center">Payment Status</th>
                <th className="px-3 py-3 text-center">Test Status</th>
                <th className="px-3 py-3 text-center">Specimen</th>
                <th className="px-3 py-3 text-center">Report</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {isLoading ? (
                <tr><td colSpan={8} className="text-center py-12 text-gray-400">Loading…</td></tr>
              ) : filtered.length === 0 ? (
                <tr><td colSpan={8} className="text-center py-12 text-gray-400">
                  <div className="text-3xl mb-2"></div>No laboratory orders found for {searchDate}
                </td></tr>
              ) : filtered.map((order, i) => (
                <tr key={order.id} className="hover:bg-emerald-50/30 transition-colors">
                  <td className="px-3 py-3 text-center text-gray-400 font-mono text-xs">{i + 1}</td>
                  <td className="px-3 py-3 font-medium text-emerald-700 whitespace-nowrap overflow-hidden">
                    <div className="flex items-center gap-2">
                      {order.sequenceNumber || '—'}
                      {order.encounterType && (
                        <span className={cn(
                          "inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider",
                          order.encounterType === 'IP'
                            ? "bg-blue-50 text-blue-700 border border-blue-200"
                            : "bg-teal-50 text-teal-700 border border-teal-200"
                        )}>
                          {order.encounterType}
                        </span>
                      )}
                    </div>
                  </td>
                  <td className="px-3 py-3 text-gray-600 font-medium overflow-hidden">
                    <div className="truncate">{order.patientNumber || order.patientId.slice(0, 8)}</div>
                    <div className="text-xs text-gray-400 font-normal truncate mt-0.5">{formatPatientInfo(order)}</div>
                  </td>
                  <td className="px-3 py-3 text-gray-700 overflow-hidden">
                    <div className="flex items-center gap-1.5">
                      <span className="text-sm leading-snug truncate">{order.lines[0]?.itemName || '—'}</span>
                      {order.lines.length > 1 && (
                        <span className="text-[10px] text-blue-600 bg-blue-50 px-1.5 py-0.5 rounded font-bold shrink-0 cursor-pointer hover:bg-blue-100 transition-colors select-none"
                          onClick={(e) => {
                            const td = (e.target as HTMLElement).closest('td')!
                            const expandDiv = td.querySelector('[data-expand]') as HTMLElement
                            if (expandDiv) expandDiv.classList.toggle('hidden')
                          }}>
                          +{order.lines.length - 1}
                        </span>
                      )}
                    </div>
                    {order.lines.length > 1 && (
                      <div data-expand className="hidden mt-1.5 text-xs text-gray-600 bg-gray-50 border border-gray-200 rounded-lg p-2 space-y-0.5 select-text">
                        {order.lines.map((l, li) => (
                          <div key={li}>• {l.itemName}</div>
                        ))}
                      </div>
                    )}
                  </td>
                  <td className="px-3 py-3 text-center">
                    <span className={cn('inline-flex items-center gap-1.5 px-2 py-1 rounded-full text-xs font-medium whitespace-nowrap',
                      order.paymentStatus === 'ORDERED'    ? 'bg-amber-50 text-amber-700' :
                      order.paymentStatus === 'PART_PAID'  ? 'bg-orange-50 text-orange-700 ring-1 ring-orange-200' :
                      order.paymentStatus === 'BILLED'     ? 'bg-blue-50 text-blue-700' :
                                                      'bg-gray-50 text-gray-600')}>
                      <span className={cn('w-1.5 h-1.5 rounded-full', STATUS_DOT[order.paymentStatus] ?? 'bg-gray-400')} />
                      {order.paymentStatus === 'PART_PAID' ? 'Part Paid' : order.paymentStatus}
                    </span>
                  </td>
                  <td className="px-3 py-3 text-center">
                    <span className={cn('inline-flex items-center gap-1.5 px-2 py-1 rounded-full text-xs font-medium whitespace-nowrap',
                      order.testStatus === 'RESULTED' ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700')}>
                      <span className={cn('w-1.5 h-1.5 rounded-full', order.testStatus === 'RESULTED' ? 'bg-emerald-400' : 'bg-amber-400')} />
                      {order.testStatus === 'RESULTED' ? 'Result Entered' : 'Pending'}
                    </span>
                  </td>
                  <td className="px-3 py-3 text-center">
                    <SpecimenStatus
                      order={order}
                      onClick={() => navigate(`/diagnostics/specimen/${order.id}`)}
                    />
                  </td>
                  <td className="px-3 py-3 text-center">
                    <button onClick={() => navigate(`/diagnostics/lab-report/${order.id}`)}
                      className="inline-flex items-center gap-1 px-2 py-1.5 text-xs font-medium text-indigo-700 bg-indigo-50 rounded-lg hover:bg-indigo-100 border border-indigo-200 transition-colors whitespace-nowrap"
                      title="Enter Report">
                      Enter Report
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

    </>
  )
}

/* ═══════════════════════════════════════════════════════════
   RADIOLOGY SECTION
   ═══════════════════════════════════════════════════════════ */
function RadiologySection({ searchDate, setSearchDate }: { searchDate: string; setSearchDate: (d: string) => void }) {
  const [search, setSearch] = useState('')
  const navigate = useNavigate()

  const { data: orders = [], isLoading } = useQuery({
    queryKey: ['diagnostics', 'pending', 'RADIOLOGY', searchDate],
    queryFn: () => diagnosticApi.getPending('RADIOLOGY', searchDate, searchDate),
    staleTime: 0,
  })

  const filtered = useMemo(() => {
    if (!search.trim()) return orders
    const q = search.toLowerCase()
    return orders.filter(o =>
      (o.sequenceNumber?.toLowerCase() ?? '').includes(q) ||
      (o.patientNumber?.toLowerCase() ?? '').includes(q) ||
      (o.patientName?.toLowerCase() ?? '').includes(q) ||
      (o.patientGender?.toLowerCase() ?? '').includes(q) ||
      (o.patientAge?.toLowerCase() ?? '').includes(q) ||
      o.lines.some(l => (l.itemName?.toLowerCase() ?? '').includes(q))
    )
  }, [orders, search])

  return (
    <>
      <div className="bg-white border border-gray-200 rounded-2xl overflow-hidden shadow-sm">
        <div className="flex items-center gap-3 px-5 py-3 border-b border-gray-100 bg-gradient-to-r from-purple-50/50 to-white">
          <div className="flex items-center gap-2">
            <label className="text-xs font-medium text-gray-600">Date</label>
            <DatePicker
              value={searchDate}
              onChange={setSearchDate}
              size="sm"
            />
          </div>
          <input type="text" value={search} onChange={e => setSearch(e.target.value)}
            placeholder="Search Radiology…" className="flex-1 max-w-xs px-3 py-1.5 border border-gray-200 rounded-lg text-sm focus:ring-2 focus:ring-purple-400" />
          <span className="text-xs text-gray-400 ml-auto">{filtered.length} orders</span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-sm table-fixed">
            <colgroup>
              <col className="w-[4%]" />
              <col className="w-[14%]" />
              <col className="w-[16%]" />
              <col className="w-[26%]" />
              <col className="w-[14%]" />
              <col className="w-[14%]" />
              <col className="w-[12%]" />
            </colgroup>
            <thead>
              <tr className="bg-gray-50/80 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                <th className="px-3 py-3 text-center">S.No</th>
                <th className="px-3 py-3 text-left">Order No</th>
                <th className="px-3 py-3 text-left">Patient ID</th>
                <th className="px-3 py-3 text-left">Studies</th>
                <th className="px-3 py-3 text-center">Payment Status</th>
                <th className="px-3 py-3 text-center">Test Status</th>
                <th className="px-3 py-3 text-center">Report</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {isLoading ? (
                <tr><td colSpan={7} className="text-center py-12 text-gray-400">Loading…</td></tr>
              ) : filtered.length === 0 ? (
                <tr><td colSpan={7} className="text-center py-12 text-gray-400">
                  <div className="text-3xl mb-2"></div>No radiology orders found for {searchDate}
                </td></tr>
              ) : filtered.map((order, i) => (
                <tr key={order.id} className="hover:bg-purple-50/30 transition-colors">
                  <td className="px-3 py-3 text-center text-gray-400 font-mono text-xs">{i + 1}</td>
                  <td className="px-3 py-3 font-medium text-purple-700 whitespace-nowrap overflow-hidden">
                    <div className="flex items-center gap-2">
                      {order.sequenceNumber || '—'}
                      {order.encounterType && (
                        <span className={cn(
                          "inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider",
                          order.encounterType === 'IP'
                            ? "bg-blue-50 text-blue-700 border border-blue-200"
                            : "bg-teal-50 text-teal-700 border border-teal-200"
                        )}>
                          {order.encounterType}
                        </span>
                      )}
                    </div>
                  </td>
                  <td className="px-3 py-3 text-gray-600 font-medium overflow-hidden">
                    <div className="truncate">{order.patientNumber || order.patientId.slice(0, 8)}</div>
                    <div className="text-xs text-gray-400 font-normal truncate mt-0.5">{formatPatientInfo(order)}</div>
                  </td>
                  <td className="px-3 py-3 text-gray-700 overflow-hidden">
                    <div className="flex items-center gap-1.5">
                      <span className="text-sm leading-snug truncate">{order.lines[0]?.itemName || '—'}</span>
                      {order.lines.length > 1 && (
                        <span className="text-[10px] text-purple-600 bg-purple-50 px-1.5 py-0.5 rounded font-bold shrink-0 cursor-pointer hover:bg-purple-100 transition-colors select-none"
                          onClick={(e) => {
                            const td = (e.target as HTMLElement).closest('td')!
                            const expandDiv = td.querySelector('[data-expand]') as HTMLElement
                            if (expandDiv) expandDiv.classList.toggle('hidden')
                          }}>
                          +{order.lines.length - 1}
                        </span>
                      )}
                    </div>
                    {order.lines.length > 1 && (
                      <div data-expand className="hidden mt-1.5 text-xs text-gray-600 bg-gray-50 border border-gray-200 rounded-lg p-2 space-y-0.5 select-text">
                        {order.lines.map((l, li) => (
                          <div key={li}>• {l.itemName}</div>
                        ))}
                      </div>
                    )}
                  </td>
                  <td className="px-3 py-3 text-center">
                    <span className={cn('inline-flex items-center gap-1.5 px-2 py-1 rounded-full text-xs font-medium whitespace-nowrap',
                      order.paymentStatus === 'ORDERED'    ? 'bg-amber-50 text-amber-700' :
                      order.paymentStatus === 'PART_PAID'  ? 'bg-orange-50 text-orange-700 ring-1 ring-orange-200' :
                      order.paymentStatus === 'BILLED'     ? 'bg-blue-50 text-blue-700' :
                                                      'bg-gray-50 text-gray-600')}>
                      <span className={cn('w-1.5 h-1.5 rounded-full', STATUS_DOT[order.paymentStatus] ?? 'bg-gray-400')} />
                      {order.paymentStatus === 'PART_PAID' ? 'Part Paid' : order.paymentStatus}
                    </span>
                  </td>
                  <td className="px-3 py-3 text-center">
                    <span className={cn('inline-flex items-center gap-1.5 px-2 py-1 rounded-full text-xs font-medium whitespace-nowrap',
                      order.testStatus === 'RESULTED' ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700')}>
                      <span className={cn('w-1.5 h-1.5 rounded-full', order.testStatus === 'RESULTED' ? 'bg-emerald-400' : 'bg-amber-400')} />
                      {order.testStatus === 'RESULTED' ? 'Result Entered' : 'Pending'}
                    </span>
                  </td>
                  <td className="px-3 py-3 text-center">
                    <button onClick={() => navigate(`/diagnostics/radiology-report/${order.id}`)}
                      className="inline-flex items-center gap-1 px-2 py-1.5 text-xs font-medium text-purple-700 bg-purple-50 rounded-lg hover:bg-purple-100 border border-purple-200 transition-colors whitespace-nowrap"
                      title="Enter Report">
                      Enter Report
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

    </>
  )
}

/* ═══════════════════════════════════════════════════════════
   ORDER SECTION
   ═══════════════════════════════════════════════════════════ */
export function OrderSection({ encounterId, patientId }: { encounterId: string; patientId: string }) {
  const { placeOrder } = useDiagnosticMutations()
  const { data: templates = [] } = useTemplates()
  const [orderType, setOrderType] = useState<DiagnosticType>('LAB')
  const [selectedLines, setSelectedLines] = useState<Array<{ serviceCatalogItemId: string; itemName: string; specimenId: string | null }>>([])
  const [searchTerm, setSearchTerm] = useState('')
  const [instruction, setInstruction] = useState('')

  // Patient search state
  const [selectedPatientId, setSelectedPatientId] = useState<string>(patientId)
  const [selectedEncounterId, setSelectedEncounterId] = useState<string>(encounterId)
  const [patientSearch, setPatientSearch] = useState('')
  const [isSearching, setIsSearching] = useState(false)

  const { data: activeInpatients = [] } = useQuery({
    queryKey: ['activeInpatientsSearch'],
    queryFn: () => encounterApi.getActiveInpatientsWithBeds(),
    staleTime: 0,
  })

  const filteredPatients = useMemo(() => {
    if (!patientSearch.trim()) return activeInpatients.slice(0, 10)
    const q = patientSearch.toLowerCase()
    return activeInpatients.filter(p =>
      (p.patientName?.toLowerCase() ?? '').includes(q) ||
      (p.patientNumber?.toLowerCase() ?? '').includes(q)
    ).slice(0, 10)
  }, [activeInpatients, patientSearch])

  const filteredTemplates = useMemo(() => {
    const byType = templates.filter(t => {
      if (orderType === 'LAB') return t.format === 'LAB_TEMPLATE'
      return t.format === 'CUSTOM_TEMPLATE'
    })
    if (!searchTerm.trim()) return byType
    const q = searchTerm.toLowerCase()
    return byType.filter(t => t.name.toLowerCase().includes(q))
  }, [templates, orderType, searchTerm])

  // Group by department
  const grouped = useMemo(() => {
    const map = new Map<string, DiagnosticTemplate[]>()
    for (const t of filteredTemplates) {
      const dept = t.department?.name ?? 'Other'
      if (!map.has(dept)) map.set(dept, [])
      map.get(dept)!.push(t)
    }
    return map
  }, [filteredTemplates])

  const addToOrder = (t: DiagnosticTemplate) => {
    if (!t.chargeId) return
    if (selectedLines.some(l => l.serviceCatalogItemId === t.chargeId)) return
    setSelectedLines(prev => [...prev, { serviceCatalogItemId: t.chargeId!, itemName: t.name, specimenId: t.specimenId }])
  }

  const removeFromOrder = (idx: number) => {
    setSelectedLines(prev => prev.filter((_, i) => i !== idx))
  }

  const submitOrder = () => {
    if (!selectedPatientId || selectedLines.length === 0) return
    const cmd: PlaceOrderCmd = {
      encounterId: selectedEncounterId || undefined,
      patientId: selectedPatientId,
      diagnosticType: orderType,
      lines: selectedLines.map(l => ({
        serviceCatalogItemId: l.serviceCatalogItemId,
        itemName: l.itemName,
        specimenId: l.specimenId || undefined,
        instruction
      })),
    }
    placeOrder.mutate(cmd, { onSuccess: () => { setSelectedLines([]); setInstruction('') } })
  }

  return (
    <div className="grid grid-cols-3 gap-4">
      {/* Left — order list */}
      <div className="col-span-2 bg-white border border-gray-200 rounded-2xl overflow-hidden shadow-sm">
        <div className="px-5 py-3 border-b border-gray-100 bg-gradient-to-r from-blue-50/50 to-white">
          <h3 className="text-sm font-bold text-gray-800">Order Tests</h3>
        </div>

        {/* Patient Search (if no encounter) */}
        {!encounterId && (
          <div className="px-5 py-4 border-b border-gray-50 bg-gray-50/30">
            <label className="text-xs font-semibold text-gray-700 block mb-1.5">Select Patient</label>
            <div className="relative">
              <input type="text" placeholder="Search patient by name or phone..."
                value={patientSearch} onChange={e => { setPatientSearch(e.target.value); setIsSearching(true) }}
                onBlur={() => setTimeout(() => setIsSearching(false), 200)}
                className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:ring-2 focus:ring-blue-400" />

              {isSearching && filteredPatients.length > 0 && (
                <div className="absolute top-full left-0 right-0 mt-1 bg-white border border-gray-200 shadow-xl rounded-xl overflow-hidden z-10">
                  {filteredPatients.map((e) => (
                    <button key={e.id} onMouseDown={() => {
                      setSelectedPatientId(e.patientId);
                      setSelectedEncounterId(e.id);
                      setPatientSearch(e.patientName ?? '');
                      setIsSearching(false)
                    }}
                      className="w-full text-left px-4 py-2 hover:bg-blue-50 text-sm border-b border-gray-50 last:border-0">
                      <div className="flex items-center justify-between">
                        <span className="font-semibold text-gray-800 block">{e.patientName}</span>
                        <span className="px-1.5 py-0.5 bg-blue-600 text-white text-[10px] font-bold rounded shadow-sm">INPATIENT</span>
                      </div>
                      <span className="text-xs text-gray-500">{e.patientNumber}</span>
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

        {/* Type toggle */}
        <div className="flex gap-2 px-5 py-3 border-b border-gray-50">
          {(['LAB', 'RADIOLOGY'] as DiagnosticType[]).map(t => (
            <button key={t} onClick={() => { setOrderType(t); setSelectedLines([]) }}
              className={cn('px-4 py-1.5 text-xs font-semibold rounded-lg border transition-colors flex items-center gap-2',
                orderType === t ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-white text-gray-600 border-gray-200 hover:bg-gray-50')}>
              {t === 'LAB' ? (
                <>
                  <FlaskConical className="w-3.5 h-3.5" />
                  Lab
                </>
              ) : (
                <>
                  <Activity className="w-3.5 h-3.5" />
                  Radiology
                </>
              )}
            </button>
          ))}
        </div>

        {/* Selected items */}
        <div className="px-5 py-3 border-b border-gray-50 min-h-[120px]">
          {selectedLines.length === 0 ? (
            <p className="text-sm text-gray-400 italic">No tests selected yet. Click tests from the right panel to add.</p>
          ) : (
            <div className="space-y-1.5">
              {selectedLines.map((l, i) => (
                <div key={i} className="flex items-center justify-between px-3 py-2 bg-indigo-50/50 rounded-lg border border-indigo-100">
                  <span className="text-sm text-gray-800">{l.itemName}</span>
                  <button onClick={() => removeFromOrder(i)} className="text-red-400 hover:text-red-600 transition-colors">
                    <X className="w-4 h-4" />
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Instruction */}
        <div className="px-5 py-3 border-b border-gray-50">
          <label className="text-xs font-medium text-gray-600 mb-1 block">Instructions / Precautions</label>
          <textarea value={instruction} onChange={e => setInstruction(e.target.value)}
            rows={2} className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm resize-none focus:ring-2 focus:ring-blue-400" />
        </div>

        <div className="px-5 py-3 flex justify-end">
          <button onClick={submitOrder} disabled={selectedLines.length === 0 || !selectedPatientId || placeOrder.isPending}
            className="px-6 py-2.5 bg-gradient-to-r from-indigo-600 to-purple-600 text-white text-sm font-bold rounded-xl hover:from-indigo-700 hover:to-purple-700 disabled:opacity-40 transition-all shadow-md">
            {placeOrder.isPending ? 'Placing…' : '+ Place Diagnostic Order'}
          </button>
        </div>
      </div>

      {/* Right — test catalog */}
      <div className="bg-white border border-gray-200 rounded-2xl overflow-hidden shadow-sm">
        <div className="px-4 py-3 border-b border-gray-100 bg-gradient-to-r from-gray-50 to-white">
          <h3 className="text-sm font-bold text-gray-800 mb-2">Available Tests</h3>
          <input type="text" value={searchTerm} onChange={e => setSearchTerm(e.target.value)}
            placeholder="Search tests…" className="w-full px-3 py-1.5 border border-gray-200 rounded-lg text-xs focus:ring-2 focus:ring-indigo-400" />
        </div>
        <div className="overflow-y-auto" style={{ maxHeight: 'calc(100vh - 340px)' }}>
          {[...grouped.entries()].map(([dept, tests]) => (
            <div key={dept}>
              <div className="px-4 py-2 bg-gray-50 text-xs font-bold text-indigo-700 uppercase tracking-wider sticky top-0 border-b border-gray-100">
                {dept}
              </div>
              {tests.map(t => {
                const added = t.chargeId ? selectedLines.some(l => l.serviceCatalogItemId === t.chargeId) : false
                return (
                  <button key={t.id} onClick={() => addToOrder(t)} disabled={added || !t.chargeId}
                    className={cn('w-full text-left px-4 py-2.5 text-sm border-b border-gray-50 transition-colors flex items-center',
                      added ? 'bg-indigo-50 text-indigo-600 font-medium' : 'hover:bg-indigo-50/30 text-gray-700')}>
                    {added && <CheckCircle2 className="w-4 h-4 mr-1.5 text-indigo-500" />}
                    <span className="flex-1">{t.name}</span>
                    {t.unit && <span className="text-xs text-gray-400 ml-2">({t.unit})</span>}
                  </button>
                )
              })}
            </div>
          ))}
          {grouped.size === 0 && (
            <p className="px-4 py-8 text-center text-sm text-gray-400">No tests found</p>
          )}
        </div>
      </div>
    </div>
  )
}
