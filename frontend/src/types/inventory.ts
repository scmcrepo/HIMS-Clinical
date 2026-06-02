export interface InventoryBatch {
  id: string
  itemId: string
  itemName: string
  departmentId: string
  departmentName: string
  batchNumber: string | null
  currentQuantity: number
  purchaseRate: number
  maximumRetailPrice: number
  sellingRate: number
  expiryDate: string | null
  isExpired: boolean
  isOutOfStock: boolean
  taxRate: number
  supplierId: string | null
}

export interface InventoryItem {
  id: string
  name: string
  hsnCode?: string
  taxRate: number
  reorderLevel: number
  status: number
}
