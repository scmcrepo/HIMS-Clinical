import { useState, useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
// cache bust comment
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  purchaseApi,
  type PurchaseRequestResponse,
  type GoodsReturnResponse,
} from '../../../services/purchase/purchaseApi'
import { goodsApi } from '../../../services/goods/goodsApi'
import { departmentApi } from '../../../services/config/departmentApi'
import { supplierApi, taxApi } from '../../../services/masters/masterApi'
import { inventoryApi } from '../../../services/inventory/inventoryApi'
import { tempStockApi } from '../../../services/tempStock/tempStockApi'
import { useAuthStore } from '../../../store/authStore'
import { toast } from '../../../hooks/useToast'
import { cn } from '../../../lib/utils'
import DatePicker from '../../../components/shared/DatePicker'
import { MedicineSearchInput } from '../../../components/shared/MedicineSearchInput'
import { Calendar, Plus, X, Trash2, ChevronRight } from 'lucide-react'

import api from '../../../lib/axios'

const ItemNameLabel = ({ itemId, fallback }: { itemId: string, fallback?: string }) => {
  const [name, setName] = useState(fallback || (itemId?.length > 20 ? 'Loading...' : itemId))
  useEffect(() => {
    if (itemId?.length > 20) {
      api.get(`/item/getItemById/${itemId}`).then(r => {
        if (r.data?.data?.name) setName(r.data.data.name)
        else if (fallback) setName(fallback)
        else setName(itemId.slice(0, 12))
      }).catch(() => { if (!fallback) setName(itemId.slice(0, 12)) })
    }
  }, [itemId])
  return <span>{name}</span>
}

const ReturnLineRow = ({ line, index, inventoryBatches }: { line: any; index: number; inventoryBatches: any[] }) => {
  const [batchInfo, setBatchInfo] = useState<any>(() => inventoryBatches.find(b => b.id === line.batchId))

  useEffect(() => {
    if (!batchInfo && line.batchId) {
      api.get(`/inventory/batches/${line.batchId}`)
        .then(r => {
          if (r.data?.data) {
            setBatchInfo(r.data.data)
          }
        })
        .catch(() => {})
    }
  }, [line.batchId, batchInfo, inventoryBatches])

  const itemName = batchInfo ? batchInfo.itemName : 'Loading...'
  const batchNumber = batchInfo ? batchInfo.batchNumber : '—'
  const rate = line.purchaseRate || (batchInfo ? batchInfo.purchaseRate : 0)
  const total = line.quantity * rate

  return (
    <tr className="hover:bg-gray-50 transition-colors">
      <td className="px-3 py-2 text-gray-500">{index + 1}</td>
      <td className="px-3 py-2 font-medium text-gray-800">{itemName}</td>
      <td className="px-3 py-2 text-gray-600">{batchNumber}</td>
      <td className="px-3 py-2 text-center font-semibold text-gray-800">{line.quantity}</td>
      <td className="px-3 py-2 text-right">₹{Math.round(rate).toLocaleString('en-IN')}</td>
      <td className="px-3 py-2 text-right font-semibold text-gray-900">₹{Math.round(total).toLocaleString('en-IN')}</td>
    </tr>
  )
}

const DEMO_DEPT_ID = '00000000-0000-0000-0000-000000000001'

const STATUS_STYLES: Record<string, string> = {
  ORDERED: 'bg-blue-50  text-blue-700  border-blue-200',
  PARTIALLY_ORDERED: 'bg-sky-50   text-sky-700   border-sky-200',
  PARTIALLY_RECEIVED: 'bg-amber-50 text-amber-700 border-amber-200',
  RECEIVED: 'bg-green-50 text-green-700 border-green-200',
  REQUESTED: 'bg-rose-50  text-rose-700  border-rose-200',
}

const formatToIndianDate = (dateStr: any) => {
  if (!dateStr) return '—'
  const str = String(dateStr)
  const parts = str.split('T')[0].split('-')
  if (parts.length === 3) {
    return `${parts[2]}/${parts[1]}/${parts[0]}`
  }
  return str
}

const guessUnitFromName = (name: string): string => {
  const upper = name.toUpperCase()
  if (upper.includes('TAB')) return 'Tablet'
  if (upper.includes('CAP')) return 'Capsule'
  if (upper.includes('SYP') || upper.includes('SYRUP')) return 'Syrup'
  if (upper.includes('INJ') || upper.includes('INJECTION')) return 'Injection'
  if (upper.includes('CREAM') || upper.includes('OINT')) return 'Tube'
  return 'NOS'
}

const parseMaskedDate = (val: string): { isValid: boolean; iso: string } => {
  if (!val || val === 'dd/mm/yyyy' || val.includes('d') || val.includes('m') || val.includes('y')) {
    return { isValid: false, iso: '' }
  }
  const parts = val.split('/')
  if (parts.length !== 3 || parts[2].length !== 4) {
    return { isValid: false, iso: '' }
  }
  const dd = parseInt(parts[0], 10)
  const mm = parseInt(parts[1], 10)
  const yyyy = parseInt(parts[2], 10)
  if (isNaN(dd) || isNaN(mm) || isNaN(yyyy)) {
    return { isValid: false, iso: '' }
  }
  const mStr = mm.toString().padStart(2, '0')
  const dStr = dd.toString().padStart(2, '0')
  const iso = `${yyyy}-${mStr}-${dStr}`
  const dateObj = new Date(iso)
  const isValid = mm >= 1 && mm <= 12 && dd >= 1 && dd <= 31 &&
    !isNaN(dateObj.getTime()) &&
    dateObj.getFullYear() === yyyy &&
    dateObj.getMonth() + 1 === mm &&
    dateObj.getDate() === dd
  return { isValid, iso }
}

const handleDateMaskKeyDown = (
  e: React.KeyboardEvent<HTMLInputElement>,
  value: string,
  setValue: (val: string) => void
) => {
  const mask = 'dd/mm/yyyy'
  if (/[0-9]/.test(e.key)) {
    e.preventDefault()
    const input = e.currentTarget
    let pos = input.selectionStart ?? 0

    // If whole input is selected
    if ((input.selectionEnd ?? 0) - pos === 10) {
      pos = 0
    }

    if (value[pos] === '/') {
      pos++
    }

    if (pos < 10) {
      const newValue = value.slice(0, pos) + e.key + value.slice(pos + 1)

      // Validate day constraints (dd <= 31, first digit <= 3)
      const dayStr = newValue.slice(0, 2)
      if (!dayStr.includes('d')) {
        const ddVal = parseInt(dayStr, 10)
        if (ddVal > 31) return
      } else {
        const firstDayChar = dayStr[0]
        if (firstDayChar !== 'd' && !['0', '1', '2', '3'].includes(firstDayChar)) {
          return
        }
      }

      if (pos >= 3 && pos <= 4) {
        // Month field: if cursor is at first month char position (3)
        // and user types 2-9, auto-pad to 0X and move past mm
        if (pos === 3) {
          const digit = parseInt(e.key, 10)
          if (digit >= 2 && digit <= 9) {
            // Auto-pad: "05", "06" etc
            const paddedMonth = '0' + e.key
            const newValue2 = newValue.slice(0, 3) + paddedMonth + newValue.slice(5)
            // Validate
            const mmVal = parseInt(paddedMonth, 10)
            if (mmVal > 12) return
            setValue(newValue2)
            setTimeout(() => {
              input.setSelectionRange(6, 6) // jump to yyyy
            }, 0)
            return
          }
        }
      }

      // Validate month constraints (mm <= 12, first digit <= 1)
      const monthStr = newValue.slice(3, 5)
      if (!monthStr.includes('m')) {
        const mmVal = parseInt(monthStr, 10)
        if (mmVal > 12) return
      } else {
        const firstMonthChar = monthStr[0]
        if (firstMonthChar !== 'm' && !['0', '1'].includes(firstMonthChar)) {
          return
        }
      }

      setValue(newValue)

      setTimeout(() => {
        let nextPos = pos + 1
        if (newValue[nextPos] === '/') {
          nextPos++
        }
        input.setSelectionRange(nextPos, nextPos)
      }, 0)
    }
  } else if (e.key === 'Backspace') {
    e.preventDefault()
    const input = e.currentTarget
    const selectionEnd = input.selectionEnd ?? 0
    let pos = input.selectionStart ?? 0

    if (selectionEnd - pos === 10) {
      setValue(mask)
      setTimeout(() => input.setSelectionRange(0, 0), 0)
      return
    }

    let prevPos = pos - 1
    if (value[prevPos] === '/') {
      prevPos--
    }

    if (prevPos >= 0) {
      const maskChar = mask[prevPos]
      const newValue = value.slice(0, prevPos) + maskChar + value.slice(prevPos + 1)
      setValue(newValue)

      setTimeout(() => {
        input.setSelectionRange(prevPos, prevPos)
      }, 0)
    }
  } else if (e.key === 'Delete') {
    e.preventDefault()
    const input = e.currentTarget
    const selectionEnd = input.selectionEnd ?? 0
    let pos = input.selectionStart ?? 0

    if (selectionEnd - pos === 10) {
      setValue(mask)
      setTimeout(() => input.setSelectionRange(0, 0), 0)
      return
    }

    if (pos < 10) {
      if (value[pos] === '/') {
        pos++
      }
      if (pos < 10) {
        const maskChar = mask[pos]
        const newValue = value.slice(0, pos) + maskChar + value.slice(pos + 1)
        setValue(newValue)
        setTimeout(() => {
          input.setSelectionRange(pos, pos)
        }, 0)
      }
    }
  } else if (
    e.key === 'ArrowLeft' ||
    e.key === 'ArrowRight' ||
    e.key === 'Tab' ||
    e.key === 'Enter' ||
    e.key === 'Escape'
  ) {
    // Allow navigation
  } else {
    if (!e.ctrlKey && !e.metaKey) {
      e.preventDefault()
    }
  }
}

const getPOItemPriceInfo = (itemId: string, itemName: string, batches: any[]) => {
  const match = batches.find(b => b.itemId === itemId)
    || batches.find(b => b.itemName?.toLowerCase() === itemName?.toLowerCase())
  return {
    mrp: match ? match.maximumRetailPrice : 0,
    pPrice: match ? match.purchaseRate : 0
  }
}

