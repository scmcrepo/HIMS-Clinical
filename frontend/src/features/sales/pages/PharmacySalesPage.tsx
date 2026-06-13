import { useState, useEffect, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { salesApi, type CreateSaleCmd, type SaleLine } from '../../../services/sales/salesApi'
import { PatientSearchInput } from '../../../components/shared/PatientSearchInput'
import type { Patient } from '../../../types/patient'
import { formatDate } from '../../../lib/dateUtils'
import { cn } from '../../../lib/utils'
import { toast } from '../../../hooks/useToast'
import { MedicineSearchInput } from '../../../components/shared/MedicineSearchInput'
import { itemApi } from '../../../services/item/itemApi'
import { inventoryApi } from '../../../services/inventory/inventoryApi'
import { patientApi } from '../../../services/patient/patientApi'
import { encounterApi } from '../../../services/encounter/encounterApi'
import { departmentApi } from '../../../services/config/departmentApi'
import type { InventoryBatch, InventoryItem } from '../../../types/inventory'
import type { PharmacySaleResponse } from '../../../services/sales/salesApi'
import { prescriptionOrdersApi } from '../../../services/opip/opipApi'
import { useConsultants } from '../../../hooks/consultant/useConsultant'
import { ConsultantSearchInput } from '../../../components/shared/ConsultantSearchInput'
import { taxApi } from '../../../services/masters/masterApi'
import { User, Plus, FileText, Edit2, Trash2 } from 'lucide-react'
import { tempStockApi, type TempStockReq } from '../../../services/tempStock/tempStockApi'

const parseMaskedDate = (val: string): { isValid: boolean; iso: string } => {
  if (!val || val === 'dd/mm/yyyy') return { isValid: false, iso: '' }
  const parts = val.split('/')
  if (parts.length !== 3) return { isValid: false, iso: '' }
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

    if ((input.selectionEnd ?? 0) - pos === 10) {
      pos = 0
    }

    if (value[pos] === '/') {
      pos++
    }

    if (pos < 10) {
      const newValue = value.slice(0, pos) + e.key + value.slice(pos + 1)

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
        if (pos === 3) {
          const digit = parseInt(e.key, 10)
          if (digit >= 2 && digit <= 9) {
            const paddedMonth = '0' + e.key
            const newValue2 = newValue.slice(0, 3) + paddedMonth + newValue.slice(5)
            const mmVal = parseInt(paddedMonth, 10)
            if (mmVal > 12) return
            setValue(newValue2)
            setTimeout(() => {
              input.setSelectionRange(6, 6)
            }, 0)
            return
          }
        }
      }

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

const DEMO_DEPT_ID = '00000000-0000-0000-0000-000000000001'

interface TempStockRow {
  item: InventoryItem | null
  batchNumber: string
  expiryDate: string
  mrp: number | ''
  purchasePrice: number | ''
  quantity: number
  taxRate: number
}

export default function PharmacySalesPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const qc = useQueryClient()
  const [tab, setTab] = useState<'new' | 'drafts'>('new')
  const [editingDraftId, setEditingDraftId] = useState<string | null>(null)
  const [editingDraftSeq, setEditingDraftSeq] = useState<string | null>(null)
  const [loadingDraftId, setLoadingDraftId] = useState<string | null>(null)
  const [deletingDraft, setDeletingDraft] = useState<{ id: string; sequenceNumber: string } | null>(null)
  const [saleKey, setSaleKey] = useState(0)
  const [selectedPatient, setSelectedPatient] = useState<Patient | null>(null)
  const [walkinName, setWalkinName] = useState('')
  const [walkinPhone, setWalkinPhone] = useState('')
  const [walkinConsultant, setWalkinConsultant] = useState('')
  const [showWalkinModal, setShowWalkinModal] = useState(false)
  const [showTempStockModal, setShowTempStockModal] = useState(false)
  const [tempStockRows, setTempStockRows] = useState<TempStockRow[]>([
    { item: null, batchNumber: '', expiryDate: '', mrp: '', purchasePrice: '', quantity: 1, taxRate: 0 }
  ])
  const [tempExpiryRawInputs, setTempExpiryRawInputs] = useState<Record<number, string>>({})
  const [selectedDeptId, setSelectedDeptId] = useState<string>(DEMO_DEPT_ID)

  const addTempStockRow = () => {
    setTempStockRows(prev => [...prev, { item: null, batchNumber: '', expiryDate: '', mrp: '', purchasePrice: '', quantity: 1, taxRate: 0 }])
  }

  const removeTempStockRow = (index: number) => {
    setTempStockRows(prev => prev.filter((_, i) => i !== index))
    setTempExpiryRawInputs(prev => {
      const next: Record<number, string> = {}
      Object.keys(prev).forEach(kStr => {
        const k = parseInt(kStr, 10)
        if (k < index) {
          next[k] = prev[k]
        } else if (k > index) {
          next[k - 1] = prev[k]
        }
      })
      return next
    })
  }

  const updateTempStockRow = <K extends keyof TempStockRow>(index: number, key: K, value: TempStockRow[K]) => {
    setTempStockRows(prev => prev.map((row, i) => {
      if (i !== index) return row
      const updated = { ...row, [key]: value }
      if (key === 'item' && value) {
        const itemVal = value as InventoryItem
        updated.taxRate = itemVal.taxRate ?? 0
      }
      return updated
    }))
  }

  const handleSaveTempStock = async () => {
    const valid = tempStockRows.filter(r => r.item && r.quantity > 0 && r.mrp !== '' && r.purchasePrice !== '')
    if (valid.length === 0) {
      toast({ title: 'Validation Error', description: 'Please add at least one complete item row (Item, MRP, Purchase Price, Qty).', variant: 'destructive' })
      return
    }

    const payload: TempStockReq[] = valid.map(r => ({
      itemId: r.item!.id,
      departmentId: selectedDeptId,
      batchNumber: r.batchNumber || 'TEMP-' + Date.now(),
      quantity: r.quantity,
      purchaseRate: Number(r.purchasePrice),
      mrp: Number(r.mrp),
      sellingRate: Number(r.mrp),
      expiryDate: r.expiryDate || undefined,
      taxRate: Number(r.taxRate) || 0
    }))

    try {
      await tempStockApi.createBulk(payload)
      toast({ title: 'Temporary Stock Saved', variant: 'success' })
      setTempStockRows([{ item: null, batchNumber: '', expiryDate: '', mrp: '', purchasePrice: '', quantity: 1, taxRate: 0 }])
      setTempExpiryRawInputs({})
      setShowTempStockModal(false)
      qc.invalidateQueries({ queryKey: ['inventory'] })
      qc.invalidateQueries({ queryKey: ['sales'] })
    } catch (e: any) {
      toast({ title: 'Failed to save temporary stock', description: e.message, variant: 'destructive' })
    }
  }
  const [currentEncounterId, setCurrentEncounterId] = useState<string | null>(null)
  const [isEncounterInpatient, setIsEncounterInpatient] = useState<boolean | null>(null)
  const isInpatientContext = isEncounterInpatient !== null ? isEncounterInpatient : !!selectedPatient?.isInpatient
  const [activePayTab, setActivePayTab] = useState<'pay_now' | 'save_draft' | 'partial_payment' | 'add_to_bill'>('pay_now')
  const [isDraftSubmit, setIsDraftSubmit] = useState(false)
  const [paidAmount, setPaidAmount] = useState<number | ''>('')
  const [paymentMode, setPaymentMode] = useState<'Cash' | 'Card' | 'UPI'>('Cash')
  const [cardType, setCardType] = useState('')
  const [cardNumber, setCardNumber] = useState('')
  const [bankName, setBankName] = useState('')
  const [selectedConsultant, setSelectedConsultant] = useState('')
  const [consultantError, setConsultantError] = useState(false)
  const { data: consultants } = useConsultants()
  const selectedConsultantId = consultants?.find(
    c => `${c.salutation || ''} ${c.firstName} ${c.lastName}`.trim() === selectedConsultant
  )?.id || ''
  const [lines, setLines] = useState<(Omit<SaleLine, 'unitRate'> & { unitRate: string | number; itemName?: string; batches?: InventoryBatch[]; taxRate?: number })[]>([
    { inventoryBatchId: '', quantity: 1, unitRate: 0, taxRate: 0 }
  ])
  const [discountAmount, setDiscountAmount] = useState<number>(0)

  const { data: depts } = useQuery({
    queryKey: ['departments'],
    queryFn: () => departmentApi.getAll(),
  })

  const { data: taxes } = useQuery({
    queryKey: ['taxes'],
    queryFn: () => taxApi.getAll().then(res => res ?? []),
  })

  useEffect(() => {
    // Try to auto-select Pharmacy department if it exists and current is demo
    if (depts && selectedDeptId === DEMO_DEPT_ID) {
      const pharm = depts.find(d => d.name.toLowerCase().includes('pharmacy'))
      if (pharm) setSelectedDeptId(pharm.id)
    }
  }, [depts])

  useEffect(() => {
    if ((!selectedPatient || !isInpatientContext) && activePayTab === 'add_to_bill') {
      setActivePayTab('pay_now')
    }
  }, [selectedPatient, isInpatientContext, activePayTab])

  // Pre-populate from Prescription Orders if encounterId is in URL
  const encounterIdParam = searchParams.get('encounterId')
  const prescribedAtParam = searchParams.get('prescribedAt')
  const processedRef = useRef<string | null>(null)
  useEffect(() => {
    // Wait until selectedDeptId is resolved to the real department (not DEMO_DEPT_ID)
    if (encounterIdParam && depts && consultants && selectedDeptId !== DEMO_DEPT_ID) {
      const key = `${encounterIdParam}-${prescribedAtParam ?? ''}`
      if (processedRef.current === key) return
      processedRef.current = key
      setSearchParams({}) // Clear immediately to prevent URL pollution
      prescriptionOrdersApi.getForEncounter(encounterIdParam).then(orders => {
        if (orders.length > 0) {
          let order = orders[0]
          if (prescribedAtParam) {
            const matched = orders.find(o => o.prescribedAt === prescribedAtParam)
            if (matched) {
              order = matched
            }
          }

          if (order.patientId) {
            patientApi.getById(order.patientId).then(p => setSelectedPatient(p)).catch(() => { })
          }
          if (order.encounterId) {
            setCurrentEncounterId(order.encounterId)
          }
          if (order.consultantName) {
            setSelectedConsultant(order.consultantName)
            setConsultantError(false)
          }

          // Fetch batches for each prescribed drug and auto-fill lines
          Promise.all(order.items.map(async (item) => {
            let actualItemId = item.drugItemId
            console.log('[DISPENSE] Processing item:', { drugName: item.drugName, drugItemId: item.drugItemId, qty: item.qty })

            // If it was free-texted, try to resolve the ID by name
            if (!actualItemId && item.drugName) {
              console.log('[DISPENSE] No drugItemId, searching by name:', item.drugName)
              try {
                const searchResults = await itemApi.search(item.drugName)
                console.log('[DISPENSE] Item search results:', searchResults.length, searchResults.map(r => ({ id: r.id, name: r.name })))
                if (searchResults.length > 0) {
                  const searchName = item.drugName.trim().toLowerCase()
                  const match = searchResults.find(r => r.name.trim().toLowerCase() === searchName) || searchResults[0]
                  if (match) {
                    actualItemId = match.id
                    console.log('[DISPENSE] Resolved to item:', match.id, match.name)
                  }
                }
              } catch (e) { console.error('[DISPENSE] Item search failed:', e) }
            }

            if (!actualItemId) {
              console.log('[DISPENSE] FAILED: No actualItemId for', item.drugName)
              return { _outOfStock: true, itemName: item.drugName }
            }
            try {
              console.log('[DISPENSE] Fetching batches for itemId:', actualItemId, 'deptId:', selectedDeptId)
              const rawBatches = await inventoryApi.getAvailableBatches(actualItemId, selectedDeptId)
              console.log('[DISPENSE] Raw batches returned:', rawBatches.length, rawBatches.map(b => ({ id: b.id, qty: b.currentQuantity, expired: b.isExpired, expiry: b.expiryDate, sellingRate: b.sellingRate })))
              const availableBatches = rawBatches
                .filter(b => !b.isExpired && (!b.expiryDate || new Date(b.expiryDate) > new Date()))
              console.log('[DISPENSE] After filtering:', availableBatches.length)

              if (availableBatches.length > 0) {
                const batch = availableBatches[0]
                const invItem = await itemApi.getById(actualItemId)
                return {
                  inventoryBatchId: batch.id,
                  quantity: item.qty > 0 ? item.qty : 1,
                  unitRate: batch.sellingRate,
                  itemName: item.drugName,
                  batches: availableBatches,
                  taxRate: invItem.taxRate ?? 0,
                  _outOfStock: false
                }
              }
            } catch (err) { console.error('[DISPENSE] Error fetching batches/item:', err) }
            return { _outOfStock: true, itemName: item.drugName }
          })).then(resolvedLines => {
            const outOfStock = resolvedLines.filter(r => r && r._outOfStock).map(r => r!.itemName)
            const validLines = resolvedLines.filter(r => r && !r._outOfStock) as any[]

            if (validLines.length > 0) {
              setLines(validLines)
              if (outOfStock.length > 0) {
                toast({ title: 'Some items out of stock', description: `Skipped: ${outOfStock.join(', ')}`, variant: 'destructive' })
              }
            } else if (outOfStock.length > 0) {
              toast({ title: 'No stock available', description: `None of the prescribed items are in stock: ${outOfStock.join(', ')}`, variant: 'destructive' })
            }
          })
        }
      }).catch(err => {
        console.error("Failed to load prescription order", err)
      })
    }
  }, [encounterIdParam, depts, consultants, selectedDeptId])

  useEffect(() => {
    if (currentEncounterId) {
      encounterApi.getById(currentEncounterId)
        .then(enc => {
          setIsEncounterInpatient(enc.encounterType === 'INPATIENT')
        })
        .catch(err => {
          console.error("Failed to load encounter type", err)
          setIsEncounterInpatient(null)
        })
    } else {
      setIsEncounterInpatient(null)
    }
  }, [currentEncounterId])

  const { data: draftSales, isLoading: isLoadingDrafts } = useQuery({
    queryKey: ['sales', 'drafts', selectedDeptId],
    queryFn: () => salesApi.getDrafts(selectedDeptId),
    enabled: !!selectedDeptId && tab === 'drafts',
    refetchInterval: 30_000,
  })

  const deleteDraftMutation = useMutation({
    mutationFn: (id: string) => salesApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sales', 'drafts'] })
      toast({ title: 'Draft deleted', variant: 'success' })
      setDeletingDraft(null)
    },
    onError: (e: Error) => {
      toast({ title: 'Delete failed', description: e.message, variant: 'destructive' })
      setDeletingDraft(null)
    }
  })

  const createMutation = useMutation({
    mutationFn: (cmd: CreateSaleCmd) => salesApi.create(cmd),
    onSuccess: (_, variables) => {
      qc.invalidateQueries({ queryKey: ['sales'] })
      qc.invalidateQueries({ queryKey: ['inventory'] })
      toast({ title: variables.isDraft ? 'Draft sale saved' : 'Sale completed', variant: 'success' })
      resetForm()
      if (!variables.isDraft) {
        navigate('/sales/salesHistory')
      }
    },
    onError: (e: Error) => toast({ title: 'Sale failed', description: e.message, variant: 'destructive' }),
  })

  const resetForm = () => {
    setLines([{ inventoryBatchId: '', quantity: 1, unitRate: 0 }])
    setSelectedPatient(null)
    setWalkinName('')
    setWalkinPhone('')
    setWalkinConsultant('')
    setSelectedConsultant('')
    setConsultantError(false)
    setShowWalkinModal(false)
    setEditingDraftId(null)
    setEditingDraftSeq(null)
    setDiscountAmount(0)
    setSaleKey(k => k + 1)
    setCurrentEncounterId(null)
    setIsEncounterInpatient(null)
    setActivePayTab('pay_now')
    setIsDraftSubmit(false)
    setPaidAmount('')
    setPaymentMode('Cash')
    setCardType('')
    setCardNumber('')
    setBankName('')
  }

  const handleTabChange = (newTab: 'new' | 'drafts') => {
    setTab(newTab)
    if (newTab !== 'new') resetForm()
  }

  const loadDraft = async (draft: PharmacySaleResponse) => {
    try {
      setLoadingDraftId(draft.id)
      if (draft.patientId) {
        try {
          const patient = await patientApi.getById(draft.patientId)
          setSelectedPatient(patient)
        } catch (e) {
          setSelectedPatient({ id: draft.patientId, fullName: draft.patientName, patientNumber: '' } as any)
        }
        setSelectedConsultant(draft.consultantName ?? '')
        setConsultantError(false)
      } else {
        setSelectedPatient(null)
        setWalkinName(draft.customerName ?? '')
        setWalkinPhone(draft.customerPhone ?? '')
        setWalkinConsultant(draft.consultantName ?? '')
        setSelectedConsultant('')
        setConsultantError(false)
      }
      setActivePayTab('pay_now')
      setIsDraftSubmit(false)
      setPaymentMode((draft.paymentMode as any) || 'Cash')
      setCardType(draft.cardType ?? '')
      setCardNumber(draft.cardNumber ?? '')
      setBankName(draft.bankName ?? '')
      setCurrentEncounterId(draft.encounterId ?? null)

      const loadedLines = await Promise.all(draft.lines.map(async (line) => {
        const batch = await inventoryApi.getBatch(line.inventoryBatchId)
        const availableBatches = (await inventoryApi.getAvailableBatches(batch.itemId, draft.departmentId))
          .filter(b => !b.isExpired && (!b.expiryDate || new Date(b.expiryDate) > new Date()))
        return {
          inventoryBatchId: line.inventoryBatchId,
          quantity: line.quantity,
          unitRate: line.unitRate,
          itemName: batch.itemName,
          batches: availableBatches
        }
      }))

      setLines(loadedLines.length > 0 ? loadedLines : [{ inventoryBatchId: '', quantity: 1, unitRate: 0 }])
      setDiscountAmount(draft.discountAmount || 0)
      setEditingDraftId(draft.id)
      setEditingDraftSeq(draft.sequenceNumber || null)
      setTab('new')
    } catch (err: any) {
      toast({ title: 'Failed to load draft', description: err.message, variant: 'destructive' })
    } finally {
      setLoadingDraftId(null)
    }
  }


  const handleMedicineSelect = async (item: InventoryItem, index: number) => {
    try {
      const availableBatches = (await inventoryApi.getAvailableBatches(item.id, selectedDeptId))
        .filter(b => !b.isExpired && (!b.expiryDate || new Date(b.expiryDate) > new Date()))
      if (availableBatches.length === 0) {
        toast({ title: 'No stock available', description: `No active batches found for ${item.name} in this department`, variant: 'destructive' })
        return
      }

      setLines(prev => {
        // Check if this item already exists in another row
        const existingRowIdx = prev.findIndex((line, i) => i !== index && line.itemName === item.name)

        if (existingRowIdx !== -1) {
          // Item already has a row — update the existing row with all batches, remove the duplicate row
          toast({
            title: `${item.name} already added`,
            description: 'Select the desired batch from the existing row\'s dropdown.',
            variant: 'default'
          })
          const updated = prev
            .filter((_, i) => i !== index) // remove the current (duplicate) row
            .map((line, i) => {
              // After filtering, find the existing row by recalculating its position
              const originalIdx = i >= index ? i + 1 : i
              if (originalIdx === existingRowIdx) {
                const firstBatch = availableBatches[0]
                return {
                  ...line,
                  batches: availableBatches,
                  // Keep the current batch selection if it's still valid, else pick first
                  inventoryBatchId: availableBatches.find(b => b.id === line.inventoryBatchId)
                    ? line.inventoryBatchId
                    : firstBatch.id,
                  unitRate: availableBatches.find(b => b.id === line.inventoryBatchId)
                    ? line.unitRate
                    : firstBatch.sellingRate,
                  taxRate: item.taxRate ?? line.taxRate ?? 0
                }
              }
              return line
            })
          return updated.length > 0 ? updated : [{ inventoryBatchId: '', quantity: 1, unitRate: 0 }]
        }

        // No duplicate — add normally, auto-select first batch
        const batch = availableBatches[0]
        return prev.map((line, i) => {
          if (i !== index) return line
          return {
            ...line,
            itemName: item.name,
            inventoryBatchId: batch.id,
            unitRate: batch.sellingRate,
            batches: availableBatches,
            taxRate: item.taxRate ?? 0
          }
        })
      })
    } catch (err) {
      toast({ title: 'Error fetching batches', variant: 'destructive' })
    }
  }

  const handleBatchSelect = (batchId: string, index: number) => {
    setLines(prev => prev.map((line, i) => {
      if (i !== index) return line
      const selectedBatch = line.batches?.find(b => b.id === batchId)
      return {
        ...line,
        inventoryBatchId: batchId,
        unitRate: selectedBatch?.sellingRate ?? 0
      }
    }))
  }

  const handleSubmit = (submitAsDraft?: boolean) => {
    const finalIsDraft = submitAsDraft !== undefined ? submitAsDraft : isDraftSubmit;

    // Check if any line is partially filled but missing batch/qty
    const errors: string[] = []
    lines.forEach((l, i) => {
      const rowNum = i + 1
      if (l.itemName && !l.inventoryBatchId) {
        errors.push(`Row ${rowNum}: Please select a batch for ${l.itemName}`)
      } else if (l.inventoryBatchId && l.quantity <= 0) {
        errors.push(`Row ${rowNum}: Quantity must be greater than zero`)
      }
    })

    if (errors.length > 0) {
      toast({ title: 'Validation Error', description: errors[0], variant: 'destructive' })
      return
    }

    const validLines = lines.filter(l => l.inventoryBatchId.trim() && l.quantity > 0 && Number(l.unitRate) > 0)
    if (!validLines.length) {
      toast({ title: 'No items to sale', description: 'Search and select at least one medicine and its batch.', variant: 'destructive' })
      return
    }

    if (selectedPatient && !selectedConsultant) {
      setConsultantError(true)
      toast({ title: 'Validation Error', description: 'Please select a consultant doctor.', variant: 'destructive' })
      return
    }

    if (!selectedPatient && !finalIsDraft && !walkinName) {
      setShowWalkinModal(true)
      return
    }

    if (!finalIsDraft && activePayTab === 'partial_payment') {
      const lineTotal = lines.reduce((sum, l) => sum + Math.round(l.quantity * Number(l.unitRate || 0)), 0)
      const net = Math.max(0, Math.round(lineTotal) - discountAmount)
      if (paidAmount === '' || Number(paidAmount) <= 0) {
        toast({ title: 'Validation Error', description: 'Please enter a valid paid amount.', variant: 'destructive' })
        return
      }
      if (Number(paidAmount) > net) {
        toast({ title: 'Validation Error', description: `Paid amount cannot exceed the net amount of ₹${net}.`, variant: 'destructive' })
        return
      }
    }

    createMutation.mutate({
      id: editingDraftId ?? undefined,
      patientId: selectedPatient?.id,
      customerName: selectedPatient ? undefined : walkinName,
      customerPhone: selectedPatient ? undefined : walkinPhone,
      consultantName: selectedPatient ? selectedConsultant : walkinConsultant,
      encounterId: currentEncounterId ?? undefined,
      departmentId: selectedDeptId,
      isDraft: finalIsDraft,
      discountAmount: discountAmount,
      lines: validLines.map(l => ({
        inventoryBatchId: l.inventoryBatchId,
        quantity: l.quantity,
        unitRate: Number(l.unitRate)
      })),
      paymentMode: finalIsDraft ? undefined : (activePayTab === 'add_to_bill' ? 'Add to Bill' : paymentMode),
      cardType: (!finalIsDraft && paymentMode === 'Card' && activePayTab !== 'add_to_bill') ? cardType : undefined,
      cardNumber: (!finalIsDraft && paymentMode === 'Card' && activePayTab !== 'add_to_bill') ? cardNumber : undefined,
      bankName: (!finalIsDraft && paymentMode === 'Card' && activePayTab !== 'add_to_bill') ? bankName : undefined,
      paidAmount: finalIsDraft ? undefined : (activePayTab === 'add_to_bill' ? 0 : (activePayTab === 'partial_payment' && paidAmount !== '') ? Number(paidAmount) : undefined),
    })
  }

  const total = lines.reduce((sum, l) => sum + Math.round(l.quantity * Number(l.unitRate || 0)), 0)
  const hasItems = lines.some(l => l.inventoryBatchId && l.inventoryBatchId.trim() !== '')

  // Backward-extract tax from within the selling price (MRP is tax-inclusive)
  const subTaxSums: Record<string, number> = {}
  lines.forEach(l => {
    if (!l.inventoryBatchId || l.quantity <= 0) return
    const taxRate = l.taxRate || 0
    if (taxRate <= 0) return
    const lineAmount = l.quantity * Number(l.unitRate || 0)
    // Tax extracted backward: amount * taxRate / (100 + taxRate)
    const lineTax = lineAmount * taxRate / (100 + taxRate)

    const matchingTax = taxes?.find(t => Math.abs(t.rate - taxRate) < 0.01)
    if (matchingTax && matchingTax.categories && matchingTax.categories.length > 0) {
      const totalComponentsRate = matchingTax.categories.reduce((s, cat) => s + (cat.rate || 0), 0)
      matchingTax.categories.forEach(cat => {
        const catRate = cat.rate || 0
        const catName = cat.name.toUpperCase().trim()
        const share = totalComponentsRate > 0 ? lineTax * (catRate / totalComponentsRate) : 0
        subTaxSums[catName] = (subTaxSums[catName] || 0) + share
      })
    } else {
      subTaxSums['CGST'] = (subTaxSums['CGST'] || 0) + lineTax / 2
      subTaxSums['SGST'] = (subTaxSums['SGST'] || 0) + lineTax / 2
    }
  })

  const inputCls = "px-2.5 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-neutral-500"

  return (
    <div className="space-y-5 max-w-5xl">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4 border-b border-gray-100 pb-5">
        <div>
          <h2 className="text-2xl font-extrabold text-gray-900 tracking-tight">Pharmacy Sales</h2>
          {/* <div className="flex items-center gap-2 mt-1">
             <span className="flex h-2 w-2 rounded-full bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.6)]"></span>
             <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">
               Outlet: <span className="text-gray-900">{depts?.find(d => d.id === selectedDeptId)?.name || 'Loading…'}</span>
             </p>
          </div> */}
        </div>
        <div className="flex items-center gap-4">
          <button
            onClick={() => {
              setTempStockRows([{ item: null, batchNumber: '', expiryDate: '', mrp: '', purchasePrice: '', quantity: 1, taxRate: 0 }])
              setTempExpiryRawInputs({})
              setShowTempStockModal(true)
            }}
            className="px-4 py-2 text-sm font-bold text-white bg-neutral-600 hover:bg-neutral-700 rounded-lg shadow-sm transition-all duration-200"
          >
            Temp Stock
          </button>
          <div className="flex gap-1 bg-gray-100/80 p-1 rounded-xl backdrop-blur-sm" role="tablist">
            {(['new', 'drafts'] as const).map(t => (
              <button key={t} role="tab" aria-selected={tab === t} onClick={() => handleTabChange(t)}
                className={cn('px-5 py-2 text-sm font-bold rounded-lg transition-all duration-200',
                  tab === t ? 'bg-white text-neutral-600 shadow-sm' : 'text-gray-500 hover:text-gray-700 hover:bg-gray-200/50')}>
                {t === 'new' ? (
                  <span className="flex items-center gap-1.5">
                    <Plus size={14} />
                    New Sale
                  </span>
                ) : (
                  <span className="flex items-center gap-1.5">
                    <FileText size={14} />
                    Draft Sales
                  </span>
                )}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* New Sale */}
      {tab === 'new' && (
        <div key={saleKey} className="bg-white border border-gray-200 rounded-xl p-5 space-y-5">
          {editingDraftId && (
            <div className="flex items-center justify-between bg-amber-50 border border-amber-200 p-3 rounded-xl shadow-sm">
              <div className="flex items-center gap-2">
                <p className="text-sm font-bold text-amber-800 tracking-tight">Editing Draft Sale</p>
                <span className="text-xs font-mono text-amber-600 bg-amber-100 px-2 py-0.5 rounded">{editingDraftSeq || editingDraftId.slice(0, 8)}</span>
              </div>
              <button onClick={resetForm} className="text-xs font-semibold px-3 py-1.5 bg-white text-amber-700 hover:bg-amber-100 rounded-lg shadow-sm border border-amber-200 transition-colors">
                Cancel Edit
              </button>
            </div>
          )}
          <div className="flex flex-col md:flex-row gap-5 items-start">
            <div className="w-full max-w-sm">
              <label className="block text-[10px] uppercase font-bold text-gray-400 mb-1.5 tracking-widest">Registered Patient</label>
              <PatientSearchInput
                selectedPatient={selectedPatient}
                onSelect={setSelectedPatient}
                placeholder="Search patient name or phone…"
                className="shadow-sm"
              />
              {selectedPatient && (
                <div className="mt-2 flex items-center gap-2 bg-neutral-50/50 border border-neutral-100 px-3 py-1.5 rounded-lg">
                  <User size={14} className="text-neutral-500 shrink-0" />
                  <p className="text-xs text-neutral-700 font-bold uppercase tracking-tight">{selectedPatient.fullName}</p>
                  <span className="text-[10px] text-neutral-400 font-mono">#{selectedPatient.patientNumber}</span>
                  <button onClick={() => { setSelectedPatient(null); setSelectedConsultant(''); setConsultantError(false); }} className="ml-auto text-neutral-400 hover:text-neutral-600">×</button>
                </div>
              )}
            </div>

            {selectedPatient && (
              <div className="w-full max-w-sm animate-in fade-in slide-in-from-left-2 duration-200">
                <label className="block text-[10px] uppercase font-bold text-gray-400 mb-1.5 tracking-widest">Consultant Name <span className="text-red-500">*</span></label>
                <ConsultantSearchInput
                  consultants={consultants ?? []}
                  value={selectedConsultantId}
                  onChange={id => {
                    const c = consultants?.find(x => x.id === id)
                    if (c) {
                      const fullName = `${c.salutation || ''} ${c.firstName} ${c.lastName}`.trim()
                      setSelectedConsultant(fullName)
                      setConsultantError(false)
                    } else {
                      setSelectedConsultant('')
                    }
                  }}
                  placeholder="Choose Consultant"
                  size="sm"
                  className={cn(
                    "w-full transition-all duration-200",
                    consultantError && "ring-1 ring-red-500 rounded-md bg-red-50/10"
                  )}
                />
                {consultantError && (
                  <p className="text-xs text-red-500 mt-1 animate-in fade-in duration-200">Please select a consultant doctor.</p>
                )}
              </div>
            )}
          </div>

          <div>
            <p className="text-xs font-medium text-gray-700 mb-2">Medicines / Items</p>
            <div className="overflow-x-auto md:overflow-x-visible pb-32 md:pb-0">
              <table className="w-full text-sm" aria-label="Sale line items">
                <thead>
                  <tr className="border-b border-gray-100 text-xs">
                    <th className="pb-2 pr-3 font-bold text-gray-400 uppercase tracking-wider text-left">Item & Batch</th>
                    <th className="pb-2 pr-3 font-bold text-gray-400 uppercase tracking-wider text-right w-24">Qty</th>
                    <th className="pb-2 pr-3 font-bold text-gray-400 uppercase tracking-wider text-right w-44">MRP</th>
                    <th className="pb-2 pr-3 font-bold text-gray-400 uppercase tracking-wider text-right w-20">Tax %</th>
                    <th className="pb-2 pr-3 font-bold text-gray-400 uppercase tracking-wider text-right w-28">Tax Value</th>
                    <th className="pb-2 pr-3 font-bold text-gray-400 uppercase tracking-wider text-right w-40">SUB TOTAL</th>
                    <th className="pb-2 w-10" />
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {lines.map((line, i) => (
                    <tr key={i} className="align-top">
                      <td className="py-2 pr-3 min-w-[280px]">
                        <MedicineSearchInput
                          value={line.itemName || ''}
                          onSelect={(item) => handleMedicineSelect(item, i)}
                          placeholder="Search medicine…"
                        />
                        {line.batches && line.batches.length > 0 && (
                          <div className="mt-1.5">
                            <select
                              value={line.inventoryBatchId}
                              onChange={(e) => handleBatchSelect(e.target.value, i)}
                              className={`${inputCls} w-full bg-neutral-50 border-neutral-200 text-xs`}
                              aria-label={`Select batch for item ${i + 1}`}
                            >
                              <option value="">Select Batch…</option>
                              {line.batches.map(b => (
                                <option key={b.id} value={b.id}>
                                  {b.batchNumber || 'No Batch #'} (Exp: {b.expiryDate ? formatDate(b.expiryDate) : 'N/A'}) - Qty: {b.currentQuantity}
                                </option>
                              ))}
                            </select>
                          </div>
                        )}
                        {/* {line.inventoryBatchId && (
                          <p className="text-[10px] text-gray-400 mt-1 font-mono uppercase truncate max-w-[200px]">
                            ID: {line.inventoryBatchId}
                          </p>
                        )} */}
                      </td>
                      <td className="py-2 pr-3 w-24">
                        <input type="number"
                          min={1}
                          max={line.inventoryBatchId ? line.batches?.find(b => b.id === line.inventoryBatchId)?.currentQuantity : 9999}
                          value={line.quantity}
                          disabled={!line.inventoryBatchId}
                          onChange={e => {
                            const valStr = e.target.value
                            if (valStr === '') {
                              setLines(prev => prev.map((l, idx) => idx === i ? { ...l, quantity: '' as any } : l))
                              return
                            }
                            if (valStr === '-') {
                              setLines(prev => prev.map((l, idx) => idx === i ? { ...l, quantity: 0 } : l))
                              return
                            }
                            let val = parseInt(valStr)
                            if (isNaN(val)) return
                            if (val < 0) val = 0
                            const max = line.inventoryBatchId ? (line.batches?.find(b => b.id === line.inventoryBatchId)?.currentQuantity ?? 9999) : 9999
                            setLines(prev => prev.map((l, idx) => idx === i ? { ...l, quantity: Math.min(val, max) } : l))
                          }}
                          onBlur={() => {
                            setLines(prev => prev.map((l, idx) => {
                              if (idx !== i) return l
                              const qty = parseInt(l.quantity as any)
                              const max = l.inventoryBatchId ? (l.batches?.find(b => b.id === l.inventoryBatchId)?.currentQuantity ?? 9999) : 9999
                              const finalQty = isNaN(qty) ? 1 : Math.max(1, Math.min(qty, max))
                              return { ...l, quantity: finalQty }
                            }))
                          }}
                          className={`${inputCls} w-full text-right disabled:opacity-50 disabled:bg-gray-100/50 no-spinner`} aria-label={`Item ${i + 1} quantity`} />
                        {line.inventoryBatchId && line.batches?.find(b => b.id === line.inventoryBatchId) && (
                          <p className="text-[10px] text-gray-400 mt-1 text-right">
                            Max: {line.batches.find(b => b.id === line.inventoryBatchId)?.currentQuantity}
                          </p>
                        )}
                      </td>
                      <td className="py-2 pr-3 w-44">
                        <div className="relative">
                          <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-xs font-semibold pointer-events-none">₹</span>
                          <input type="number"
                            value={line.unitRate || ''}
                            readOnly
                            className={`${inputCls} w-full pl-7 text-right bg-gray-50 border-gray-200 cursor-default no-spinner text-gray-400`}
                            aria-label={`Item ${i + 1} rate`}
                          />
                        </div>
                      </td>
                      <td className="py-4 pr-3 w-20 text-right text-sm text-gray-700">
                        {(line.taxRate || 0)}%
                      </td>
                      <td className="py-4 pr-3 w-28 text-right text-sm text-gray-700 tabular-nums">
                        {(() => {
                          const taxRate = line.taxRate || 0
                          const qty = Number(line.quantity) || 0
                          const lineAmount = qty * Number(line.unitRate || 0)
                          const lineTax = taxRate > 0 ? (lineAmount * taxRate) / (100 + taxRate) : 0
                          return `₹${lineTax.toFixed(2)}`
                        })()}
                      </td>
                      <td className="py-4 pr-3 text-right font-bold text-gray-900 w-40 tabular-nums">
                        ₹{Math.round(line.quantity * Number(line.unitRate || 0)).toLocaleString()}
                      </td>
                      <td className="py-4 text-center">
                        <button onClick={() => {
                          if (lines.length > 1) {
                            setLines(prev => prev.filter((_, idx) => idx !== i))
                          } else {
                            setLines([{ inventoryBatchId: '', quantity: 1, unitRate: 0 }])
                            setDiscountAmount(0)
                          }
                        }}
                          className="w-6 h-6 flex items-center justify-center rounded-full bg-red-50 text-red-500 hover:bg-red-500 hover:text-white transition-all text-sm"
                          aria-label="Remove item">×</button>
                      </td>
                    </tr>
                  ))}
                  <tr>
                    <td colSpan={7} className="py-3">
                      <button
                        type="button"
                        onClick={() => setLines(prev => [...prev, { inventoryBatchId: '', quantity: 1, unitRate: 0 }])}
                        className="text-sm text-neutral-600 hover:text-neutral-700 font-medium"
                      >
                        + Add Item
                      </button>
                    </td>
                  </tr>
                </tbody>
                <tfoot>
                  <tr className="border-t border-gray-200">
                    <td colSpan={5} className="pt-4 text-right text-sm font-bold text-gray-500 uppercase tracking-wide">Total Amount</td>
                    <td className="pt-4 pr-3 text-right font-semibold text-lg text-gray-900 tabular-nums">
                      ₹{Math.round(total).toLocaleString()}
                    </td>
                    <td />
                  </tr>
                  {lines.some(l => l.inventoryBatchId) && (
                    <>
                      <tr>
                        <td colSpan={5} className="pt-2 text-right text-sm font-bold text-gray-500 uppercase tracking-wide">Discount (₹)</td>
                        <td className="pt-2 pr-3 text-right">
                          <input
                            type="number"
                            min={0}
                            max={Math.round(total)}
                            value={discountAmount || ''}
                            onChange={e => {
                              const val = Math.max(0, parseInt(e.target.value) || 0)
                              setDiscountAmount(Math.min(val, Math.round(total)))
                            }}
                            className={`${inputCls} w-32 text-right inline-block no-spinner`}
                            placeholder="0"
                          />
                        </td>
                        <td />
                      </tr>
                      {Object.entries(subTaxSums).map(([name, amount]) => (
                        <tr key={name} className="border-t border-gray-50/50">
                          <td colSpan={5} className="pt-2 text-right text-sm font-bold text-gray-500 uppercase tracking-wide">{name}</td>
                          <td className="pt-2 pr-3 text-right font-semibold text-gray-700 tabular-nums">
                            ₹{amount.toFixed(2)}
                          </td>
                          <td />
                        </tr>
                      ))}
                      <tr className="border-t border-gray-100">
                        <td colSpan={5} className="pt-3 text-right text-sm font-bold text-gray-700 uppercase tracking-wide">Net Amount</td>
                        <td className="pt-3 pr-3 text-right font-extrabold text-xl text-neutral-600 tabular-nums">
                          ₹{Math.max(0, Math.round(total) - discountAmount).toLocaleString()}
                        </td>
                        <td />
                      </tr>
                    </>
                  )}
                </tfoot>
              </table>
            </div>
          </div>

          {/* Payment Section Panel */}
          {hasItems && (
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden mt-6 animate-in fade-in slide-in-from-bottom-2 duration-300">
              {/* Tabs */}
              <div className="flex border-b border-gray-200 bg-gray-50">
                <button
                  type="button"
                  onClick={() => {
                    setActivePayTab('pay_now');
                    setIsDraftSubmit(false);
                  }}
                  className={cn(
                    "px-5 py-3 text-xs font-bold uppercase tracking-wider transition-all duration-200 border-r border-gray-200",
                    activePayTab === 'pay_now'
                      ? "bg-gray-800 text-white"
                      : "text-gray-600 hover:text-gray-900 hover:bg-gray-100/50"
                  )}
                >
                  Collect Now
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setActivePayTab('save_draft');
                    setIsDraftSubmit(true);
                  }}
                  className={cn(
                    "px-5 py-3 text-xs font-bold uppercase tracking-wider transition-all duration-200 border-r border-gray-200",
                    activePayTab === 'save_draft'
                      ? "bg-gray-800 text-white"
                      : "text-gray-600 hover:text-gray-900 hover:bg-gray-100/50"
                  )}
                >
                  Save Draft
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setActivePayTab('partial_payment');
                    setIsDraftSubmit(false);
                  }}
                  className={cn(
                    "px-5 py-3 text-xs font-bold uppercase tracking-wider transition-all duration-200",
                    activePayTab === 'partial_payment'
                      ? "bg-gray-800 text-white"
                      : "text-gray-600 hover:text-gray-900 hover:bg-gray-100/50"
                  )}
                >
                  Collect Partial Amount
                </button>
                {isInpatientContext && (
                  <button
                    type="button"
                    onClick={() => {
                      setActivePayTab('add_to_bill');
                      setIsDraftSubmit(false);
                    }}
                    className={cn(
                      "px-5 py-3 text-xs font-bold uppercase tracking-wider transition-all duration-200 border-l border-gray-200",
                      activePayTab === 'add_to_bill'
                        ? "bg-gray-800 text-white"
                        : "text-gray-600 hover:text-gray-900 hover:bg-gray-100/50"
                    )}
                  >
                    Add to Bill
                  </button>
                )}
              </div>

              {/* Content Area */}
              <div className="p-6">
                {activePayTab === 'pay_now' && (
                  <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
                    <div className="flex-1 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4 items-end">
                      <div>
                        <label className="block text-[10px] uppercase font-bold text-gray-400 mb-1.5 tracking-widest">Mode</label>
                        <select
                          value={paymentMode}
                          onChange={e => setPaymentMode(e.target.value as 'Cash' | 'Card' | 'UPI')}
                          className={cn(inputCls, "w-full bg-white shadow-sm h-10")}
                        >
                          <option value="Cash">Cash</option>
                          <option value="Card">Card</option>
                          <option value="UPI">UPI</option>
                        </select>
                      </div>

                      {paymentMode === 'Card' && (
                        <>
                          <div>
                            <label className="block text-[10px] uppercase font-bold text-gray-400 mb-1.5 tracking-widest">Card Type</label>
                            <select
                              value={cardType}
                              onChange={e => setCardType(e.target.value)}
                              className={cn(inputCls, "w-full bg-white shadow-sm h-10")}
                            >
                              <option value="">Select Card Type</option>
                              <option value="Credit">Credit</option>
                              <option value="Debit">Debit</option>
                            </select>
                          </div>
                          <div>
                            <label className="block text-[10px] uppercase font-bold text-gray-400 mb-1.5 tracking-widest">Card No</label>
                            <input
                              type="text"
                              value={cardNumber}
                              onChange={e => setCardNumber(e.target.value)}
                              placeholder="Card No"
                              className={cn(inputCls, "w-full bg-white shadow-sm h-10")}
                            />
                          </div>
                          {/* <div>
                            <label className="block text-[10px] uppercase font-bold text-gray-400 mb-1.5 tracking-widest">Bank Name</label>
                            <input
                              type="text"
                              value={bankName}
                              onChange={e => setBankName(e.target.value)}
                              placeholder="Bank Name"
                              className={cn(inputCls, "w-full bg-white shadow-sm h-10")}
                            />
                          </div> */}
                        </>
                      )}
                    </div>

                    <div className="shrink-0 flex items-center justify-end">
                      <button
                        type="button"
                        onClick={() => {
                          handleSubmit(false);
                        }}
                        disabled={createMutation.isPending}
                        className="w-64 py-3.5 bg-neutral-600 hover:bg-neutral-700 active:bg-neutral-800 text-white text-sm font-bold uppercase tracking-wider rounded-lg shadow-md hover:shadow-lg transition-all duration-200"
                      >
                        {createMutation.isPending ? 'Processing…' : 'Collect Now'}
                      </button>
                    </div>
                  </div>
                )}

                {activePayTab === 'save_draft' && (
                  <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
                    <div>
                      <p className="text-sm text-gray-500 font-medium">
                        Save this sale as a draft. Stock items will be reserved but no payment will be recorded yet.
                      </p>
                    </div>
                    <div className="shrink-0 flex items-center justify-end">
                      <button
                        type="button"
                        onClick={() => {
                          handleSubmit(true);
                        }}
                        disabled={createMutation.isPending}
                        className="w-64 py-3.5 bg-neutral-600 hover:bg-neutral-700 active:bg-neutral-800 text-white text-sm font-bold uppercase tracking-wider rounded-lg shadow-md hover:shadow-lg transition-all duration-200"
                      >
                        {createMutation.isPending ? 'Saving…' : 'Save Draft'}
                      </button>
                    </div>
                  </div>
                )}

                {activePayTab === 'partial_payment' && (
                  <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
                    <div className="flex-1 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4 items-end">
                      <div>
                        <label className="block text-[10px] uppercase font-bold text-gray-400 mb-1.5 tracking-widest">Amount</label>
                        <input
                          type="number"
                          value={paidAmount}
                          onChange={e => setPaidAmount(e.target.value === '' ? '' : Number(e.target.value))}
                          placeholder="Enter amount to pay"
                          min="0"
                          max={Math.max(0, Math.round(total) - discountAmount)}
                          className={cn(inputCls, "w-full bg-white shadow-sm h-10 no-spinner")}
                        />
                      </div>
                      <div>
                        <label className="block text-[10px] uppercase font-bold text-gray-400 mb-1.5 tracking-widest">Mode</label>
                        <select
                          value={paymentMode}
                          onChange={e => setPaymentMode(e.target.value as 'Cash' | 'Card' | 'UPI')}
                          className={cn(inputCls, "w-full bg-white shadow-sm h-10")}
                        >
                          <option value="Cash">Cash</option>
                          <option value="Card">Card</option>
                          <option value="UPI">UPI</option>
                        </select>
                      </div>

                      {paymentMode === 'Card' && (
                        <>
                          <div>
                            <label className="block text-[10px] uppercase font-bold text-gray-400 mb-1.5 tracking-widest">Card Type</label>
                            <select
                              value={cardType}
                              onChange={e => setCardType(e.target.value)}
                              className={cn(inputCls, "w-full bg-white shadow-sm h-10")}
                            >
                              <option value="">Select Card Type</option>
                              <option value="Credit">Credit</option>
                              <option value="Debit">Debit</option>
                            </select>
                          </div>
                          <div>
                            <label className="block text-[10px] uppercase font-bold text-gray-400 mb-1.5 tracking-widest">Card No</label>
                            <input
                              type="text"
                              value={cardNumber}
                              onChange={e => setCardNumber(e.target.value)}
                              placeholder="Card No"
                              className={cn(inputCls, "w-full bg-white shadow-sm h-10")}
                            />
                          </div>
                        </>
                      )}
                    </div>

                    <div className="shrink-0 flex items-center justify-end">
                      <button
                        type="button"
                        onClick={() => {
                          handleSubmit(false);
                        }}
                        disabled={createMutation.isPending || paidAmount === '' || paidAmount <= 0}
                        className="w-64 py-3.5 bg-neutral-600 hover:bg-neutral-700 active:bg-neutral-800 text-white text-sm font-bold uppercase tracking-wider rounded-lg shadow-md hover:shadow-lg transition-all duration-200 disabled:opacity-50"
                      >
                        {createMutation.isPending ? 'Processing…' : 'Collect Partial Amount'}
                      </button>
                    </div>
                  </div>
                )}

                {activePayTab === 'add_to_bill' && (
                  <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
                    <div className="flex-1 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4 items-end">
                      {/* <div>
                        <label className="block text-[10px] uppercase font-bold text-gray-400 mb-1.5 tracking-widest">Issue Type</label>
                        <select
                          className={cn(inputCls, "w-full bg-white shadow-sm h-10")}
                          disabled
                        >
                          <option value="IP">IP</option>
                        </select>
                      </div> */}
                    </div>

                    <div className="shrink-0 flex items-center justify-end">
                      <button
                        type="button"
                        onClick={() => {
                          handleSubmit(false);
                        }}
                        disabled={createMutation.isPending}
                        className="w-64 py-3.5 bg-neutral-500 hover:bg-neutral-600 active:bg-neutral-700 text-white text-sm font-bold uppercase tracking-wider rounded-lg shadow-md hover:shadow-lg transition-all duration-200"
                      >
                        {createMutation.isPending ? 'Processing…' : 'ADD TO BILL'}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {/* Draft Sales */}
      {tab === 'drafts' && (
        <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
          <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-gray-900">Pending Drafts</h3>
            <span className="text-xs text-gray-400">{draftSales?.length ?? 0} drafts pending</span>
          </div>
          {isLoadingDrafts && <p className="text-sm text-gray-500 px-5 py-4" aria-live="polite">Loading drafts…</p>}
          <table className="w-full text-sm" aria-label="Draft sales">
            <thead><tr className="bg-gray-50 border-b border-gray-100 text-left text-xs">
              <th className="px-4 py-2.5 font-semibold text-gray-600">Draft ID</th>
              <th className="px-4 py-2.5 font-semibold text-gray-600">Patient</th>
              <th className="px-4 py-2.5 font-semibold text-gray-600">Items</th>
              <th className="px-4 py-2.5 font-semibold text-gray-600 text-right">Amount</th>
              <th className="px-4 py-2.5 font-semibold text-gray-600 text-right">Actions</th>
            </tr></thead>
            <tbody className="divide-y divide-gray-100">
              {draftSales?.map(s => (
                <tr key={s.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-3 font-mono text-xs text-gray-700">{s.sequenceNumber || s.id.slice(0, 8)}</td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      <span className="text-gray-700 font-medium">{s.patientName}</span>
                      {!s.patientId && (
                        <span className="text-[9px] font-bold text-amber-600 bg-amber-50 px-1.5 py-0.5 rounded uppercase tracking-tighter shrink-0">Walk-in</span>
                      )}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-gray-500 text-xs">{s.lines.length} item{s.lines.length !== 1 ? 's' : ''}</td>
                  <td className="px-4 py-3 text-right font-bold text-gray-900 tabular-nums">
                    ₹{Number(s.totalAmount).toLocaleString()}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex items-center justify-end gap-2">
                      <button onClick={() => loadDraft(s)} disabled={loadingDraftId === s.id}
                        className="px-3 py-1.5 bg-neutral-50 text-neutral-600 hover:bg-neutral-100 hover:text-neutral-700 text-xs font-semibold rounded-lg transition-colors disabled:opacity-50 flex items-center gap-1">
                        <Edit2 size={12} />
                        <span>{loadingDraftId === s.id ? 'Loading…' : 'Edit / Resume'}</span>
                      </button>
                      <button onClick={() => setDeletingDraft({ id: s.id, sequenceNumber: s.sequenceNumber || s.id.slice(0, 8) })}
                        disabled={deleteDraftMutation.isPending}
                        className="px-3 py-1.5 bg-red-50 text-red-600 hover:bg-red-100 hover:text-red-700 text-xs font-semibold rounded-lg transition-colors disabled:opacity-50 flex items-center gap-1">
                        <Trash2 size={12} />
                        <span>Delete</span>
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {(!draftSales || draftSales.length === 0) && !isLoadingDrafts && (
                <tr><td colSpan={5} className="px-4 py-10 text-center text-gray-400 text-sm">No pending draft sales</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Walk-in Details Modal */}
      {showWalkinModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-2 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200"
          style={{ marginTop: 0 }}
        >
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md overflow-hidden animate-in zoom-in-95 duration-200">
            <div className="px-6 py-5 border-b border-gray-100 flex items-center justify-between bg-gray-50 rounded-t-2xl">
              <h3 className="font-bold text-gray-900">Walk-in Customer Details</h3>
              <button onClick={() => setShowWalkinModal(false)} className="text-gray-400 hover:text-gray-600 transition-colors">×</button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-[10px] uppercase font-bold text-gray-400 mb-1.5 tracking-widest">Full Name <span className="text-red-500">*</span></label>
                <input type="text" autoFocus value={walkinName} onChange={e => { const v = e.target.value; if (v === '' || /^[A-Za-z\s]+$/.test(v)) setWalkinName(v) }}
                  placeholder="Enter customer name" className={`${inputCls} w-full h-11 text-base shadow-sm`} />
              </div>
              <div>
                <label className="block text-[10px] uppercase font-bold text-gray-400 mb-1.5 tracking-widest">Contact Number <span className="text-red-500">*</span></label>
                <input type="tel" maxLength={10} value={walkinPhone}
                  onChange={e => setWalkinPhone(e.target.value.replace(/\D/g, '').slice(0, 10))}
                  placeholder="10-digit mobile number" className={`${inputCls} w-full h-11 text-base shadow-sm`} />
              </div>
              <div>
                <label className="block text-[10px] uppercase font-bold text-gray-400 mb-1.5 tracking-widest">Consultant Name</label>
                <input type="text" value={walkinConsultant} onChange={e => { const v = e.target.value; if (v === '' || /^[A-Za-z\s]+$/.test(v)) setWalkinConsultant(v) }}
                  placeholder="Enter consultant/doctor name" className={`${inputCls} w-full h-11 text-base shadow-sm`} />
              </div>
              <div className="pt-4 flex gap-3">
                <button onClick={() => setShowWalkinModal(false)} className="flex-1 px-4 py-2.5 text-sm font-semibold text-gray-600 hover:bg-gray-100 rounded-xl transition-colors">
                  Cancel
                </button>
                <button
                  disabled={!walkinName.trim() || !walkinPhone.trim() || createMutation.isPending}
                  onClick={() => handleSubmit(false)}
                  className="flex-1 px-4 py-2.5 text-sm font-semibold bg-neutral-600 text-white rounded-xl shadow-lg shadow-neutral-200 hover:bg-neutral-700 disabled:opacity-50 transition-all"
                >
                  Confirm & Complete
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Delete Draft Confirmation Modal */}
      {deletingDraft && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md overflow-hidden animate-in zoom-in-95 duration-200">
            <div className="px-6 py-5 border-b border-gray-100 flex items-center justify-between bg-gray-50 rounded-t-2xl">
              <h3 className="font-bold text-gray-900">Delete Draft Sale</h3>
              <button onClick={() => setDeletingDraft(null)} className="text-gray-400 hover:text-gray-600 text-xl font-medium transition-colors">×</button>
            </div>
            <div className="p-6 space-y-4">
              <p className="text-sm text-gray-600">
                Are you sure you want to delete draft sale <span className="font-mono font-bold text-gray-900">{deletingDraft.sequenceNumber}</span>? This action cannot be undone.
              </p>
              <div className="pt-4 flex gap-3">
                <button onClick={() => setDeletingDraft(null)} className="flex-1 px-4 py-2.5 text-sm font-semibold text-gray-600 hover:bg-gray-100 rounded-xl transition-colors">
                  Cancel
                </button>
                <button
                  disabled={deleteDraftMutation.isPending}
                  onClick={() => deleteDraftMutation.mutate(deletingDraft.id)}
                  className="flex-1 px-4 py-2.5 text-sm font-semibold bg-red-600 text-white rounded-xl shadow-lg shadow-red-200 hover:bg-red-700 disabled:opacity-50 transition-all"
                >
                  {deleteDraftMutation.isPending ? 'Deleting…' : 'Delete Draft'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {showTempStockModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200" style={{ marginTop: 0 }}>
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-6xl overflow-hidden animate-in zoom-in-95 duration-200">
            <div className="px-6 py-5 border-b border-gray-100 flex items-center justify-between bg-gray-50 rounded-t-2xl">
              <div>
                <h3 className="font-bold text-gray-900 text-lg">Add Temporary Stock</h3>
                <p className="text-xs text-gray-500 mt-0.5">Staging area for incoming stock before formal GRN is processed. Department: PHARMACY.</p>
              </div>
              <button onClick={() => setShowTempStockModal(false)} className="text-gray-400 hover:text-gray-600 text-xl font-medium transition-colors">×</button>
            </div>
            <div className="p-6 space-y-4 max-h-[70vh] overflow-y-auto">
              <table className="w-full text-sm text-left">
                <thead>
                  <tr className="border-b border-gray-200 text-xs font-bold text-gray-600 uppercase tracking-wider bg-gray-50/50">
                    <th className="px-2 py-3 w-12 text-center">S.No</th>
                    <th className="px-2 py-3 w-[240px]">Item *</th>
                    <th className="px-2 py-3 w-[110px]">Batch No</th>
                    <th className="px-2 py-3 w-[130px]">Expiry Date</th>
                    <th className="px-2 py-3 w-[90px]">MRP *</th>
                    <th className="px-2 py-3 w-[90px]">P.Price *</th>
                    <th className="px-2 py-3 w-[70px] text-center">Qty *</th>
                    <th className="px-2 py-3 w-[110px] text-center">Tax %</th>
                    <th className="px-2 py-3 w-[100px] text-right">Sub Total</th>
                    <th className="px-2 py-3 w-10 text-center"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {tempStockRows.map((row, idx) => {
                    const purchasePrice = Number(row.purchasePrice) || 0
                    const quantity = Number(row.quantity) || 0
                    const taxRate = Number(row.taxRate) || 0
                    const subtotal = purchasePrice * quantity * (1 + taxRate / 100)

                    return (
                      <tr key={idx} className="hover:bg-neutral-50/50 transition-colors align-middle">
                        <td className="px-2 py-2.5 text-center text-gray-400 font-semibold font-mono">{idx + 1}</td>
                        <td className="px-2 py-2.5">
                          <MedicineSearchInput
                            value={row.item?.name || ''}
                            onSelect={(item) => updateTempStockRow(idx, 'item', item)}
                            onClear={() => updateTempStockRow(idx, 'item', null)}
                            placeholder="Type to search..."
                            className="w-full"
                          />
                        </td>
                        <td className="px-2 py-2.5">
                          <input
                            type="text"
                            value={row.batchNumber}
                            onChange={(e) => updateTempStockRow(idx, 'batchNumber', e.target.value)}
                            placeholder="Batch No"
                            className="w-full px-2 py-1.5 border border-gray-300 rounded text-xs focus:outline-none focus:ring-1 focus:ring-neutral-500 font-mono uppercase"
                          />
                        </td>
                        <td className="px-2 py-2.5">
                          <input
                            type="text"
                            spellCheck={false}
                            autoComplete="off"
                            value={tempExpiryRawInputs[idx] ?? (row.expiryDate ? (() => { const p = row.expiryDate.split('-'); return p.length === 3 ? `${p[2]}/${p[1]}/${p[0]}` : '' })() : 'dd/mm/yyyy')}
                            onFocus={e => {
                              if (e.target.value === 'dd/mm/yyyy') {
                                const el = e.target
                                setTimeout(() => el.setSelectionRange(0, 0), 0)
                              }
                            }}
                            onKeyDown={e => handleDateMaskKeyDown(e, e.currentTarget.value, (val) => {
                              setTempExpiryRawInputs(prev => ({ ...prev, [idx]: val }))
                              const { isValid, iso } = parseMaskedDate(val)
                              updateTempStockRow(idx, 'expiryDate', isValid ? iso : '')
                            })}
                            onBlur={e => {
                              const val = e.target.value
                              if (val === 'dd/mm/yyyy') {
                                updateTempStockRow(idx, 'expiryDate', '')
                                setTempExpiryRawInputs(prev => ({ ...prev, [idx]: 'dd/mm/yyyy' }))
                                return
                              }
                              const { isValid, iso } = parseMaskedDate(val)
                              if (!isValid) {
                                toast({ title: 'Invalid date. Enter a valid dd/mm/yyyy', variant: 'destructive' })
                                updateTempStockRow(idx, 'expiryDate', '')
                                setTempExpiryRawInputs(prev => ({ ...prev, [idx]: 'dd/mm/yyyy' }))
                              } else if (iso < new Date().toISOString().split('T')[0]) {
                                toast({ title: 'Expiry Date cannot be in the past', variant: 'destructive' })
                                updateTempStockRow(idx, 'expiryDate', '')
                                setTempExpiryRawInputs(prev => ({ ...prev, [idx]: 'dd/mm/yyyy' }))
                              } else {
                                updateTempStockRow(idx, 'expiryDate', iso)
                              }
                            }}
                            className="w-full px-2 py-1.5 border border-gray-300 rounded text-xs focus:outline-none focus:ring-1 focus:ring-neutral-500 font-mono text-center"
                          />
                        </td>
                        <td className="px-2 py-2.5">
                          <input
                            type="number"
                            step="0.01"
                            min="0"
                            value={row.mrp}
                            onChange={(e) => updateTempStockRow(idx, 'mrp', e.target.value === '' ? '' : parseFloat(e.target.value))}
                            placeholder="0.00"
                            className="w-full px-2 py-1.5 border border-gray-300 rounded text-xs focus:outline-none focus:ring-1 focus:ring-neutral-500 text-right"
                          />
                        </td>
                        <td className="px-2 py-2.5">
                          <input
                            type="number"
                            step="0.01"
                            min="0"
                            value={row.purchasePrice}
                            onChange={(e) => updateTempStockRow(idx, 'purchasePrice', e.target.value === '' ? '' : parseFloat(e.target.value))}
                            placeholder="0.00"
                            className="w-full px-2 py-1.5 border border-gray-300 rounded text-xs focus:outline-none focus:ring-1 focus:ring-neutral-500 text-right"
                          />
                        </td>
                        <td className="px-2 py-2.5">
                          <input
                            type="number"
                            min="1"
                            value={row.quantity}
                            onChange={(e) => updateTempStockRow(idx, 'quantity', parseInt(e.target.value) || 1)}
                            className="w-full px-2 py-1.5 border border-gray-300 rounded text-xs focus:outline-none focus:ring-1 focus:ring-neutral-500 text-center"
                          />
                        </td>
                        <td className="px-2 py-2.5">
                          <select
                            value={row.taxRate}
                            onChange={e => {
                              const val = parseFloat(e.target.value);
                              const cleanVal = isNaN(val) ? 0 : val;
                              updateTempStockRow(idx, 'taxRate', cleanVal);
                            }}
                            className="w-full px-1 py-1 border border-gray-300 rounded text-xs bg-white focus:outline-none focus:ring-1 focus:ring-neutral-500 font-mono text-center"
                          >
                            <option value="0">0%</option>
                            {(taxes || []).map(t => (
                              <option key={t.id} value={t.rate}>
                                {t.name} ({t.rate}%)
                              </option>
                            ))}
                          </select>
                        </td>
                        <td className="px-2 py-2.5 text-right font-mono font-semibold text-gray-700">
                          ₹{subtotal.toFixed(2)}
                        </td>
                        <td className="px-2 py-2.5 text-center">
                          {tempStockRows.length > 1 && (
                            <button
                              onClick={() => removeTempStockRow(idx)}
                              className="text-red-500 hover:text-red-700 font-bold text-lg leading-none transition-colors"
                            >
                              ×
                            </button>
                          )}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>

              <div className="flex justify-between items-center pt-2">
                <button
                  onClick={addTempStockRow}
                  className="px-4 py-2 text-sm font-bold text-neutral-600 hover:bg-neutral-50 border border-gray-200 rounded-xl transition-all"
                >
                  + Add Line
                </button>
                <div className="text-right">
                  <span className="text-xs text-gray-400">Total Bill Amount: </span>
                  <span className="text-base font-extrabold text-neutral-700 font-mono">
                    ₹{tempStockRows.reduce((sum, r) => sum + (Number(r.purchasePrice) || 0) * (Number(r.quantity) || 0) * (1 + (Number(r.taxRate) || 0) / 100), 0).toFixed(2)}
                  </span>
                </div>
              </div>
            </div>
            <div className="px-6 py-5 border-t border-gray-100 flex justify-end gap-3 bg-gray-50">
              <button
                onClick={() => setShowTempStockModal(false)}
                className="px-4 py-2.5 text-sm font-semibold text-gray-600 hover:bg-gray-100 rounded-xl transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleSaveTempStock}
                className="px-6 py-2.5 text-sm font-semibold bg-neutral-600 text-white rounded-xl shadow-lg shadow-neutral-200 hover:bg-neutral-700 transition-all"
              >
                Save Stock
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
