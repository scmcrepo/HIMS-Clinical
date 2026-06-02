export type ServiceType         = 'INDIVIDUAL' | 'PACKAGE' | 'INPATIENT'
export type ServiceCategoryType = 'DIAGNOSTICS' | 'ROOM_CHARGE' | 'PHARMACY' | 'CONSULTATION' | 'PROCEDURE' | 'SURGERY' | 'PHARMACY_ITEM' | 'OTHER'
export type BillType            = 'CASH' | 'CREDIT' | 'INSURANCE'

export interface PricingTier { id: string; billType: BillType; unitRate: number }
export interface ServiceItem {
  id: string; name: string; categoryId: string
  serviceType: ServiceType; requiresOrder: boolean; status: string
  pricingTiers: PricingTier[]
}
export interface ServiceCategory { id: string; name: string; categoryType: ServiceCategoryType }
