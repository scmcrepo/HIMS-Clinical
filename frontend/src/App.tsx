import { useEffect } from 'react'
import { AppRouter } from './router/AppRouter'
import { GlobalErrorBoundary } from './components/shared/GlobalErrorBoundary'
import { Toaster } from './components/ui/Toaster'
import { useAuthStore } from './store/authStore'
import { authApi } from './services/auth/authApi'
import { configApi } from './services/config/configApi'

export default function App() {
  const { setUser, setLoading, setSessionTimeout } = useAuthStore()

  useEffect(() => {
    setLoading(true)
    
    // Fetch user profile
    authApi.me()
      .then(res => setUser(res.data))
      .catch(() => setUser(null))
      .finally(() => setLoading(false))

    // Fetch session timeout configuration
    configApi.getSessionTimeout()
      .then(timeout => setSessionTimeout(timeout))
      .catch(() => setSessionTimeout(15))
  }, [setUser, setLoading, setSessionTimeout])

  return (
    <GlobalErrorBoundary>
      <AppRouter />
      <Toaster />
    </GlobalErrorBoundary>
  )
}
