/**
 * MasterDataPage.tsx
 * ─────────────────────────────────────────────────────────────────────────────
 * Unified master-data management hub with 20 tabs.
 */
import { useSearchParams } from 'react-router-dom'
import { useAuthStore } from '../../../store/authStore'
import {
  Building2, Bed, Tag, LayoutList, Coins, UserCog, Timer, Hospital, Package,
  Handshake, Hash, Printer, Microscope, ShieldCheck, TestTube, Users, Truck,
  Percent, UsersRound
} from 'lucide-react'

import AccountUnitTab from '../components/tabs/AccountUnitTab'
import BedTab from '../components/tabs/BedTab'
import BedTypeTab from '../components/tabs/BedTypeTab'
import CategoryTab from '../components/tabs/CategoryTab'
import ChargeTab from '../components/tabs/ChargeTab'
import ConsultantTab from '../components/tabs/ConsultantTab'
import DepartmentTab from '../components/tabs/DepartmentTab'
import FrequencyTab from '../components/tabs/FrequencyTab'
import HospitalProfileTab from '../components/tabs/HospitalProfileTab'
import ItemTab from '../components/tabs/ItemTab'
import PayersTab from '../components/tabs/PayersTab'
import PrefixTab from '../components/tabs/PrefixTab'
// import MoleculesTab from '../components/tabs/MoleculesTab'
import PrintTemplateTab from '../components/tabs/PrintTemplateTab'
import ResultTemplateTab from '../components/tabs/ResultTemplateTab'
import RolesTab from '../components/tabs/RolesTab'
import SpecimenTab from '../components/tabs/SpecimenTab'
import StaffTab from '../components/tabs/StaffTab'
import SupplierTab from '../components/tabs/SupplierTab'
import TaxTab from '../components/tabs/TaxTab'
import UsersTab from '../components/tabs/UsersTab'

const TABS = [
  { id: 'account_unit', label: 'Account Unit', icon: Building2, featureKey: 'SETTINGS_ACCOUNTUNIT' },
  { id: 'bed', label: 'Bed', icon: Bed, featureKey: 'SETTINGS_BED' },
  { id: 'bed_type', label: 'Bed Type', icon: Tag, featureKey: 'SETTINGS_BEDTYPE' },
  { id: 'category', label: 'Category', icon: LayoutList, featureKey: 'SETTINGS_CATEGORY' },
  { id: 'charge', label: 'Charge', icon: Coins, featureKey: 'SETTINGS_CHARGES' },
  { id: 'consultant', label: 'Consultant', icon: UserCog, featureKey: 'SETTINGS_CONSULTANT' },
  { id: 'department', label: 'Department', icon: Building2, featureKey: 'SETTINGS_DEPARTMENT' },
  { id: 'frequency',       label: 'Frequency',       icon: Timer, featureKey: 'SETTINGS_FREQUENCY' },
  { id: 'hospital_profile', label: 'Hospital Profile', icon: Hospital, featureKey: 'SETTINGS_HOSPITALPROFILE' },
  { id: 'item', label: 'Item', icon: Package, featureKey: 'SETTINGS_ITEM' },
  { id: 'payers', label: 'Payers', icon: Handshake, featureKey: 'SETTINGS_PAYERTYPE' },
  { id: 'prefix', label: 'Prefix', icon: Hash, featureKey: 'SETTINGS_PREFIX' },
  // { id: 'molecules', label: 'Molecules', icon: '🧬', featureKey: 'SETTINGS_MOLECULE' },
  { id: 'print_template', label: 'Print Template', icon: Printer, featureKey: 'SETTINGS_PRINT_TEMPLATE' },
  { id: 'result_template', label: 'Result Template', icon: Microscope, featureKey: 'SETTINGS_RESULT_TEMPLATE' },
  { id: 'roles', label: 'Roles', icon: ShieldCheck, featureKey: 'SETTINGS_ROLE' },
  { id: 'specimen', label: 'Specimen', icon: TestTube, featureKey: 'SETTINGS_SPECIMEN' },
  { id: 'staff', label: 'Staff', icon: Users, featureKey: 'SETTINGS_STAFF' },
  { id: 'supplier', label: 'Supplier', icon: Truck, featureKey: 'SETTINGS_SUPPLIER' },
  { id: 'tax', label: 'Tax', icon: Percent, featureKey: 'SETTINGS_TAX' },
  { id: 'users', label: 'Users', icon: UsersRound, featureKey: 'SETTINGS_USERS' },
] as const
type TabId = typeof TABS[number]['id']

export default function MasterDataPage() {
  const [sp] = useSearchParams()
  const tabParam = sp.get('tab') as TabId
  const { hasPermission } = useAuthStore()

  // Filter allowed tabs for current user
  const allowedTabs = TABS.filter(t => !t.featureKey || hasPermission(t.featureKey))
  const fallbackTab = allowedTabs[0]?.id || 'bed'

  const activeTab = (tabParam && allowedTabs.some(t => t.id === tabParam)) ? tabParam : fallbackTab

  return (
    <div className="space-y-0 max-w-4xl mx-auto">
      <div className="w-full">
        {/* Main content area */}
        <main className="w-full">
          {activeTab === 'account_unit' && hasPermission('SETTINGS_ACCOUNTUNIT') && <AccountUnitTab />}
          {activeTab === 'bed' && hasPermission('SETTINGS_BED') && <BedTab />}
          {activeTab === 'bed_type' && hasPermission('SETTINGS_BEDTYPE') && <BedTypeTab />}
          {activeTab === 'category' && hasPermission('SETTINGS_CATEGORY') && <CategoryTab />}
          {activeTab === 'charge' && hasPermission('SETTINGS_CHARGES') && <ChargeTab />}
          {activeTab === 'consultant' && hasPermission('SETTINGS_CONSULTANT') && <ConsultantTab />}
          {activeTab === 'department' && hasPermission('SETTINGS_DEPARTMENT') && <DepartmentTab />}
          {activeTab === 'frequency'       && hasPermission('SETTINGS_FREQUENCY') && <FrequencyTab />}
          {activeTab === 'hospital_profile' && hasPermission('SETTINGS_HOSPITALPROFILE') && <HospitalProfileTab />}
          {activeTab === 'item' && hasPermission('SETTINGS_ITEM') && <ItemTab />}
          {activeTab === 'payers' && hasPermission('SETTINGS_PAYERTYPE') && <PayersTab />}
          {activeTab === 'prefix' && hasPermission('SETTINGS_PREFIX') && <PrefixTab />}
          {activeTab === 'print_template' && hasPermission('SETTINGS_PRINT_TEMPLATE') && <PrintTemplateTab />}
          {activeTab === 'result_template' && hasPermission('SETTINGS_RESULT_TEMPLATE') && <ResultTemplateTab />}
          {activeTab === 'roles' && hasPermission('SETTINGS_ROLE') && <RolesTab />}
          {activeTab === 'specimen' && hasPermission('SETTINGS_SPECIMEN') && <SpecimenTab />}
          {activeTab === 'staff' && hasPermission('SETTINGS_STAFF') && <StaffTab />}
          {activeTab === 'supplier' && hasPermission('SETTINGS_SUPPLIER') && <SupplierTab />}
          {activeTab === 'tax' && hasPermission('SETTINGS_TAX') && <TaxTab />}
          {activeTab === 'users' && hasPermission('SETTINGS_USERS') && <UsersTab />}
        </main>
      </div>
    </div>
  )
}
