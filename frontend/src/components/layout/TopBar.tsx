import { useAuthStore } from '../../store/authStore'
import { useLogout } from '../../hooks/auth/useAuth'
export function TopBar() {
  const user = useAuthStore(s => s.user)
  const logout = useLogout()
  return (
    <header className="relative z-10 h-14 bg-white border-b border-gray-200 flex items-center justify-between px-6 shrink-0">
      <div />
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
