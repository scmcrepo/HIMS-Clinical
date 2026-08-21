import { lazy, Suspense } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { ProtectedRoute } from '../components/layout/ProtectedRoute'
import { useAuthStore } from '../store/authStore'
import { NAV_GROUPS } from '../components/layout/Sidebar'

interface PermissionRouteProps {
  featureKey: string
  element: React.ReactElement
}

function PermissionRoute({ featureKey, element }: PermissionRouteProps) {
  const hasPermission = useAuthStore(s => s.hasPermission(featureKey))
  if (!hasPermission) {
    return <Navigate to="/" replace />
  }
  return element
}

/** Resolves the first sidebar route the current user has permission to access */
function DefaultRedirect() {
  const { hasPermission } = useAuthStore()
  const user = useAuthStore(s => s.user)

  // Hospital Admin should always land on Reports
  if (user?.isHospitalAdmin) {
    return <Navigate to="/reports/patients" replace />
  }

  // Branch Admin should always land on Reports
  if (user?.roles?.includes('BRANCH_ADMIN')) {
    return <Navigate to="/reports/patients" replace />
  }

  // Nurse should always land on IP Patient List (In Patient List)
  if (user?.roles?.includes('NURSE')) {
    return <Navigate to="/ip-ward?tab=ward&role=nurse" replace />
  }

  // Pharmacist should always land on Prescribed Orders
  if (user?.roles?.includes('PHARMACIST')) {
    return <Navigate to="/prescription-orders" replace />
  }

  for (const group of NAV_GROUPS) {
    // Skip group if it has a featureKey the user can't access
    if (group.featureKey && !hasPermission(group.featureKey)) continue

    // If the group itself is a link (no sub-items)
    if (group.to) return <Navigate to={group.to} replace />

    // Otherwise find the first permitted child item
    if (group.items) {
      for (const item of group.items) {
        if (!item.featureKey || hasPermission(item.featureKey)) {
          return <Navigate to={item.to} replace />
        }
      }
    }
  }

  // Fallback if somehow nothing is accessible
  return <Navigate to="/patients" replace />
}

// Clinical
const LoginPage               = lazy(() => import('../features/auth/pages/LoginPage'))
const AgentTokensPage         = lazy(() => import('../features/agent-tokens/AgentTokensPage'))
const ClaimsControlTowerPage  = lazy(() => import('../features/claims/pages/ClaimsControlTowerPage'))
const PreAuthTrackerPage      = lazy(() => import('../features/preauth/pages/PreAuthTrackerPage'))
const PatientListPage         = lazy(() => import('../features/patient/pages/PatientListPage'))
const PatientRegistrationPage = lazy(() => import('../features/patient/pages/PatientRegistrationPage'))
const PatientDetailPage       = lazy(() => import('../features/patient/pages/PatientDetailPage'))
const PatientEditPage         = lazy(() => import('../features/patient/pages/PatientEditPage'))
const EncounterPage           = lazy(() => import('../features/encounter/pages/EncounterPage'))
const EncounterListPage       = lazy(() => import('../features/encounter/pages/EncounterListPage'))
const CreateEncounterPage     = lazy(() => import('../features/encounter/pages/CreateEncounterPage'))
const AppointmentPage         = lazy(() => import('../features/appointment/pages/AppointmentPage'))
const BookAppointmentPage     = lazy(() => import('../features/appointment/pages/BookAppointmentPage'))
const RescheduleAppointmentPage = lazy(() => import('../features/appointment/pages/RescheduleAppointmentPage'))
const DiagnosticsPage         = lazy(() => import('../features/diagnostic/pages/DiagnosticsPage'))
const LabReportPage           = lazy(() => import('../features/diagnostic/pages/LabReportPage'))
const RadiologyReportPage     = lazy(() => import('../features/diagnostic/pages/RadiologyReportPage'))
const SpecimenCollectionPage  = lazy(() => import('../features/diagnostic/pages/SpecimenCollectionPage'))
const BedManagementPage       = lazy(() => import('../features/bed/pages/BedManagementPage'))
const OrderSetPage            = lazy(() => import('../features/orderset/pages/OrderSetPage'))
// const FavoritesPage           = lazy(() => import('../features/favorites/pages/FavoritesPage'))
const PrescriptionOrdersPage  = lazy(() => import('../features/sales/pages/PrescriptionOrdersPage'))
const OpQueuePage             = lazy(() => import('../features/opip/pages/OpQueuePage'))
const OpCaseSheetPage         = lazy(() => import('../features/opip/pages/OpCaseSheetPage'))
const IpCaseSheetPage         = lazy(() => import('../features/opip/pages/IpCaseSheetPage'))
const IpWardPage              = lazy(() => import('../features/opip/pages/IpWardPage'))
const TemplateListPage        = lazy(() => import('../features/casesheet/pages/TemplateListPage'))
const TemplateFormPage        = lazy(() => import('../features/casesheet/pages/TemplateFormPage'))
const DischargeTemplateListPage = lazy(() => import('../features/casesheet/pages/DischargeTemplateListPage'))
const DischargeTemplateFormPage = lazy(() => import('../features/casesheet/pages/DischargeTemplateFormPage'))
const CopilotDashboard          = lazy(() => import('../features/copilot/CopilotDashboard'))

