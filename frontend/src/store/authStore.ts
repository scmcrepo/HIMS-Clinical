import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export interface AuthUser {
  id: string
  username: string
  featureKeys: string[]
  isSuperAdmin?: boolean
  consultantId?: string | null
  departmentId?: string | null
}

interface AuthState {
  user: AuthUser | null
  isLoading: boolean
  lastActivityTime: number
  sessionTimeout: number
  setUser: (user: AuthUser | null) => void
  setLoading: (v: boolean) => void
  updateActivity: () => void
  setSessionTimeout: (minutes: number) => void
  hasPermission: (featureKey: string) => boolean
  isAuthenticated: () => boolean
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      isLoading: true,
      lastActivityTime: Date.now(),
      sessionTimeout: 15,
      setUser: user => set({ user, lastActivityTime: Date.now() }),
      setLoading: isLoading => set({ isLoading }),
      updateActivity: () => set({ lastActivityTime: Date.now() }),
      setSessionTimeout: sessionTimeout => set({ sessionTimeout }),
      hasPermission: featureKey => {
        const { user } = get()
        if (user?.isSuperAdmin) return true
        return user?.featureKeys?.includes(featureKey) ?? false
      },
      isAuthenticated: () => get().user !== null,
    }),
    { name: 'hms-auth', partialize: state => ({ user: state.user }) }
  )
)
