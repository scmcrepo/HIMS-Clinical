/**
 * masterApi.ts
 * ─────────────────────────────────────────────────────────────────────────────
 * Covers the master-data entities that have no dedicated API file yet.
 * Entities that already have their own API files (specimen, catalog, prefix,
 * user/role, consultant, bed/bedType) are imported directly from those files.
 */
import api from '../../lib/axios'
import type { ApiResponse, PageResponse } from '../../types/api'

// ── Account Unit ─────────────────────────────────────────────────────────────
export interface AccountUnit { id: string; name: string; code: string; type: string; status: number }
export const accountUnitApi = {
  getPaginated: (params?: { start?: number; limit?: number; value?: string }) => api.get<ApiResponse<PageResponse<AccountUnit>>>('/accountUnit/page', { params }).then(r => r.data.data!),
  getAll:  () => api.get<ApiResponse<AccountUnit[]>>('/accountUnit').then(r => r.data.data ?? []),
  create:  (b: Omit<AccountUnit,'id'>) => api.post<ApiResponse<AccountUnit>>('/accountUnit', b).then(r => r.data.data!),
  update:  (id: string, b: Partial<AccountUnit>) => api.put<ApiResponse<AccountUnit>>('/accountUnit', { ...b, id }).then(r => r.data.data!),
  remove:  (id: string) => api.delete(`/accountUnit/${id}`),
}

// ── Bed Type (Room Category) – wraps bedApi ────────────────────────────────
// bedApi.getBedTypes() already exists; create/update below
export interface BedTypePayload { name: string; serviceCatalogItemId?: string; status?: 'ACTIVE' | 'INACTIVE' }
export const bedTypeApi = {
  getPaginated: (params?: { start?: number; limit?: number; value?: string }) => api.get<ApiResponse<PageResponse<any>>>('/bedType/page', { params }).then(r => r.data.data!),
  create: (b: BedTypePayload) => api.post<ApiResponse<any>>('/bedType', b).then(r => r.data.data!),
  update: (id: string, b: Partial<BedTypePayload>) => api.put<ApiResponse<any>>('/bedType', { ...b, id }).then(r => r.data.data!),
}

// ── Department – wraps departmentApi (read), adds create ──────────────────
export interface DepartmentPayload { 
  name: string; 
  departmentType: string; 
  displayOrder?: string; 
  status?: string;
  departmentCategories?: { category: { id: string } }[];
  stockDepartmentAccesses?: string[];
  departmentTemplates?: { template: { id: string } }[];
}
export const deptCreateApi = {
  getAll: () => api.get<ApiResponse<any[]>>('/department').then(r => r.data.data ?? []),
  getPaginated: (params?: { start?: number; limit?: number; value?: string }) => api.get<ApiResponse<PageResponse<any>>>('/department/page', { params }).then(r => r.data.data!),
  create: (b: DepartmentPayload) => api.post<ApiResponse<any>>('/department', b).then(r => r.data.data!),
  update: (id: string, b: Partial<DepartmentPayload>) => api.put<ApiResponse<any>>('/department', { ...b, id }).then(r => r.data.data!),
  getAccesses: (id: string) => api.get<ApiResponse<string[]>>(`/department/getDepartmentsAccess/${id}`).then(r => r.data.data!),
  getCategories: (id: string) => api.get<ApiResponse<any[]>>(`/department/getDepartmentsCategory/${id}`).then(r => r.data.data!),
}

export const templateApi = {
  getTemplatesByName: (type: string, name: string) => api.get<ApiResponse<any[]>>('/template/getTemplatesByName', { params: { type, name } }).then(r => r.data.data!),
  getDepartmentTemplates: (id: string) => api.get<ApiResponse<any[]>>(`/template/getDepartmentTemplateByDepartmentId/${id}`).then(r => r.data.data!),
  removeDepartmentTemplate: (templateId: string, deptId: string) => api.get<ApiResponse<void>>(`/template/removeDepartmentTemplates/${templateId}/${deptId}`).then(r => r.data.data!),
}

// ── Hospital Profile – wraps configApi (already exists) ───────────────────
// Use configApi.getHospital() / configApi.saveHospital() directly.

