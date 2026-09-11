import { useEffect } from 'react'
import { AppRouter } from './router/AppRouter'
import { GlobalErrorBoundary } from './components/shared/GlobalErrorBoundary'
import { Toaster } from './components/ui/Toaster'
import { useAuthStore } from './store/authStore'
import { authApi } from './services/auth/authApi'
import { configApi } from './services/config/configApi'
import { applyTheme, cacheTheme } from './theme/theme'

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

    // The hospital's theme colour. main.tsx has already painted the cached value, so
    // this is the reconciliation step: it corrects a stale cache, and picks up the
    // colour on a machine that has never loaded this hospital before. A failure here
    // leaves the default palette in place, which is a perfectly usable application.
    configApi.getTheme()
      .then(color => { applyTheme(color); cacheTheme(color) })
      .catch(() => { /* not signed in yet, or offline — default palette stands */ })
  }, [setUser, setLoading, setSessionTimeout])

  return (
    <GlobalErrorBoundary>
      <AppRouter />
      <Toaster />
    </GlobalErrorBoundary>
  )
}
