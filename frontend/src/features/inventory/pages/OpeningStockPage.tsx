import { useState, useMemo, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { inventoryApi } from '../../../services/inventory/inventoryApi'
import type { InventoryBatch } from '../../../types/inventory'
import { cn } from '../../../lib/utils'
import { ChevronDown } from 'lucide-react'

interface GroupedItem {
  itemName: string
  departmentName: string
  batches: InventoryBatch[]
}

/* ── Status badge ─────────────────────────────────────────── */
function StatusBadge({ batch }: { batch: InventoryBatch }) {
  if (batch.isExpired)
    return <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-red-100 text-red-700">⚠ Expired</span>
  if (batch.isOutOfStock)
    return <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-gray-100 text-gray-600">✕ Out of Stock</span>
  return <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-emerald-100 text-emerald-700">✓ In Stock</span>
}

/* ── Currency formatter ───────────────────────────────────── */
const fmt = (n: number) =>
  new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 }).format(n)

/* ── Expiry Date Formatter ──────────────────────────────── */
function formatExpiryDate(dateStr: string) {
  if (!dateStr) return ''
  const parts = dateStr.split('-')
  if (parts.length === 3) {
    const [y, m, d] = parts
    if (y.length === 4) return `${d}/${m}/${y}`
  }
  const date = new Date(dateStr)
  if (isNaN(date.getTime())) return dateStr
  const d = String(date.getDate()).padStart(2, '0')
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const y = date.getFullYear()
  return `${d}/${m}/${y}`
}

/* ── Summary cards ───────────────────────────────────────── */
function SummaryCard({ label, value, color, icon }: { label: string; value: string | number; color: string; icon: string }) {
  return (
    <div className={cn('rounded-xl border p-4 flex items-center gap-3', color)}>
      <div className="text-2xl">{icon}</div>
      <div>
        <p className="text-xs font-medium opacity-70">{label}</p>
        <p className="text-lg font-bold">{value}</p>
      </div>
    </div>
  )
}

