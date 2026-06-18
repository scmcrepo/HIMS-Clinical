import { useEffect } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../../store/authStore'
import { useLogout } from '../../hooks/auth/useAuth'
import { branchApi } from '../../services/branch/branchApi'

export function TopBar() {
  const user = useAuthStore(s => s.user)
  const logout = useLogout()
  const queryClient = useQueryClient()
  const { selectedBranchId, setSelectedBranch } = useAuthStore()

  const { data: branches } = useQuery({
    queryKey: ['branches', user?.tenantId],
    queryFn: () => branchApi.getAll().then(r => r.data ?? []),
    enabled: !!user?.isHospitalAdmin && !!user?.tenantId,
  })

  useEffect(() => {
    if (user?.isHospitalAdmin && branches && branches.length > 0) {
      // Always ensure a specific branch is selected (never 'all' or null)
      if (!selectedBranchId || selectedBranchId === 'all') {
        const defaultBranch = branches.find(b => b.isDefault) || branches[0]
        setSelectedBranch(defaultBranch.id, defaultBranch.name)
      } else {
        const exists = branches.some(b => b.id === selectedBranchId)
        if (!exists) {
          const defaultBranch = branches.find(b => b.isDefault) || branches[0]
          setSelectedBranch(defaultBranch.id, defaultBranch.name)
        }
      }
    }
  }, [branches, user?.isHospitalAdmin, selectedBranchId, setSelectedBranch])

  const handleBranchChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const val = e.target.value
    const branch = branches?.find(b => b.id === val)
    if (branch) {
      setSelectedBranch(branch.id, branch.name)
    }
    queryClient.invalidateQueries()
  }

  // Hospital (tenant) name, with the active branch appended for branch-scoped staff.
  // SUPERADMIN with no tenant shows a platform badge.
  const tenantLabel = user?.tenantName ?? (user?.isSuperAdmin ? 'Platform Administration' : '')

  return (
    <header className="relative z-10 h-14 bg-white border-b border-gray-200 flex items-center justify-between px-6 shrink-0">
      <div className="flex items-center gap-2 text-sm">
        <span className="font-medium text-gray-700">{tenantLabel}</span>
        {user?.branchName && (
          <>
            <span className="text-gray-300">/</span>
            <span className="text-gray-500" aria-label="Active branch">{user.branchName}</span>
          </>
        )}
        {user?.isHospitalAdmin && branches && branches.length > 0 && (
          <>
            <span className="text-gray-300">/</span>
            <div className="relative inline-block">
              <select
                value={selectedBranchId || ''}
                onChange={handleBranchChange}
                className="block pl-2 pr-8 py-1 text-xs font-semibold text-gray-600 bg-gray-50 border border-gray-200 rounded-lg focus:outline-none focus:ring-1 focus:ring-gray-300 cursor-pointer appearance-none"
                aria-label="Select active branch"
              >
                {branches.map(b => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </select>
              <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center px-2 text-gray-500">
                <svg className="fill-current h-3 w-3" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20">
                  <path d="M9.293 12.95l.707.707L15.657 8l-1.414-1.414L10 10.828 5.757 6.586 4.343 8z" />
                </svg>
              </div>
            </div>
          </>
        )}
      </div>
      <div className="flex items-center gap-4">
        <span className="text-sm text-gray-600" aria-label="Logged in as">{user?.username}</span>
        <button onClick={() => logout.mutate()} aria-label="Logout"
          className="text-sm text-gray-500 hover:text-red-600 transition-colors px-2 py-1 rounded">
          Logout
        </button>
      </div>
    </header>
  )
}
