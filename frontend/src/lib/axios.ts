import axios from 'axios'
import { useAuthStore } from '../store/authStore'

const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use(
  config => {
    // If the caller explicitly set X-Branch-Id (even to ''), don't override it.
    // An empty string means "tenant-level, no branch scoping".
    const explicitBranch = config.headers['X-Branch-Id']
    if (explicitBranch === '' || explicitBranch === null) {
      // Caller wants tenant-level — remove the header entirely
      delete config.headers['X-Branch-Id']
      return config
    }

    const state = useAuthStore.getState()
    const branchId = state.selectedBranchId || state.user?.branchId
    if (branchId && branchId !== 'all') {
      config.headers['X-Branch-Id'] = branchId
    }
    return config
  },
  error => Promise.reject(error)
)

api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.data?.message) {
      err.message = err.response.data.message;
    }
    if (err.response?.status === 401) {
      // Only clear user and redirect if not already on the login page
      // to avoid infinite reload loops
      if (window.location.pathname !== '/login') {
        useAuthStore.getState().setUser(null)
        window.location.replace('/login')
      }
    }
    return Promise.reject(err)
  }
)

export default api

