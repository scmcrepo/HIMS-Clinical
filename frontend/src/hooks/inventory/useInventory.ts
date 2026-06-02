import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { inventoryApi, type ReceiveLine } from '../../services/inventory/inventoryApi'
import { toast } from '../useToast'

export function useAvailableBatches(itemId: string | undefined, departmentId: string | undefined) {
  return useQuery({
    queryKey: ['inventory', 'batches', itemId, departmentId],
    queryFn: () => inventoryApi.getAvailableBatches(itemId!, departmentId!),
    enabled: !!itemId && !!departmentId,
  })
}

export function useExpiredBatches(departmentId: string | undefined) {
  return useQuery({
    queryKey: ['inventory', 'expired', departmentId],
    queryFn: () => inventoryApi.getExpired(departmentId!),
    enabled: !!departmentId,
  })
}

export function useInventoryMutations() {
  const qc = useQueryClient()
  const invalidate = () => qc.invalidateQueries({ queryKey: ['inventory'] })

  const receiveGoods = useMutation({
    mutationFn: ({ departmentId, lines }: { departmentId: string; lines: ReceiveLine[] }) =>
      inventoryApi.receiveGoods(departmentId, lines),
    onSuccess: (data) => {
      invalidate()
      toast({ title: `${data.length} batch(es) received`, variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Receive failed', description: e.message, variant: 'destructive' }),
  })

  const adjustStock = useMutation({
    mutationFn: ({ batchId, qty, reason }: { batchId: string; qty: number; reason?: string }) =>
      inventoryApi.adjustStock(batchId, qty, reason),
    onSuccess: () => { invalidate(); toast({ title: 'Stock adjusted', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Adjustment failed', description: e.message, variant: 'destructive' }),
  })

  const consumeStock = useMutation({
    mutationFn: ({ batchId, quantity }: { batchId: string; quantity: number }) =>
      inventoryApi.consumeStock(batchId, quantity),
    onSuccess: () => { invalidate(); toast({ title: 'Stock consumed', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Consume failed', description: e.message, variant: 'destructive' }),
  })

  return { receiveGoods, adjustStock, consumeStock }
}
