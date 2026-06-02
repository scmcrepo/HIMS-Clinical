import { Navigate, Outlet } from 'react-router-dom'
import { useAuthStore } from '../../store/authStore'
import { AppShell } from './AppShell'
import { SessionTimeoutModal } from '../auth/SessionTimeoutModal'

export function ProtectedRoute() {
  const { isAuthenticated, isLoading } = useAuthStore()
  if (isLoading) return <div className="min-h-screen flex items-center justify-center text-gray-500 text-sm">Loading…</div>
  if (!isAuthenticated()) return <Navigate to="/login" replace />
  return (
    <AppShell>
      <Outlet />
      <SessionTimeoutModal />
    </AppShell>
  )
}