export default function PurchaseManagementPage() {
  const qc = useQueryClient()
  const today = new Date().toISOString().split('T')[0]
  const currentUser = useAuthStore(s => s.user)

  const [adjustTempStocks, setAdjustTempStocks] = useState<Record<number, { qty: number; active: boolean }>>({})
  const [showConfirmModal, setShowConfirmModal] = useState(false)

  const checkTempStockForLine = async (i: number, itemId: string | undefined, batchNumber: string | undefined) => {
    if (!itemId || !itemId.trim() || !batchNumber || !batchNumber.trim()) {
      setAdjustTempStocks(prev => {
        const next = { ...prev }
        delete next[i]
        return next
      })
      return
    }
    try {
      const qty = await tempStockApi.getQuantity(itemId, batchNumber)
      if (qty > 0) {
        setAdjustTempStocks(prev => ({
          ...prev,
          [i]: { qty, active: true }
        }))
      } else {
        setAdjustTempStocks(prev => {
          const next = { ...prev }
          delete next[i]
          return next
        })
      }
    } catch (err) {
      console.error('Failed to get temp stock quantity', err)
    }
  }

  // Active Main Tab: 'request' | 'order' | 'grn' | 'return'
  const [sp, setSp] = useSearchParams()
  const tabParam = sp.get('tab')
  const activeTab = (tabParam === 'order' || tabParam === 'grn' || tabParam === 'return' || tabParam === 'request') ? tabParam : 'order'
  const setActiveTab = (newTab: 'request' | 'order' | 'grn' | 'return') => {
    setSp(prev => {
      const next = new URLSearchParams(prev)
      next.set('tab', newTab)
      return next
    })
  }

  // Common Master Data Queries
  const { data: departments = [] } = useQuery({
    queryKey: ['departments'],
    queryFn: departmentApi.getAll,
  })

  const { data: suppliers = [] } = useQuery({
    queryKey: ['suppliers'],
    queryFn: supplierApi.getAll,
  })

  const { data: taxes = [] } = useQuery({
    queryKey: ['taxes'],
    queryFn: taxApi.getAll,
  })

  const { data: inventoryBatches = [] } = useQuery({
    queryKey: ['opening-stock-all'],
    queryFn: () => inventoryApi.getAllBatches(),
    staleTime: 30_000,
  })

  // -------------------------------------------------------------
  // TAB 1: PURCHASE REQUEST STATES & MUTATIONS
  // -------------------------------------------------------------
  const [requestFilterDate, setRequestFilterDate] = useState(today)
  const [requestFilterDeptId, setRequestFilterDeptId] = useState('ALL')

  // Toggle states for creation vs listing
  const [isCreatingRequest, setIsCreatingRequest] = useState(false)
  const [requestDeptId, setRequestDeptId] = useState('')
  const [requestLines, setRequestLines] = useState<Array<{ itemId: string; name: string; quantity: number; unit: string }>>([
    { itemId: '', name: '', quantity: 1, unit: 'NOS' }
  ])

  const [selectedRequestDetails, setSelectedRequestDetails] = useState<PurchaseRequestResponse | null>(null)
  const [selectedLinesIndices, setSelectedLinesIndices] = useState<number[]>([])
  const [sourceRequestId, setSourceRequestId] = useState<string | null>(null)


  useEffect(() => {
    if (selectedRequestDetails) {
      const indices: number[] = []
      try {
        if (selectedRequestDetails.notes) {
          const parsed = JSON.parse(selectedRequestDetails.notes)
          if (Array.isArray(parsed)) {
            parsed.forEach((line: any, idx: number) => {
              const isOrdered = line.status === 'ORDERED' || (line.orderedQty && line.orderedQty >= line.quantity)
              if (!isOrdered) {
                indices.push(idx)
              }
            })
          }
        }
      } catch { }
      setSelectedLinesIndices(indices)
    } else {
      setSelectedLinesIndices([])
    }
  }, [selectedRequestDetails])

  const { data: requests = [], isLoading: isLoadingRequests } = useQuery({
    queryKey: ['purchase-requests'],
    queryFn: purchaseApi.getAllRequests,
  })

  const createRequestMutation = useMutation({
    mutationFn: () => purchaseApi.createRequest(
      requestDeptId,
      JSON.stringify(requestLines.filter(l => l.itemId && l.quantity > 0))
    ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['purchase-requests'] })
      toast({ title: 'Purchase request raised successfully', variant: 'success' })
      setIsCreatingRequest(false)
      setRequestDeptId('')
      setRequestLines([{ itemId: '', name: '', quantity: 1, unit: 'NOS' }])
    },
    onError: (e: Error) => {
      toast({ title: 'Failed to raise request', description: e.message, variant: 'destructive' })
    },
  })

  const filteredRequests = requests.filter(req => {
    const matchesDate = req.requestDate === requestFilterDate
    const matchesDept = requestFilterDeptId === 'ALL' || req.departmentId === requestFilterDeptId
    return matchesDate && matchesDept
  })

  // -------------------------------------------------------------
  // TAB 2: PURCHASE ORDER STATES & MUTATIONS
  // -------------------------------------------------------------
  type POExtLine = { itemId: string; name: string; mrp: number; pPrice: number; quantity: number; unit: string; sourceRequestLineIndex?: number }
  const [poView, setPoView] = useState<'list' | 'create' | 'detail'>('list')
  const [poFilterDate, setPoFilterDate] = useState(today)
  const [poFilterDeptId] = useState('ALL')
  const [poFilterSupplierId, setPoFilterSupplierId] = useState('ALL')
  const [poDeptId, setPoDeptId] = useState(DEMO_DEPT_ID)
  const [poSupplierId, setPoSupplierId] = useState('')
  const [poLines, setPoLines] = useState<POExtLine[]>([])
  const [selectedPO, setSelectedPO] = useState<any>(null)
  const [selectedPOLinesIndices, setSelectedPOLinesIndices] = useState<number[]>([])
  const [sourceOrderId, setSourceOrderId] = useState<string | null>(null)

  const { data: purchaseOrders = [], isLoading: isLoadingOrders } = useQuery({
    queryKey: ['purchase-orders', poFilterDate],
    queryFn: () => purchaseApi.getByDate(poFilterDate),
    enabled: activeTab === 'order',
  })

  const updateRequestMutation = useMutation({
    mutationFn: (data: { id: string; requestStatus: string; notes: string }) =>
      purchaseApi.updateRequest(data.id, data.requestStatus, data.notes),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['purchase-requests'] })
    }
  })

  const updateOrderMutation = useMutation({
    mutationFn: (data: { id: string; orderStatus: string; notes: string; lines: any[] }) =>
      purchaseApi.updateOrder(data.id, data.orderStatus, data.notes, data.lines),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['purchase-orders'] })
    }
  })

  const createPOMutation = useMutation({
    mutationFn: () =>
      purchaseApi.create(
        poDeptId,
        poSupplierId || null,
        poLines.filter(l => l.itemId && l.quantity > 0).map(l => ({ itemId: l.itemId, quantity: l.quantity, unitRate: Math.round(l.pPrice * 100) })),
        JSON.stringify(poLines.filter(l => l.itemId && l.quantity > 0))
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['purchase-orders'] })
      toast({ title: 'Purchase order saved successfully', variant: 'success' })
      setPoView('list')
      setPoLines([])
      setPoSupplierId('')

      if (sourceRequestId) {
        const sourceReq = requests.find(r => r.id === sourceRequestId)
        if (sourceReq && sourceReq.notes) {
          try {
            const parsedNotes = JSON.parse(sourceReq.notes)
            poLines.forEach(line => {
              if (line.sourceRequestLineIndex !== undefined && parsedNotes[line.sourceRequestLineIndex]) {
                const prItem = parsedNotes[line.sourceRequestLineIndex]
                prItem.orderedQty = (prItem.orderedQty || 0) + line.quantity
                if (prItem.orderedQty >= prItem.quantity) {
                  prItem.status = 'ORDERED'
                } else {
                  prItem.status = 'PARTIALLY_ORDERED'
                }
              }
            })
            const allOrdered = parsedNotes.every((n: any) => n.status === 'ORDERED' || (n.orderedQty && n.orderedQty >= n.quantity))
            const anyOrdered = parsedNotes.some((n: any) => n.status === 'ORDERED' || (n.orderedQty && n.orderedQty >= n.quantity))
            const newStatus = allOrdered ? 'ORDERED' : (anyOrdered ? 'PARTIALLY_ORDERED' : 'REQUESTED')

            updateRequestMutation.mutate({
              id: sourceRequestId,
              requestStatus: newStatus,
              notes: JSON.stringify(parsedNotes)
            })
          } catch (e) { }
        }
      }
      setSourceRequestId(null)
    },
    onError: (e: Error) =>
      toast({ title: 'Create failed', description: e.message, variant: 'destructive' }),
  })

  const poOrderAmount = poLines.reduce((sum, l) => sum + (l.pPrice * l.quantity), 0)

  const filteredPO = purchaseOrders.filter(o => {
    const matchesDept = poFilterDeptId === 'ALL' || o.departmentId === poFilterDeptId
    const matchesSupp = poFilterSupplierId === 'ALL' || o.supplierId === poFilterSupplierId
    return matchesDept && matchesSupp
  })

  // -------------------------------------------------------------
  // TAB 3: GOODS RECEIVED (GRN) STATES & MUTATIONS
  // -------------------------------------------------------------
  type GRNExtLine = { itemId: string; name: string; batchNumber: string; expiryDate: string; mrp: number; pPrice: number; quantity: number; unit: string; freeQty: number; taxPct: number; sourceOrderLineIndex?: number }
  const [grnView, setGrnView] = useState<'list' | 'create' | 'detail'>('list')
  const [grnFilterDate, setGrnFilterDate] = useState(today)
  const [grnFilterSupplierId, setGrnFilterSupplierId] = useState('ALL')
  const [grnDeptId, setGrnDeptId] = useState(DEMO_DEPT_ID)
  const [grnSupplierId, setGrnSupplierId] = useState('')
  const [grnInvoiceNumber, setGrnInvoiceNumber] = useState('')
  const [grnInvoiceDate, setGrnInvoiceDate] = useState(today)
  const [grnInvoiceType, setGrnInvoiceType] = useState('')
  const [grnLines, setGrnLines] = useState<GRNExtLine[]>([])
  const [selectedGRN, setSelectedGRN] = useState<any>(null)
  const [grnExpiryRawInputs, setGrnExpiryRawInputs] = useState<Record<number, string>>({})
  const [grnInvoiceDateRaw, setGrnInvoiceDateRaw] = useState(() => {
    const parts = today.split('-')
    return parts.length === 3 ? `${parts[2]}/${parts[1]}/${parts[0]}` : ''
  })

  const { data: grnReceipts = [], isLoading: isLoadingGRN } = useQuery({
    queryKey: ['goods-received', grnFilterDate],
    queryFn: () => goodsApi.getByDate(grnFilterDate),
    enabled: activeTab === 'grn',
  })

  const receiveGoodsMutation = useMutation({
    mutationFn: () => goodsApi.receiveGoods(
      grnSupplierId || undefined,
      selectedPO?.id,
      grnDeptId,
      grnInvoiceNumber || undefined,
      grnInvoiceDate || undefined,
      grnInvoiceType || undefined,
      grnLines.filter(l => l.itemId && l.quantity > 0).map(l => {
        const origIdx = grnLines.findIndex(orig => orig === l)
        const adj = adjustTempStocks[origIdx]
        const line: any = {
          itemId: l.itemId,
          quantity: l.quantity,
          purchaseRate: l.pPrice,
          maximumRetailPrice: l.mrp,
          sellingRate: l.mrp,
          freeQty: l.freeQty || 0,
          tempQuantity: (adj && adj.active) ? adj.qty : undefined
        }
        if (l.batchNumber) line.batchNumber = l.batchNumber
        if (l.expiryDate) line.expiryDate = l.expiryDate
        return line
      })
    ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['goods-received'] })
      toast({ title: 'Goods received successfully', variant: 'success' })
      setGrnView('list')
      setGrnLines([])
      setGrnInvoiceNumber('')
      setGrnSupplierId('')
      setGrnInvoiceType('')
      setGrnInvoiceDate(today)
      setAdjustTempStocks({})

      if (sourceOrderId) {
        const sourceOrder = purchaseOrders.find(o => o.id === sourceOrderId)
        if (sourceOrder) {
          const linesToUpdate = [...sourceOrder.lines]
          grnLines.forEach(grnLine => {
            if (grnLine.sourceOrderLineIndex !== undefined && linesToUpdate[grnLine.sourceOrderLineIndex]) {
              const origLine = linesToUpdate[grnLine.sourceOrderLineIndex]
              origLine.receivedQuantity = (origLine.receivedQuantity || 0) + grnLine.quantity
            }
          })
          const allReceived = linesToUpdate.every((l: any) => l.receivedQuantity >= l.quantity)
          const anyReceived = linesToUpdate.some((l: any) => l.receivedQuantity > 0)
          const newStatus = allReceived ? 'RECEIVED' : (anyReceived ? 'PARTIALLY_RECEIVED' : 'ORDERED')

          updateOrderMutation.mutate({
            id: sourceOrderId,
            orderStatus: newStatus,
            notes: sourceOrder.notes || '',
            lines: linesToUpdate
          })
        }
      }
      setSourceOrderId(null)
    },
    onError: (e: Error) => toast({ title: 'Receive failed', description: e.message, variant: 'destructive' }),
  })

  const grnTotal = grnLines.reduce((sum, l) => sum + (l.pPrice * l.quantity), 0)

  // -------------------------------------------------------------
  // TAB 4: PURCHASE RETURN STATES & MUTATIONS
  // -------------------------------------------------------------
  const [returnFilterDate, setReturnFilterDate] = useState(today)
  const [isReturnModalOpen, setIsReturnModalOpen] = useState(false)
  const [returnDeptId, setReturnDeptId] = useState(DEMO_DEPT_ID)
  const [returnSupplierId, setReturnSupplierId] = useState('')
  const [returnNotes, setReturnNotes] = useState('')
  const [returnLines, setReturnLines] = useState<Array<{ itemId: string; name: string; batchId: string; quantity: number; maxQty: number }>>([
    { itemId: '', name: '', batchId: '', quantity: 0, maxQty: 1 }
  ])
  const [selectedReturnDetails, setSelectedReturnDetails] = useState<GoodsReturnResponse | null>(null)

  useEffect(() => {
    if (departments.length > 0) {
      const ph = departments.find(d => d.name.toUpperCase().includes('PHARMACY'))
      const defaultId = ph?.id || departments[0].id
      if (!requestDeptId) setRequestDeptId(defaultId)
      if (returnDeptId === DEMO_DEPT_ID) setReturnDeptId(defaultId)
      if (poDeptId === DEMO_DEPT_ID) setPoDeptId(defaultId)
      if (grnDeptId === DEMO_DEPT_ID) setGrnDeptId(defaultId)
    }
  }, [departments, requestDeptId, returnDeptId, poDeptId, grnDeptId])

  const { data: returns = [], isLoading: isLoadingReturns } = useQuery({
    queryKey: ['purchase-returns', returnFilterDate],
    queryFn: () => purchaseApi.getReturnsByDate(returnFilterDate),
    enabled: activeTab === 'return',
  })

  const { data: allBatches = [] } = useQuery({
    queryKey: ['all-batches'],
    queryFn: inventoryApi.getAllBatches,
    enabled: activeTab === 'return' && isReturnModalOpen,
  })

  const createReturnMutation = useMutation({
    mutationFn: () => purchaseApi.createReturn(
      returnSupplierId || null,
      returnDeptId,
      returnLines.filter(l => l.batchId && l.quantity > 0).map(l => ({ batchId: l.batchId, quantity: l.quantity })),
      returnNotes || undefined
    ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['purchase-returns'] })
      qc.invalidateQueries({ queryKey: ['all-batches'] })
      toast({ title: 'Goods return completed successfully', variant: 'success' })
      setIsReturnModalOpen(false)
      setReturnSupplierId('')
      setReturnNotes('')
      setReturnLines([{ itemId: '', name: '', batchId: '', quantity: 0, maxQty: 1 }])
    },
    onError: (e: Error) => toast({ title: 'Return failed', description: e.message, variant: 'destructive' }),
  })

  const updateReturnLine = (i: number, field: string, value: any) => {
    setReturnLines(prev => prev.map((l, idx) => {
      if (idx !== i) return l
      if (field === 'itemId') {
        return {
          ...l,
          itemId: value.id,
          name: value.name,
          batchId: '',
          quantity: 0,
          maxQty: 1
        }
      }
      if (field === 'batchId') {
        const batch = allBatches.find(b => b.id === value)
        return {
          ...l,
          batchId: value,
          maxQty: batch ? batch.currentQuantity : 1,
          quantity: 0
        }
      }
      return { ...l, [field]: value }
    }))
  }

  const handleCreateReturn = () => {
    if (!returnSupplierId) {
      toast({ title: 'Please select a supplier', variant: 'destructive' })
      return
    }
    const hasInvalidQty = returnLines.some(l => l.batchId && l.quantity <= 0)
    if (hasInvalidQty) {
      toast({ title: 'Return quantity must be greater than 0 for all selected items', variant: 'destructive' })
      return
    }
    const validLines = returnLines.filter(l => l.batchId && l.quantity > 0)
    if (!validLines.length) {
      toast({ title: 'Add at least one item batch to return', variant: 'destructive' })
      return
    }
    // Check if returning more than stock
    const exceedsStock = validLines.some(l => l.quantity > l.maxQty)
    if (exceedsStock) {
      toast({ title: 'Return quantity exceeds available stock', variant: 'destructive' })
      return
    }
    createReturnMutation.mutate()
  }

  const closeReturnModal = () => {
    setIsReturnModalOpen(false)
    setReturnSupplierId('')
    setReturnNotes('')
    setReturnLines([{ itemId: '', name: '', batchId: '', quantity: 0, maxQty: 1 }])
  }

  const inputCls = "px-2.5 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-neutral-500 w-full bg-white font-medium"

  // Parsed request details if notes holds a JSON items list


  return (
    <div className="space-y-5 max-w-7xl mx-auto px-4 py-2 pb-16">
      {/* Tab Switcher & Title Header */}
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4 border-b border-gray-100 pb-5">
        <div>
          <h1 className="text-2xl font-extrabold text-gray-900 tracking-tight">
            {activeTab === 'grn' ? 'Goods Receipt Note (GRN)' :
              activeTab === 'return' ? 'Purchase Return' :
                'Purchase Order'}
          </h1>
        </div>
      </div>

      {/* -------------------------------------------------------------
          TAB 1 CONTENT: PURCHASE REQUEST
          ------------------------------------------------------------- */}
      {activeTab === 'request' && (
        <div className="space-y-4">
          {!isCreatingRequest ? (
            selectedRequestDetails ? (() => {
              let parsedDetailsLines: Array<{ itemId: string; name: string; quantity: number; unit: string; orderedQty?: number; status?: string }> = []
              if (selectedRequestDetails.notes) {
                try {
                  parsedDetailsLines = JSON.parse(selectedRequestDetails.notes)
                } catch { }
              }
              return (
                <div className="space-y-4">
                  {/* Header Row */}
                  <div className="flex justify-between items-center bg-transparent py-2">
                    <button
                      onClick={() => setSelectedRequestDetails(null)}
                      className="flex items-center gap-2 px-4 py-2 border border-gray-300 rounded-full text-xs font-bold text-gray-700 bg-white hover:bg-gray-50 shadow-sm"
                    >
                      &lt; REQUEST HISTORY
                    </button>
                    <h2 className="text-2xl font-bold text-gray-800">Purchase Request</h2>
                  </div>

                  {/* Main Card */}
                  <div className="bg-white border border-gray-200 rounded-xl shadow-sm overflow-hidden">
                    {/* Row 1: Request Info */}
                    <div className="p-6 border-b border-gray-100 flex flex-col md:flex-row justify-between items-start md:items-center gap-4 bg-white">
                      <div>
                        <span className="text-sm font-semibold text-gray-750">Request No : </span>
                        <span className="text-neutral-600 font-mono text-xl font-bold">
                          {selectedRequestDetails.sequenceNumber || `PR-${selectedRequestDetails.id.slice(0, 5)}`}
                        </span>
                      </div>
                      <div>
                        <span className="text-sm font-semibold text-gray-750">Status : </span>
                        <span className="text-sm font-bold text-gray-900 uppercase">
                          {String(selectedRequestDetails.requestStatus || 'REQUESTED')}
                        </span>
                      </div>
                      <div>
                        <span className="text-sm font-semibold text-gray-750">Request Date : </span>
                        <span className="text-sm font-bold text-gray-900">
                          {formatToIndianDate(selectedRequestDetails.requestDate)}
                        </span>
                      </div>
                    </div>

                    {/* Row 2: Sub-header bar */}
                    <div className="p-6 border-b border-gray-100 bg-[#f9fafb] flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
                      <div className="flex gap-4">
                        <div>
                          <span className="text-[10px] font-bold text-gray-400 block uppercase tracking-wider">Request To</span>
                          <span className="text-sm font-bold text-gray-800">
                            {departments.find(d => d.id === selectedRequestDetails.departmentId)?.name || 'PHARMACY'}
                          </span>
                        </div>
                        <div className="border-l border-gray-200 pl-4">
                          <span className="text-[10px] font-bold text-gray-400 block uppercase tracking-wider">Request By</span>
                          <span className="text-sm font-bold text-gray-800">
                            {currentUser?.username || 'super user'}
                          </span>
                        </div>
                      </div>
                      <button
                        disabled={parsedDetailsLines.length > 0 && parsedDetailsLines.every(line => line.status === 'ORDERED' || (line.orderedQty && line.orderedQty >= line.quantity))}
                        onClick={() => {
                          const selectedItems = parsedDetailsLines.filter((_, idx) => selectedLinesIndices.includes(idx))
                          if (selectedItems.length === 0) {
                            toast({ title: 'Please select at least one item', variant: 'destructive' })
                            return
                          }

                          // Map selectedItems to PO lines
                          const mappedPOLines: POExtLine[] = selectedLinesIndices
                            .map(idx => {
                              const item = parsedDetailsLines[idx]
                              const remainingQty = item.quantity - (item.orderedQty || 0)
                              const priceInfo = getPOItemPriceInfo(item.itemId, item.name, inventoryBatches)
                              return {
                                itemId: item.itemId,
                                name: item.name,
                                mrp: priceInfo.mrp,
                                pPrice: priceInfo.pPrice,
                                quantity: remainingQty > 0 ? remainingQty : 0,
                                unit: item.unit,
                                sourceRequestLineIndex: idx
                              }
                            })
                            .filter(line => line.quantity > 0)

                          setSourceRequestId(selectedRequestDetails.id)
                          setPoLines(mappedPOLines)
                          setPoDeptId(selectedRequestDetails.departmentId || DEMO_DEPT_ID)
                          setPoView('create')
                          setActiveTab('order')
                          setSelectedRequestDetails(null)
                          toast({ title: `Added ${selectedItems.length} item(s) to Purchase Order`, variant: 'success' })
                        }}
                        className="px-5 py-2 bg-neutral-600 hover:bg-neutral-700 text-white text-xs font-bold rounded-lg shadow-sm flex items-center gap-1 transition-all uppercase disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        CREATE PO &gt;
                      </button>
                    </div>

                    {/* Table */}
                    <div className="overflow-x-auto">
                      <table className="w-full text-xs text-left">
                        <thead>
                          <tr className="bg-gray-50 border-b border-gray-200 text-gray-600 uppercase font-semibold">
                            <th className="px-6 py-3 w-16">S.NO</th>
                            <th className="px-6 py-3">ITEM</th>
                            <th className="px-6 py-3 w-40">REQUESTED QTY</th>
                            <th className="px-6 py-3 w-40">ORDERED QTY</th>
                            <th className="px-6 py-3 w-20 text-center">
                              <input
                                type="checkbox"
                                disabled={parsedDetailsLines.length > 0 && parsedDetailsLines.every(line => line.status === 'ORDERED' || (line.orderedQty && line.orderedQty >= line.quantity))}
                                checked={
                                  parsedDetailsLines.length > 0 &&
                                  selectedLinesIndices.length > 0 &&
                                  parsedDetailsLines.every((line, idx) =>
                                    (line.status === 'ORDERED' || (line.orderedQty && line.orderedQty >= line.quantity)) ||
                                    selectedLinesIndices.includes(idx)
                                  )
                                }
                                onChange={(e) => {
                                  if (e.target.checked) {
                                    const availableIndices = parsedDetailsLines
                                      .map((line, i) => (line.status === 'ORDERED' || (line.orderedQty && line.orderedQty >= line.quantity) ? -1 : i))
                                      .filter(i => i !== -1);
                                    setSelectedLinesIndices(availableIndices)
                                  } else {
                                    setSelectedLinesIndices([])
                                  }
                                }}
                                className="rounded border-gray-300 text-neutral-600 focus:ring-neutral-500 w-4 h-4 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                              />
                            </th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100">
                          {parsedDetailsLines.map((line, idx) => (
                            <tr key={idx} className="hover:bg-gray-50 transition-colors">
                              <td className="px-6 py-4 text-gray-500 font-medium">{idx + 1}</td>
                              <td className="px-6 py-4 font-semibold text-gray-800">{line.name}</td>
                              <td className="px-6 py-4 text-gray-700 font-medium">
                                {line.quantity} {line.unit}
                              </td>
                              <td className="px-6 py-4 text-gray-500 font-medium">
                                {line.orderedQty || 0} {line.unit}
                              </td>
                              <td className="px-6 py-4 text-center">
                                {line.status === 'ORDERED' || (line.orderedQty && line.orderedQty >= line.quantity) ? (
                                  <span className="inline-flex items-center justify-center px-2 py-1 text-[10px] font-bold text-green-700 bg-green-100 rounded-md">
                                    ORDERED
                                  </span>
                                ) : (
                                  <input
                                    type="checkbox"
                                    checked={selectedLinesIndices.includes(idx)}
                                    onChange={(e) => {
                                      if (e.target.checked) {
                                        setSelectedLinesIndices([...selectedLinesIndices, idx])
                                      } else {
                                        setSelectedLinesIndices(selectedLinesIndices.filter(i => i !== idx))
                                      }
                                    }}
                                    className="rounded border-gray-300 text-neutral-600 focus:ring-neutral-500 w-4 h-4 cursor-pointer"
                                  />
                                )}
                              </td>
                            </tr>
                          ))}
                          {parsedDetailsLines.length === 0 && (
                            <tr>
                              <td colSpan={5} className="px-6 py-12 text-center text-gray-400 text-sm">
                                No items found in this purchase request
                              </td>
                            </tr>
                          )}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>
              )
            })()
              : (
                <>
                  {/* History / Filter View */}
                  <div className="bg-white border border-gray-200 rounded-xl p-4 flex flex-col md:flex-row items-center justify-between gap-4 shadow-sm">
                    <div className="flex flex-wrap items-center gap-4 w-full md:w-auto">
                      <div className="flex items-center gap-2">
                        <Calendar size={16} className="text-gray-400" />
                        <DatePicker value={requestFilterDate} onChange={setRequestFilterDate} size="sm" />
                      </div>
                      <div className="w-48">
                        <select
                          value={requestFilterDeptId}
                          onChange={e => setRequestFilterDeptId(e.target.value)}
                          className="w-full text-xs font-medium text-gray-700 border border-gray-300 rounded-lg p-2 focus:ring-2 focus:ring-neutral-500"
                        >
                          <option value="ALL">All Department</option>
                          {departments.map(d => (
                            <option key={d.id} value={d.id}>{d.name}</option>
                          ))}
                        </select>
                      </div>
                    </div>
                    <button
                      onClick={() => {
                        setIsCreatingRequest(true)
                        const pharm = departments.find(d => d.name.toUpperCase().includes('PHARMACY'))
                        setRequestDeptId(pharm?.id || departments[0]?.id || '')
                        setRequestLines([{ itemId: '', name: '', quantity: 1, unit: 'NOS' }])
                      }}
                      className="w-full md:w-auto px-5 py-2 bg-neutral-600 hover:bg-neutral-700 text-white text-xs font-bold rounded-lg shadow-sm transition-all"
                    >
                      + ADD GRN
                    </button>
                  </div>

                  <div className="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
                    {isLoadingRequests ? (
                      <p className="text-sm text-gray-500 p-6 text-center">Loading requests...</p>
                    ) : (
                      <div className="overflow-x-auto">
                        <table className="w-full text-sm text-left">
                          <thead>
                            <tr className="bg-gray-50 border-b border-gray-200 text-xs text-gray-600 uppercase tracking-wider">
                              <th className="px-6 py-3 font-semibold w-16">S.NO</th>
                              <th className="px-6 py-3 font-semibold">Request No</th>
                              <th className="px-6 py-3 font-semibold">Date</th>
                              <th className="px-6 py-3 font-semibold">Request To</th>
                              <th className="px-6 py-3 font-semibold">Request By</th>
                              <th className="px-6 py-3 font-semibold">Status</th>
                              <th className="px-6 py-3 font-semibold text-center w-20"></th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-gray-100">
                            {filteredRequests.map((req, idx) => {
                              const dept = departments.find(d => d.id === req.departmentId)
                              return (
                                <tr key={req.id} className="hover:bg-gray-50 transition-colors">
                                  <td className="px-6 py-4 text-gray-500 font-medium">{idx + 1}</td>
                                  <td className="px-6 py-4 font-mono font-semibold text-gray-900">{req.sequenceNumber || `PR-${req.id.slice(0, 5)}`}</td>
                                  <td className="px-6 py-4 text-gray-500">{formatToIndianDate(req.requestDate)}</td>
                                  <td className="px-6 py-4 font-medium text-gray-700">{dept ? dept.name : 'PHARMACY'}</td>
                                  <td className="px-6 py-4 text-gray-600">{currentUser?.username || 'super user'}</td>
                                  <td className="px-6 py-4">
                                    <span className={cn('px-2.5 py-0.5 text-xs font-semibold rounded-full border', STATUS_STYLES[String(req.requestStatus || 'REQUESTED')] || STATUS_STYLES.REQUESTED)}>
                                      {String(req.requestStatus || 'REQUESTED')}
                                    </span>
                                  </td>
                                  <td className="px-6 py-4 text-center">
                                    <button
                                      onClick={() => setSelectedRequestDetails(req)}
                                      className="p-1 text-gray-400 hover:text-neutral-600 transition-colors"
                                      title="View details"
                                    >
                                      <ChevronRight size={18} />
                                    </button>
                                  </td>
                                </tr>
                              )
                            })}
                            {filteredRequests.length === 0 && (
                              <tr>
                                <td colSpan={7} className="px-6 py-12 text-center text-gray-400 text-sm">
                                  No purchase requests found for this filter
                                </td>
                              </tr>
                            )}
                          </tbody>
                        </table>
                      </div>
                    )}
                  </div>
                </>
              )
          ) : (
            /* Creation View matching the new UI with inline direct rows editing */
            <div className="bg-white border border-gray-200 rounded-xl p-5 space-y-4 shadow-sm">
              <div className="flex items-center justify-between">
                <button
                  onClick={() => setIsCreatingRequest(false)}
                  className="px-4 py-2 border border-gray-300 rounded-full text-xs font-bold text-gray-600 hover:bg-gray-50 flex items-center gap-2 shadow-sm transition-all"
                >
                  &lt; REQUEST HISTORY
                </button>
              </div>

              <div className="border-t border-gray-100 pt-3">
                <h2 className="text-lg font-bold text-gray-800 mb-4">Create Purchase Request</h2>

                <div className="flex items-center gap-3 mb-4 bg-gray-50 p-3 rounded-lg border border-gray-200 w-fit">
                  <label className="text-xs font-bold text-gray-700">Request To</label>
                  <select
                    value={requestDeptId}
                    onChange={e => setRequestDeptId(e.target.value)}
                    className="px-2.5 py-1 border border-gray-300 rounded-md text-xs bg-white focus:outline-none focus:ring-1 focus:ring-neutral-500 font-semibold text-gray-805"
                  >
                    {departments.map(d => (
                      <option key={d.id} value={d.id}>{d.name}</option>
                    ))}
                  </select>
                </div>

                {/* Items List Table - OVERFLOW AUTO with bottom padding for dropdown space */}
                <div className="border border-gray-200 rounded-lg shadow-sm mb-4 bg-white overflow-x-auto pb-32">
                  <table className="w-full text-xs text-left">
                    <thead>
                      <tr className="bg-gray-50 border-b border-gray-200 text-[10px] font-bold text-gray-600 uppercase tracking-wider">
                        <th className="px-4 py-2.5 w-16">S.NO</th>
                        <th className="px-4 py-2.5">ITEM *</th>
                        <th className="px-4 py-2.5 w-56">REQUESTED QTY *</th>
                        <th className="px-4 py-2.5 w-16 text-center"></th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {requestLines.map((line, idx) => (
                        <tr key={idx} className="hover:bg-gray-50/50 transition-colors align-middle">
                          <td className="px-4 py-3 text-gray-500 font-medium">{idx + 1}</td>
                          <td className="px-4 py-2 min-w-[300px] relative overflow-visible">
                            <MedicineSearchInput
                              initialValue={line.name}
                              onSelect={item => {
                                setRequestLines(prev => prev.map((l, i) => i === idx ? {
                                  ...l,
                                  itemId: item.id,
                                  name: item.name,
                                  unit: guessUnitFromName(item.name)
                                } : l))
                              }}
                              placeholder="Search medicine…"
                            />
                          </td>
                          <td className="px-4 py-2">
                            <div className="flex items-center gap-3">
                              <input
                                type="number"
                                min={1}
                                value={line.quantity}
                                onChange={e => {
                                  const newQty = parseInt(e.target.value) || 1
                                  setRequestLines(prev => prev.map((l, i) => i === idx ? { ...l, quantity: newQty } : l))
                                }}
                                className="px-2 py-1.5 border border-gray-300 rounded w-20 text-xs focus:outline-none focus:ring-1 focus:ring-neutral-500 bg-white font-semibold text-gray-800"
                              />
                              <span className="text-xs font-bold text-gray-500">{line.unit}</span>
                            </div>
                          </td>
                          <td className="px-4 py-2 text-center">
                            {requestLines.length > 1 && (
                              <button
                                type="button"
                                onClick={() => setRequestLines(prev => prev.filter((_, i) => i !== idx))}
                                className="p-1 text-gray-400 hover:text-red-650 hover:bg-red-50 rounded transition-colors"
                              >
                                <X size={14} />
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                <div className="mb-4">
                  <button
                    type="button"
                    onClick={() => setRequestLines(prev => [...prev, { itemId: '', name: '', quantity: 1, unit: 'NOS' }])}
                    className="inline-flex items-center gap-1 text-[11px] text-neutral-600 hover:text-neutral-700 font-bold bg-gray-50 border border-gray-200 px-2.5 py-1 rounded shadow-sm hover:bg-gray-100 transition-all"
                  >
                    <Plus size={12} /> Add Line
                  </button>
                </div>

                <div className="border-t border-dashed border-gray-200 pt-4 flex justify-end gap-2">
                  <button
                    onClick={() => setIsCreatingRequest(false)}
                    className="px-4 py-2 bg-gray-500 hover:bg-gray-600 text-white text-[11px] font-bold rounded transition-all uppercase"
                  >
                    CANCEL
                  </button>
                  <button
                    onClick={() => {
                      const validLines = requestLines.filter(l => l.itemId && l.quantity > 0)
                      if (validLines.length === 0) {
                        toast({ title: 'Please search and select at least one item', variant: 'destructive' })
                        return
                      }
                      createRequestMutation.mutate()
                    }}
                    disabled={createRequestMutation.isPending}
                    className="px-4 py-2 bg-neutral-600 hover:bg-neutral-700 text-white text-[11px] font-bold rounded transition-all uppercase"
                  >
                    {createRequestMutation.isPending ? 'SAVING...' : 'SAVE REQUEST'}
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {activeTab === 'order' && (
        <div className="space-y-4">
          {poView === 'list' && (
            <>
              <div className="bg-white border border-gray-200 rounded-xl p-3 flex flex-wrap items-center justify-between gap-3 shadow-sm">
                <div className="flex flex-wrap items-center gap-3">
                  <div className="flex items-center gap-2"><DatePicker value={poFilterDate} onChange={setPoFilterDate} size="sm" /></div>
                  {/* <select value={poFilterDeptId} onChange={e => setPoFilterDeptId(e.target.value)} className="text-xs border border-gray-300 rounded-lg p-1.5 w-40">
                    <option value="ALL">All Department</option>
                    {departments.filter((d: any) => d.status !== 'INACTIVE' && d.status !== 0).map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
                  </select> */}
                  <select value={poFilterSupplierId} onChange={e => setPoFilterSupplierId(e.target.value)} className="text-xs border border-gray-300 rounded-lg p-1.5 w-40">
                    <option value="ALL">All Supplier</option>
                    {suppliers.filter((s: any) => s.status !== 'INACTIVE' && s.status !== 0).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
                  </select>
                </div>
                <button onClick={() => { setPoView('create'); setPoLines([]); const ph = departments.find(d => d.name.toUpperCase().includes('PHARMACY')); setPoDeptId(ph?.id || departments[0]?.id || ''); setPoSupplierId('') }} className="px-5 py-2 bg-neutral-600 hover:bg-neutral-700 text-white text-xs font-bold rounded-lg shadow-sm">+ ADD PURCHASE ORDER</button>
              </div>
              <div className="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
                {isLoadingOrders ? <p className="text-sm text-gray-500 p-6 text-center">Loading...</p> : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-xs text-left">
                      <thead><tr className="bg-gray-50 border-b border-gray-200 text-[10px] text-gray-600 uppercase tracking-wider font-semibold">
                        <th className="px-4 py-2.5 w-14">S.NO</th><th className="px-4 py-2.5">ORDER NO</th><th className="px-4 py-2.5">DATE</th><th className="px-4 py-2.5">SUPPLIER</th><th className="px-4 py-2.5 text-right">AMOUNT</th><th className="px-4 py-2.5">STATUS</th><th className="px-4 py-2.5 text-center w-20">ACTION</th>
                      </tr></thead>
                      <tbody className="divide-y divide-gray-100">
                        {filteredPO.map((o, idx) => {
                          const supp = suppliers.find(s => s.id === o.supplierId); const amt = o.lines.reduce((s, l) => s + (l.quantity * ((l.unitRate ?? 0) / 100)), 0); return (
                            <tr key={o.id} className="hover:bg-gray-50 transition-colors">
                              <td className="px-4 py-3 text-gray-500">{idx + 1}</td>
                              <td className="px-4 py-3 font-mono font-semibold text-gray-900">{o.sequenceNumber || `PO-${o.id.slice(0, 5)}`}</td>
                              <td className="px-4 py-3 text-gray-500">{formatToIndianDate(o.orderDate)}</td>
                              <td className="px-4 py-3 text-gray-700">{supp?.name || '—'}</td>
                              <td className="px-4 py-3 text-right font-medium text-gray-800">{Math.round(amt).toLocaleString('en-IN')}</td>
                              <td className="px-4 py-3"><span className={cn('px-2 py-0.5 text-[10px] font-semibold rounded-full border', STATUS_STYLES[String(o.orderStatus || 'ORDERED')] || STATUS_STYLES.ORDERED)}>{String(o.orderStatus || 'ORDERED').replace('_', ' ')}</span></td>
                              <td className="px-4 py-3 text-center"><button onClick={() => { setSelectedPO(o); setPoView('detail') }} className="p-1 text-gray-400 hover:text-neutral-600"><ChevronRight size={16} /></button></td>
                            </tr>
                          )
                        })}
                        {filteredPO.length === 0 && <tr><td colSpan={7} className="px-4 py-10 text-center text-gray-400">No purchase orders found</td></tr>}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </>
          )}

          {poView === 'create' && (
            <div className="bg-white border border-gray-200 rounded-xl p-5 space-y-4 shadow-sm">
              <button onClick={() => setPoView('list')} className="px-4 py-2 border border-gray-300 rounded-full text-xs font-bold text-gray-600 hover:bg-gray-50 shadow-sm">&lt; ORDER HISTORY</button>
              <div className="border-t border-gray-100 pt-3">
                <h2 className="text-lg font-bold text-gray-800 mb-4">Create Purchase Order</h2>
                <div className="flex flex-wrap items-center gap-6 mb-4 bg-gray-50 p-3 rounded-lg border border-gray-200">
                  <div className="flex items-center gap-2"><span className="text-xs font-bold text-gray-700">Department :</span><span className="text-xs font-semibold text-gray-800">{departments.find(d => d.id === poDeptId)?.name?.toUpperCase() || 'PHARMACY'}</span></div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-bold text-gray-700">Supplier : <span className="text-red-500">*</span></span>
                    <select value={poSupplierId} onChange={e => setPoSupplierId(e.target.value)} className="px-2 py-1 border border-gray-300 rounded text-xs bg-white font-semibold outline-none focus:border-neutral-500 focus:ring-1 focus:ring-neutral-500">
                      <option value="">Select Supplier</option>
                      {suppliers.filter((s: any) => s.status !== 'INACTIVE' && s.status !== 0).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
                    </select>
                  </div>
                </div>
                <div className="border border-gray-200 rounded-lg shadow-sm mb-3 bg-white overflow-x-auto md:overflow-x-visible pb-32 md:pb-0">
                  <table className="w-full text-xs text-left min-w-[700px]">
                    <thead><tr className="bg-gray-50 border-b border-gray-200 text-[10px] font-bold text-gray-600 uppercase tracking-wider">
                      <th className="px-3 py-2.5 w-14">S.NO</th><th className="px-3 py-2.5">ITEM</th><th className="px-3 py-2.5 w-24">MRP *</th><th className="px-3 py-2.5 w-24">P.PRICE *</th><th className="px-3 py-2.5 w-32">ORDER QTY *</th><th className="px-3 py-2.5 w-28 text-right">SUB TOTAL</th><th className="px-3 py-2.5 w-12"></th>
                    </tr></thead>
                    <tbody className="divide-y divide-gray-100">
                      {poLines.map((line, idx) => (
                        <tr key={idx} className="align-middle hover:bg-gray-50/50">
                          <td className="px-3 py-2 text-gray-500">{idx + 1}</td>
                          <td className="px-3 py-2 font-medium text-gray-800">{line.name}</td>
                          <td className="px-3 py-2">
                            <div className="flex items-center gap-1">
                              <span className="text-gray-500 font-medium text-xs">₹</span>
                              <input type="number" step="0.01" min={0.01} value={line.mrp || ''} onChange={e => {
                                const val = parseFloat(e.target.value);
                                const cleanVal = isNaN(val) ? 0 : Math.max(0, val);
                                setPoLines(p => p.map((l, i) => i === idx ? { ...l, mrp: cleanVal } : l));
                              }} className="px-1.5 py-1 border border-gray-300 rounded w-full text-xs" />
                            </div>
                          </td>
                          <td className="px-3 py-2">
                            <div className="flex items-center gap-1">
                              <span className="text-gray-500 font-medium text-xs">₹</span>
                              <input type="number" step="0.01" min={0.01} value={line.pPrice || ''} onChange={e => {
                                const val = parseFloat(e.target.value);
                                const cleanVal = isNaN(val) ? 0 : Math.max(0, val);
                                setPoLines(p => p.map((l, i) => i === idx ? { ...l, pPrice: cleanVal } : l));
                              }} className="px-1.5 py-1 border border-gray-300 rounded w-full text-xs" />
                            </div>
                          </td>
                          <td className="px-3 py-2"><div className="flex items-center gap-1"><input type="number" min={1} value={line.quantity} onChange={e => {
                            const val = parseInt(e.target.value);
                            const cleanVal = isNaN(val) ? 0 : Math.max(0, val);
                            setPoLines(p => p.map((l, i) => i === idx ? { ...l, quantity: cleanVal } : l));
                          }} className="px-1.5 py-1 border border-gray-300 rounded w-16 text-xs" /><span className="text-[10px] text-gray-500 font-bold">{line.unit}</span></div></td>
                          <td className="px-3 py-2 text-right font-semibold text-gray-800">{Math.round(line.pPrice * line.quantity).toLocaleString('en-IN')}</td>
                          <td className="px-3 py-2 text-center"><button onClick={() => setPoLines(p => p.filter((_, i) => i !== idx))} className="text-gray-400 hover:text-red-600"><X size={14} /></button></td>
                        </tr>
                      ))}
                      {/* Search row */}
                      <tr className="align-middle bg-gray-50/30">
                        <td className="px-3 py-2 text-gray-400">—</td>
                        <td className="px-3 py-2 relative overflow-visible" colSpan={4}>
                          <MedicineSearchInput clearOnSelect onSelect={item => {
                            const priceInfo = getPOItemPriceInfo(item.id, item.name, inventoryBatches)
                            setPoLines(p => [...p, { itemId: item.id, name: item.name, mrp: priceInfo.mrp, pPrice: priceInfo.pPrice, quantity: 1, unit: guessUnitFromName(item.name) }])
                          }} placeholder="Enter the Item Name" />
                        </td>
                        <td className="px-3 py-2" colSpan={2}></td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <div className="flex items-center justify-end mb-3"><span className="text-xs font-bold text-gray-700 mr-3">Order Amount :</span><span className="text-sm font-bold text-gray-900">{Math.round(poOrderAmount).toLocaleString('en-IN')}</span></div>
                <div className="border-t border-dashed border-gray-200 pt-4 flex justify-end gap-2">
                  <button onClick={() => setPoView('list')} className="px-5 py-2 bg-gray-500 hover:bg-gray-600 text-white text-[11px] font-bold rounded uppercase">CANCEL</button>
                  <button onClick={() => {
                    if (!poSupplierId) { toast({ title: 'Please select a supplier', variant: 'destructive' }); return }
                    const validLines = poLines.filter(l => l.itemId)
                    if (validLines.length === 0) { toast({ title: 'Add at least one item', variant: 'destructive' }); return }
                    if (validLines.some(l => !l.quantity || l.quantity <= 0)) { toast({ title: 'Quantity must be greater than zero for all items', variant: 'destructive' }); return }
                    if (validLines.some(l => !l.pPrice || l.pPrice <= 0)) { toast({ title: 'Purchase Price (P.PRICE) must be greater than zero', variant: 'destructive' }); return }
                    if (validLines.some(l => !l.mrp || l.mrp <= 0)) { toast({ title: 'MRP must be greater than zero', variant: 'destructive' }); return }
                    if (validLines.some(l => l.pPrice > l.mrp)) { toast({ title: 'P.PRICE cannot be greater than MRP', variant: 'destructive' }); return }
                    createPOMutation.mutate()
                  }} disabled={createPOMutation.isPending} className="px-5 py-2 bg-neutral-600 hover:bg-neutral-700 text-white text-[11px] font-bold rounded uppercase">{createPOMutation.isPending ? 'SAVING...' : 'SAVE ORDER'}</button>
                </div>
              </div>
            </div>
          )}

          {poView === 'detail' && selectedPO && (() => {
            const supp = suppliers.find(s => s.id === selectedPO.supplierId); const dept = departments.find(d => d.id === selectedPO.departmentId); let parsedLines: any[] = []; try { if (selectedPO.notes) parsedLines = JSON.parse(selectedPO.notes) } catch { } const totalAmt = selectedPO.lines.reduce((s: number, l: any) => s + (l.quantity * ((l.unitRate ?? 0) / 100)), 0); return (
              <div className="bg-white border border-gray-200 rounded-xl p-5 space-y-4 shadow-sm">
                <button onClick={() => { setPoView('list'); setSelectedPO(null) }} className="px-4 py-2 border border-gray-300 rounded-full text-xs font-bold text-gray-600 hover:bg-gray-50 shadow-sm">&lt; ORDER HISTORY</button>
                <div className="border-t border-gray-100 pt-3">
                  <div className="flex flex-wrap items-center justify-between mb-4">
                    <h2 className="text-base font-bold text-gray-800">Order No : <span className="text-neutral-600">{selectedPO.sequenceNumber || `PO-${selectedPO.id.slice(0, 5)}`}</span></h2>
                    <span className="text-xs font-bold text-gray-600">Status : <span className="font-extrabold text-gray-900">{String(selectedPO.orderStatus || 'ORDERED')}</span></span>
                    <span className="text-xs font-bold text-gray-600">Order Date : <span className="font-extrabold text-gray-900">{formatToIndianDate(selectedPO.orderDate)}</span></span>
                  </div>
                  <div className="flex flex-wrap items-center gap-8 mb-4 bg-gray-50 p-3 rounded-lg border border-gray-200 justify-between">
                    <div className="flex gap-8">
                      <div><span className="text-xs font-bold text-gray-600">Department : </span> <span className="text-xs font-semibold text-gray-900 ml-2">{dept?.name?.toUpperCase() || 'PHARMACY'}</span></div>
                      <div><span className="text-xs font-bold text-gray-600">Supplier : </span> <span className="text-xs font-semibold text-gray-900 ml-2">{supp?.name?.toUpperCase() || '—'}</span></div>
                    </div>
                    <button
                      disabled={selectedPO.lines.every((l: any) => l.receivedQuantity >= l.quantity)}
                      onClick={() => {
                        const selectedItems = selectedPO.lines.filter((_: any, idx: number) => selectedPOLinesIndices.includes(idx))
                        if (selectedItems.length === 0) {
                          toast({ title: 'Please select at least one item', variant: 'destructive' })
                          return
                        }

                        const mappedGRNLines: GRNExtLine[] = selectedPOLinesIndices
                          .map(idx => {
                            const item = selectedPO.lines[idx]
                            const pl = parsedLines.find((p: any) => p.itemId === item.itemId) || {}
                            const pendingQty = item.quantity - (item.receivedQuantity || 0)
                            return {
                              itemId: item.itemId,
                              name: pl.name || item.itemId,
                              batchNumber: '',
                              expiryDate: '',
                              mrp: pl.mrp || (item.unitRate / 100),
                              pPrice: item.unitRate / 100,
                              quantity: pendingQty > 0 ? pendingQty : 0,
                              unit: pl.unit || 'NOS',
                              freeQty: 0,
                              taxPct: pl.taxPct || 0,
                              sourceOrderLineIndex: idx
                            }
                          })
                          .filter(line => line.quantity > 0)

                        setSourceOrderId(selectedPO.id)
                        setGrnLines(mappedGRNLines)
                        setGrnDeptId(selectedPO.departmentId || DEMO_DEPT_ID)
                        setGrnSupplierId(selectedPO.supplierId || '')
                        setGrnView('create')
                        setActiveTab('grn')
                        setSelectedPO(null)
                      }}
                      className="px-5 py-2 bg-neutral-600 hover:bg-neutral-700 text-white text-xs font-bold rounded-lg shadow-sm flex items-center gap-1 transition-all uppercase disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      CREATE GRN &gt;
                    </button>
                  </div>
                  <div className="overflow-x-auto">
                    <table className="w-full text-xs text-left border border-gray-200 rounded-lg overflow-hidden mb-4">
                      <thead><tr className="bg-gray-50 border-b border-gray-200 text-[10px] font-bold text-gray-600 uppercase">
                        <th className="px-3 py-2.5 w-14">S.NO.</th><th className="px-3 py-2.5">ITEM</th><th className="px-3 py-2.5 w-20 text-right">MRP</th><th className="px-3 py-2.5 w-20 text-right">P.PRICE</th><th className="px-3 py-2.5 w-28 text-center">ORDER QTY</th><th className="px-3 py-2.5 w-28 text-center">RECEIVED QTY</th><th className="px-3 py-2.5 w-28 text-right">SUB TOTAL</th>
                        <th className="px-3 py-2.5 w-12 text-center">
                          <input
                            type="checkbox"
                            disabled={selectedPO.lines.every((l: any) => l.receivedQuantity >= l.quantity)}
                            checked={
                              selectedPO.lines.length > 0 &&
                              selectedPOLinesIndices.length > 0 &&
                              selectedPO.lines.every((l: any, idx: number) =>
                                l.receivedQuantity >= l.quantity || selectedPOLinesIndices.includes(idx)
                              )
                            }
                            onChange={(e) => {
                              if (e.target.checked) {
                                const availableIndices = selectedPO.lines
                                  .map((l: any, i: number) => l.receivedQuantity >= l.quantity ? -1 : i)
                                  .filter((i: number) => i !== -1);
                                setSelectedPOLinesIndices(availableIndices)
                              } else {
                                setSelectedPOLinesIndices([])
                              }
                            }}
                            className="rounded border-gray-300 text-neutral-600 focus:ring-neutral-500 w-4 h-4 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                          />
                        </th>
                      </tr></thead>
                      <tbody className="divide-y divide-gray-100">
                        {selectedPO.lines.map((l: any, idx: number) => {
                          const pl = parsedLines.find((p: any) => p.itemId === l.itemId) || {}; const unitRate = (l.unitRate ?? 0) / 100; return (
                            <tr key={l.id} className="hover:bg-gray-50">
                              <td className="px-3 py-2 text-gray-500">{idx + 1}</td>
                              <td className="px-3 py-2 font-medium text-gray-800">{pl?.name || l.itemId.slice(0, 12)}</td>
                              <td className="px-3 py-2 text-right">{Math.round(pl?.mrp ?? unitRate)}</td>
                              <td className="px-3 py-2 text-right">{Math.round(unitRate)}</td>
                              <td className="px-3 py-2 text-center">{l.quantity} <span className="text-gray-400 ml-1">{pl?.unit || 'NOS'}</span></td>
                              <td className="px-3 py-2 text-center">{l.receivedQuantity} <span className="text-gray-400 ml-1">{pl?.unit || 'NOS'}</span></td>
                              <td className="px-3 py-2 text-right font-semibold">{Math.round(l.quantity * unitRate).toLocaleString('en-IN')}</td>
                              <td className="px-3 py-2 text-center">
                                {l.receivedQuantity >= l.quantity ? (
                                  <span className="inline-flex items-center justify-center px-2 py-1 text-[10px] font-bold text-green-700 bg-green-100 rounded-md">RECEIVED</span>
                                ) : (
                                  <input
                                    type="checkbox"
                                    checked={selectedPOLinesIndices.includes(idx)}
                                    onChange={(e) => {
                                      if (e.target.checked) {
                                        setSelectedPOLinesIndices([...selectedPOLinesIndices, idx])
                                      } else {
                                        setSelectedPOLinesIndices(selectedPOLinesIndices.filter(i => i !== idx))
                                      }
                                    }}
                                    className="rounded border-gray-300 text-neutral-600 focus:ring-neutral-500 w-4 h-4 cursor-pointer"
                                  />
                                )}
                              </td>
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  </div>
                  <div className="flex items-center justify-end"><span className="text-xs font-bold text-gray-700 mr-3">Order Amount :</span><span className="text-sm font-bold text-gray-900">{Math.round(totalAmt).toLocaleString('en-IN')}</span></div>
                </div>
              </div>
            )
          })()}
        </div>
      )}

      {activeTab === 'grn' && (
        <div className="space-y-4">
          {grnView === 'list' && (
            <>
              <div className="bg-white border border-gray-200 rounded-xl p-3 flex flex-wrap items-center justify-between gap-3 shadow-sm">
                <div className="flex flex-wrap items-center gap-3">
                  <div className="flex items-center gap-2"><DatePicker value={grnFilterDate} onChange={setGrnFilterDate} size="sm" /></div>
                  {/* <select value={grnFilterDeptId} onChange={e => setGrnFilterDeptId(e.target.value)} className="text-xs border border-gray-300 rounded-lg p-1.5 w-40">
                    <option value="ALL">All Department</option>
                    {departments.filter((d: any) => d.status !== 'INACTIVE' && d.status !== 0).map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
                  </select> */}
                  <select value={grnFilterSupplierId} onChange={e => setGrnFilterSupplierId(e.target.value)} className="text-xs border border-gray-300 rounded-lg p-1.5 w-40">
                    <option value="ALL">All Supplier</option>
                    {suppliers.filter((s: any) => s.status !== 'INACTIVE' && s.status !== 0).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
                  </select>
                </div>
                <button onClick={() => { setGrnView('create'); setGrnLines([]); const ph = departments.find(d => d.name.toUpperCase().includes('PHARMACY')); setGrnDeptId(ph?.id || departments[0]?.id || ''); setGrnSupplierId(''); setGrnInvoiceNumber(''); setGrnInvoiceType(''); setGrnInvoiceDate(today) }} className="px-5 py-2 bg-neutral-600 hover:bg-neutral-700 text-white text-xs font-bold rounded-lg shadow-sm">+ ADD GRN</button>
              </div>
              <div className="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
                {isLoadingGRN ? <p className="text-sm text-gray-500 p-6 text-center">Loading...</p> : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-xs text-left min-w-[900px]">
                      <thead><tr className="bg-gray-50 border-b border-gray-200 text-[10px] text-gray-600 uppercase tracking-wider font-semibold">
                        <th className="px-3 py-2.5 w-12">S.NO</th><th className="px-3 py-2.5">INVOICE NO</th><th className="px-3 py-2.5">INVOICE TYPE</th><th className="px-3 py-2.5">INVOICE DATE</th><th className="px-3 py-2.5">SUPPLIER</th><th className="px-3 py-2.5">RECEIPT NO</th><th className="px-3 py-2.5">RECEIPT DATE</th><th className="px-3 py-2.5">DEPARTMENT</th><th className="px-3 py-2.5 text-right">INVOICE VALUE</th><th className="px-3 py-2.5 text-center w-16">ACTION</th>
                      </tr></thead>
                      <tbody className="divide-y divide-gray-100">
                        {grnReceipts.map((r, idx) => {
                          const supp = suppliers.find(s => s.id === r.supplierId); const dept = departments.find(d => d.id === r.departmentId); const val = r.lines.reduce((s, l) => s + (l.quantity * l.purchaseRate), 0); return (
                            <tr key={r.id} className="hover:bg-gray-50 transition-colors">
                              <td className="px-3 py-3 text-gray-500">{idx + 1}</td>
                              <td className="px-3 py-3 font-medium text-gray-800">{r.invoiceNumber || '—'}</td>
                              <td className="px-3 py-3 text-gray-600 font-semibold">{r.notes || '—'}</td>
                              <td className="px-3 py-3 text-gray-500">{formatToIndianDate(r.invoiceDate)}</td>
                              <td className="px-3 py-3 text-gray-700">{supp?.name || '—'}</td>
                              <td className="px-3 py-3 font-mono font-semibold text-gray-900">{r.sequenceNumber || `GRN-${r.id.slice(0, 5)}`}</td>
                              <td className="px-3 py-3 text-gray-500">{formatToIndianDate(r.receiptDate)}</td>
                              <td className="px-3 py-3 text-gray-700">{dept?.name || 'PHARMACY'}</td>
                              <td className="px-3 py-3 text-right font-medium text-gray-800">₹{Math.round(val).toLocaleString('en-IN')}</td>
                              <td className="px-3 py-3 text-center"><button onClick={() => { setSelectedGRN(r); setGrnView('detail') }} className="p-1 text-gray-400 hover:text-neutral-600"><ChevronRight size={16} /></button></td>
                            </tr>
                          )
                        })}
                        {grnReceipts.length === 0 && <tr><td colSpan={10} className="px-4 py-10 text-center text-gray-400">No receipts found</td></tr>}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </>
          )}

          {grnView === 'create' && (
            <div className="bg-white border border-gray-200 rounded-xl p-5 space-y-4 shadow-sm">
              <button onClick={() => setGrnView('list')} className="px-4 py-2 border border-gray-300 rounded-full text-xs font-bold text-gray-600 hover:bg-gray-50 shadow-sm">&lt; GRN</button>
              <div className="border-t border-gray-100 pt-3">
                <h2 className="text-lg font-bold text-gray-800 mb-4">Create GRN</h2>
                <div className="flex flex-wrap items-center gap-6 mb-4 bg-gray-50 p-3 rounded-lg border border-gray-200">
                  <div className="flex items-center gap-2"><span className="text-xs font-bold text-gray-700">Department :</span><span className="text-xs font-semibold text-gray-800">{departments.find(d => d.id === grnDeptId)?.name.toUpperCase() || 'PHARMACY'}</span></div>
                  <div className="flex items-center gap-2"><span className="text-xs font-bold text-gray-700">Supplier :<span className="text-red-500">*</span></span>
                    <select value={grnSupplierId} onChange={e => setGrnSupplierId(e.target.value)} className="px-2 py-1 border border-gray-300 rounded text-xs bg-white font-semibold">
                      <option value="">Select Supplier</option>
                      {suppliers.filter((s: any) => s.status !== 'INACTIVE' && s.status !== 0).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
                    </select>
                  </div>
                </div>
                <div className="border border-gray-200 rounded-lg shadow-sm mb-3 bg-white overflow-x-auto md:overflow-x-visible pb-32 md:pb-0">
                  <table className="w-full text-xs text-left min-w-[900px]">
                    <thead><tr className="bg-gray-50 border-b border-gray-200 text-[10px] font-bold text-gray-600 uppercase tracking-wider">
                      <th className="px-2 py-2.5 w-10">S.NO</th><th className="px-2 py-2.5 w-40">ITEM</th><th className="px-2 py-2.5 w-24">BATCH NO *</th><th className="px-2 py-2.5 w-36">EXPIRY DATE *</th><th className="px-2 py-2.5 w-20">MRP *</th><th className="px-2 py-2.5 w-20">P.PRICE *</th><th className="px-2 py-2.5 w-16">QTY *</th><th className="px-2 py-2.5 w-16">FREE QTY</th><th className="px-2 py-2.5 w-32">TAX % *</th><th className="px-2 py-2.5 w-24 text-right">SUB TOTAL</th><th className="px-2 py-2.5 w-8"></th>
                    </tr></thead>
                    <tbody className="divide-y divide-gray-100">
                      {grnLines.map((line, idx) => (
                        <tr key={idx} className="align-middle hover:bg-gray-50/50">
                          <td className="px-2 py-2 text-gray-500">{idx + 1}</td>
                          <td className="px-2 py-2 font-medium text-gray-800 max-w-[130px] truncate">{line.name}</td>
                          <td className="px-2 py-2">
                            <input
                              value={line.batchNumber}
                              onChange={e => {
                                const val = e.target.value
                                setGrnLines(p => p.map((l, i) => i === idx ? { ...l, batchNumber: val } : l))
                                setAdjustTempStocks(prev => {
                                  const next = { ...prev }
                                  delete next[idx]
                                  return next
                                })
                              }}
                              onBlur={e => checkTempStockForLine(idx, line.itemId, e.target.value)}
                              className="px-1 py-1 border border-gray-300 rounded w-full text-xs"
                            />
                            {adjustTempStocks[idx] && (
                              <div className="mt-1 bg-amber-50 border border-amber-200 px-2 py-0.5 rounded text-[10px] text-amber-800 font-semibold max-w-[120px] text-center">
                                <span>Temp: {adjustTempStocks[idx].qty}</span>
                              </div>
                            )}
                          </td>
                          <td className="px-2 py-2 w-36">
                            <input
                              type="text"
                              spellCheck={false}
                              autoComplete="off"
                              value={grnExpiryRawInputs[idx] ?? (line.expiryDate ? (() => { const p = line.expiryDate.split('-'); return p.length === 3 ? `${p[2]}/${p[1]}/${p[0]}` : '' })() : 'dd/mm/yyyy')}
                              onFocus={e => {
                                if (e.target.value === 'dd/mm/yyyy') {
                                  const el = e.target
                                  setTimeout(() => el.setSelectionRange(0, 0), 0)
                                }
                              }}
                              onKeyDown={e => handleDateMaskKeyDown(e, e.currentTarget.value, (val) => {
                                setGrnExpiryRawInputs(prev => ({ ...prev, [idx]: val }))
                                const { isValid, iso } = parseMaskedDate(val)
                                setGrnLines(p => p.map((l, i) => i === idx ? { ...l, expiryDate: isValid ? iso : '' } : l))
                              })}
                              onBlur={e => {
                                const val = e.target.value
                                if (val === 'dd/mm/yyyy') {
                                  setGrnLines(p => p.map((l, i) => i === idx ? { ...l, expiryDate: '' } : l))
                                  setGrnExpiryRawInputs(prev => ({ ...prev, [idx]: 'dd/mm/yyyy' }))
                                  return
                                }
                                const { isValid, iso } = parseMaskedDate(val)
                                if (!isValid) {
                                  toast({ title: 'Invalid date. Enter a valid dd/mm/yyyy', variant: 'destructive' })
                                  setGrnLines(p => p.map((l, i) => i === idx ? { ...l, expiryDate: '' } : l))
                                  setGrnExpiryRawInputs(prev => ({ ...prev, [idx]: 'dd/mm/yyyy' }))
                                } else if (iso < today) {
                                  toast({ title: 'Expiry Date cannot be in the past', variant: 'destructive' })
                                  setGrnLines(p => p.map((l, i) => i === idx ? { ...l, expiryDate: '' } : l))
                                  setGrnExpiryRawInputs(prev => ({ ...prev, [idx]: 'dd/mm/yyyy' }))
                                } else {
                                  setGrnLines(p => p.map((l, i) => i === idx ? { ...l, expiryDate: iso } : l))
                                }
                              }}
                              className="px-1 py-1 border border-gray-300 rounded w-full text-xs"
                            />
                          </td>
                          <td className="px-2 py-2">
                            <div className="flex items-center gap-1">
                              <span className="text-gray-500 font-bold">₹</span>
                              <input type="number" step="1" min={0} value={line.mrp || ''} onChange={e => {
                                const val = parseInt(e.target.value) || 0;
                                setGrnLines(p => p.map((l, i) => i === idx ? { ...l, mrp: val } : l));
                              }} className="px-1 py-1 border border-gray-300 rounded w-full text-xs" />
                            </div>
                          </td>
                          <td className="px-2 py-2">
                            <div className="flex items-center gap-1">
                              <span className="text-gray-500 font-bold">₹</span>
                              <input type="number" step="1" min={0} value={line.pPrice || ''} onChange={e => {
                                const val = parseInt(e.target.value) || 0;
                                setGrnLines(p => p.map((l, i) => i === idx ? { ...l, pPrice: val } : l));
                              }} className="px-1 py-1 border border-gray-300 rounded w-full text-xs" />
                            </div>
                          </td>
                          <td className="px-2 py-2"><div className="flex items-center gap-0.5"><input type="number" min={1} value={line.quantity} onChange={e => {
                            const val = parseInt(e.target.value);
                            const cleanVal = isNaN(val) ? 0 : Math.max(0, val);
                            setGrnLines(p => p.map((l, i) => i === idx ? { ...l, quantity: cleanVal } : l));
                          }} className="px-1 py-1 border border-gray-300 rounded w-12 text-xs" /><span className="text-[9px] text-gray-400">{line.unit}</span></div></td>
                          <td className="px-2 py-2"><input type="number" min={0} value={line.freeQty || ''} onChange={e => {
                            const val = parseInt(e.target.value);
                            const cleanVal = isNaN(val) ? 0 : Math.max(0, val);
                            setGrnLines(p => p.map((l, i) => i === idx ? { ...l, freeQty: cleanVal } : l));
                          }} className="px-1 py-1 border border-gray-300 rounded w-full text-xs" /></td>
                          <td className="px-2 py-2">
                            <select
                              value={line.taxPct}
                              onChange={e => {
                                const val = parseFloat(e.target.value);
                                const cleanVal = isNaN(val) ? 0 : val;
                                setGrnLines(p => p.map((l, i) => i === idx ? { ...l, taxPct: cleanVal } : l));
                              }}
                              className="px-1 py-1 border border-gray-300 rounded w-full text-xs bg-white"
                            >
                              <option value="0">0%</option>
                              {taxes.map(t => (
                                <option key={t.id} value={t.rate}>
                                  {t.name} ({t.rate}%)
                                </option>
                              ))}
                            </select>
                          </td>
                          <td className="px-2 py-2 text-right font-semibold text-gray-800">₹{Math.round(line.pPrice * line.quantity).toLocaleString('en-IN')}</td>
                          <td className="px-2 py-2 text-center">
                            <button
                              onClick={() => {
                                setGrnLines(p => p.filter((_, i) => i !== idx))
                                setAdjustTempStocks(prev => {
                                  const next: Record<number, { qty: number; active: boolean }> = {}
                                  Object.keys(prev).forEach(key => {
                                    const i = parseInt(key)
                                    if (i < idx) {
                                      next[i] = prev[i]
                                    } else if (i > idx) {
                                      next[i - 1] = prev[i]
                                    }
                                  })
                                  return next
                                })
                              }}
                              className="text-gray-400 hover:text-red-600"
                            >
                              <X size={14} />
                            </button>
                          </td>
                        </tr>
                      ))}
                      <tr className="align-middle bg-gray-50/30">
                        <td className="px-2 py-2 text-gray-400">—</td>
                        <td className="px-2 py-2 relative overflow-visible" colSpan={8}>
                          <MedicineSearchInput clearOnSelect onSelect={async item => {
                            let autofilledBatch = ''
                            let autofilledExpiry = ''
                            let autofilledMrp = 0
                            let autofilledPPrice = 0
                            let autofilledTax = item.taxRate || 0

                            try {
                              const temps = await tempStockApi.getByItem(item.id)
                              const activeTemp = temps.find(t => t.quantity > 0)
                              if (activeTemp) {
                                autofilledBatch = activeTemp.batchNumber || ''
                                autofilledExpiry = activeTemp.expiryDate || ''
                                autofilledMrp = activeTemp.mrp || 0
                                autofilledPPrice = activeTemp.purchaseRate || 0
                                if (activeTemp.taxRate !== undefined && activeTemp.taxRate !== null) {
                                  autofilledTax = activeTemp.taxRate
                                }
                              }
                            } catch (err) {
                              console.error('Failed to auto-fetch temporary stock', err)
                            }

                            setGrnLines(p => {
                              const next = [...p, { 
                                itemId: item.id, 
                                name: item.name, 
                                batchNumber: autofilledBatch, 
                                expiryDate: autofilledExpiry, 
                                mrp: autofilledMrp, 
                                pPrice: autofilledPPrice, 
                                quantity: 1, 
                                unit: guessUnitFromName(item.name), 
                                freeQty: 0, 
                                taxPct: autofilledTax 
                              }]
                              if (autofilledBatch) {
                                setTimeout(() => {
                                  checkTempStockForLine(next.length - 1, item.id, autofilledBatch)
                                }, 0)
                              }
                              return next
                            })
                          }} placeholder="Enter the Item Name" />
                        </td>
                        <td className="px-2 py-2" colSpan={2}></td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                {/* Invoice details */}
                <div className="grid grid-cols-2 gap-4 mb-3 bg-gray-50 p-3 rounded-lg border border-gray-200">
                  <div className="flex items-center gap-2"><span className="text-xs font-bold text-gray-600">Invoice No : <span className="text-red-500">*</span></span><input value={grnInvoiceNumber} onChange={e => setGrnInvoiceNumber(e.target.value)} className="px-2 py-1 border border-gray-300 rounded text-xs w-40 bg-white" /></div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-bold text-gray-600">Invoice Date : <span className="text-red-500">*</span></span>
                    <input
                      type="text"
                      spellCheck={false}
                      autoComplete="off"
                      value={grnInvoiceDateRaw || 'dd/mm/yyyy'}
                      onFocus={e => {
                        if (e.target.value === 'dd/mm/yyyy') {
                          const el = e.target
                          setTimeout(() => el.setSelectionRange(0, 0), 0)
                        }
                      }}
                      onKeyDown={e => handleDateMaskKeyDown(e, e.currentTarget.value, (val) => {
                        setGrnInvoiceDateRaw(val)
                        const { isValid, iso } = parseMaskedDate(val)
                        if (isValid) setGrnInvoiceDate(iso)
                        else setGrnInvoiceDate('')
                      })}
                      onBlur={e => {
                        const val = e.target.value
                        if (val === 'dd/mm/yyyy') {
                          const tp = today.split('-')
                          const defaultVal = `${tp[2]}/${tp[1]}/${tp[0]}`
                          setGrnInvoiceDateRaw(defaultVal)
                          setGrnInvoiceDate(today)
                          return
                        }
                        const { isValid, iso } = parseMaskedDate(val)
                        if (!isValid) {
                          toast({ title: 'Invalid date. Enter a valid dd/mm/yyyy', variant: 'destructive' })
                          const tp = today.split('-')
                          const defaultVal = `${tp[2]}/${tp[1]}/${tp[0]}`
                          setGrnInvoiceDateRaw(defaultVal)
                          setGrnInvoiceDate(today)
                        } else if (iso > today) {
                          toast({ title: 'Invoice Date cannot be in the future', variant: 'destructive' })
                          const tp = today.split('-')
                          const defaultVal = `${tp[2]}/${tp[1]}/${tp[0]}`
                          setGrnInvoiceDateRaw(defaultVal)
                          setGrnInvoiceDate(today)
                        } else {
                          setGrnInvoiceDate(iso)
                        }
                      }}
                      className="px-2 py-1 border border-gray-300 rounded text-xs w-36 bg-white"
                    />
                  </div>
                  <div className="flex items-center gap-2"><span className="text-xs font-bold text-gray-600">Invoice Type : <span className="text-red-500">*</span></span>
                    <select value={grnInvoiceType} onChange={e => setGrnInvoiceType(e.target.value)} className="px-2 py-1 border border-gray-300 rounded text-xs bg-white">
                      <option value="">Select Invoice Type</option>
                      <option value="CASH">CASH</option>
                      <option value="CREDIT">CREDIT</option>
                    </select>
                  </div>
                  <div className="flex items-center gap-2"><span className="text-xs font-bold text-gray-600">Invoice Amount :</span><span className="text-sm font-bold text-gray-900">₹{Math.round(grnTotal).toLocaleString('en-IN')}</span></div>
                </div>
                <div className="border-t border-dashed border-gray-200 pt-4 flex justify-end gap-2">
                  <button onClick={() => setGrnView('list')} className="px-5 py-2 bg-gray-500 hover:bg-gray-600 text-white text-[11px] font-bold rounded uppercase">CANCEL</button>
                  <button onClick={() => {
                    if (!grnSupplierId) { toast({ title: 'Supplier is mandatory', variant: 'destructive' }); return }
                    if (!grnInvoiceNumber.trim()) { toast({ title: 'Invoice No is mandatory', variant: 'destructive' }); return }
                    if (!grnInvoiceDate) { toast({ title: 'Invoice Date is mandatory', variant: 'destructive' }); return }
                    if (!grnInvoiceType) { toast({ title: 'Invoice Type is mandatory', variant: 'destructive' }); return }
                    const validLines = grnLines.filter(l => l.itemId)
                    if (validLines.length === 0) { toast({ title: 'Add at least one item', variant: 'destructive' }); return }
                    if (validLines.some(l => !l.batchNumber || !l.batchNumber.trim())) { toast({ title: 'Batch Number is mandatory for all items', variant: 'destructive' }); return }
                    if (validLines.some(l => !l.expiryDate)) { toast({ title: 'Expiry Date is mandatory for all items', variant: 'destructive' }); return }
                    if (validLines.some(l => l.expiryDate && l.expiryDate < today)) { toast({ title: 'Expiry Date cannot be in the past', variant: 'destructive' }); return }
                    if (validLines.some(l => !l.mrp || l.mrp <= 0)) { toast({ title: 'MRP must be greater than zero', variant: 'destructive' }); return }
                    if (validLines.some(l => !l.pPrice || l.pPrice <= 0)) { toast({ title: 'Purchase Price (P.PRICE) must be greater than zero', variant: 'destructive' }); return }
                    if (validLines.some(l => !l.quantity || l.quantity <= 0)) { toast({ title: 'Quantity must be greater than zero for all items', variant: 'destructive' }); return }
                    if (validLines.some(l => l.taxPct === undefined || l.taxPct === null || isNaN(l.taxPct) || l.taxPct < 0)) { toast({ title: 'Tax % is mandatory and cannot be negative', variant: 'destructive' }); return }
                    
                    const hasTempStocks = Object.values(adjustTempStocks).some(x => x && x.qty > 0)
                    if (hasTempStocks) {
                      setShowConfirmModal(true)
                    } else {
                      receiveGoodsMutation.mutate()
                    }
                  }} disabled={receiveGoodsMutation.isPending} className="px-5 py-2 bg-neutral-600 hover:bg-neutral-700 text-white text-[11px] font-bold rounded uppercase">
                    {receiveGoodsMutation.isPending ? 'SAVING...' : 'SAVE GRN'}
                  </button>
                </div>
              </div>
            </div>
          )}

          {grnView === 'detail' && selectedGRN && (() => {
            const supp = suppliers.find(s => s.id === selectedGRN.supplierId); const dept = departments.find(d => d.id === selectedGRN.departmentId); const total = selectedGRN.lines.reduce((s: number, l: any) => s + (l.quantity * l.purchaseRate), 0); return (
              <div className="bg-white border border-gray-200 rounded-xl p-3 space-y-3 shadow-sm">
                <button onClick={() => { setGrnView('list'); setSelectedGRN(null) }} className="px-4 py-1.5 border border-gray-300 rounded-full text-[10px] font-bold text-gray-600 hover:bg-gray-50 shadow-sm">&lt; GRN</button>
                <div className="border-t border-gray-100 pt-2">
                  <div className="flex flex-wrap items-center justify-between mb-3">
                    <h2 className="text-sm font-bold text-gray-800">Receipt No : <span className="text-neutral-600">{selectedGRN.sequenceNumber || `GRN-${selectedGRN.id.slice(0, 5)}`}</span></h2>
                    <span className="text-[11px] font-bold text-gray-600">Request Date : <span className="font-extrabold text-gray-900">{formatToIndianDate(selectedGRN.receiptDate)}</span></span>
                  </div>
                  <div className="flex flex-wrap items-center gap-6 mb-3 bg-gray-50 p-2.5 rounded-lg border border-gray-200">
                    <div><span className="text-xs font-bold text-gray-600">Department : </span> <span className="text-xs font-semibold text-gray-900 ml-2">{dept?.name?.toUpperCase() || 'PHARMACY'}</span></div>
                    <div><span className="text-xs font-bold text-gray-600">Supplier : </span> <span className="text-xs font-semibold text-gray-900 ml-2">{supp?.name?.toUpperCase() || '—'}</span></div>
                  </div>
                  <div className="overflow-x-auto">
                    <table className="w-full text-xs text-left border border-gray-200 rounded-lg overflow-hidden mb-4 min-w-[700px]">
                      <thead><tr className="bg-gray-50 border-b border-gray-200 text-[10px] font-bold text-gray-600 uppercase">
                        <th className="px-2 py-2.5 w-12">S.NO.</th><th className="px-2 py-2.5">ITEM</th><th className="px-2 py-2.5 w-24">BATCH NO</th><th className="px-2 py-2.5 w-24">EXPIRY DATE</th><th className="px-2 py-2.5 w-16">MRP</th><th className="px-2 py-2.5 w-16">P.PRICE</th><th className="px-2 py-2.5 w-24 text-center">QUANTITY</th><th className="px-2 py-2.5 w-24 text-right">SUB TOTAL</th>
                      </tr></thead>
                      <tbody className="divide-y divide-gray-100">
                        {selectedGRN.lines.map((l: any, idx: number) => (
                          <tr key={l.id} className="hover:bg-gray-50">
                            <td className="px-2 py-2 text-gray-500">{idx + 1}</td>
                            <td className="px-2 py-2 font-medium text-gray-800">
                              <ItemNameLabel itemId={l.itemId} />
                            </td>
                            <td className="px-2 py-2 text-gray-600">{l.batchNumber || '—'}</td>
                            <td className="px-2 py-2 text-gray-600">{formatToIndianDate(l.expiryDate)}</td>
                            <td className="px-2 py-2">₹{Math.round(l.maximumRetailPrice ?? 0).toLocaleString('en-IN')}</td>
                            <td className="px-2 py-2">₹{Math.round(l.purchaseRate ?? 0).toLocaleString('en-IN')}</td>
                            <td className="px-2 py-2 text-center">{l.quantity}</td>
                            <td className="px-2 py-2 text-right font-semibold">₹{Math.round(l.quantity * l.purchaseRate).toLocaleString('en-IN')}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <div className="grid grid-cols-2 gap-4 bg-gray-50 p-3 rounded-lg border border-gray-200">
                    <div className="space-y-1">
                      <div className="flex gap-4"><span className="text-xs font-bold text-gray-600 w-24">Invoice No</span><span className="text-xs text-gray-800">{selectedGRN.invoiceNumber || '—'}</span></div>
                      <div className="flex gap-4"><span className="text-xs font-bold text-gray-600 w-24">Invoice Type</span><span className="text-xs text-gray-800 font-semibold text-neutral-700">{selectedGRN.notes || '—'}</span></div>
                      <div className="flex gap-4"><span className="text-xs font-bold text-gray-600 w-24">Invoice Date</span><span className="text-xs text-gray-800">{formatToIndianDate(selectedGRN.invoiceDate)}</span></div>
                    </div>
                    <div className="space-y-1 text-right">
                      <div className="flex items-center justify-end"><span className="text-xs text-gray-600 mr-3">Total</span><span className="text-xs font-bold text-gray-900">₹{Math.round(total).toLocaleString('en-IN')}</span></div>
                      <div className="flex items-center justify-end"><span className="text-xs font-bold text-gray-700 mr-3">Bill Amount</span><span className="text-sm font-extrabold text-gray-900">₹{Math.round(total).toLocaleString('en-IN')}</span></div>
                    </div>
                  </div>
                </div>
              </div>
            )
          })()}
        </div>
      )}

      {/* -------------------------------------------------------------
          TAB 4 CONTENT: PURCHASE RETURN
          ------------------------------------------------------------- */}
      {activeTab === 'return' && (
        <div className="space-y-4">
          <div className="bg-white border border-gray-200 rounded-xl p-4 flex flex-col md:flex-row items-center justify-between gap-4 shadow-sm">
            <div className="flex flex-wrap items-center gap-3">
              <DatePicker value={returnFilterDate} onChange={setReturnFilterDate} size="sm" />
            </div>
            <button
              onClick={() => setIsReturnModalOpen(true)}
              className="w-full md:w-auto px-5 py-2 bg-neutral-600 hover:bg-neutral-700 text-white text-xs font-bold rounded-lg shadow-sm transition-all"
            >
              + ADD PURCHASE RETURN
            </button>
          </div>

          <div className="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
            {isLoadingReturns ? (
              <p className="text-sm text-gray-500 p-6 text-center">Loading returns...</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm text-left">
                  <thead>
                    <tr className="bg-gray-50 border-b border-gray-200 text-xs text-gray-600 uppercase tracking-wider font-semibold">
                      <th className="px-6 py-3 w-16">S.NO</th>
                      <th className="px-6 py-3">Return No</th>
                      <th className="px-6 py-3">Date</th>
                      <th className="px-6 py-3">Supplier</th>
                      <th className="px-6 py-3">Department</th>
                      <th className="px-6 py-3">Notes</th>
                      <th className="px-6 py-3 text-center w-20"></th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {returns.map((ret, idx) => {
                      const supp = suppliers.find(s => s.id === ret.supplierId)
                      const dept = departments.find(d => d.id === ret.departmentId)
                      return (
                        <tr key={ret.id} className="hover:bg-gray-50 transition-colors">
                          <td className="px-6 py-4 text-gray-500 font-medium">{idx + 1}</td>
                          <td className="px-6 py-4 font-mono font-semibold text-gray-900">{ret.sequenceNumber || `RET-${ret.id.slice(0, 5)}`}</td>
                          <td className="px-6 py-4 text-gray-500">{formatToIndianDate(ret.returnDate)}</td>
                          <td className="px-6 py-4 text-gray-700 font-medium">{supp ? supp.name : 'Direct / Other'}</td>
                          <td className="px-6 py-4 text-gray-700 font-medium">{dept ? dept.name : 'Store'}</td>
                          <td className="px-6 py-4 text-gray-600 truncate max-w-xs">{ret.notes || '—'}</td>
                          <td className="px-6 py-4 text-center">
                            <button
                              onClick={() => setSelectedReturnDetails(ret)}
                              className="p-1 text-gray-400 hover:text-neutral-600 transition-colors"
                              title="View details"
                            >
                              <ChevronRight size={18} />
                            </button>
                          </td>
                        </tr>
                      )
                    })}
                    {returns.length === 0 && (
                      <tr>
                        <td colSpan={7} className="px-6 py-12 text-center text-gray-400 text-sm">
                          No purchase returns found for this date
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}

      {/* -------------------------------------------------------------
          MODAL: ADD PURCHASE RETURN
          ------------------------------------------------------------- */}
      {isReturnModalOpen && (
        <div 
          className="fixed inset-0 z-50 flex items-center justify-center p-2 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200"
          style={{ marginTop: 0 }}
        >
          <div className="bg-white rounded-xl shadow-2xl max-w-2xl w-full overflow-hidden border border-gray-100 animate-in fade-in zoom-in-95 duration-150">
            <div className="px-6 py-4 bg-gray-50 border-b border-gray-100 flex items-center justify-between">
              <h3 className="font-bold text-gray-800 text-base">Add Purchase Return</h3>
              <button onClick={closeReturnModal} className="text-gray-400 hover:text-gray-600">
                <X size={20} />
              </button>
            </div>
            <div className="p-6 space-y-4 max-h-[70vh] overflow-y-auto overflow-visible">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-gray-600 mb-1.5">Supplier *</label>
                  <select
                    value={returnSupplierId}
                    onChange={e => {
                      setReturnSupplierId(e.target.value)
                      setReturnLines([{ itemId: '', name: '', batchId: '', quantity: 0, maxQty: 1 }])
                    }}
                    className={inputCls}
                  >
                    <option value="">Select Supplier</option>
                    {suppliers.map(s => (
                      <option key={s.id} value={s.id}>{s.name}</option>
                    ))}
                  </select>
                </div>
                {/* <div>
                  <label className="block text-xs font-semibold text-gray-600 mb-1.5">Department *</label>
                  <select
                    value={returnDeptId}
                    onChange={e => setReturnDeptId(e.target.value)}
                    className={inputCls}
                  >
                    {departments.map(d => (
                      <option key={d.id} value={d.id}>{d.name}</option>
                    ))}
                  </select>
                </div> */}
                <div>
                  <label className="block text-xs font-semibold text-gray-600 mb-1.5">Notes</label>
                  <input
                    value={returnNotes}
                    onChange={e => setReturnNotes(e.target.value)}
                    placeholder="Reason for return"
                    className={inputCls}
                  />
                </div>
              </div>

              {/* Return Lines Table */}
              <div className="border border-gray-200 rounded-lg overflow-visible bg-white relative pb-32">
                <table className="w-full text-xs text-left">
                  <thead>
                    <tr className="bg-gray-50 border-b border-gray-200 text-gray-600 font-semibold">
                      <th className="px-3 py-2">Item *</th>
                      <th className="px-3 py-2 w-52">Batch *</th>
                      <th className="px-3 py-2 w-24">Return Qty *</th>
                      <th className="px-3 py-2 w-20">Stock Qty</th>
                      <th className="px-3 py-2 w-12 text-center"></th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {returnLines.map((line, i) => (
                      <tr key={i} className="align-middle hover:bg-gray-50/50 transition-colors">
                        <td className="px-3 py-2 relative overflow-visible min-w-[200px]">
                          <MedicineSearchInput
                            initialValue={line.name}
                            onSelect={item => updateReturnLine(i, 'itemId', item)}
                            placeholder="Search medicine…"
                          />
                        </td>
                        <td className="px-3 py-2">
                          <select
                            value={line.batchId}
                            disabled={!line.itemId || !returnSupplierId}
                            onChange={e => updateReturnLine(i, 'batchId', e.target.value)}
                            className={inputCls}
                          >
                            <option value="">Select Stock Batch</option>
                            {allBatches
                              .filter(b => b.itemId === line.itemId && b.departmentId === returnDeptId && b.supplierId === returnSupplierId && b.currentQuantity > 0)
                              .map(b => (
                                <option key={b.id} value={b.id}>
                                  Batch: {b.batchNumber || 'N/A'} (Stock: {b.currentQuantity})
                                </option>
                              ))}
                          </select>
                        </td>
                        <td className="px-3 py-2">
                          <input
                            type="number"
                            min={0}
                            max={line.maxQty}
                            disabled={!line.batchId}
                            value={line.quantity}
                            onChange={e => updateReturnLine(i, 'quantity', parseInt(e.target.value) || 0)}
                            className={inputCls}
                          />
                        </td>
                        <td className="px-3 py-2 text-gray-500 font-medium">
                          {line.batchId ? line.maxQty : '—'}
                        </td>
                        <td className="px-3 py-2 text-center">
                          {returnLines.length > 1 && (
                            <button
                              type="button"
                              onClick={() => setReturnLines(prev => prev.filter((_, idx) => idx !== i))}
                              className="text-red-500 hover:text-red-700 p-1 hover:bg-red-50 rounded-lg transition-colors"
                            >
                              <Trash2 size={16} />
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <button
                type="button"
                onClick={() => setReturnLines(prev => [...prev, { itemId: '', name: '', batchId: '', quantity: 0, maxQty: 1 }])}
                className="inline-flex items-center gap-1.5 text-xs text-neutral-600 hover:text-neutral-700 font-bold bg-gray-50 border border-gray-250 px-3 py-1.5 rounded-lg shadow-sm hover:bg-gray-100 transition-all"
              >
                <Plus size={14} /> Add Return Line
              </button>
            </div>
            <div className="px-6 py-4 bg-gray-50 border-t border-gray-100 flex justify-end gap-3">
              <button
                onClick={closeReturnModal}
                className="px-4 py-2 border border-gray-300 rounded-lg text-xs font-semibold text-gray-600 hover:bg-gray-100"
              >
                Cancel
              </button>
              <button
                onClick={handleCreateReturn}
                disabled={createReturnMutation.isPending || !returnSupplierId || !returnLines.some(l => l.batchId)}
                className="px-5 py-2 bg-neutral-600 text-white text-xs font-bold rounded-lg hover:bg-neutral-700 disabled:opacity-50"
              >
                {createReturnMutation.isPending ? 'Processing...' : 'Return Stock'}
              </button>
            </div>
          </div>
        </div>
      )}



      {/* -------------------------------------------------------------
          DETAILS MODAL: VIEW PURCHASE RETURN DETAILS
          ------------------------------------------------------------- */}
      {selectedReturnDetails && (
        <div 
          className="fixed inset-0 z-50 flex items-center justify-center p-2 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200"
          style={{ marginTop: 0 }}
        >
          <div className="bg-white rounded-xl shadow-2xl max-w-2xl w-full overflow-hidden border border-gray-100 animate-in fade-in zoom-in-95 duration-150">
            <div className="px-6 py-4 bg-gray-50 border-b border-gray-100 flex items-center justify-between">
              <h3 className="font-bold text-gray-800 text-base">Purchase Return Details</h3>
              <button onClick={() => setSelectedReturnDetails(null)} className="text-gray-400 hover:text-gray-600">
                <X size={20} />
              </button>
            </div>
            <div className="p-6 space-y-4 max-h-[75vh] overflow-y-auto">
              <div className="grid grid-cols-2 gap-4 border-b border-gray-100 pb-4">
                <div>
                  <span className="block text-[10px] uppercase font-bold text-gray-400">Return No</span>
                  <span className="font-mono text-sm font-semibold text-gray-900">{selectedReturnDetails.sequenceNumber || `RET-${selectedReturnDetails.id.slice(0, 5)}`}</span>
                </div>
                <div>
                  <span className="block text-[10px] uppercase font-bold text-gray-400">Date</span>
                  <span className="text-sm text-gray-700 font-medium">{formatToIndianDate(selectedReturnDetails.returnDate)}</span>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4 border-b border-gray-100 pb-4">
                <div>
                  <span className="block text-[10px] uppercase font-bold text-gray-400">Supplier</span>
                  <span className="text-sm text-gray-700 font-medium">
                    {suppliers.find(s => s.id === selectedReturnDetails.supplierId)?.name || 'Direct / Other'}
                  </span>
                </div>
                <div>
                  <span className="block text-[10px] uppercase font-bold text-gray-400">Department</span>
                  <span className="text-sm text-gray-700 font-medium">
                    {departments.find(d => d.id === selectedReturnDetails.departmentId)?.name || 'Main Store'}
                  </span>
                </div>
              </div>

              {/* Returned Items Table */}
              <div className="space-y-2">
                <span className="block text-[10px] uppercase font-bold text-gray-400">Returned Items</span>
                <div className="border border-gray-200 rounded-lg overflow-hidden">
                  <table className="w-full text-xs text-left">
                    <thead>
                      <tr className="bg-gray-50 border-b border-gray-200 text-[10px] font-bold text-gray-600 uppercase">
                        <th className="px-3 py-2 w-12">S.No.</th>
                        <th className="px-3 py-2">Item Name</th>
                        <th className="px-3 py-2 w-28">Batch No</th>
                        <th className="px-3 py-2 w-20 text-center">Qty</th>
                        <th className="px-3 py-2 w-24 text-right">P.Price</th>
                        <th className="px-3 py-2 w-28 text-right">Total</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100 bg-white">
                      {selectedReturnDetails.lines && selectedReturnDetails.lines.length > 0 ? (
                        selectedReturnDetails.lines.map((line, idx) => (
                          <ReturnLineRow
                            key={line.id || idx}
                            line={line}
                            index={idx}
                            inventoryBatches={inventoryBatches}
                          />
                        ))
                      ) : (
                        <tr>
                          <td colSpan={6} className="px-3 py-4 text-center text-gray-400">
                            No items found in this return
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </div>

              <div>
                <span className="block text-[10px] uppercase font-bold text-gray-400 mb-1">Notes / Reason</span>
                <p className="text-sm text-gray-600 bg-gray-50 rounded-lg p-3 border border-gray-100 min-h-16 whitespace-pre-wrap">
                  {selectedReturnDetails.notes || 'No notes provided.'}
                </p>
              </div>
            </div>
            <div className="px-6 py-4 bg-gray-50 border-t border-gray-100 flex justify-end">
              <button
                onClick={() => setSelectedReturnDetails(null)}
                className="px-5 py-2 bg-neutral-600 text-white text-xs font-semibold rounded-lg hover:bg-neutral-700 transition-colors shadow-sm"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
      {showConfirmModal && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-gray-900/60 backdrop-blur-sm animate-in fade-in duration-200" style={{ marginTop: 0 }}>
          <div className="bg-white rounded-xl shadow-2xl max-w-md w-full overflow-hidden border border-gray-100 animate-in fade-in zoom-in-95 duration-150">
            <div className="p-6">
              <div className="flex items-center gap-3 text-neutral-800 mb-3">
                <div className="p-2 bg-neutral-100 rounded-full">
                  <svg className="w-5 h-5 text-neutral-700" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                  </svg>
                </div>
                <h3 className="text-base font-bold text-neutral-900">Temporary Stock Detected</h3>
              </div>
              <p className="text-xs text-neutral-500 mb-4 leading-relaxed">
                The following items have pending temporary stock. Would you like to adjust (subtract) the temporary quantity from this incoming batch?
              </p>
              
              <div className="space-y-2.5 max-h-48 overflow-y-auto mb-5 border-t border-b border-gray-100 py-3">
                {grnLines.map((line, idx) => {
                  const adj = adjustTempStocks[idx]
                  if (!adj || adj.qty <= 0) return null
                  return (
                    <div key={idx} className="flex items-center justify-between bg-neutral-50 px-3 py-2 rounded-lg border border-neutral-200/50">
                      <div>
                        <div className="text-xs font-bold text-neutral-800 max-w-[200px] truncate">{line.name}</div>
                        <div className="text-[10px] text-neutral-400 font-mono mt-0.5">Batch: {line.batchNumber}</div>
                      </div>
                      <div className="flex items-center gap-3">
                        <span className="text-xs font-bold text-neutral-700">Temp Qty: {adj.qty}</span>
                        <input
                          type="checkbox"
                          checked={adj.active}
                          onChange={e => {
                            setAdjustTempStocks(prev => ({
                              ...prev,
                              [idx]: { ...prev[idx], active: e.target.checked }
                            }))
                          }}
                          className="rounded border-gray-300 text-neutral-900 focus:ring-neutral-900 w-4 h-4 cursor-pointer"
                        />
                      </div>
                    </div>
                  )
                })}
              </div>

              <div className="flex justify-end gap-2">
                <button
                  onClick={() => setShowConfirmModal(false)}
                  className="px-4 py-2 border border-neutral-300 hover:bg-neutral-50 text-neutral-700 text-xs font-bold rounded-lg transition-colors"
                >
                  CANCEL
                </button>
                <button
                  onClick={() => {
                    setShowConfirmModal(false)
                    receiveGoodsMutation.mutate()
                  }}
                  className="px-4 py-2 bg-neutral-900 hover:bg-black text-white text-xs font-bold rounded-lg transition-colors shadow-sm"
                >
                  CONFIRM & SAVE
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
