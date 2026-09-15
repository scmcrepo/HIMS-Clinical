import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { authApi } from '../../services/auth/authApi'
import { useAuthStore } from '../../store/authStore'
import { queryClient } from '../../lib/queryClient'
import { applyTheme, cacheTheme } from '../../theme/theme'

interface LoginVars {
  username: string
  password: string
  branchId?: string | null
  forceLogout?: boolean
}

export function useLogin() {
  const { setUser } = useAuthStore()
  const navigate = useNavigate()
  return useMutation({
    mutationFn: ({ username, password, branchId, forceLogout }: LoginVars) =>
      authApi.login(username, password, branchId, forceLogout),
    onSuccess: async res => {
      // Any payload carrying a `status` is an interstitial, not a session:
      // MULTIPLE_BRANCHES, MFA_REQUIRED, MFA_ENROLMENT_REQUIRED. LoginResponse
      // has no such field, so this discriminates safely.
      //
      // Written generically on purpose. This previously named MULTIPLE_BRANCHES
      // explicitly, which meant the MFA interstitials would have fallen through
      // to setUser() and navigate('/') — the UI would have believed it was
      // signed in while the server had created no session. Every call would then
      // 401, and it would have looked like an authentication bypass. Any future
      // interstitial is now handled correctly by default rather than by
      // remembering to add a case.
      if (res.data?.status) {
        return
      }
      setUser(res.data ?? null)
      // Refetch the hospital's theme now that we have an authenticated session.
      // Before login the query failed (401) and sat in error state — without
      // this invalidation it would never retry, which is why the theme only
      // appeared after a manual page refresh.
      queryClient.invalidateQueries({ queryKey: ['config', 'theme'] })
      navigate('/')
    },
  })
}

/**
 * Completes a multi-factor login (WO-029 / U-002).
 *
 * Separate from useLogin because it is a different call with different inputs,
 * and because the failure modes need to be distinguishable: a wrong password
 * sends you back to the start, whereas a wrong code leaves you on the challenge
 * with attempts remaining.
 */
export function useMfaVerify() {
  const { setUser } = useAuthStore()
  const navigate = useNavigate()
  return useMutation({
    mutationFn: ({ challengeId, code }: { challengeId: string; code: string }) =>
      authApi.verifyMfa(challengeId, code),
      onSuccess: async res => {
      if (res.data?.status) {
        return
      }
      setUser(res.data ?? null)
      queryClient.invalidateQueries({ queryKey: ['config', 'theme'] })
      navigate('/')
    },
  })
}

export function useLogout() {
  const { setUser } = useAuthStore()
  const navigate = useNavigate()
  return useMutation({
    mutationFn: () => authApi.logout(),
    onSuccess: () => {
      setUser(null)
      queryClient.clear()
      // Drop the cached hospital colour. On a shared machine the next person to sign in
      // may belong to a different hospital, and without this they would see the previous
      // hospital's colour flash before theirs arrives.
      cacheTheme(null)
      applyTheme(null)
      navigate('/login')
    },
  })
}

export function usePermission(featureKey: string): boolean {
  return useAuthStore(s => s.hasPermission(featureKey))
}
