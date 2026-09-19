import { useEffect, useState, useRef } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../../store/authStore'
import { useLogout } from '../../hooks/auth/useAuth'
import { branchApi } from '../../services/branch/branchApi'
import { authApi } from '../../services/auth/authApi'
import { ChevronDown, Check, Building2 } from 'lucide-react'

export function TopBar() {
  const user = useAuthStore(s => s.user)
  const logout = useLogout()
  const queryClient = useQueryClient()
  const { selectedBranchId, setSelectedBranch } = useAuthStore()
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement>(null)
  const [logoVersion, setLogoVersion] = useState(() => Date.now())
  const [logoError, setLogoError] = useState(false)

  useEffect(() => {
    const handleLogoChange = (e: Event) => {
      const customEvent = e as CustomEvent
      setLogoVersion(customEvent.detail?.version || Date.now())
      setLogoError(false)
    }
    window.addEventListener('hospital-logo-changed', handleLogoChange)
    return () => window.removeEventListener('hospital-logo-changed', handleLogoChange)
  }, [])

  useEffect(() => {
    setLogoError(false)
  }, [selectedBranchId, logoVersion])

  const { data: rawBranches } = useQuery({
    queryKey: ['branches', user?.tenantId],
    queryFn: () => branchApi.getAll().then(r => r.data ?? []),
    enabled: !!user?.isHospitalAdmin && !!user?.tenantId,
  })

  // Filter out the auto-created "Main Branch" (isDefault=true) for Hospital Admin.
  // Hospital Admin only sees branches they explicitly created.
  const branches = rawBranches?.filter(b => !b.isDefault) || []

  useEffect(() => {
    if (user?.isHospitalAdmin && branches && branches.length > 0) {
      // Always ensure a specific branch is selected (never 'all' or null)
      if (!selectedBranchId || selectedBranchId === 'all') {
        setSelectedBranch(branches[0].id, branches[0].name)
      } else {
        const exists = branches.some(b => b.id === selectedBranchId)
        if (!exists) {
          setSelectedBranch(branches[0].id, branches[0].name)
        }
      }
    }
  }, [branches, user?.isHospitalAdmin, selectedBranchId, setSelectedBranch])

  useEffect(() => {
    if (selectedBranchId) {
      queryClient.invalidateQueries()
    }
  }, [selectedBranchId, queryClient])

  // Handle click outside to close dropdown
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setDropdownOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const handleBranchSelect = async (branchId: string, branchName: string) => {
    setSelectedBranch(branchId, branchName)
    setDropdownOpen(false)
    queryClient.clear()
    try {
      const res = await authApi.me()
      useAuthStore.getState().setUser(res.data)
    } catch (err) {
      console.error('Failed to reload user info:', err)
    }
  }

  // Hospital (tenant) name, with the active branch appended for branch-scoped staff.
  // SUPERADMIN with no tenant shows a platform badge.
  const tenantLabel = user?.tenantName ?? (user?.isSuperAdmin ? 'Platform Administration' : '')

  // Only show branch dropdown for hospital admin when there are actual branches
  const showBranchDropdown = user?.isHospitalAdmin && branches && branches.length > 0
  const activeBranch = branches.find(b => b.id === selectedBranchId)

  return (
    <header className="relative z-20 h-14 bg-white border-b border-gray-100 flex items-center justify-between px-3 lg:px-6 shrink-0 shadow-sm">
      <div className="flex-1" />

      <div className="flex items-center justify-center gap-3 text-sm">
        {!user?.isSuperAdmin && (
          <div className="w-8 h-8 rounded-lg bg-neutral-50 border border-neutral-200/80 flex items-center justify-center overflow-hidden shadow-sm shrink-0">
            {!logoError ? (
              <img
                src={`/api/hospitalProfile/logo?tenantId=${user?.tenantId || ''}&branchId=${selectedBranchId || ''}&t=${logoVersion}`}
                onError={() => setLogoError(true)}
                className="w-full h-full object-contain p-0.5"
                alt="Hospital Logo"
              />
            ) : (
              <Building2 className="w-4 h-4 text-neutral-500" />
            )}
          </div>
        )}
        <span className="font-semibold text-neutral-800 tracking-tight text-xs lg:text-sm truncate max-w-[200px] lg:max-w-none">{tenantLabel}</span>
        {user?.branchName && (
          <>
            <span className="text-gray-300">/</span>
            <span className="text-gray-500 font-medium" aria-label="Active branch">{user.branchName}</span>
          </>
        )}
        {showBranchDropdown && (
          <>
            <span className="text-gray-300">/</span>
            <div className="relative inline-block" ref={dropdownRef}>
              <button
                onClick={() => setDropdownOpen(!dropdownOpen)}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-neutral-700 bg-neutral-50 hover:bg-neutral-100 border border-neutral-200/80 rounded-lg focus:outline-none transition-all shadow-sm active:scale-95 cursor-pointer"
                aria-label="Select active branch"
              >
                <span>{activeBranch ? activeBranch.name : 'Select Branch'}</span>
                <ChevronDown size={13} className={`text-neutral-500 transition-transform duration-200 ${dropdownOpen ? 'rotate-180' : ''}`} />
              </button>

              {dropdownOpen && (
                <div className="absolute left-0 mt-1.5 w-56 bg-white border border-neutral-150 rounded-xl shadow-lg ring-1 ring-black/5 py-1 z-50 animate-in fade-in slide-in-from-top-2 duration-150">
                  <div className="px-3 py-1.5 text-[10px] font-bold text-neutral-400 uppercase tracking-wider border-b border-neutral-50">
                    Switch Branch
                  </div>
                  {branches.map(b => {
                    const isSelected = b.id === selectedBranchId
                    return (
                      <button
                        key={b.id}
                        onClick={() => handleBranchSelect(b.id, b.name)}
                        className={`w-full text-left px-3 py-2 text-xs font-medium flex items-center justify-between transition-colors hover:bg-neutral-50 ${
                          isSelected ? 'text-neutral-900 bg-neutral-50/50' : 'text-neutral-600 hover:text-neutral-900'
                        }`}
                      >
                        <span className={isSelected ? 'font-semibold' : ''}>{b.name}</span>
                        {isSelected && <Check size={14} className="text-neutral-900 stroke-[2.5]" />}
                      </button>
                    )
                  })}
                </div>
              )}
            </div>
          </>
        )}
      </div>

      <div className="flex items-center justify-end gap-4 flex-1">
        <span className="text-xs lg:text-sm font-semibold text-neutral-600 truncate max-w-[120px]" aria-label="Logged in as">{user?.username}</span>
        <button onClick={() => logout.mutate()} aria-label="Logout"
          className="text-sm font-medium text-neutral-400 hover:text-red-500 hover:bg-red-50/50 transition-all px-2.5 py-1 rounded-lg cursor-pointer">
          Logout
        </button>
      </div>
    </header>
  )
}
