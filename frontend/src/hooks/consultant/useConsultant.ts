import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { consultantApi, type Consultant } from '../../services/consultant/consultantApi'
import { slotApi, type SlotUpsertItem } from '../../services/slot/slotApi'
import { toast } from '../useToast'

export function useConsultants() {
  return useQuery({
    queryKey: ['consultants'],
    queryFn: consultantApi.getAll,
  })
}

export function useConsultantTypes() {
  return useQuery({
    queryKey: ['consultant-types'],
    queryFn: consultantApi.getTypes,
  })
}

export function useConsultantMutations() {
  const qc = useQueryClient()

  const create = useMutation({
    mutationFn: ({ consultant, photo }: { consultant: Omit<Consultant, 'id'>; photo?: File }) => 
      consultantApi.create(consultant, photo),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['consultants'] })
      toast({ title: 'Consultant registered', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Registration failed', description: e.message, variant: 'destructive' }),
  })

  const update = useMutation({
    mutationFn: ({ id, consultant, photo }: { id: string; consultant: Partial<Consultant>; photo?: File }) => 
      consultantApi.update(id, consultant, photo),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['consultants'] })
      toast({ title: 'Consultant updated', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Update failed', description: e.message, variant: 'destructive' }),
  })

  const remove = useMutation({
    mutationFn: consultantApi.delete,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['consultants'] })
      toast({ title: 'Consultant deleted', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Delete failed', description: e.message, variant: 'destructive' }),
  })

  return { create, update, remove }
}

export function useConsultantSlots(consultantId: string) {
  return useQuery({
    queryKey: ['consultant-slots', consultantId],
    queryFn: () => slotApi.getByConsultant(consultantId),
    enabled: !!consultantId,
  })
}

export function useSlotMutations(consultantId: string) {
  const qc = useQueryClient()

  const upsert = useMutation({
    mutationFn: (daysList: SlotUpsertItem[]) => slotApi.updateSlots(consultantId, daysList),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['consultant-slots', consultantId] })
      toast({ title: 'Slots updated', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Slot update failed', description: e.message, variant: 'destructive' }),
  })

  return { upsert }
}