// Finance
const BillingListPage         = lazy(() => import('../features/billing/pages/BillingListPage'))
const BillingPage             = lazy(() => import('../features/billing/pages/BillingPage'))
const CreateBillPage          = lazy(() => import('../features/billing/pages/CreateBillPage'))
const RecordPaymentPage       = lazy(() => import('../features/billing/pages/RecordPaymentPage'))
const DisallowedChargesPage   = lazy(() => import('../features/billing/pages/DisallowedChargesPage'))
const TotalDiscountPage       = lazy(() => import('../features/billing/pages/TotalDiscountPage'))
const RefundChargePage        = lazy(() => import('../features/billing/pages/RefundChargePage'))
const PettyCashPage           = lazy(() => import('../features/billing/pages/PettyCashPage'))
const InsurancePage           = lazy(() => import('../features/insurance/pages/InsurancePage'))
const SalesLayout             = lazy(() => import('../features/sales/pages/SalesLayout'))
const PharmacySalesPage       = lazy(() => import('../features/sales/pages/PharmacySalesPage'))
const SalesHistoryPage        = lazy(() => import('../features/sales/pages/SalesHistoryPage'))
const SalesViewPage           = lazy(() => import('../features/sales/pages/SalesViewPage'))
const SalesReturnPage         = lazy(() => import('../features/sales/pages/SalesReturnPage'))
const StockAdjustmentPage     = lazy(() => import('../features/inventory/pages/StockAdjustmentPage'))
const PatientsReportPage      = lazy(() => import('../features/report/pages/PatientsReportPage'))
const BillsReportPage         = lazy(() => import('../features/report/pages/BillsReportPage'))
const CollectionsReportPage   = lazy(() => import('../features/report/pages/CollectionsReportPage'))
const DiagnosticsReportPage   = lazy(() => import('../features/report/pages/DiagnosticsReportPage'))
const RevenueReportPage       = lazy(() => import('../features/report/pages/RevenueReportPage'))
const InPatientsReportPage    = lazy(() => import('../features/report/pages/InPatientsReportPage'))
const PurchaseReportPage      = lazy(() => import('../features/report/pages/PurchaseReportPage'))
const InventoryReportPage     = lazy(() => import('../features/report/pages/InventoryReportPage'))
const SalesReportPage         = lazy(() => import('../features/report/pages/SalesReportPage'))
const InsuranceReportPage     = lazy(() => import('../features/report/pages/InsuranceReportPage'))
const StocksReportPage        = lazy(() => import('../features/report/pages/StocksReportPage'))


// Inventory
const PurchaseManagementPage  = lazy(() => import('../features/purchase/pages/PurchaseManagementPage'))
const InventoryPage           = lazy(() => import('../features/inventory/pages/InventoryPage'))
const OpeningStockPage        = lazy(() => import('../features/inventory/pages/OpeningStockPage'))

// Admin
const UserManagementPage      = lazy(() => import('../features/user/pages/UserManagementPage'))
const BranchManagementPage    = lazy(() => import('../features/branch/pages/BranchManagementPage'))
const TenantManagementPage    = lazy(() => import('../features/tenant/pages/TenantManagementPage'))
const PrefixConfigPage        = lazy(() => import('../features/prefix/pages/PrefixConfigPage'))
const SystemConfigPage        = lazy(() => import('../features/config/pages/SystemConfigPage'))
const SmsTemplatesPage        = lazy(() => import('../features/config/pages/SmsTemplatesPage'))
const BulkImportPage          = lazy(() => import('../features/bulkimport/pages/BulkImportPage'))
const SettingsPage            = lazy(() => import('../features/settings/pages/SettingsPage'))
const ConsultantSlotsPage     = lazy(() => import('../features/settings/pages/ConsultantSlotsPage'))
const MasterDataPage          = lazy(() => import('../features/masters/pages/MasterDataPage'))
const SmtpConfigPage          = lazy(() => import('../features/config/pages/SmtpConfigPage'))

function PageLoader() {
  return (
    <div className="flex items-center justify-center h-full min-h-48" aria-live="polite">
      <div className="w-6 h-6 border-2 border-neutral-200 border-t-neutral-600 rounded-full animate-spin" role="status" />
    </div>
  )
}


