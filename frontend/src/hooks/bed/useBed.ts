import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { bedApi } from '../../services/bed/bedApi'
import { toast } from '../useToast'

export function useBeds() {
  return useQuery({ queryKey: ['beds'], queryFn: () => bedApi.getAll() })
}

export function useBedTypes() {
  return useQuery({ queryKey: ['bedTypes'], queryFn: () => bedApi.getBedTypes() })
}

export function useBedSummary() {
  return useQuery({
    queryKey: ['beds', 'summary'],
    queryFn: () => bedApi.getSummary(),
    refetchInterval: 30_000, // auto-refresh every 30 seconds
  })
}

export function useAvailableBeds(roomCategoryId?: string) {
  return useQuery({
    queryKey: ['beds', 'available', roomCategoryId],
    queryFn: () => bedApi.getAvailable(roomCategoryId),
  })
}

export function useBedMutations() {
  const qc = useQueryClient()
  const invalidate = () => qc.invalidateQueries({ queryKey: ['beds'] })

  const allocate = useMutation({
    mutationFn: ({ bedId, encounterId, consultantId, billId, billType, payorId }: { bedId: string; encounterId: string; consultantId?: string; billId?: string; billType?: string; payorId?: string }) =>
      bedApi.allocate(bedId, encounterId, consultantId, billId, billType, payorId),
    onSuccess: () => { invalidate(); toast({ title: 'Bed allocated', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Allocation failed', description: e.message, variant: 'destructive' }),
  })

  const release = useMutation({
    mutationFn: (bedId: string) => bedApi.release(bedId),
    onSuccess: () => { invalidate(); toast({ title: 'Bed released', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Release failed', description: e.message, variant: 'destructive' }),
  })

  const setMaintenance = useMutation({
    mutationFn: (bedId: string) => bedApi.setMaintenance(bedId),
    onSuccess: () => { invalidate(); toast({ title: 'Bed set to maintenance', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  const clearMaintenance = useMutation({
    mutationFn: (bedId: string) => bedApi.clearMaintenance(bedId),
    onSuccess: () => { invalidate(); toast({ title: 'Bed returned to service', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  const transfer = useMutation({
    mutationFn: ({ encounterId, newBedId, fromDate }: { encounterId: string; newBedId: string; fromDate?: string }) =>
      bedApi.transfer(encounterId, newBedId, fromDate),
    onSuccess: () => { invalidate(); toast({ title: 'Bed transferred successfully', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Transfer failed', description: e.message, variant: 'destructive' }),
  })

  const vacate = useMutation({
    mutationFn: ({ encounterId, dischargeDate }: { encounterId: string; dischargeDate?: string }) =>
      bedApi.vacate(encounterId, dischargeDate),
    onSuccess: () => { invalidate(); toast({ title: 'Bed vacated successfully', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Vacate failed', description: e.message, variant: 'destructive' }),
  })

  return { allocate, release, setMaintenance, clearMaintenance, transfer, vacate }
}
