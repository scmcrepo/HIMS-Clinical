import { useEffect, useRef, useState, useCallback } from 'react'
import { useAuthStore } from '../../store/authStore'
import { authApi } from '../../services/auth/authApi'
import { cn } from '../../lib/utils'

// Warning threshold in milliseconds (60 seconds)
const WARNING_THRESHOLD = 60 * 1000

export function SessionTimeoutModal() {
  const { user, sessionTimeout, setUser, updateActivity } = useAuthStore()

  const [timeLeft, setTimeLeft] = useState<number>(0)
  const [showModal, setShowModal] = useState(false)
  const [isRefreshing, setIsRefreshing] = useState(false)

  // Refs to always hold the latest values without causing effect restarts
  const isRefreshingRef = useRef(false)
  const showModalRef = useRef(false)
  const lastActivityRef = useRef(Date.now())

  const timeoutMinutes = sessionTimeout && sessionTimeout > 0 ? sessionTimeout : 15
  const sessionTimeoutMs = timeoutMinutes * 60 * 1000

  const handleLogout = useCallback(async () => {
    try {
      setShowModal(false)
      showModalRef.current = false
      await authApi.logout()
    } catch (e) {
      // ignore
    } finally {
      setUser(null)
      window.location.replace('/login')
    }
  }, [setUser])

  const handleStayLoggedIn = useCallback(async () => {
    if (isRefreshingRef.current) return
    isRefreshingRef.current = true
    setIsRefreshing(true)
    try {
      await authApi.heartbeat()
      const now = Date.now()
      lastActivityRef.current = now
      localStorage.setItem('hms-last-activity', now.toString())
      updateActivity()
      setShowModal(false)
      showModalRef.current = false
    } catch (e) {
      handleLogout()
    } finally {
      isRefreshingRef.current = false
      setIsRefreshing(false)
    }
  }, [updateActivity, handleLogout])

  useEffect(() => {
    if (!user) return

    // Reset activity time on mount so we never show the modal immediately after login
    const initialTime = Date.now()
    lastActivityRef.current = initialTime
    localStorage.setItem('hms-last-activity', initialTime.toString())

    let lastUpdate = 0
    const handleInteraction = () => {
      const now = Date.now()
      if (now - lastUpdate > 5000) {
        lastUpdate = now
        // Only update activity when modal is not showing and not refreshing
        if (!showModalRef.current && !isRefreshingRef.current) {
          lastActivityRef.current = now
          localStorage.setItem('hms-last-activity', now.toString())
          updateActivity()
        }
      }
    }

    let lastHeartbeat = Date.now()
    const handleHeartbeatInteraction = () => {
      handleInteraction()
      const now = Date.now()
      if (now - lastHeartbeat > 2 * 60 * 1000) {
        lastHeartbeat = now
        authApi.heartbeat().catch(() => {})
      }
    }

    const events = ['mousedown', 'click', 'keydown', 'scroll', 'touchstart', 'mousemove', 'wheel']
    events.forEach(name =>
      window.addEventListener(name, handleHeartbeatInteraction, { passive: true })
    )

    const interval = setInterval(() => {
      // Skip if a "Stay Signed In" refresh is in progress
      if (isRefreshingRef.current) return

      // Read auth state from localStorage to sync logout from other tabs
      const authRaw = localStorage.getItem('hms-auth')
      if (authRaw) {
        try {
          const authObj = JSON.parse(authRaw)
          if (!authObj.state || !authObj.state.user) {
            handleLogout()
            return
          }
        } catch (e) {
          // ignore
        }
      }

      // Check cross-tab activity in localStorage
      const storedLastActivityRaw = localStorage.getItem('hms-last-activity')
      const storedLastActivity = storedLastActivityRaw ? parseInt(storedLastActivityRaw, 10) : lastActivityRef.current
      const actualLastActivity = Math.max(lastActivityRef.current, storedLastActivity)

      const elapsed = Date.now() - actualLastActivity
      const remaining = sessionTimeoutMs - elapsed

      if (remaining <= 0) {
        handleLogout()
      } else if (remaining <= WARNING_THRESHOLD) {
        setTimeLeft(Math.ceil(remaining / 1000))
        if (!showModalRef.current) {
          showModalRef.current = true
          setShowModal(true)
        }
      } else {
        // User is active — hide modal if it was showing
        if (showModalRef.current) {
          showModalRef.current = false
          setShowModal(false)
        }
      }
    }, 1000)

    return () => {
      clearInterval(interval)
      events.forEach(name =>
        window.removeEventListener(name, handleHeartbeatInteraction)
      )
    }
  }, [user, sessionTimeoutMs, updateActivity, handleLogout])

  if (!showModal) return null

  const progress = (timeLeft / 60) * 100

  return (
    <div className="fixed inset-0 z-[9999] flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-md animate-in fade-in duration-500">
      <div className="bg-white/95 backdrop-blur-xl rounded-[2rem] shadow-[0_32px_64px_-16px_rgba(0,0,0,0.2)] border border-white/20 p-10 max-w-sm w-full relative overflow-hidden transform animate-in zoom-in-95 slide-in-from-bottom-10 duration-500">
        
        {/* Background Decorative Element */}
        <div className="absolute -top-24 -right-24 w-48 h-48 bg-indigo-500/5 rounded-full blur-3xl" />
        <div className="absolute -bottom-24 -left-24 w-48 h-48 bg-amber-500/5 rounded-full blur-3xl" />

        <div className="relative flex flex-col items-center text-center">
          
          {/* Progress Ring & Icon */}
          <div className="relative w-32 h-32 mb-8 group">
            <svg className="w-full h-full -rotate-90 transform" viewBox="0 0 100 100">
              {/* Background Circle */}
              <circle
                cx="50" cy="50" r="45"
                className="stroke-slate-100 fill-none"
                strokeWidth="6"
              />
              {/* Progress Circle */}
              <circle
                cx="50" cy="50" r="45"
                className={cn(
                  "fill-none transition-all duration-1000 ease-linear",
                  timeLeft > 20 ? "stroke-indigo-500" : "stroke-rose-500"
                )}
                strokeWidth="6"
                strokeDasharray="282.7"
                strokeDashoffset={282.7 - (282.7 * progress) / 100}
                strokeLinecap="round"
              />
            </svg>
            
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <span className={cn(
                "text-4xl font-black tabular-nums transition-colors duration-300",
                timeLeft > 20 ? "text-slate-800" : "text-rose-600 animate-pulse"
              )}>
                {timeLeft}
              </span>
              <span className="text-[10px] uppercase tracking-widest font-bold text-slate-400">Seconds</span>
            </div>
          </div>
          
          <h2 className="text-2xl font-extrabold text-slate-900 tracking-tight mb-2">Session Timeout</h2>
          <p className="text-slate-500 text-sm leading-relaxed mb-10 px-4">
            You've been inactive for a while. For your security, we'll log you out soon.
          </p>

          <div className="flex flex-col w-full gap-3">
            <button
              onClick={handleStayLoggedIn}
              disabled={isRefreshing}
              className="group relative w-full h-14 bg-slate-900 text-white rounded-2xl font-bold overflow-hidden transition-all hover:bg-slate-800 active:scale-95 disabled:opacity-70"
            >
              <div className="absolute inset-0 flex items-center justify-center gap-2">
                {isRefreshing ? (
                  <div className="w-5 h-5 border-2 border-white/20 border-t-white rounded-full animate-spin" />
                ) : (
                  <>
                    <span>Stay Signed In</span>
                    <svg className="w-4 h-4 transition-transform group-hover:translate-x-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7l5 5m0 0l-5 5m5-5H6" />
                    </svg>
                  </>
                )}
              </div>
            </button>
            
            <button
              onClick={handleLogout}
              className="w-full h-14 bg-transparent text-slate-400 rounded-2xl font-bold hover:text-slate-600 hover:bg-slate-50 transition-all active:scale-95"
            >
              Sign Out Now
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
