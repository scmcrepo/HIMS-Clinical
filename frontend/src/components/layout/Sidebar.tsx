import { useState, useEffect } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { cn } from '../../lib/utils'
import {
  ChevronDown, ChevronRight, PanelLeftClose,
  ConciergeBell, Stethoscope, BedDouble, ReceiptText, Microscope, Pill, Settings, BarChart3,
  CalendarDays, UserPlus, ClipboardList, ListChecks, Users, Bed, Receipt, FlaskConical, Scan,
  ShoppingCart, History, RotateCcw, FileText, PackageCheck, PackageX, Boxes,
  Tag, Tags, Coins, UserCog, Upload, Building2, Star, Timer, Hospital, Package, LayoutList,
  Handshake, Hash, Printer, FileSpreadsheet, ShieldCheck, TestTube, Truck, Percent, UsersRound,
  Wallet, TrendingUp, Banknote,
  type LucideIcon,
} from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { configApi } from '../../services/config/configApi'
import { useAuthStore } from '../../store/authStore'

interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  featureKey?: string
}

interface NavGroup {
  label: string
  icon: LucideIcon
  to?: string
  featureKey?: string
  items?: NavItem[]
}

export const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Front desk', icon: ConciergeBell, items: [
      { to: '/appointments', label: 'Appointments', icon: CalendarDays, featureKey: 'APPOINTMENT' },
      { to: '/patients', label: 'Registration', icon: UserPlus, featureKey: 'REGISTRATION' },
      { to: '/encounters', label: 'Encounters', icon: ClipboardList, featureKey: 'OUT_PATIENT' },
    ]
  },
  {
    label: 'Consultant', icon: Stethoscope, items: [
      { to: '/op-queue', label: 'OP Queue', icon: ListChecks, featureKey: 'OUT_PATIENT' },
    ]
  },
  {
    label: 'Inpatient', icon: BedDouble, items: [
      { to: '/ip-ward?tab=ward', label: 'In Patient List', icon: Users, featureKey: 'IN_PATIENT' },
      { to: '/ip-ward?tab=beds', label: 'Bed Management', icon: Bed, featureKey: 'BEDMANAGEMENT' },
      { to: '/ip-ward?tab=requests', label: 'Admission Requests', icon: ClipboardList, featureKey: 'IN_PATIENT' },
    ]
  },
  {
    label: 'Billing', icon: ReceiptText, items: [
      { to: '/billing/op', label: 'OP Billing', icon: Receipt, featureKey: 'PATIENT_BILLS' },
      { to: '/billing/ip', label: 'IP Billing', icon: ReceiptText, featureKey: 'PATIENT_BILLS' },
    ]
  },
  {
    label: 'Diagnostics', icon: Microscope, items: [
      { to: '/diagnostics?tab=lab', label: 'Laboratory', icon: FlaskConical, featureKey: 'LAB_REPORT' },
      { to: '/diagnostics?tab=radiology', label: 'Radiology', icon: Scan, featureKey: 'RADIOLOGY' },
    ]
  },
  {
    label: 'Pharmacy', icon: Pill, items: [
      { to: '/sales/sales', label: 'Sales', icon: ShoppingCart, featureKey: 'SALES' },
      { to: '/prescription-orders', label: 'Prescribed Orders', icon: ClipboardList, featureKey: 'PRESCRIBED_ORDERS' },
      { to: '/sales/salesHistory', label: 'Sales History', icon: History, featureKey: 'SALES' },
      { to: '/sales/salesReturn', label: 'Sales return', icon: RotateCcw, featureKey: 'SALES_RETURN' },
      { to: '/purchase-management?tab=order', label: 'Purchase order', icon: FileText, featureKey: 'PURCHASE_ORDER' },
      { to: '/purchase-management?tab=grn', label: 'GRN', icon: PackageCheck, featureKey: 'INVENTORY_GRN' },
      { to: '/purchase-management?tab=return', label: 'GRN Return', icon: PackageX, featureKey: 'INVENTORY_GOODS_RETURN' },
      // { to: '/opening-stock', label: 'Opening Stock', icon: Boxes, featureKey: 'STOCK' },
      { to: '/sales/stockAdjustment', label: 'Stock Adjustment', icon: FileSpreadsheet, featureKey: 'STOCK_ADJUSTMENT' },
    ]
  },
  {
    label: 'Reports', icon: BarChart3, items: [
      { to: '/reports/patients', label: 'Encounter', icon: ClipboardList, featureKey: 'REPORT_ENCOUNTER' },
      { to: '/reports/bills', label: 'Bills', icon: Receipt, featureKey: 'REPORT_BILLING' },
      { to: '/reports/collections', label: 'Collections', icon: Wallet, featureKey: 'REPORT_COLLECTION' },
      { to: '/reports/diagnostics', label: 'Diagnostics', icon: Microscope, featureKey: 'REPORT_DIAGNOSTICS' },
      { to: '/reports/revenue', label: 'Revenue Analysis', icon: TrendingUp, featureKey: 'REPORT_REVENUE' },
      { to: '/reports/in-patients', label: 'In Patients', icon: BedDouble, featureKey: 'REPORT_INPATIENT' },
      { to: '/reports/purchase', label: 'Purchase', icon: Package, featureKey: 'REPORT_PROCUREMENT' },
      { to: '/reports/stocks', label: 'Stocks', icon: Boxes, featureKey: 'REPORT_INVENTORY' },
      { to: '/reports/sales', label: 'Sales', icon: Banknote, featureKey: 'REPORT_PHARMACY' },
    ]
  },
  {
    label: 'Settings', icon: Settings, items: [
      { to: '/admin/masters?tab=bed', label: 'Bed', icon: Bed, featureKey: 'SETTINGS_BED' },
      { to: '/admin/masters?tab=bed_type', label: 'Bed Type', icon: Tag, featureKey: 'SETTINGS_BEDTYPE' },
      { to: '/admin/casesheet-templates', label: 'Case Sheet Templates', icon: FileText, featureKey: 'SETTINGS_CASESHEET_TEMPLATE' },
      { to: '/admin/discharge-templates', label: 'Discharge Templates', icon: FileText, featureKey: 'SETTINGS_CASESHEET_TEMPLATE' },
      { to: '/admin/masters?tab=category', label: 'Category', icon: Tags, featureKey: 'SETTINGS_CATEGORY' },
      { to: '/admin/masters?tab=charge', label: 'Charge', icon: Coins, featureKey: 'SETTINGS_CHARGES' },
      { to: '/admin/masters?tab=consultant', label: 'Consultant', icon: UserCog, featureKey: 'SETTINGS_CONSULTANT' },
      { to: '/admin/bulk-import', label: 'Data Import', icon: Upload, featureKey: 'DATA_IMPORT' },
      { to: '/admin/masters?tab=department', label: 'Department', icon: Building2, featureKey: 'SETTINGS_DEPARTMENT' },
      { to: '/favorites', label: 'Favorites', icon: Star, featureKey: 'ATTACHMENT' },
      { to: '/admin/masters?tab=frequency', label: 'Frequency', icon: Timer, featureKey: 'SETTINGS_FREQUENCY' },
      { to: '/admin/config', label: 'Hospital Profile', icon: Hospital, featureKey: 'SETTINGS_HOSPITALPROFILE' },
      { to: '/admin/masters?tab=item', label: 'Item', icon: Package, featureKey: 'SETTINGS_ITEM' },
      { to: '/order-sets', label: 'Order Sets', icon: LayoutList, featureKey: 'SETTINGS_ORDERSET' },
      { to: '/admin/masters?tab=payers', label: 'Payers', icon: Handshake, featureKey: 'SETTINGS_PAYERTYPE' },
      { to: '/admin/masters?tab=prefix', label: 'Prefix', icon: Hash, featureKey: 'SETTINGS_PREFIX' },
      { to: '/admin/masters?tab=scheduled_drug', label: 'Scheduled Drug', icon: Pill, featureKey: 'SETTINGS_SCHEDULEDDRUG' },
      { to: '/admin/masters?tab=print_template', label: 'Print Template', icon: Printer, featureKey: 'SETTINGS_PRINT_TEMPLATE' },
      { to: '/admin/masters?tab=result_template', label: 'Result Template', icon: FileSpreadsheet, featureKey: 'SETTINGS_RESULT_TEMPLATE' },
      { to: '/admin/masters?tab=roles', label: 'Roles', icon: ShieldCheck, featureKey: 'SETTINGS_ROLE' },
      { to: '/admin/masters?tab=specimen', label: 'Specimen', icon: TestTube, featureKey: 'SETTINGS_SPECIMEN' },
      { to: '/admin/masters?tab=staff', label: 'Staff', icon: Users, featureKey: 'SETTINGS_STAFF' },
      { to: '/admin/masters?tab=supplier', label: 'Supplier', icon: Truck, featureKey: 'SETTINGS_SUPPLIER' },
      { to: '/admin/masters?tab=tax', label: 'Tax', icon: Percent, featureKey: 'SETTINGS_TAX' },
      { to: '/admin/masters?tab=users', label: 'Users', icon: UsersRound, featureKey: 'SETTINGS_USERS' },
    ]
  },
]

