import { useEffect, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
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
  }, [setUser, setLoading, setSessionTimeout])

  // ── Hospital theme colour ────────────────────────────────────────────────
  // Fetched via useQuery so that React Query's refetchOnWindowFocus (enabled
  // globally) re-fetches the theme every time the browser tab regains focus.
  // staleTime: 0 ensures the query is always considered stale, so switching
  // tabs always triggers a server round-trip. This is what makes a theme
  // change made by an admin in one session appear in every other session as
  // soon as the user clicks back to it — no manual refresh needed.
  //
  // main.tsx has already painted the localStorage-cached value before React
  // renders, so this is the reconciliation step: it corrects a stale cache
  // and picks up the colour on a machine that has never loaded this hospital.
  const { data: themeColor } = useQuery({
    queryKey: ['config', 'theme'],
    queryFn: configApi.getTheme,
    staleTime: 0,
    refetchInterval: 10000,
  })

  // Track whether we've applied at least once, so the very first render
  // (before the query resolves) keeps the eagerly-cached colour from main.tsx.
  const appliedOnce = useRef(false)
  useEffect(() => {
    if (themeColor !== undefined) {
      applyTheme(themeColor)
      cacheTheme(themeColor)
      appliedOnce.current = true
    }
  }, [themeColor])

  // Re-apply the theme instantly when the ThemeColorPicker saves, so every
  // screen picks up the new colour without a full page refresh.
  useEffect(() => {
    const handler = (e: Event) => {
      const color = (e as CustomEvent<string | null>).detail
      applyTheme(color)
      cacheTheme(color)
    }
    window.addEventListener('hospital-theme-changed', handler)
    return () => window.removeEventListener('hospital-theme-changed', handler)
  }, [])

  return (
    <GlobalErrorBoundary>
      <AppRouter />
      <Toaster />
    </GlobalErrorBoundary>
  )
}
