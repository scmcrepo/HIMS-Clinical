import { useAuthStore } from '../../store/authStore'
import { useLogout } from '../../hooks/auth/useAuth'
export function TopBar() {
  const user = useAuthStore(s => s.user)
  const logout = useLogout()
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
        {user?.isHospitalAdmin && (
          <span className="ml-1 rounded bg-gray-100 px-1.5 py-0.5 text-[11px] font-medium text-gray-500">All branches</span>
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