// ── Inventory Item ─────────────────────────────────────────────────────────
export interface ItemPayload {
  name: string;
  hsnCode?: string | undefined;
  taxRate: number;
  reorderLevel: number;
  unitOfMeasureId?: string | undefined;
  conversionFactor: number;
  requiresBatch: boolean;
  requiresPrescription: boolean;
  manufacturer?: string;
  rack?: string;
  categoryId?: string;
  cimsId?: string;
  mrp?: string;
  secondLevelUnit?: string;
  purchaseUnit?: string;
  sellingUnit?: string;
  scheduledDrug?: string;
  status?: number | string;
}
export const itemMasterApi = {
  getAll:  (params?: { start?: number; limit?: number; value?: string; id?: string }) => 
    api.get<ApiResponse<any>>('/item', { params }).then(r => {
      const d = r.data.data;
      return Array.isArray(d) ? d : (d?.content ?? []);
    }),
  getPaginated: (params?: { start?: number; limit?: number; value?: string; id?: string }) => 
    api.get<ApiResponse<PageResponse<any>>>('/item', { params }).then(r => r.data.data!),
  create:  (b: ItemPayload) => api.post<ApiResponse<any>>('/item', b).then(r => r.data.data!),
  update:  (id: string, b: Partial<ItemPayload>) => api.put<ApiResponse<any>>('/item', { ...b, id }).then(r => r.data.data!),
  deactivate: (id: string) => api.delete(`/item/${id}`),
  getUnitTypes: () => api.get<ApiResponse<string[]>>('/item/unitTypes').then(r => r.data.data ?? []),
  getScheduledDrugTypes: () => api.get<ApiResponse<string[]>>('/item/scheduledDrugType').then(r => r.data.data ?? []),
}

// ── Payers / TPA ──────────────────────────────────────────────────────────
export interface Payer { id: string; name: string; code: string; payerType: string; contactPerson?: string; contactPhone?: string; email?: string; address?: string; status: number | string }
export const payerApi = {
  getPaginated: (params?: { start?: number; limit?: number; value?: string }) => api.get<ApiResponse<PageResponse<Payer>>>('/payerType/page', { params }).then(r => r.data.data!),
  getAll:  () => api.get<ApiResponse<Payer[]>>('/payerType').then(r => r.data.data ?? []),
  create:  (b: Omit<Payer,'id'>) => api.post<ApiResponse<Payer>>('/payerType', b).then(r => r.data.data!),
  update:  (id: string, b: Partial<Payer>) => api.put<ApiResponse<Payer>>('/payerType', { ...b, id }).then(r => r.data.data!),
  deactivate: (id: string) => api.delete(`/payerType/${id}`),
}

// ── Molecule ───────────────────────────────────────────────────────────────
export interface Molecule { id: string; name: string; cimsId?: string; status: number }
export const moleculeApi = {
  getAll:  (params?: { start?: number; limit?: number; value?: string }) => 
    api.get<ApiResponse<any>>('/molecules', { params }).then(r => {
      const d = r.data.data;
      return Array.isArray(d) ? d : (d?.content ?? []);
    }),
  getPaginated: (params?: { start?: number; limit?: number; value?: string }) => 
    api.get<ApiResponse<PageResponse<Molecule>>>('/molecules', { params }).then(r => r.data.data!),
  create:  (b: Omit<Molecule,'id'>) => api.post<ApiResponse<Molecule>>('/molecules', b).then(r => r.data.data!),
  update:  (id: string, b: Partial<Molecule>) => api.put<ApiResponse<Molecule>>('/molecules', { ...b, id }).then(r => r.data.data!),
}

// ── Print Template ─────────────────────────────────────────────────────────
export interface PrintTemplate {
  id: string
  name: string
  documentType: string
  printMode: 'HTML' | 'DOT_MATRIX'
  pageSize: string
  height: string
  width: string
  marginTop: string
  marginBottom: string
  marginLeft: string
  marginRight: string
  margin?: string
  pugTemplate?: string
  content?: string
  defaultPrinter?: string
  isDefault: boolean
  status: number
}

export const printTemplateApi = {
  getPaginated: (params?: { start?: number; limit?: number; value?: string }) =>
    api.get<ApiResponse<PageResponse<PrintTemplate>>>('/print-templates/page', { params }).then(r => r.data.data!),
  getAll:  () =>
    api.get<ApiResponse<PrintTemplate[]>>('/print-templates').then(r => r.data.data ?? []),
  getById: (id: string) =>
    api.get<ApiResponse<PrintTemplate>>(`/print-templates/${id}`).then(r => r.data.data!),
  getByType: (documentType: string) =>
    api.get<ApiResponse<PrintTemplate>>(`/print-templates/by-type/${documentType}`).then(r => r.data.data!),
  create:  (b: Omit<PrintTemplate, 'id'>) =>
    api.post<ApiResponse<PrintTemplate>>('/print-templates', b).then(r => r.data.data!),
  update:  (id: string, b: Partial<PrintTemplate>) =>
    api.put<ApiResponse<PrintTemplate>>(`/print-templates/${id}`, { ...b, id }).then(r => r.data.data!),
  remove:  (id: string) =>
    api.delete(`/print-templates/${id}`),
}

