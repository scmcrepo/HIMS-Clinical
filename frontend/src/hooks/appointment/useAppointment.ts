import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { appointmentApi, type BookAppointmentCmd, type RescheduleCmd, type CreateTimeBlockCmd } from '../../services/appointment/appointmentApi'
import { toast } from '../useToast'
import { useAuthStore } from '../../store/authStore'

export function useProviderAppointments(providerId: string | undefined, fromDate: string, toDate?: string) {
  const branchId = useAuthStore(state => state.selectedBranchId || state.user?.branchId || null)
  const actualTo = toDate || fromDate
  return useQuery({
    queryKey: ['appointments', 'provider', branchId, providerId, fromDate, actualTo],
    queryFn: () => providerId 
      ? appointmentApi.getByProvider(providerId, fromDate, actualTo)
      : appointmentApi.getByDate(fromDate, actualTo),
    enabled: !!fromDate,
    staleTime: 0,
  })
}

/**
 * Appointments across a date range, for the month and week calendar grids.
 *
 * <p>Same endpoint the list view uses — {@link useProviderAppointments} with a
 * `to` date — but named for the calendar's intent and disabled until a range
 * exists, so the calendar does not fire a request while it is still resolving
 * which month it is showing.
 */
export function useDateRangeAppointments(startDate: string, endDate: string, providerId?: string) {
  const branchId = useAuthStore(state => state.selectedBranchId || state.user?.branchId || null)
  return useQuery({
    queryKey: ['appointments', 'range', branchId, providerId ?? 'all', startDate, endDate],
    queryFn: () => providerId
      ? appointmentApi.getByProvider(providerId, startDate, endDate)
      : appointmentApi.getByDate(startDate, endDate),
    enabled: !!startDate && !!endDate,
    staleTime: 0,
  })
}

/** Every consultant's leaves and time blocks in a range — calendar background events. */
export function useLeavesInRange(startDate: string, endDate: string, consultantId?: string) {
  const branchId = useAuthStore(state => state.selectedBranchId || state.user?.branchId || null)
  return useQuery({
    queryKey: ['consultant-leaves', 'range', branchId, consultantId ?? 'all', startDate, endDate],
    queryFn: () => appointmentApi.getLeavesInRange(startDate, endDate, consultantId),
    enabled: !!startDate && !!endDate,
    staleTime: 0,
  })
}

export function useSlotAvailability(providerId: string | undefined, date: string) {
  const branchId = useAuthStore(state => state.selectedBranchId || state.user?.branchId || null)
  return useQuery({
    queryKey: ['slots', 'availability', branchId, providerId, date],
    queryFn: () => appointmentApi.getAvailability(providerId!, date),
    enabled: !!providerId && !!date,
    staleTime: 0,
  })
}

export function useDayBoard(date: string) {
  const branchId = useAuthStore(state => state.selectedBranchId || state.user?.branchId || null)
  return useQuery({
    queryKey: ['appointments', 'day-board', branchId, date],
    queryFn: () => appointmentApi.getDayBoard(date),
    enabled: !!date,
    staleTime: 0,
  })
}

export function useAvailabilityCheck(providerId: string | undefined, date: string) {
  const branchId = useAuthStore(state => state.selectedBranchId || state.user?.branchId || null)
  return useQuery({
    queryKey: ['slots', 'availability-check', branchId, providerId, date],
    queryFn: () => appointmentApi.getAvailabilityCheck(providerId!, date),
    enabled: !!providerId && !!date,
    staleTime: 0,
  })
}

export function usePatientAppointments(patientId: string | undefined, page = 0) {
  const branchId = useAuthStore(state => state.selectedBranchId || state.user?.branchId || null)
  return useQuery({
    queryKey: ['appointments', 'patient', branchId, patientId, page],
    queryFn: () => appointmentApi.getByPatient(patientId!, page),
    enabled: !!patientId,
  })
}

export function useAppointment(appointmentId: string | undefined) {
  const branchId = useAuthStore(state => state.selectedBranchId || state.user?.branchId || null)
  return useQuery({
    queryKey: ['appointment', branchId, appointmentId],
    queryFn: () => appointmentApi.getById(appointmentId!),
    enabled: !!appointmentId,
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

export function useConsultantCalendar(startDate: string, endDate: string, consultantId?: string) {
  return useQuery({
    queryKey: ['consultant-calendar', startDate, endDate, consultantId],
    queryFn: () => appointmentApi.getCalendar(startDate, endDate, consultantId),
    enabled: !!startDate && !!endDate,
    staleTime: 0,
  })
}

export function useConsultantLeaves() {
  return useQuery({
    queryKey: ['consultant-leaves'],
    queryFn: () => appointmentApi.getLeaves(),
    staleTime: 0,
  })
}

export function useConsultantLeavesById(consultantId: string | undefined) {
  return useQuery({
    queryKey: ['consultant-leaves-by-id', consultantId],
    queryFn: () => appointmentApi.getLeavesByConsultantId(consultantId!),
    enabled: !!consultantId,
    staleTime: 0,
  })
}

export function useConsultantLeaveMutations() {
  const qc = useQueryClient()
  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['consultant-calendar'] })
    qc.invalidateQueries({ queryKey: ['consultant-leaves'] })
    qc.invalidateQueries({ queryKey: ['consultant-leaves-by-id'] })
    qc.invalidateQueries({ queryKey: ['appointments'] })
  }

  const createLeave = useMutation({
    mutationFn: (cmd: { startDate: string; endDate: string; reason?: string; consultantId?: string }) =>
      appointmentApi.createLeave(cmd),
    onSuccess: () => { invalidate(); toast({ title: 'Leave marked successfully', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Failed to mark leave', description: e.message, variant: 'destructive' }),
  })

  const deleteLeave = useMutation({
    mutationFn: (leaveId: string) => appointmentApi.deleteLeave(leaveId),
    onSuccess: () => { invalidate(); toast({ title: 'Leave cancelled', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Failed to cancel leave', description: e.message, variant: 'destructive' }),
  })

  const createTimeBlocks = useMutation({
    mutationFn: (cmd: CreateTimeBlockCmd) => appointmentApi.createTimeBlocks(cmd),
    onSuccess: (blocks) => {
      invalidate()
      toast({
        title: blocks.length > 1 ? `${String(blocks.length)} time ranges blocked` : 'Time blocked',
        variant: 'success',
      })
    },
    onError: (e: Error) => toast({ title: 'Failed to block time', description: e.message, variant: 'destructive' }),
  })

  return { createLeave, deleteLeave, createTimeBlocks }
}
