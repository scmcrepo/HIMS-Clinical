import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { authApi } from '../../services/auth/authApi'
import { useAuthStore } from '../../store/authStore'
import { queryClient } from '../../lib/queryClient'

export function useLogin() {
  const { setUser } = useAuthStore()
  const navigate = useNavigate()
  return useMutation({
    mutationFn: ({ username, password }: { username: string; password: string }) =>
      authApi.login(username, password),
    onSuccess: res => {
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