export function Sidebar() {
  const location = useLocation()
  const navigate = useNavigate()
  const [isCollapsed, setIsCollapsed] = useState(false)
  const [logoVersion, setLogoVersion] = useState(() => Date.now())

  useEffect(() => {
    const handleLogoChange = (e: Event) => {
      const customEvent = e as CustomEvent
      setLogoVersion(customEvent.detail?.version || Date.now())
    }
    window.addEventListener('hospital-logo-changed', handleLogoChange)
    return () => window.removeEventListener('hospital-logo-changed', handleLogoChange)
  }, [])

  const { data: profile } = useQuery({
    queryKey: ['config', 'hospital'],
    queryFn: () => configApi.getHospital(),
    staleTime: 60000 // Cache for 1 minute
  })

  const { hasPermission } = useAuthStore()

  // Filter NAV_GROUPS by permissions
  const visibleGroups = NAV_GROUPS.map(group => {
    if (group.featureKey && !hasPermission(group.featureKey)) return null
    const visibleItems = group.items?.filter(item => {
      if (item.featureKey && !hasPermission(item.featureKey)) return false
      return true
    })
    if (group.items && visibleItems?.length === 0) return null
    return { ...group, items: visibleItems }
  }).filter(Boolean) as NavGroup[]

  const hospitalName = profile?.['hospital.name.param'] || 'HMS'

  // Find which group contains the active route
  const getActiveGroup = () => {
    return visibleGroups.find(group => {
      if (group.to && location.pathname.startsWith(group.to)) return true
      return group.items?.some(item => {
        const path = item.to.split('?')[0]
        return location.pathname.startsWith(path)
      })
    })?.label || null
  }

  const [openGroup, setOpenGroup] = useState<string | null>(getActiveGroup())

  // Update open group when location changes
  useEffect(() => {
    const activeGroup = getActiveGroup()
    if (activeGroup) {
      setOpenGroup(activeGroup)
    }
  }, [location.pathname])

  const toggleGroup = (group: NavGroup) => {
    if (group.items && group.items.length > 0) {
      const isAlreadyOnFirstItem = location.pathname + location.search === group.items[0].to
      if (openGroup === group.label && isAlreadyOnFirstItem) {
        setOpenGroup(null)
      } else {
        navigate(group.items[0].to)
        setOpenGroup(group.label)
      }
    }
  }

  return (
    <aside
      className={cn(
        "bg-white border-r border-neutral-200 flex flex-col shrink-0 transition-all duration-300 ease-in-out",
        isCollapsed ? "w-16" : "w-56"
      )}
      aria-label="Main navigation"
    >
      <div className={cn(
        "px-4 py-4 border-b border-neutral-200 flex items-center shrink-0 min-h-[65px] overflow-hidden",
        isCollapsed ? "justify-center px-2" : "justify-between px-5"
      )}>
        {isCollapsed ? (
          <button
            onClick={() => setIsCollapsed(false)}
            className="w-10 h-10 rounded-xl bg-neutral-50 border border-neutral-200 flex items-center justify-center overflow-hidden shadow-sm hover:bg-neutral-100 hover:scale-105 transition-all duration-200 cursor-pointer"
            title="Expand sidebar"
          >
            <img
              src={`/api/hospitalProfile/logo?t=${logoVersion}`}
              onError={(e) => {
                e.currentTarget.src = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 24 24' stroke-width='2' stroke='%23171717' class='w-5 h-5'%3E%3Cpath stroke-linecap='round' stroke-linejoin='round' d='M2.25 21h19.5m-18-18v18m10.5-18v18m6-13.5V21M6.75 6.75h.75m-.75 3h.75m-.75 3h.75m3-6h.75m-.75 3h.75m-.75 3h.75M6.75 21v-3.375c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21M3 3h12v18H3V3z' /%3E%3C/svg%3E"
              }}
              className="w-full h-full object-contain p-1"
              alt="Logo"
            />
          </button>
        ) : (
          <>
            <div className="flex items-center gap-3 overflow-hidden flex-1">
              <div className="w-8 h-8 rounded-lg bg-neutral-50 border border-neutral-100 flex items-center justify-center overflow-hidden shadow-sm shrink-0">
                <img
                  src={`/api/hospitalProfile/logo?t=${logoVersion}`}
                  onError={(e) => {
                    e.currentTarget.src = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 24 24' stroke-width='2' stroke='%23171717' class='w-5 h-5'%3E%3Cpath stroke-linecap='round' stroke-linejoin='round' d='M2.25 21h19.5m-18-18v18m10.5-18v18m6-13.5V21M6.75 6.75h.75m-.75 3h.75m-.75 3h.75m3-6h.75m-.75 3h.75m-.75 3h.75M6.75 21v-3.375c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21M3 3h12v18H3V3z' /%3E%3C/svg%3E"
                  }}
                  className="w-full h-full object-contain p-1"
                  alt="Logo"
                />
              </div>
              <div className="flex flex-col min-w-0 flex-1">
                <h1 className="text-xs font-extrabold text-neutral-900 tracking-tight whitespace-nowrap overflow-hidden text-ellipsis uppercase">
                  {hospitalName}
                </h1>
                <p className="text-[9px] font-medium text-neutral-400 whitespace-nowrap uppercase tracking-wider">
                  Hospital Profile
                </p>
              </div>
            </div>
            <button
              onClick={() => setIsCollapsed(true)}
              className="p-1.5 rounded-lg hover:bg-neutral-100 text-neutral-500 transition-colors shrink-0 ml-2"
              title="Collapse sidebar"
            >
              <PanelLeftClose size={18} />
            </button>
          </>
        )}
      </div>

      <nav className="flex-1 overflow-y-auto thin-scrollbar p-3 space-y-2" role="navigation">
        {visibleGroups.map(group => {
          const isLink = group.to !== undefined
          const isOpen = openGroup === group.label
          const GroupIcon = group.icon
          return (
            <div key={group.label} className="space-y-1">
              {!isCollapsed ? (
                <>
                  {isLink ? (
                    <NavLink
                      to={group.to!}
                      className={({ isActive }) => cn(
                        'w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-semibold transition-all group',
                        isActive
                          ? 'bg-neutral-100 text-neutral-900'
                          : 'text-neutral-600 hover:bg-neutral-50 hover:text-neutral-900'
                      )}
                    >
                      <GroupIcon size={16} className="shrink-0" aria-hidden="true" />
                      <span className="text-[10px] font-bold text-neutral-400 uppercase tracking-wider group-hover:text-neutral-600 transition-colors">
                        {group.label}
                      </span>
                    </NavLink>
                  ) : (
                    <button
                      onClick={() => toggleGroup(group)}
                      className={cn(
                        "w-full flex items-center justify-between px-3 py-2 rounded-lg transition-colors group",
                        isOpen ? "bg-neutral-50" : "hover:bg-neutral-50"
                      )}
                    >
                      <div className="flex items-center gap-3">
                        <GroupIcon size={16} className="shrink-0 text-neutral-500 group-hover:text-neutral-700 transition-colors" aria-hidden="true" />
                        <span className="text-[10px] font-bold text-neutral-400 uppercase tracking-wider group-hover:text-neutral-600 transition-colors">
                          {group.label}
                        </span>
                      </div>
                      {isOpen ? (
                        <ChevronDown size={14} className="text-neutral-300 group-hover:text-neutral-400" />
                      ) : (
                        <ChevronRight size={14} className="text-neutral-300 group-hover:text-neutral-400" />
                      )}
                    </button>
                  )}

                  {!isLink && isOpen && (
                    <div className="space-y-0.5 pl-4">
                      {group.items?.map(item => {
                        const ItemIcon = item.icon
                        return (
                          <NavLink
                            key={item.to}
                            to={item.to}
                            className={({ isActive }) => {
                              const isQueryActive = item.to.includes('?')
                                ? (location.pathname + location.search === item.to)
                                || (location.pathname === '/diagnostics' && item.to.includes('tab=lab') && !location.search)
                                || (location.pathname === '/purchase-management' && item.to.includes('tab=order') && !location.search)
                                || (location.pathname === '/ip-ward' && item.to.includes('tab=ward') && !location.search)
                                : isActive
                              return cn(
                                'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-semibold transition-all group',
                                isQueryActive
                                  ? 'bg-neutral-100 text-neutral-900'
                                  : 'text-neutral-600 hover:bg-neutral-50 hover:text-neutral-900'
                              )
                            }}
                          >
                            <ItemIcon size={16} className="shrink-0" aria-hidden="true" />
                            <span className="whitespace-nowrap text-xs">
                              {item.label}
                            </span>
                          </NavLink>
                        )
                      })}
                    </div>
                  )}
                </>
              ) : (
                <div
                  className="flex justify-center p-2 cursor-pointer hover:bg-neutral-100 rounded-lg transition-colors text-neutral-500 hover:text-neutral-800"
                  title={group.label}
                  onClick={() => {
                    if (isLink) {
                      navigate(group.to!)
                    } else {
                      setIsCollapsed(false)
                      toggleGroup(group)
                    }
                  }}
                >
                  <GroupIcon size={20} aria-hidden="true" />
                </div>
              )}
            </div>
          )
        })}
      </nav>
    </aside>
  )
}
