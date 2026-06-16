import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export interface AuthUser {
  id: string
  username: string
  featureKeys: string[]
  roles?: string[]
  isSuperAdmin?: boolean
  consultantId?: string | null
  departmentId?: string | null
  isHospitalAdmin?: boolean
  tenantId: string | null   // null for platform-level SUPERADMIN
  tenantName: string | null
  branchId: string | null   // null for SUPERADMIN or HOSPITAL_ADMIN (tenant-wide)
  branchName: string | null
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
  tenantId: () => string | null
  branchId: () => string | null
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
        // featureKeys are already tenant-scoped server-side; no extra filtering needed.
        return user?.featureKeys?.includes(featureKey) ?? false
      },
      isAuthenticated: () => get().user !== null,
      tenantId: () => get().user?.tenantId ?? null,
      branchId: () => get().user?.branchId ?? null,
    }),
    {
      name: 'hms-auth',
      // Persist tenant fields so a refresh keeps the active tenant.
      partialize: state => ({ user: state.user }),
    }
  )
)
