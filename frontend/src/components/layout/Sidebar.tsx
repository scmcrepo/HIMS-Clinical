import { useState, useEffect } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { cn } from '../../lib/utils'
import { ChevronDown, ChevronRight, PanelLeftClose } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { configApi } from '../../services/config/configApi'

interface NavItem {
  to: string
  label: string
  icon: string
}

interface NavGroup {
  label: string
  icon: string
  to?: string
  items?: NavItem[]
}

const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Front desk', icon: '🛎️', items: [
      { to: '/appointments', label: 'Appointments', icon: '📅' },
      { to: '/patients', label: 'Registration', icon: '👤' },
      { to: '/encounters', label: 'Encounters', icon: '🏥' },
      { to: '/op-queue', label: 'OP Queue', icon: '🩺' },
      { to: '/beds', label: 'Bed Allocation', icon: '🛏️' },
    ]
  },
  {
    label: 'Inpatient', icon: '🏨', items: [
      { to: '/ip-ward?tab=ward', label: 'IP Ward', icon: '🏥' },
      { to: '/ip-ward?tab=beds', label: 'Bed Management', icon: '🛏️' },
    ]
  },
  {
    label: 'Billing', icon: '💳', items: [
      { to: '/billing/op', label: 'OP Billing', icon: '💵' },
      { to: '/billing/ip', label: 'IP Billing', icon: '🏥' },
    ]
  },
  {
    label: 'Diagnostics', icon: '🔬', items: [
      { to: '/diagnostics?tab=lab', label: 'Laboratory', icon: '🔬' },
      { to: '/diagnostics?tab=radiology', label: 'Radiology', icon: '🧬' },
    ]
  },
  {
    label: 'Pharmacy', icon: '💊', items: [
      { to: '/sales/sales', label: 'Sales', icon: '💊' },
      { to: '/prescription-orders', label: 'Prescription Orders', icon: '📋' },
      { to: '/sales/salesHistory', label: 'Sales History', icon: '📜' },
      { to: '/sales/salesReturn', label: 'Sales return', icon: '🔄' },
      { to: '/purchase-management?tab=order', label: 'Purchase order', icon: '📋' },
      { to: '/purchase-management?tab=grn', label: 'GRN', icon: '📦' },
      { to: '/purchase-management?tab=return', label: 'GRN Return', icon: '⏪' },
      { to: '/opening-stock', label: 'Opening Stock', icon: '📥' },
    ]
  },
  {
    label: 'Settings', icon: '⚙️', items: [
      { to: '/admin/config', label: 'Hospital Profile', icon: '🏢' },
      { to: '/order-sets',  label: 'Order Sets',       icon: '📦' },
      { to: '/admin/masters?tab=frequency', label: 'Frequency',  icon: '⏱️' },
      { to: '/favorites',   label: 'Favorites',        icon: '⭐' },
      // { to: '/admin/prefix', label: 'Sequence', icon: '🔢' },
      { to: '/admin/bulk-import', label: 'Data Import', icon: '📤' },
      { to: '/admin/casesheet-templates', label: 'Case Sheet Templates', icon: '📋' },
      // { to: '/admin/users', label: 'Users & Roles', icon: '👥' },
      // { to: '/admin/masters?tab=account_unit', label: 'Account Unit', icon: '🏦' },
      { to: '/admin/masters?tab=bed', label: 'Bed', icon: '🛏️' },
      { to: '/admin/masters?tab=bed_type', label: 'Bed Type', icon: '🏷️' },
      { to: '/admin/masters?tab=category', label: 'Category', icon: '📋' },
      { to: '/admin/masters?tab=charge', label: 'Charge', icon: '💳' },
      { to: '/admin/masters?tab=consultant', label: 'Consultant', icon: '👨‍⚕️' },
      { to: '/admin/masters?tab=department', label: 'Department', icon: '🏢' },
      // { to: '/admin/masters?tab=hospital_profile', label: 'Hospital Profile (Master)', icon: '🏥' },
      { to: '/admin/masters?tab=item', label: 'Item', icon: '📦' },
      { to: '/admin/masters?tab=payers', label: 'Payers', icon: '🤝' },
      { to: '/admin/masters?tab=prefix', label: 'Prefix', icon: '🔢' },
      { to: '/admin/masters?tab=print_template', label: 'Print Template', icon: '🖨️' },
      { to: '/admin/masters?tab=result_template', label: 'Result Template', icon: '🔬' },
      { to: '/admin/masters?tab=roles', label: 'Roles', icon: '🔑' },
      { to: '/admin/masters?tab=specimen', label: 'Specimen', icon: '🧪' },
      { to: '/admin/masters?tab=staff', label: 'Staff', icon: '👷' },
      { to: '/admin/masters?tab=supplier', label: 'Supplier', icon: '🚚' },
      { to: '/admin/masters?tab=tax', label: 'Tax', icon: '📝' },
      { to: '/admin/masters?tab=users', label: 'Users', icon: '👥' },
    ]
  },
  {
    label: 'Reports', icon: '📊', items: [
      { to: '/reports/patients', label: 'Encounter', icon: '👥' },
      { to: '/reports/bills', label: 'Bills', icon: '📋' },
      { to: '/reports/collections', label: 'Collections', icon: '💰' },
      { to: '/reports/diagnostics', label: 'Diagnostics', icon: '🔬' },
      { to: '/reports/revenue', label: 'Revenue Analysis', icon: '📈' },
      { to: '/reports/in-patients', label: 'In Patients', icon: '🛏️' },
      { to: '/reports/purchase', label: 'Purchase', icon: '📦' },
      { to: '/reports/stocks', label: 'Stocks', icon: '📦' },
      // { to: '/reports/inventory', label: 'Inventory Analysis', icon: '📦📊' },
      { to: '/reports/sales', label: 'Sales', icon: '💵' },
      // { to: '/reports/insurance', label: 'Insurance', icon: '🛡️' },
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

  const hospitalName = profile?.['hospital.name.param'] || 'HMS'

  // Find which group contains the active route
  const getActiveGroup = () => {
    return NAV_GROUPS.find(group => {
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
        "bg-white border-r border-gray-200 flex flex-col shrink-0 transition-all duration-300 ease-in-out",
        isCollapsed ? "w-16" : "w-56"
      )}
      aria-label="Main navigation"
    >
      <div className={cn(
        "px-4 py-4 border-b border-gray-200 flex items-center shrink-0 min-h-[65px] overflow-hidden",
        isCollapsed ? "justify-center px-2" : "justify-between px-5"
      )}>
        {isCollapsed ? (
          <button
            onClick={() => setIsCollapsed(false)}
            className="w-10 h-10 rounded-xl bg-gray-50 border border-gray-200 flex items-center justify-center overflow-hidden shadow-sm hover:bg-gray-100 hover:scale-105 transition-all duration-200 cursor-pointer"
            title="Expand sidebar"
          >
            <img
              src={`/api/hospitalProfile/logo?t=${logoVersion}`}
              onError={(e) => {
                e.currentTarget.src = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 24 24' stroke-width='2' stroke='%231d4ed8' class='w-5 h-5'%3E%3Cpath stroke-linecap='round' stroke-linejoin='round' d='M2.25 21h19.5m-18-18v18m10.5-18v18m6-13.5V21M6.75 6.75h.75m-.75 3h.75m-.75 3h.75m3-6h.75m-.75 3h.75m-.75 3h.75M6.75 21v-3.375c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21M3 3h12v18H3V3z' /%3E%3C/svg%3E"
              }}
              className="w-full h-full object-contain p-1"
              alt="Logo"
            />
          </button>
        ) : (
          <>
            <div className="flex items-center gap-3 overflow-hidden flex-1">
              <div className="w-8 h-8 rounded-lg bg-gray-50 border border-gray-100 flex items-center justify-center overflow-hidden shadow-sm shrink-0">
                <img
                  src={`/api/hospitalProfile/logo?t=${logoVersion}`}
                  onError={(e) => {
                    e.currentTarget.src = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 24 24' stroke-width='2' stroke='%231d4ed8' class='w-5 h-5'%3E%3Cpath stroke-linecap='round' stroke-linejoin='round' d='M2.25 21h19.5m-18-18v18m10.5-18v18m6-13.5V21M6.75 6.75h.75m-.75 3h.75m-.75 3h.75m3-6h.75m-.75 3h.75m-.75 3h.75M6.75 21v-3.375c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21M3 3h12v18H3V3z' /%3E%3C/svg%3E"
                  }}
                  className="w-full h-full object-contain p-1"
                  alt="Logo"
                />
              </div>
              <div className="flex flex-col min-w-0 flex-1">
                <h1 className="text-xs font-extrabold text-blue-700 tracking-tight whitespace-nowrap overflow-hidden text-ellipsis uppercase">
                  {hospitalName}
                </h1>
                <p className="text-[9px] font-medium text-gray-400 whitespace-nowrap uppercase tracking-wider">
                  Hospital Profile
                </p>
              </div>
            </div>
            <button
              onClick={() => setIsCollapsed(true)}
              className="p-1.5 rounded-lg hover:bg-gray-100 text-gray-500 transition-colors shrink-0 ml-2"
              title="Collapse sidebar"
            >
              <PanelLeftClose size={18} />
            </button>
          </>
        )}
      </div>

      <nav className="flex-1 overflow-y-auto thin-scrollbar p-3 space-y-2" role="navigation">
        {NAV_GROUPS.map(group => {
          const isLink = group.to !== undefined
          const isOpen = openGroup === group.label
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
                          ? 'bg-blue-50 text-blue-700'
                          : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
                      )}
                    >
                      <span className="text-sm shrink-0" aria-hidden="true">{group.icon}</span>
                      <span className="text-[10px] font-bold text-gray-400 uppercase tracking-wider group-hover:text-gray-600 transition-colors">
                        {group.label}
                      </span>
                    </NavLink>
                  ) : (
                    <button
                      onClick={() => toggleGroup(group)}
                      className={cn(
                        "w-full flex items-center justify-between px-3 py-2 rounded-lg transition-colors group",
                        isOpen ? "bg-gray-50" : "hover:bg-gray-50"
                      )}
                    >
                      <div className="flex items-center gap-3">
                        <span className="text-sm shrink-0" aria-hidden="true">{group.icon}</span>
                        <span className="text-[10px] font-bold text-gray-400 uppercase tracking-wider group-hover:text-gray-600 transition-colors">
                          {group.label}
                        </span>
                      </div>
                      {isOpen ? (
                        <ChevronDown size={14} className="text-gray-300 group-hover:text-gray-400" />
                      ) : (
                        <ChevronRight size={14} className="text-gray-300 group-hover:text-gray-400" />
                      )}
                    </button>
                  )}

                  {!isLink && isOpen && (
                    <div className="space-y-0.5 pl-4">
                      {group.items?.map(item => (
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
                                ? 'bg-blue-50 text-blue-700'
                                : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
                            )
                          }}
                        >
                          <span className="text-lg shrink-0" aria-hidden="true">{item.icon}</span>
                          <span className="whitespace-nowrap text-xs">
                            {item.label}
                          </span>
                        </NavLink>
                      ))}
                    </div>
                  )}
                </>
              ) : (
                <div
                  className="flex justify-center p-2 text-xl cursor-pointer hover:bg-gray-100 rounded-lg transition-colors"
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
                  {group.icon}
                </div>
              )}
            </div>
          )
        })}
      </nav>
    </aside>
  )
}
