/**
 * MasterDataPage.tsx
 * ─────────────────────────────────────────────────────────────────────────────
 * Unified master-data management hub with 20 tabs.
 */
import { useSearchParams } from 'react-router-dom'

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
  { id: 'account_unit', label: 'Account Unit', icon: '🏦' },
  { id: 'bed', label: 'Bed', icon: '🛏️' },
  { id: 'bed_type', label: 'Bed Type', icon: '🏷️' },
  { id: 'category', label: 'Category', icon: '📋' },
  { id: 'charge', label: 'Charge', icon: '💳' },
  { id: 'consultant', label: 'Consultant', icon: '👨‍⚕️' },
  { id: 'department', label: 'Department', icon: '🏢' },
  { id: 'frequency',       label: 'Frequency',       icon: '⏱️' },
  { id: 'hospital_profile', label: 'Hospital Profile', icon: '🏥' },
  { id: 'item', label: 'Item', icon: '📦' },
  { id: 'payers', label: 'Payers', icon: '🤝' },
  { id: 'prefix', label: 'Prefix', icon: '🔢' },
  // { id: 'molecules', label: 'Molecules', icon: '🧬' },
  { id: 'print_template', label: 'Print Template', icon: '🖨️' },
  { id: 'result_template', label: 'Result Template', icon: '🔬' },
  { id: 'roles', label: 'Roles', icon: '🔑' },
  { id: 'specimen', label: 'Specimen', icon: '🧪' },
  { id: 'staff', label: 'Staff', icon: '👷' },
  { id: 'supplier', label: 'Supplier', icon: '🚚' },
  { id: 'tax', label: 'Tax', icon: '📝' },
  { id: 'users', label: 'Users', icon: '👥' },
] as const
type TabId = typeof TABS[number]['id']

export default function MasterDataPage() {
  const [sp] = useSearchParams()
  const tabParam = sp.get('tab') as TabId
  const activeTab = (tabParam && TABS.some(t => t.id === tabParam)) ? tabParam : 'account_unit'

  return (
    <div className="space-y-0 max-w-4xl mx-auto">
      {/* Page header */}
      {/* <div className="mb-6">
        <h2 className="text-2xl font-bold text-gray-900 tracking-tight">Master Data</h2>
        <p className="text-sm text-gray-500 mt-0.5">Configure all reference data used across the HMS</p>
      </div> */}

      <div className="w-full">
        {/* Main content area */}
        <main className="w-full">
          {activeTab === 'account_unit' && <AccountUnitTab />}
          {activeTab === 'bed' && <BedTab />}
          {activeTab === 'bed_type' && <BedTypeTab />}
          {activeTab === 'category' && <CategoryTab />}
          {activeTab === 'charge' && <ChargeTab />}
          {activeTab === 'consultant' && <ConsultantTab />}
          {activeTab === 'department' && <DepartmentTab />}
          {activeTab === 'frequency'       && <FrequencyTab />}
          {activeTab === 'hospital_profile' && <HospitalProfileTab />}
          {activeTab === 'item' && <ItemTab />}
          {activeTab === 'payers' && <PayersTab />}
          {activeTab === 'prefix' && <PrefixTab />}
          {/* {activeTab === 'molecules' && <MoleculesTab />} */}
          {activeTab === 'print_template' && <PrintTemplateTab />}
          {activeTab === 'result_template' && <ResultTemplateTab />}
          {activeTab === 'roles' && <RolesTab />}
          {activeTab === 'specimen' && <SpecimenTab />}
          {activeTab === 'staff' && <StaffTab />}
          {activeTab === 'supplier' && <SupplierTab />}
          {activeTab === 'tax' && <TaxTab />}
          {activeTab === 'users' && <UsersTab />}
        </main>
      </div>
    </div>
  )
}
