import { lazy, Suspense } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { ProtectedRoute } from '../components/layout/ProtectedRoute'
import { useAuthStore } from '../store/authStore'

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

// Clinical
const LoginPage               = lazy(() => import('../features/auth/pages/LoginPage'))
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
const FavoritesPage           = lazy(() => import('../features/favorites/pages/FavoritesPage'))
const PrescriptionOrdersPage  = lazy(() => import('../features/sales/pages/PrescriptionOrdersPage'))
const OpQueuePage             = lazy(() => import('../features/opip/pages/OpQueuePage'))
const OpCaseSheetPage         = lazy(() => import('../features/opip/pages/OpCaseSheetPage'))
const IpCaseSheetPage         = lazy(() => import('../features/opip/pages/IpCaseSheetPage'))
const IpWardPage              = lazy(() => import('../features/opip/pages/IpWardPage'))
const TemplateListPage        = lazy(() => import('../features/casesheet/pages/TemplateListPage'))
const TemplateFormPage        = lazy(() => import('../features/casesheet/pages/TemplateFormPage'))

// Finance
const BillingListPage         = lazy(() => import('../features/billing/pages/BillingListPage'))
const BillingPage             = lazy(() => import('../features/billing/pages/BillingPage'))
const CreateBillPage          = lazy(() => import('../features/billing/pages/CreateBillPage'))
const RecordPaymentPage       = lazy(() => import('../features/billing/pages/RecordPaymentPage'))
const TotalDiscountPage       = lazy(() => import('../features/billing/pages/TotalDiscountPage'))
const RefundChargePage        = lazy(() => import('../features/billing/pages/RefundChargePage'))
const InsurancePage           = lazy(() => import('../features/insurance/pages/InsurancePage'))
const SalesLayout             = lazy(() => import('../features/sales/pages/SalesLayout'))
const PharmacySalesPage       = lazy(() => import('../features/sales/pages/PharmacySalesPage'))
const SalesHistoryPage        = lazy(() => import('../features/sales/pages/SalesHistoryPage'))
const SalesViewPage           = lazy(() => import('../features/sales/pages/SalesViewPage'))
const SalesReturnPage         = lazy(() => import('../features/sales/pages/SalesReturnPage'))
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
const PrefixConfigPage        = lazy(() => import('../features/prefix/pages/PrefixConfigPage'))
const SystemConfigPage        = lazy(() => import('../features/config/pages/SystemConfigPage'))
const SmsTemplatesPage        = lazy(() => import('../features/config/pages/SmsTemplatesPage'))
const BulkImportPage          = lazy(() => import('../features/bulkimport/pages/BulkImportPage'))
const SettingsPage            = lazy(() => import('../features/settings/pages/SettingsPage'))
const ConsultantSlotsPage     = lazy(() => import('../features/settings/pages/ConsultantSlotsPage'))
const MasterDataPage          = lazy(() => import('../features/masters/pages/MasterDataPage'))

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
          <Route index element={<Navigate to="/patients" replace />} />

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
          <Route path="/favorites"  element={<FavoritesPage />} />
          <Route path="/prescription-orders" element={<PrescriptionOrdersPage />} />
          <Route path="/op-queue"   element={<OpQueuePage />} />
          <Route path="/ip-ward"    element={<IpWardPage />} />
          <Route path="/op-casesheet/:encounterId"  element={<OpCaseSheetPage />} />
          <Route path="/ip-casesheet/:encounterId"  element={<IpCaseSheetPage />} />
          <Route path="/admin/casesheet-templates" element={<PermissionRoute featureKey="SETTINGS_CASESHEET_TEMPLATE" element={<TemplateListPage />} />} />
          <Route path="/admin/casesheet-templates/new" element={<PermissionRoute featureKey="SETTINGS_CASESHEET_TEMPLATE" element={<TemplateFormPage />} />} />
          <Route path="/admin/casesheet-templates/:templateId" element={<PermissionRoute featureKey="SETTINGS_CASESHEET_TEMPLATE" element={<TemplateFormPage />} />} />

          {/* Finance */}
          <Route path="/billing">
            <Route index element={<Navigate to="op" replace />} />
            <Route path="op" element={<BillingListPage type="OP" key="OP" />} />
            <Route path="ip" element={<BillingListPage type="IP" key="IP" />} />
            {/* NEW: Create Bill as a page (was modal) */}
            <Route path="create" element={<CreateBillPage />} />
            <Route path=":billId" element={<BillingPage />} />
            {/* NEW: Record Payment as a page (was modal) */}
            <Route path=":billId/payment" element={<RecordPaymentPage />} />
            {/* NEW: Total Discount as a page (was modal) */}
            <Route path=":billId/discount" element={<TotalDiscountPage />} />
            {/* NEW: Refund Charge / Advance Refund as a page (was modal) */}
            <Route path=":billId/refund" element={<RefundChargePage />} />
          </Route>

          <Route path="/insurance"  element={<InsurancePage />} />
          <Route path="/sales" element={<SalesLayout />}>
            <Route index element={<Navigate to="sales" replace />} />
            <Route path="sales" element={<PharmacySalesPage />} />
            <Route path="salesHistory" element={<SalesHistoryPage />} />
            <Route path="salesHistory/view/:saleId" element={<SalesViewPage />} />
            <Route path="salesReturn" element={<SalesReturnPage />} />
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
          <Route path="/admin/prefix"      element={<PermissionRoute featureKey="SETTINGS_PREFIX" element={<PrefixConfigPage />} />} />
          <Route path="/admin/config"      element={<PermissionRoute featureKey="SETTINGS_HOSPITALPROFILE" element={<SystemConfigPage />} />} />
          <Route path="/admin/sms"         element={<PermissionRoute featureKey="SETTINGS_SMS_TEMPLATE" element={<SmsTemplatesPage />} />} />
          <Route path="/admin/bulk-import" element={<PermissionRoute featureKey="DATA_IMPORT" element={<BulkImportPage />} />} />
          <Route path="/settings/bulkUpload" element={<PermissionRoute featureKey="DATA_IMPORT" element={<BulkImportPage />} />} />
          <Route path="/settings"          element={<PermissionRoute featureKey="SETTINGS_CONFIGURATION" element={<SettingsPage />} />} />
          {/* NEW: Consultant Slots as a page (was modal) */}
          <Route path="/settings/consultants/:consultantId/slots" element={<PermissionRoute featureKey="SETTINGS_CONSULTANT" element={<ConsultantSlotsPage />} />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  )
}
