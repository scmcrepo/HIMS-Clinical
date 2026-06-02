export type Gender = 'MALE' | 'FEMALE' | 'OTHER'
export type EntityStatus = 'INACTIVE' | 'ACTIVE' | 'DELETED'

export interface Patient {
  id: string
  patientNumber: string
  salutation: string | null
  firstName: string
  lastName: string
  fullName: string
  gender: Gender
  dateOfBirth: string | null
  estimatedDateOfBirth: string
  age: string
  contactNumber: string | null
  email: string | null
  bloodGroup: string | null
  address: string | null
  primaryProviderId: string | null
  areaId: string | null
  categoryId: string | null
  isClinicalTrial: boolean
  status: EntityStatus
  isInpatient?: boolean
  activeEncounterId?: string | null
}
