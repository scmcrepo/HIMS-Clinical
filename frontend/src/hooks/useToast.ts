import { useState, useCallback } from 'react'

export interface Toast { id: string; title: string; description?: string; variant?: 'default' | 'success' | 'destructive' }

let toastFn: ((t: Omit<Toast, 'id'>) => void) | null = null

export function toast(t: Omit<Toast, 'id'>) {
  toastFn?.(t)
}

export function useToastState() {
  const [toasts, setToasts] = useState<Toast[]>([])
  const addToast = useCallback((t: Omit<Toast, 'id'>) => {
    const id = Math.random().toString(36).slice(2)
    setToasts(prev => [...prev, { ...t, id }])
    setTimeout(() => setToasts(prev => prev.filter(x => x.id !== id)), 4000)
  }, [])
  toastFn = addToast
  return { toasts, addToast }
}
