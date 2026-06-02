export type BedStatus = 'ALLOCATED' | 'AVAILABLE' | 'MAINTENANCE'
export type BillingCycle = 'HOURLY' | 'DAILY'

export interface RoomCategory {
  id: string
  name: string
  billingCycle: BillingCycle
  status: 'ACTIVE' | 'INACTIVE' | 'DELETED'
}

export interface Bed {
  id: string
  name: string
  roomCategoryId: string
  bedStatus: BedStatus
  floor: string | null
  ward: string | null
  status: 'ACTIVE' | 'INACTIVE' | 'DELETED'
  roomCategoryName?: string
  allocatedPatientName?: string
  allocatedPatientNumber?: string
  allocatedEncounterId?: string
  allocatedConsultantName?: string
}

export interface BedOccupancy {
  id: string
  bedId: string
  encounterId: string
  billId: string | null
  fromDatetime: string
  toDatetime: string | null
  isActive: boolean
}

export interface BedStatusSummary {
  total: number
  available: number
  allocated: number
  maintenance: number
}