// ── Result Template (Diagnostic) – thin wrapper over diagTemplateApi ───────
export interface ResultTemplatePayload {
  name: string;
  diagnosticType: string;
  format: string;
  chargeId?: string;
  specimenId?: string;
  departmentId?: string;
  orderNumber?: number;
  header?: string;
  method?: string;
  templateHtml?: string;
  labTemplateDetails?: Array<{
    id?: string;
    resultName: string;
    normalRange?: string;
    normalRangeExp?: string;
    unit?: string;
    labType: string;
    orderNumber: number;
    rowCount?: number;
    status?: number;
  }>;
}
export const resultTemplateApi = {
  getPaginated: (params?: { start?: number; limit?: number; value?: string }) => api.get<ApiResponse<PageResponse<any>>>('/diagTemplate/page', { params }).then(r => r.data.data!),
  create: (b: ResultTemplatePayload) => {
    const { departmentId, ...rest } = b;
    const payload = {
      ...rest,
      department: departmentId ? { id: departmentId } : null
    };
    return api.post<ApiResponse<any>>('/diagTemplate?isNew=true', payload).then(r => r.data.data!);
  },
  update: (id: string, b: Partial<ResultTemplatePayload>) => {
    const { departmentId, ...rest } = b;
    const payload = {
      ...rest,
      id,
      ...(departmentId !== undefined ? { department: departmentId ? { id: departmentId } : null } : {})
    };
    return api.post<ApiResponse<any>>('/diagTemplate?isNew=false', payload).then(r => r.data.data!);
  },
  remove: (id: string) => api.delete(`/diagTemplate/${id}`),
  getDepartments: () => api.get<ApiResponse<any[]>>('/diagTemplate/departments').then(r => r.data.data ?? []),
}

// ── Staff ──────────────────────────────────────────────────────────────────
export interface Staff { id: string; name: string; staffType: string; contact?: string; email?: string; designation?: string; status: 'ACTIVE' | 'INACTIVE' | number }
export const staffApi = {
  getPaginated: (params?: { start?: number; limit?: number; value?: string }) => api.get<ApiResponse<PageResponse<Staff>>>('/staff/page', { params }).then(r => r.data.data!),
  getAll:  () => api.get<ApiResponse<Staff[]>>('/staff').then(r => r.data.data ?? []),
  create:  (b: Omit<Staff,'id'>) => api.post<ApiResponse<Staff>>('/staff', b).then(r => r.data.data!),
  update:  (id: string, b: Partial<Staff>) => api.put<ApiResponse<Staff>>('/staff', { ...b, id }).then(r => r.data.data!),
  deactivate: (id: string) => api.delete(`/staff/${id}`),
}

export interface Supplier { id: string; name: string; code?: string; contact?: string; contactPerson?: string; email?: string; address?: string; gstNumber?: string; status: any }
export const supplierApi = {
  getPaginated: (params?: { start?: number; limit?: number; value?: string }) => api.get<ApiResponse<PageResponse<Supplier>>>('/suppliers/page', { params }).then(r => r.data.data!),
  getAll:  () => api.get<ApiResponse<Supplier[]>>('/suppliers').then(r => r.data.data ?? []),
  create:  (b: Omit<Supplier,'id'>) => api.post<ApiResponse<Supplier>>('/suppliers', b).then(r => r.data.data!),
  update:  (id: string, b: Partial<Supplier>) => api.put<ApiResponse<Supplier>>(`/suppliers/${id}`, b).then(r => r.data.data!),
  deactivate: (id: string) => api.delete(`/suppliers/${id}`),
}

// ── Tax ────────────────────────────────────────────────────────────────────
export interface TaxCategory {
  id?: string
  taxId?: string
  name: string
  rate: number
}
export interface Tax {
  id: string
  name: string
  rate: number
  hsnCode?: string
  status: number
  categories?: TaxCategory[]
}
export const taxApi = {
  getPaginated: (params?: { start?: number; limit?: number; value?: string }) => api.get<ApiResponse<PageResponse<Tax>>>('/tax/page', { params }).then(r => r.data.data!),
  getAll:     () => api.get<ApiResponse<Tax[]>>('/tax').then(r => r.data.data ?? []),
  create:     (b: Omit<Tax,'id'>) => api.post<ApiResponse<Tax>>('/tax', b).then(r => r.data.data!),
  update:     (id: string, b: Partial<Tax>) => api.put<ApiResponse<Tax>>('/tax', { ...b, id }).then(r => r.data.data!),
  deactivate: (id: string) => api.delete(`/tax/${id}`),
  getDetails: (id: string) => api.get<ApiResponse<TaxCategory[]>>(`/tax/${id}/details`).then(r => r.data.data ?? []),
}

