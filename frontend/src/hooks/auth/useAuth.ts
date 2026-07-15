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
      if (res.data?.status === 'MULTIPLE_BRANCHES') {
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
