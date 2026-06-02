import axios from 'axios'
import { useAuthStore } from '../store/authStore'

const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.response.use(
  res => res,
  err => {
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

