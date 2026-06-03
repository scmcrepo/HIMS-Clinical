import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { opQueueApi, ipCasesheetApi, recordApi, templateApi } from '../../services/casesheet/casesheetApi'
import { toast } from '../useToast'
import type { SaveRecordRequest, CaseSheetVisitType } from '../../types/casesheet'

export function useCasesheetTemplates(specialization?: string, visitType?: CaseSheetVisitType) {
  return useQuery({
    queryKey: ['casesheet-templates', specialization, visitType],
    queryFn:  () => templateApi.list(specialization, visitType),
  })
}

export function useDefaultTemplate(specialization: string, visitType: CaseSheetVisitType, enabled = true) {
  return useQuery({
    queryKey: ['casesheet-template-default', specialization, visitType],
    queryFn:  () => templateApi.getDefault(specialization, visitType),
    enabled,
    retry: false,
  })
}

export function useOpCasesheet(encounterId: string, specialization?: string, visitType?: string) {
  return useQuery({
    queryKey: ['op-casesheet', encounterId, specialization],
    queryFn:  () => opQueueApi.loadCasesheet(encounterId, specialization, visitType),
    enabled: !!encounterId,
  })
}

export function useIpCasesheet(encounterId: string, specialization = 'GENERAL') {
  return useQuery({
    queryKey: ['ip-casesheet', encounterId, specialization],
    queryFn:  () => ipCasesheetApi.loadCasesheet(encounterId, specialization),
    enabled: !!encounterId,
  })
}

export function useCasesheetMutations(encounterId: string) {
  const qc = useQueryClient()
  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['op-casesheet',  encounterId] })
    qc.invalidateQueries({ queryKey: ['ip-casesheet',  encounterId] })
    qc.invalidateQueries({ queryKey: ['encounter',     encounterId] })
    qc.invalidateQueries({ queryKey: ['encounters'] })
    qc.invalidateQueries({ queryKey: ['op-queue'] })
  }

  const saveRecord = useMutation({
    mutationFn: (req: SaveRecordRequest) => recordApi.save(encounterId, req),
    onSuccess: () => { invalidate(); toast({ title: 'Case sheet saved', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Save failed', description: e.message, variant: 'destructive' }),
  })

  const markConsulted = useMutation({
    mutationFn: () => opQueueApi.markConsulted(encounterId),
    onSuccess: () => { invalidate(); toast({ title: 'Encounter marked as consulted', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  const requestAdmission = useMutation({
    mutationFn: (payload: { reason?: string; adviceToPatient?: string; instructionsToNurses?: string }) =>
      opQueueApi.requestAdmission(encounterId, payload),
    onSuccess: () => { invalidate(); toast({ title: 'Admission requested', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  return { saveRecord, markConsulted, requestAdmission }
}
