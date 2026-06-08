export interface StockAdjustmentLine {
  id: string
  inventoryBatchId: string
  batchNumber: string
  itemName: string
  adjustmentQty: number
  adjustmentType: 'ADD' | 'SUBTRACT'
  reason: string | null
}

export interface StockAdjustment {
  id: string
  departmentId: string
  departmentName: string
  sequenceNumber: string
  adjustmentDate: string
  notes: string | null
  authorisedBy: string
  createdAt: string
  lines: StockAdjustmentLine[]
}

export interface CreateStockAdjustmentLinePayload {
  inventoryBatchId: string
  adjustmentQty: number
  adjustmentType: 'ADD' | 'SUBTRACT'
  reason?: string
}

export interface CreateStockAdjustmentPayload {
  departmentId: string
  notes?: string
  lines: CreateStockAdjustmentLinePayload[]
}
