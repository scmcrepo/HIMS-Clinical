import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'

export interface ReportMeta { name: string; description: string; category: string }
export interface ReportParam { name: string; type: string; required: boolean; defaultValue: string; description: string }
export interface ReportInfo { reportName: string; description: string; category: string; parameters: ReportParam[] }

const REPORT_CATEGORY_PATHS: Record<string, string> = {
  patient_registration_daywise: 'patient',
  patient_registration_details: 'patient',
  consultantwise_registration: 'patient',
  departmentwise_registration: 'patient',
  patient_registration: 'patient',

  appointments_daywise: 'appointments',
  appointment_scheduled_details: 'appointments',
  appointment_cancelled_details: 'appointments',
  appointments_consultant: 'appointments',
  appointments_cancelled_daywise: 'appointments',
  appointments_cancelled_consultant: 'appointments',

  encounters_report: 'encounters',
  visit_details: 'encounters',
  consultant_wise_visit: 'encounters',
  department_wise_visit: 'encounters',
  department_wise_consulted: 'encounters',
  consultant_wise_consulted: 'encounters',
  consultation_summary: 'encounters',
  consultant_wise_visit_detail: 'encounters',
  dept_wise_consultant_visit: 'encounters',

  bills_raised_daywise: 'billing',
  bills_cancelled_daywise: 'billing',
  discount_report: 'billing',
  bills_overdue: 'billing',
  unsettled_bills: 'billing',
  bill_detail: 'billing',
  // Kept on the billing path: it is the pre-existing generic claim summary and
  // is served by BillingReportService, not the new insurance module.
  insurance_summary: 'billing',
  bill_raised_summary: 'billing',
  bill_cancelled_summary: 'billing',
  discount_summary: 'billing',
  outstanding_bills_summary: 'billing',
  ip_outstanding_bills_summary: 'billing',
  overdue_bills_summary: 'billing',

  net_collection_summary: 'collections',
  net_collection_detail: 'collections',
  receipts_summary: 'collections',
  receipts_detail: 'collections',
  deposits_summary: 'collections',
  deposits_detail: 'collections',
  refunds_summary: 'collections',
  refunds_detail: 'collections',
  petty_cash_summary: 'collections',
  petty_cash_detail: 'collections',

  lab_tests_done: 'diagnostics',
  lab_tests_done_detail: 'diagnostics',
  lab_pending: 'diagnostics',
  lab_pending_detail: 'diagnostics',
  diagnostics_result_finalisation: 'diagnostics',
  diagnostics_result_dispatch: 'diagnostics',

  net_revenue_report: 'revenue',
  consultant_revenue_opip: 'revenue',
  department_revenue_opip: 'revenue',
  consultant_revenue: 'revenue',
  department_revenue: 'revenue',
  room_revenue: 'revenue',
  consultant_earnings: 'revenue',

  admissions_report: 'inpatient',
  discharges_report: 'inpatient',
  bed_occupancy_period: 'inpatient',
  beds_transferred: 'inpatient',
  bed_occupancy: 'inpatient',
  ip_discharge_summary: 'inpatient',

  purchase_orders_report: 'procurement',
  goods_received_report: 'procurement',
  goods_returned_report: 'procurement',
  purchase_register: 'procurement',

  current_stock: 'inventory',
  expired_items: 'inventory',
  items_expiring_month: 'inventory',
  slow_moving_items: 'inventory',
  zero_stock_items: 'inventory',
  scheduled_drug_sales: 'inventory',
  below_reorder_level: 'inventory',
  stock_adjustments: 'inventory',

  // WO-021 — the ten insurance desk reports.
  preauth_raised: 'insurance',
  preauth_status: 'insurance',
  enhancement_raised: 'insurance',
  enhancement_status: 'insurance',
  claim_dispatch: 'insurance',
  disallowance_summary: 'insurance',
  disallowance_detail: 'insurance',
  document_pending_status: 'insurance',
  ip_outstanding_credit_bills: 'insurance',
  insurance_ageing_analysis: 'insurance',

  pharmacy_sales_bills: 'pharmacy',
  pharmacy_sales_collection: 'pharmacy',
  stock_ledger: 'pharmacy',
}

function getReportPath(name: string): string {
  const cat = REPORT_CATEGORY_PATHS[name]
  return cat ? `/report/${cat}` : `/report`
}

/**
 * Converts a snake_case report name to Title Case.
 * e.g. "patient_registration_details" → "Patient Registration Details"
 */
function formatReportName(name: string): string {
  return name
    .split('_')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

/**
 * Builds the download filename in the format:
 * "BranchName_Report Name_DD-MM-YYYY_HH-MM-SS.ext"
 */
function buildDownloadFileName(reportName: string, ext: string, branchName?: string): string {
  const now = new Date()
  const dd = String(now.getDate()).padStart(2, '0')
  const mm = String(now.getMonth() + 1).padStart(2, '0')
  const yyyy = now.getFullYear()
  const hh = String(now.getHours()).padStart(2, '0')
  const min = String(now.getMinutes()).padStart(2, '0')

  const datePart = `${dd}-${mm}-${yyyy}`
  const timePart = `${hh}-${min}`
  const readableName = formatReportName(reportName)
  const branch = branchName?.trim() || 'Report'

  return `${branch}_${readableName}_${datePart}_${timePart}.${ext}`
}

export const reportApi = {
  listReports: () => api.get<ApiResponse<ReportMeta[]>>('/report/info').then(r => r.data.data ?? []),
  getReportInfo: (name: string) => 
    api.get<ApiResponse<ReportInfo>>(`${getReportPath(name)}/info/${name}`).then(r => r.data.data!),
  executeHtml: (name: string, params: Record<string, string>) =>
    api.post<ApiResponse<{ htmlContent: string }>>(`${getReportPath(name)}/${name}?format=HTML`, params).then(r => r.data.data!),
  executeJson: (name: string, params: Record<string, string>) =>
    api.post<ApiResponse<any[]>>(`${getReportPath(name)}/${name}?format=JSON`, params).then(r => r.data.data ?? []),
  downloadPdf: async (name: string, params: Record<string, string>, branchName?: string) => {
    const res = await api.post(`${getReportPath(name)}/${name}?format=PDF`, params, { responseType: 'blob' })
    const url = URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }))
    const fileName = buildDownloadFileName(name, 'pdf', branchName)
    const a = document.createElement('a'); a.href = url; a.download = fileName; a.click()
    URL.revokeObjectURL(url)
  },
  downloadXlsx: async (name: string, params: Record<string, string>, branchName?: string) => {
    const res = await api.post(`${getReportPath(name)}/${name}?format=XLSX`, params, { responseType: 'blob' })
    const url = URL.createObjectURL(new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }))
    const fileName = buildDownloadFileName(name, 'xlsx', branchName)
    const a = document.createElement('a'); a.href = url; a.download = fileName; a.click()
    URL.revokeObjectURL(url)
  },
}
