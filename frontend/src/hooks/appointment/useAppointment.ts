import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { appointmentApi, type BookAppointmentCmd, type RescheduleCmd } from '../../services/appointment/appointmentApi'
import { toast } from '../useToast'

export function useProviderAppointments(providerId: string | undefined, date: string) {
  return useQuery({
    queryKey: ['appointments', 'provider', providerId, date],
    queryFn: () => providerId 
      ? appointmentApi.getByProvider(providerId, date)
      : appointmentApi.getByDate(date),
    enabled: !!date,
    staleTime: 0,
  })
}

export function useSlotAvailability(providerId: string | undefined, date: string) {
  return useQuery({
    queryKey: ['slots', 'availability', providerId, date],
    queryFn: () => appointmentApi.getAvailability(providerId!, date),
    enabled: !!providerId && !!date,
    staleTime: 0,
  })
}

export function usePatientAppointments(patientId: string | undefined, page = 0) {
  return useQuery({
    queryKey: ['appointments', 'patient', patientId, page],
    queryFn: () => appointmentApi.getByPatient(patientId!, page),
    enabled: !!patientId,
  })
}

export function useAppointmentMutations() {
  const qc = useQueryClient()
  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['appointments'] })
    qc.invalidateQueries({ queryKey: ['op-queue'] })
    qc.invalidateQueries({ queryKey: ['encounters'] })
  }

  const book = useMutation({
    mutationFn: (cmd: BookAppointmentCmd) => appointmentApi.book(cmd),
    onSuccess: () => { invalidate(); toast({ title: 'Appointment booked', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Booking failed', description: e.message, variant: 'destructive' }),
  })

  const reschedule = useMutation({
    mutationFn: ({ id, cmd }: { id: string; cmd: RescheduleCmd }) =>
      appointmentApi.reschedule(id, cmd),
    onSuccess: () => { invalidate(); toast({ title: 'Appointment rescheduled', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Reschedule failed', description: e.message, variant: 'destructive' }),
  })

  const checkIn = useMutation({
    mutationFn: (id: string) => appointmentApi.checkIn(id),
    onSuccess: () => { invalidate(); toast({ title: 'Patient checked in', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Check-in failed', description: e.message, variant: 'destructive' }),
  })

  const cancel = useMutation({
    mutationFn: (id: string) => appointmentApi.cancel(id),
    onSuccess: () => { invalidate(); toast({ title: 'Appointment cancelled', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Cancel failed', description: e.message, variant: 'destructive' }),
  })

  const linkPatient = useMutation({
    mutationFn: ({ id, patientId }: { id: string; patientId: string }) =>
      appointmentApi.linkPatient(id, patientId),
    onSuccess: () => { invalidate(); toast({ title: 'Patient linked', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Link failed', description: e.message, variant: 'destructive' }),
  })

  return { book, reschedule, checkIn, cancel, linkPatient }
}
