import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { billingApi, type AddChargeCmd, type ApplyDiscountCmd, type GenerateBillCmd, type RecordPaymentCmd, type RefundCmd } from '../../services/billing/billingApi'
import { toast } from '../useToast'

export function useBill(billId: string | undefined) {
  return useQuery({
    queryKey: ['bill', billId],
    queryFn: () => billingApi.getBillById(billId!),
    enabled: !!billId,
    staleTime: 0,
  })
}

export function useBillsByPatient(patientId: string | undefined) {
  return useQuery({
    queryKey: ['bills', 'patient', patientId],
    queryFn: () => billingApi.getBillsByPatient(patientId!),
    enabled: !!patientId,
    staleTime: 0,
  })
}

export function useCreateBill() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (cmd: import('../../services/billing/billingApi').CreateBillCmd) => billingApi.createBill(cmd),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['bills'] })
      toast({ title: 'Bill created', variant: 'success' })
    },
    onError: (e: any) => toast({ title: 'Error', description: e.response?.data?.message || e.message, variant: 'destructive' }),
  })
}

export function useBillingMutations(billId: string) {
  const qc = useQueryClient()
  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['bill', billId] })
    qc.invalidateQueries({ queryKey: ['bills'] })
    qc.invalidateQueries({ queryKey: ['diagnostics'] })
    qc.invalidateQueries({ queryKey: ['encounters'] })
    qc.invalidateQueries({ queryKey: ['op-queue'] })
  }

  const recordPayment = useMutation({
    mutationFn: (cmd: RecordPaymentCmd) => billingApi.recordPayment(billId, cmd),
    onSuccess: () => { invalidate(); toast({ title: 'Payment recorded', variant: 'success' }) },
    onError: (e: any) => toast({ title: 'Error', description: e.response?.data?.message || e.message, variant: 'destructive' }),
  })

  const generateBill = useMutation({
    mutationFn: (cmd: GenerateBillCmd) => billingApi.generateBill(billId, cmd),
    onSuccess: () => { invalidate(); toast({ title: 'Bill generated successfully', variant: 'success' }) },
    onError: (e: any) => toast({ title: 'Error', description: e.response?.data?.message || e.message, variant: 'destructive' }),
  })

  const applyDiscount = useMutation({
    mutationFn: (cmd: ApplyDiscountCmd) => billingApi.applyDiscount(billId, cmd),
    onSuccess: () => { invalidate(); toast({ title: 'Discount applied', variant: 'success' }) },
    onError: (e: any) => toast({ title: 'Error', description: e.response?.data?.message || e.message, variant: 'destructive' }),
  })

  const cancelDiscount = useMutation({
    mutationFn: () => billingApi.cancelDiscount(billId),
    onSuccess: () => { invalidate(); toast({ title: 'Discount cancelled', variant: 'success' }) },
    onError: (e: any) => toast({ title: 'Error', description: e.response?.data?.message || e.message, variant: 'destructive' }),
  })

  const addCharge = useMutation({
    mutationFn: (cmd: AddChargeCmd) => billingApi.addCharge(billId, cmd),
    onSuccess: () => { invalidate(); toast({ title: 'Charge added', variant: 'success' }) },
    onError: (e: any) => toast({ title: 'Error', description: e.response?.data?.message || e.message, variant: 'destructive' }),
  })

  const removeCharge = useMutation({
    mutationFn: ({ lineItemId, reason }: { lineItemId: string; reason?: string }) =>
      billingApi.removeCharge(billId, lineItemId, reason),
    onSuccess: () => { invalidate(); toast({ title: 'Charge removed', variant: 'success' }) },
    onError: (e: any) => toast({ title: 'Error', description: e.response?.data?.message || e.message, variant: 'destructive' }),
  })

  const updateCharge = useMutation({
    mutationFn: (cmd: import('../../services/billing/billingApi').UpdateChargeCmd) =>
      billingApi.updateCharge(billId, cmd),
    onSuccess: () => { invalidate(); toast({ title: 'Charge updated', variant: 'success' }) },
    onError: (e: any) => toast({ title: 'Error', description: e.response?.data?.message || e.message, variant: 'destructive' }),
  })

  const refund = useMutation({
    mutationFn: (cmd: RefundCmd) => billingApi.refund(billId, cmd),
    onSuccess: () => { invalidate(); toast({ title: 'Refund processed successfully', variant: 'success' }) },
    onError: (e: any) => toast({ title: 'Error', description: e.response?.data?.message || e.message, variant: 'destructive' }),
  })

  return { recordPayment, generateBill, applyDiscount, cancelDiscount, addCharge, removeCharge, updateCharge, refund }
}