// ── Category (Master Data) ───────────────────────────────────────────────────
export type CategoryType = 'PATIENT' | 'ITEM_CATEGORY' | 'CHARGE' | 'EQUIPMENT' | 'INSTRUMENT';
export type ChargeCategoryType = 'DIAGNOSTICS' | 'CONSULTATION' | 'ROOM_CHARGE' | 'OTHERS' | 'PACKAGES' | 'SURGERY';
export type DiagnosticType = 'LAB' | 'RADIOLOGY';
export type EntityStatus = 'ACTIVE' | 'INACTIVE' | 'DELETED';

export interface Category {
  id?: string | undefined;
  name: string;
  type: CategoryType;
  paramValue?: string | undefined;
  chargeCategoryType?: ChargeCategoryType | undefined;
  subType?: ChargeCategoryType | undefined;
  diagnosticType?: DiagnosticType | undefined;
  status?: EntityStatus | undefined;
}

export const categoryMasterApi = {
  getPaginated: (params?: { start?: number; limit?: number; value?: string }) => api.get<ApiResponse<PageResponse<Category>>>('/category/page', { params }).then(r => r.data.data!),
  getAll: (type?: string) => api.get<ApiResponse<Category[]>>('/category', { params: { type } }).then(r => r.data.data ?? []),
  getTypes: () => api.get<ApiResponse<string[]>>('/category/types').then(r => r.data.data ?? []),
  create: (b: Omit<Category, 'id'>) => api.post<ApiResponse<Category>>('/category', b).then(r => r.data.data!),
  update: (id: string, b: Partial<Category>) => api.put<ApiResponse<Category>>('/category', { ...b, id }).then(r => r.data.data!),
}

// ── Charge (Service Catalog Item) — already in catalogApi ─────────────────
// catalogApi.createItem / updateItem / search already exist.
// This re-exports the relevant types for use in master page.
export type { CreateItemCmd } from '../catalog/catalogApi'

// ── Unit of Measure ────────────────────────────────────────────────────────
export interface UnitOfMeasure { id: string; name: string; symbol?: string; status: number }
export const uomApi = {
  getAll: () => api.get<ApiResponse<UnitOfMeasure[]>>('/uom').then(r => r.data.data ?? []),
}

// ── Charge Master ──────────────────────────────────────────────────────────
export interface ChargeTariff {
  id?: string;
  payorId?: string | null;
  billType: 'CASH' | 'CREDIT' | 'INSURANCE';
  rate: number;
}

export interface PackageCharge {
  id?: string;
  subCharge?: { id?: string; name: string; categoryId: string } | null;
  categoryId?: string | null;
  quantity: number;
  amount: number;
  mode: boolean; // true = include, false = exclude
}

export interface Charge {
  id?: string;
  name: string;
  categoryId: string;
  chargeType: 'CHARGE' | 'PACKAGE' | 'IP';
  quantitative?: boolean;
  tariffs: ChargeTariff[];
  packageCharges?: PackageCharge[];
  startDate?: string;
  endDate?: string;
  status?: number;
}

export const chargeApi = {
  getPaginated: (params?: { start?: number; limit?: number; value?: string }) =>
    api.get<ApiResponse<PageResponse<Charge>>>('/charge/page', { params }).then(r => r.data.data!),
  getAll: (value?: string) =>
    api.get<ApiResponse<Charge[]>>('/charge', { params: { value } }).then(r => r.data.data!),
  getById: (id: string) => api.get<ApiResponse<Charge>>(`/charge/${id}`).then(r => r.data.data!),
  create: (b: Charge) => api.post<ApiResponse<Charge>>('/charge', b).then(r => r.data.data!),
  update: (b: Charge) => api.put<ApiResponse<Charge>>('/charge', b).then(r => r.data.data!),
  remove: (id: string) => api.delete(`/charge/${id}`),
  validateDelete: (id: string) => api.get<ApiResponse<string>>(`/charge/validateDelete/${id}`).then(r => r.data.data!),
  searchByName: (searchTerm: string) => api.get<ApiResponse<Charge[]>>(`/charge/search/name/${searchTerm}`).then(r => r.data.data ?? []),
}



// ── Frequency Master ───────────────────────────────────────────────────────────
export interface FrequencyItem { id: string; name: string; value: number; status?: any }

export const frequencyMasterApi = {
  getAll:      ()                        => api.get<ApiResponse<FrequencyItem[]>>('/frequency').then(r => r.data.data ?? []),
  getPaginated:(params?: { start?: number; limit?: number; value?: string }) =>
    api.get<ApiResponse<any>>('/frequency/page', { params }).then(r => r.data.data!),
  create:      (b: Omit<FrequencyItem,'id'>) => api.post<ApiResponse<FrequencyItem>>('/frequency', b).then(r => r.data.data!),
  update:      (b: FrequencyItem)            => api.put<ApiResponse<FrequencyItem>>('/frequency', b).then(r => r.data.data!),
  remove:      (id: string)                  => api.delete(`/frequency/${id}`),
}