/* ── Grouped row component as Accordion ───────────────────── */
function GroupedAccordion({ group, rowIdx }: { group: GroupedItem; rowIdx: number }) {
  const [isExpanded, setIsExpanded] = useState(false)

  // Calculate total quantity for this group
  const totalQty = group.batches.reduce((sum: number, b: InventoryBatch) => sum + b.currentQuantity, 0)
  
  // Find if any batch is expired
  const hasExpired = group.batches.some((b: InventoryBatch) => b.isExpired)
  // Find if all batches are out of stock
  const allOutOfStock = group.batches.every((b: InventoryBatch) => b.isOutOfStock)

  return (
    <div className={cn(
      "border border-gray-200 rounded-xl overflow-hidden mb-3 bg-white shadow-sm transition-all",
      isExpanded ? "ring-1 ring-blue-500 border-blue-500" : "hover:border-gray-300"
    )}>
      {/* Header */}
      <button
        onClick={() => setIsExpanded(!isExpanded)}
        className="w-full flex flex-wrap items-center justify-between p-4 text-left gap-4 bg-white hover:bg-gray-50/50 transition-colors focus:outline-none"
        aria-expanded={isExpanded}
      >
        <div className="flex items-center gap-4 flex-1">
          {/* Index number */}
          <span className="text-xs font-mono text-gray-400 w-6 text-center">{rowIdx + 1}</span>
          
          <div>
            {/* Item Name */}
            <h3 className="font-bold text-gray-900 text-sm md:text-base flex items-center gap-2">
              {group.itemName}
              {hasExpired && (
                <span className="px-2 py-0.5 rounded-full text-[10px] font-extrabold bg-red-100 text-red-700 animate-pulse">
                  Contains Expired
                </span>
              )}
            </h3>
            
            {/* Department and batch count */}
            <div className="flex items-center gap-2 mt-1 text-xs text-gray-500">
              <span className="font-medium px-2 py-0.5 bg-gray-100 rounded-md text-gray-600">
                {group.departmentName}
              </span>
              <span>•</span>
              <span>{group.batches.length} {group.batches.length === 1 ? 'Batch' : 'Batches'}</span>
            </div>
          </div>
        </div>

        {/* Aggregate stock and status */}
        <div className="flex items-center gap-4">
          <div className="text-right">
            <span className="text-xs text-gray-400 block font-medium">Total Stock</span>
            <span className={cn(
              "text-sm font-bold",
              allOutOfStock ? "text-gray-500" : "text-gray-900"
            )}>
              {totalQty.toLocaleString()} units
            </span>
          </div>

          {/* Chevron */}
          <div className={cn(
            "w-8 h-8 rounded-full bg-gray-50 flex items-center justify-center border border-gray-200 text-gray-500 transition-transform duration-200",
            isExpanded ? "transform rotate-180 bg-blue-50 text-blue-600 border-blue-200" : ""
          )}>
            <ChevronDown size={16} />
          </div>
        </div>
      </button>

      {/* Expanded Batches Sub-table */}
      {isExpanded && (
        <div className="border-t border-gray-100 bg-gray-50/50 p-4 overflow-x-auto">
          <table className="w-full text-xs text-left min-w-[650px]">
            <thead>
              <tr className="border-b border-gray-200 text-[10px] font-bold text-gray-500 uppercase tracking-wider bg-gray-100/50 rounded-t-lg">
                <th className="px-3 py-2">Batch No.</th>
                <th className="px-3 py-2 text-right">In-Stock Qty</th>
                <th className="px-3 py-2 text-right">Purchase Rate</th>
                <th className="px-3 py-2 text-right">MRP</th>
                <th className="px-3 py-2 text-right">Selling Rate</th>
                <th className="px-3 py-2">Expiry Date</th>
                <th className="px-3 py-2">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200/60">
              {group.batches.map((b: InventoryBatch) => (
                <tr key={b.id} className={cn(
                  "hover:bg-blue-50/30 transition-colors",
                  b.isExpired ? "bg-red-50/20" : b.isOutOfStock ? "bg-gray-50/40" : ""
                )}>
                  {/* Batch Number */}
                  <td className="px-3 py-2.5 font-medium text-gray-800">
                    {b.batchNumber || <span className="text-gray-400 italic">—</span>}
                  </td>
                  
                  {/* In-Stock Qty */}
                  <td className="px-3 py-2.5 text-right font-bold text-gray-800">
                    {b.currentQuantity.toLocaleString()}
                  </td>
                  
                  {/* Purchase Rate */}
                  <td className="px-3 py-2.5 text-right text-gray-600 font-mono">
                    {fmt(b.purchaseRate)}
                  </td>
                  
                  {/* MRP */}
                  <td className="px-3 py-2.5 text-right text-gray-600 font-mono">
                    {fmt(b.maximumRetailPrice)}
                  </td>
                  
                  {/* Selling Rate */}
                  <td className="px-3 py-2.5 text-right text-gray-700 font-semibold font-mono">
                    {fmt(b.sellingRate)}
                  </td>
                  
                  {/* Expiry Date */}
                  <td className="px-3 py-2.5 whitespace-nowrap">
                    {b.expiryDate ? (
                      <span className={b.isExpired ? 'text-red-600 font-semibold' : 'text-gray-600'}>
                        {formatExpiryDate(b.expiryDate)}
                      </span>
                    ) : (
                      <span className="text-gray-400 italic">—</span>
                    )}
                  </td>
                  
                  {/* Status */}
                  <td className="px-3 py-2.5">
                    <StatusBadge batch={b} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export default function OpeningStockPage() {
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<'all' | 'in_stock' | 'out_of_stock' | 'expired'>('all')
  const [currentPage, setCurrentPage] = useState(1)

  useEffect(() => {
    setCurrentPage(1)
  }, [search, statusFilter])

  const { data: batches = [], isLoading, isError, refetch } = useQuery<InventoryBatch[]>({
    queryKey: ['opening-stock-all'],
    queryFn: () => inventoryApi.getAllBatches(),
    staleTime: 30_000,
  })

  /* ── Computed stats ─────────────────────────────────────── */
  const stats = useMemo(() => ({
    total: batches.length,
    inStock: batches.filter(b => !b.isExpired && !b.isOutOfStock).length,
    outOfStock: batches.filter(b => b.isOutOfStock && !b.isExpired).length,
    expired: batches.filter(b => b.isExpired).length,
    totalUnits: batches.reduce((s, b) => s + b.currentQuantity, 0),
  }), [batches])

  /* ── Group batches by itemName + departmentName ──────────── */
  const grouped = useMemo(() => {
    const q = search.trim().toLowerCase()
    const filtered = batches.filter(b => {
      const matchText = !q
        || b.itemName.toLowerCase().includes(q)
        || b.departmentName.toLowerCase().includes(q)
        || (b.batchNumber ?? '').toLowerCase().includes(q)
        || b.id.toLowerCase().includes(q)

      const matchStatus =
        statusFilter === 'all' ? true
          : statusFilter === 'in_stock' ? !b.isExpired && !b.isOutOfStock
            : statusFilter === 'expired' ? b.isExpired
              : b.isOutOfStock && !b.isExpired

      return matchText && matchStatus
    })

    // Group by itemName + departmentName
    const map = new Map<string, GroupedItem>()
    filtered.forEach(b => {
      const key = `${b.itemName}||${b.departmentName}`
      if (!map.has(key)) {
        map.set(key, { itemName: b.itemName, departmentName: b.departmentName, batches: [] })
      }
      map.get(key)!.batches.push(b)
    })
    return Array.from(map.values())
  }, [batches, search, statusFilter])

  const itemsPerPage = 10
  const startIndex = (currentPage - 1) * itemsPerPage
  const endIndex = Math.min(startIndex + itemsPerPage, grouped.length)
  const paginatedGrouped = grouped.slice(startIndex, endIndex)

  /* ── Render ─────────────────────────────────────────────── */
  return (
    <div className="space-y-6 max-w-7xl">

      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
            Opening Stock
          </h2>
          <p className="text-sm text-gray-500 mt-0.5">
            All inventory batches uploaded via Bulk Import → Opening Stock template.
          </p>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <SummaryCard label="Total Batches" value={stats.total} color="bg-blue-50  border-blue-200  text-blue-800" icon="📦" />
        <SummaryCard label="In Stock" value={stats.inStock} color="bg-emerald-50 border-emerald-200 text-emerald-800" icon="✓" />
        <SummaryCard label="Out of Stock" value={stats.outOfStock} color="bg-gray-50  border-gray-200  text-gray-700" icon="✕" />
        <SummaryCard label="Expired Batches" value={stats.expired} color="bg-red-50   border-red-200   text-red-800" icon="⚠" />
      </div>

      {/* Filters */}
      <div className="bg-white border border-gray-200 rounded-xl p-4 flex flex-wrap gap-3 items-center">
        {/* Search */}
        <div className="relative flex-1 min-w-52">
          <input
            id="opening-stock-search"
            type="text"
            placeholder="Search by Item Name, Department, Batch No…"
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="w-full pl-9 pr-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            aria-label="Search opening stock"
          />
        </div>

        {/* Status filter chips */}
        <div className="flex gap-2 flex-wrap" role="group" aria-label="Filter by status">
          {([
            ['all', 'All', 'bg-gray-100 text-gray-700'],
            ['in_stock', 'In Stock', 'bg-emerald-100 text-emerald-700'],
            ['out_of_stock', 'Out of Stock', 'bg-gray-200 text-gray-600'],
            ['expired', 'Expired', 'bg-red-100 text-red-700'],
          ] as const).map(([val, label, cls]) => (
            <button key={val}
              onClick={() => setStatusFilter(val)}
              className={cn(
                'px-3 py-1.5 rounded-full text-xs font-semibold border transition-all',
                statusFilter === val
                  ? cn(cls, 'ring-2 ring-offset-1 ring-blue-400 border-transparent')
                  : 'bg-white border-gray-200 text-gray-500 hover:bg-gray-50'
              )}
              aria-pressed={statusFilter === val}
            >
              {label}
            </button>
          ))}
        </div>

        <p className="text-xs text-gray-400 ml-auto">
          Showing <span className="font-semibold text-gray-600">{grouped.length}</span> items
          {' '}(<span className="font-semibold text-gray-600">{batches.length}</span> batches total)
        </p>
      </div>

      {/* Accordion List */}
      <div className="space-y-3">
        {isLoading && (
          <div className="bg-white border border-gray-200 rounded-xl flex items-center justify-center py-20 gap-3 text-gray-500 shadow-sm">
            <div className="w-5 h-5 border-2 border-blue-200 border-t-blue-600 rounded-full animate-spin" />
            Loading opening stock…
          </div>
        )}
        {isError && (
          <div className="bg-white border border-gray-200 rounded-xl flex flex-col items-center justify-center py-16 text-center gap-2 shadow-sm">
            <p className="text-sm font-medium text-red-600">Failed to load stock data.</p>
            <button onClick={() => refetch()}
              className="mt-2 px-4 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700">
              Try Again
            </button>
          </div>
        )}
        {!isLoading && !isError && grouped.length === 0 && (
          <div className="bg-white border border-gray-200 rounded-xl flex flex-col items-center justify-center py-16 text-center gap-2 shadow-sm">
            <p className="text-sm font-medium text-gray-600">
              {batches.length === 0 ? 'No stock records found.' : 'No records match your search.'}
            </p>
            {batches.length === 0 && (
              <p className="text-xs text-gray-400 max-w-xs">
                Go to <strong>Admin → Bulk Import</strong>, select <em>Opening Stock</em>, download the template, fill it in, and upload it.
              </p>
            )}
          </div>
        )}
        {!isLoading && !isError && grouped.length > 0 && (
          <div className="space-y-3">
            {paginatedGrouped.map((group, idx) => (
              <GroupedAccordion key={`${group.itemName}||${group.departmentName}`} group={group} rowIdx={startIndex + idx} />
            ))}
          </div>
        )}
      </div>

      {/* Pagination Controls */}
      {!isLoading && !isError && grouped.length > 0 && (() => {
        const totalPages = Math.ceil(grouped.length / itemsPerPage)
        if (totalPages <= 1) return null;
        
        const pages: Array<number | string> = []
        const range = 1
        for (let i = 1; i <= totalPages; i++) {
          if (i === 1 || i === totalPages || (i >= currentPage - range && i <= currentPage + range)) {
            pages.push(i)
          } else if (pages[pages.length - 1] !== '...') {
            pages.push('...')
          }
        }

        return (
          <div className="flex flex-col sm:flex-row items-center justify-between gap-4 bg-white border border-gray-200 rounded-xl p-4 shadow-sm">
            <p className="text-xs font-semibold text-gray-500">
              Showing <span className="text-gray-900 font-extrabold">{startIndex + 1}</span> to{' '}
              <span className="text-gray-900 font-extrabold">{endIndex}</span> of{' '}
              <span className="text-gray-900 font-extrabold">{grouped.length}</span> items
            </p>
            
            <div className="flex items-center gap-1.5">
              <button
                onClick={() => setCurrentPage(prev => Math.max(1, prev - 1))}
                disabled={currentPage === 1}
                className="px-3 py-1.5 border border-gray-300 rounded-lg text-xs font-bold text-gray-600 bg-white hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                Previous
              </button>
              
              {pages.map((p, idx) => {
                if (p === '...') {
                  return <span key={`dots-${idx}`} className="px-2 text-gray-400 text-xs font-bold">...</span>
                }
                const pageNum = p as number
                return (
                  <button
                    key={`page-${pageNum}`}
                    onClick={() => setCurrentPage(pageNum)}
                    className={cn(
                      "w-8 h-8 rounded-lg text-xs font-bold transition-all border",
                      currentPage === pageNum
                        ? "bg-blue-600 text-white border-blue-600 shadow-sm"
                        : "bg-white text-gray-700 border-gray-300 hover:bg-gray-50"
                    )}
                  >
                    {pageNum}
                  </button>
                )
              })}
              
              <button
                onClick={() => setCurrentPage(prev => Math.min(totalPages, prev + 1))}
                disabled={currentPage === totalPages}
                className="px-3 py-1.5 border border-gray-300 rounded-lg text-xs font-bold text-gray-600 bg-white hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                Next
              </button>
            </div>
          </div>
        )
      })()}

      {/* Footer hint */}
      {!isLoading && batches.length > 0 && (
        <p className="text-xs text-gray-400 text-center">
          Total units across all batches: <span className="font-semibold text-gray-600">{stats.totalUnits.toLocaleString()}</span>
          &ensp;·&ensp; Data stored in the <code className="bg-gray-100 px-1 rounded">inventory_batches</code> table.
        </p>
      )}
    </div>
  )
}
