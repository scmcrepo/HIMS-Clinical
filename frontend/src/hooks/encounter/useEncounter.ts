import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { encounterApi, type RecordVitalsCmd, type RecordCasesheetCmd, type DischargeCmd } from '../../services/encounter/encounterApi'
import { toast } from '../useToast'

export function useEncounter(encounterId: string | undefined) {
  return useQuery({ queryKey: ['encounter', encounterId], queryFn: () => encounterApi.getById(encounterId!), enabled: !!encounterId })
}

export function usePatientEncounters(patientId: string | undefined, page = 0) {
  return useQuery({ queryKey: ['encounters', 'patient', patientId, page], queryFn: () => encounterApi.getByPatient(patientId!, page), enabled: !!patientId })
}

export function useEncounterMutations(encounterId: string) {
  const qc = useQueryClient()
  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['encounter', encounterId] })
    qc.invalidateQueries({ queryKey: ['encounters'] })
    qc.invalidateQueries({ queryKey: ['op-queue'] })
    qc.invalidateQueries({ queryKey: ['ip-ward'] })
    qc.invalidateQueries({ queryKey: ['beds'] })
    qc.invalidateQueries({ queryKey: ['patients'] })
    qc.invalidateQueries({ queryKey: ['bills'] })
    qc.invalidateQueries({ queryKey: ['appointments'] })
  }

  const recordVitals = useMutation({
    mutationFn: (cmd: RecordVitalsCmd) => encounterApi.recordVitals(encounterId, cmd),
    onSuccess: () => { invalidate(); toast({ title: 'Vitals recorded', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })
  const recordCasesheet = useMutation({
    mutationFn: (cmd: RecordCasesheetCmd) => encounterApi.recordCasesheet(encounterId, cmd),
    onSuccess: () => { invalidate(); toast({ title: 'Casesheet saved', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })
  const discharge = useMutation({
    mutationFn: (cmd: DischargeCmd) => encounterApi.discharge(encounterId, cmd),
    onSuccess: () => { invalidate(); toast({ title: 'Patient discharged', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Discharge failed', description: e.message, variant: 'destructive' }),
  })
  const cancel = useMutation({
    mutationFn: () => encounterApi.cancel(encounterId),
    onSuccess: () => { invalidate(); toast({ title: 'Encounter cancelled', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })
  return { recordVitals, recordCasesheet, discharge, cancel }
}