export function AppRouter() {
  return (
    <Suspense fallback={<PageLoader />}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route index element={<DefaultRedirect />} />

          {/* Clinical */}
          <Route path="/patients">
            <Route index element={<PatientListPage />} />
            <Route path="register" element={<PatientRegistrationPage />} />
            <Route path=":patientId" element={<PatientDetailPage />} />
            <Route path=":patientId/edit" element={<PatientEditPage />} />
          </Route>

          <Route path="/encounters">
            <Route index element={<EncounterListPage />} />
            {/* NEW: Create Encounter as a page (was modal) */}
            <Route path="create" element={<CreateEncounterPage />} />
            <Route path=":encounterId" element={<EncounterPage />} />
          </Route>

          <Route path="/appointments">
            <Route index element={<AppointmentPage />} />
            {/* NEW: Book Appointment as a page (was modal) */}
            <Route path="book" element={<BookAppointmentPage />} />
            {/* NEW: Reschedule Appointment as a page (was modal) */}
            <Route path="reschedule" element={<RescheduleAppointmentPage />} />
          </Route>

          <Route path="/diagnostics">
            <Route index element={<DiagnosticsPage />} />
            {/* NEW: Lab Report as a page (was modal) */}
            <Route path="lab-report/:orderId" element={<LabReportPage />} />
            {/* NEW: Radiology Report as a page (was modal) */}
            <Route path="radiology-report/:orderId" element={<RadiologyReportPage />} />
            {/* NEW: Specimen Collection as a page (was modal) */}
            <Route path="specimen/:orderId" element={<SpecimenCollectionPage />} />
          </Route>

          <Route path="/beds"       element={<BedManagementPage />} />
          <Route path="/order-sets" element={<OrderSetPage />} />
          {/* <Route path="/favorites"  element={<PermissionRoute featureKey="SETTINGS_FAVORITES" element={<FavoritesPage />} />} /> */}
          <Route path="/prescription-orders" element={<PrescriptionOrdersPage />} />
          <Route path="/op-queue"   element={<OpQueuePage />} />
          <Route path="/ip-ward"    element={<IpWardPage />} />
          <Route path="/op-casesheet/:encounterId"  element={<OpCaseSheetPage />} />
          <Route path="/ip-casesheet/:encounterId"  element={<IpCaseSheetPage />} />
          <Route path="/admin/casesheet-templates" element={<PermissionRoute featureKey="SETTINGS_CASESHEET_TEMPLATE" element={<TemplateListPage />} />} />
          <Route path="/admin/casesheet-templates/new" element={<PermissionRoute featureKey="SETTINGS_CASESHEET_TEMPLATE" element={<TemplateFormPage />} />} />
          <Route path="/admin/casesheet-templates/:templateId" element={<PermissionRoute featureKey="SETTINGS_CASESHEET_TEMPLATE" element={<TemplateFormPage />} />} />
          <Route path="/admin/discharge-templates" element={<PermissionRoute featureKey="SETTINGS_DISCHARGE_TEMPLATE" element={<DischargeTemplateListPage />} />} />
          <Route path="/admin/discharge-templates/new" element={<PermissionRoute featureKey="SETTINGS_DISCHARGE_TEMPLATE" element={<DischargeTemplateFormPage />} />} />
          <Route path="/admin/discharge-templates/:templateId" element={<PermissionRoute featureKey="SETTINGS_DISCHARGE_TEMPLATE" element={<DischargeTemplateFormPage />} />} />

          {/* Finance */}
          <Route path="/billing">
            <Route index element={<Navigate to="op" replace />} />
            <Route path="op" element={<BillingListPage type="OP" key="OP" />} />
            <Route path="ip" element={<BillingListPage type="IP" key="IP" />} />
            {/* NEW: Create Bill as a page (was modal) */}
            <Route path="create" element={<CreateBillPage />} />
            {/* NEW: Record Payment as a page (was modal) */}
            <Route path=":billId/payment" element={<RecordPaymentPage />} />
            <Route path=":billId/disallowance" element={<DisallowedChargesPage />} />
            {/* NEW: Total Discount as a page (was modal) */}
            <Route path=":billId/discount" element={<TotalDiscountPage />} />
            {/* NEW: Refund Charge / Advance Refund as a page (was modal) */}
            <Route path=":billId/refund" element={<RefundChargePage />} />
            <Route path=":billId" element={<BillingPage />} />
            <Route path="petty-cash" element={<PermissionRoute featureKey="PETTY_CASH" element={<PettyCashPage />} />} />
          </Route>

          <Route path="/insurance"  element={<InsurancePage />} />
          <Route path="/sales" element={<SalesLayout />}>
            <Route index element={<Navigate to="sales" replace />} />
            <Route path="sales" element={<PharmacySalesPage />} />
            <Route path="salesHistory" element={<SalesHistoryPage />} />
            <Route path="salesHistory/view/:saleId" element={<SalesViewPage />} />
            <Route path="salesReturn" element={<SalesReturnPage />} />
            <Route path="stockAdjustment" element={<StockAdjustmentPage />} />
          </Route>
          <Route path="/reports">
            <Route index element={<Navigate to="patients" replace />} />
            <Route path="patients" element={<PatientsReportPage />} />
            <Route path="bills" element={<BillsReportPage />} />
            <Route path="collections" element={<CollectionsReportPage />} />
            <Route path="diagnostics" element={<DiagnosticsReportPage />} />
            <Route path="revenue" element={<RevenueReportPage />} />
            <Route path="in-patients" element={<InPatientsReportPage />} />
            <Route path="purchase" element={<PurchaseReportPage />} />
            <Route path="inventory" element={<InventoryReportPage />} />
            <Route path="stocks" element={<StocksReportPage />} />
            <Route path="sales" element={<SalesReportPage />} />
            <Route path="insurance" element={<InsuranceReportPage />} />
          </Route>

          {/* Inventory */}
          <Route path="/purchase-management" element={<PurchaseManagementPage />} />
          <Route path="/goods-received"  element={<Navigate to="/purchase-management" replace />} />
          <Route path="/purchase-orders" element={<Navigate to="/purchase-management" replace />} />
          <Route path="/inventory"       element={<InventoryPage />} />
          <Route path="/opening-stock"   element={<OpeningStockPage />} />

          {/* Admin */}
          <Route path="/admin/masters"     element={<MasterDataPage />} />
          <Route path="/admin/users"       element={<PermissionRoute featureKey="SETTINGS_USERS" element={<UserManagementPage />} />} />
          <Route path="/admin/branches"    element={<BranchManagementPage />} />
          <Route path="/admin/tenants"     element={<TenantManagementPage />} />
          <Route path="/admin/prefix"      element={<PermissionRoute featureKey="SETTINGS_PREFIX" element={<PrefixConfigPage />} />} />
          {/* Agent credentials (WO-001/T-010). AGENT_TOKEN_MANAGE is seeded by
              V176 for existing tenants and granted to HOSPITAL_ADMIN/ADMIN; new
              tenants get it via TenantService.seedRbac. */}
          <Route path="/admin/agent-tokens" element={<PermissionRoute featureKey="AGENT_TOKEN_MANAGE" element={<AgentTokensPage />} />} />
          {/* Screens 5.2 and 5.3. CLAIM_PAYMENTS, not NHCX_CLAIMS: certifying
              that money arrived is an accounts job, separate from filing. */}
          <Route path="/insurance/claims" element={<PermissionRoute featureKey="CLAIM_PAYMENTS" element={<ClaimsControlTowerPage />} />} />
          <Route path="/insurance/reports" element={<PermissionRoute featureKey="REPORT_INSURANCE" element={<InsuranceReportPage />} />} />
          {/* Screen 4.2. The estimate builder and enhancement modal are mounted
              from the encounter, not here — a pre-auth is raised against a
              specific admission, not from a standalone page. */}
          <Route path="/insurance/preauth" element={<PermissionRoute featureKey="PREAUTH_MANAGE" element={<PreAuthTrackerPage />} />} />
          <Route path="/admin/copilot"      element={<PermissionRoute featureKey="HITL_MANAGE" element={<CopilotDashboard />} />} />
          <Route path="/admin/config"      element={<PermissionRoute featureKey="SETTINGS_HOSPITALPROFILE" element={<SystemConfigPage />} />} />
          <Route path="/admin/sms"         element={<PermissionRoute featureKey="SETTINGS_CONFIGURATION" element={<SmsTemplatesPage />} />} />
          <Route path="/admin/bulk-import" element={<PermissionRoute featureKey="DATA_IMPORT" element={<BulkImportPage />} />} />
          <Route path="/settings/bulkUpload" element={<PermissionRoute featureKey="DATA_IMPORT" element={<BulkImportPage />} />} />
          <Route path="/settings"          element={<PermissionRoute featureKey="SETTINGS_CONFIGURATION" element={<SettingsPage />} />} />
          <Route path="/admin/smtp-config" element={<PermissionRoute featureKey="SETTINGS_SMTP" element={<SmtpConfigPage />} />} />
          {/* NEW: Consultant Slots as a page (was modal) */}
          <Route path="/settings/consultants/:consultantId/slots" element={<PermissionRoute featureKey="SETTINGS_CONSULTANT" element={<ConsultantSlotsPage />} />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  )
}
