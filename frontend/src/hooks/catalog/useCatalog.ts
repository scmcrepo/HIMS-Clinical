import { useState, useEffect } from 'react'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { catalogApi } from '../../services/catalog/catalogApi'

export function useCatalogSearch(q: string, page = 0, excludeRoomCharges = false, diagnosticsAndConsultationsOnly = false) {
  const [debouncedQ, setDebouncedQ] = useState(q)

  useEffect(() => {
    const handler = setTimeout(() => setDebouncedQ(q), 400)
    return () => clearTimeout(handler)
  }, [q])

  return useQuery({ 
    queryKey: ['catalog', 'search', debouncedQ, page, excludeRoomCharges, diagnosticsAndConsultationsOnly], 
    queryFn: () => catalogApi.search(debouncedQ, page, 20, excludeRoomCharges, diagnosticsAndConsultationsOnly), 
    enabled: debouncedQ.length >= 1, 
    placeholderData: keepPreviousData 
  })
}
export function useCatalogCategories() {
  return useQuery({ queryKey: ['catalog', 'categories'], queryFn: () => catalogApi.getCategories(), staleTime: 1000 * 60 * 10 })
}
export function useCatalogByCategory(categoryId: string | undefined) {
  return useQuery({ queryKey: ['catalog', 'category', categoryId], queryFn: () => catalogApi.getByCategory(categoryId!), enabled: !!categoryId })
}
