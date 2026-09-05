import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { authApi } from '../../services/auth/authApi'
import { useAuthStore } from '../../store/authStore'
import { queryClient } from '../../lib/queryClient'

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
    onSuccess: res => {
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
    onSuccess: res => {
      if (res.data?.status) {
        return
      }
      setUser(res.data ?? null)
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
      navigate('/login')
    },
  })
}

export function usePermission(featureKey: string): boolean {
  return useAuthStore(s => s.hasPermission(featureKey))
}
