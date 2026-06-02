import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { patientApi, type RegisterPatientCmd, type UpdatePatientCmd } from '../../services/patient/patientApi'
import { toast } from '../useToast'

export function usePatient(patientId: string | undefined) {
  return useQuery({
    queryKey: ['patient', patientId],
    queryFn: () => patientApi.getById(patientId!),
    enabled: !!patientId,
  })
}

export function usePatientSearch(query: string, page = 0) {
  const [debouncedQuery, setDebouncedQuery] = useState(query)

  useEffect(() => {
    const handler = setTimeout(() => setDebouncedQuery(query), 400)
    return () => clearTimeout(handler)
  }, [query])

  return useQuery({
    queryKey: ['patients', 'search', debouncedQuery, page],
    queryFn: () => patientApi.search(debouncedQuery, page),
    enabled: debouncedQuery.length >= 2,
    placeholderData: keepPreviousData,
  })
}

export function useRegisterPatient() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (cmd: RegisterPatientCmd) => patientApi.register(cmd),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['patients'] })
      qc.invalidateQueries({ queryKey: ['encounters'] })
      toast({ title: 'Patient registered', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Registration failed', description: e.message, variant: 'destructive' }),
  })
}

export function useUpdatePatient(patientId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (cmd: UpdatePatientCmd) => patientApi.update(patientId, cmd),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['patient', patientId] })
      toast({ title: 'Patient updated', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Update failed', description: e.message, variant: 'destructive' }),
  })
}
