import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { diagnosticApi, type PlaceOrderCmd, type RecordResultCmd } from '../../services/diagnostic/diagnosticApi'
import { diagnosticReportApi } from '../../services/diagnostic/diagnosticReportApi'
import { departmentApi } from '../../services/config/departmentApi'
import { diagTemplateApi } from '../../services/diagnostic/diagTemplateApi'
import { specimenApi } from '../../services/diagnostic/specimenApi'
import { toast } from '../useToast'
import type { DiagnosticType } from '../../types/diagnostic'

export function useEncounterOrders(encounterId: string | undefined) {
  return useQuery({ queryKey: ['diagnostics', 'encounter', encounterId], queryFn: () => diagnosticApi.getByEncounter(encounterId!), enabled: !!encounterId })
}

export function useSpecimenCollections(diagnosticId: string | undefined) {
  return useQuery({
    queryKey: ['specimenCollections', diagnosticId],
    queryFn: () => diagnosticApi.getSpecimenCollections(diagnosticId!),
    enabled: !!diagnosticId,
  })
}

export function usePendingOrders(type: DiagnosticType, from: string, to: string) {
  return useQuery({
    queryKey: ['diagnostics', 'pending', type, from, to],
    queryFn: () => diagnosticApi.getPending(type, from, to),
    enabled: !!from && !!to,
  })
}

export function useTemplates() {
  return useQuery({ queryKey: ['diagTemplates'], queryFn: diagTemplateApi.getAll })
}

export function useDepartments() {
  return useQuery({ queryKey: ['departments'], queryFn: departmentApi.getAll })
}

export function useOrderLineReports(orderLineId: string | undefined) {
  return useQuery({
    queryKey: ['diagReports', orderLineId],
    queryFn: () => diagnosticReportApi.getReportsByOrderLine(orderLineId!),
    enabled: !!orderLineId,
  })
}

export function useDiagnosticMutations() {
  const qc = useQueryClient()
  const invalidate = (encounterId?: string) => {
    qc.invalidateQueries({ queryKey: ['diagnostics'] })
    if (encounterId) qc.invalidateQueries({ queryKey: ['diagnostics', 'encounter', encounterId] })
  }

  const placeOrder = useMutation({
    mutationFn: (cmd: PlaceOrderCmd) => diagnosticApi.placeOrder(cmd),
    onSuccess: (_, cmd) => { invalidate(cmd.encounterId); toast({ title: 'Order placed', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Order failed', description: e.message, variant: 'destructive' }),
  })
  const recordResult = useMutation({
    mutationFn: ({ orderId, cmd }: { orderId: string; cmd: RecordResultCmd }) => diagnosticApi.recordResult(orderId, cmd),
    onSuccess: () => { invalidate(); toast({ title: 'Result recorded', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })
  const cancelOrder = useMutation({
    mutationFn: (orderId: string) => diagnosticApi.cancelOrder(orderId),
    onSuccess: () => { invalidate(); toast({ title: 'Order cancelled', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })
  const saveLabReports = useMutation({
    mutationFn: ({ orderLineId, templateId, reports }: { orderLineId: string; templateId: string | null; reports: Record<string, string> }) =>
      diagnosticReportApi.saveLabReports(orderLineId, templateId, reports),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['diagReports'] }); toast({ title: 'Reports saved', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })
  const saveCustomReport = useMutation({
    mutationFn: ({ orderLineId, templateId, templateData }: { orderLineId: string; templateId: string; templateData: string }) =>
      diagnosticReportApi.saveCustomReport(orderLineId, templateId, templateData),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['diagReports'] }); toast({ title: 'Report saved', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  const recordSpecimenCollection = useMutation({
    mutationFn: (cmd: { diagnosticId: string; specimenId?: string | undefined; orderLineId?: string | undefined; notes?: string | undefined }) =>
      diagnosticApi.recordSpecimenCollection(cmd),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['specimenCollections'] })
      toast({ title: 'Specimen Collected', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  return { placeOrder, recordResult, cancelOrder, saveLabReports, saveCustomReport, recordSpecimenCollection }
}

export function useSpecimens() {
  return useQuery({ queryKey: ['specimens'], queryFn: specimenApi.getAll, staleTime: 0, gcTime: 0 })
}

/** For clinical dropdowns — only ACTIVE specimens */
export function useActiveSpecimens() {
  return useQuery({ queryKey: ['specimens', 'active'], queryFn: specimenApi.getActive })
}

export function useSpecimenMutations() {
  const qc = useQueryClient()

  const saveSpecimen = useMutation({
    mutationFn: (data: { id?: string; name: string; description?: string; status: 'ACTIVE' | 'INACTIVE' }) => {
      if (data.id) return specimenApi.update(data as any)
      return specimenApi.create(data as any)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['specimens'] })
      toast({ title: 'Specimen saved', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  return { saveSpecimen }
}
