import { useState } from 'react'
import { format, addDays, subDays } from 'date-fns'
import { useNavigate } from 'react-router-dom'
import { useProviderAppointments, useAppointmentMutations } from '../../../hooks/appointment/useAppointment'
import { useConsultants } from '../../../hooks/consultant/useConsultant'
import { cn } from '../../../lib/utils'
import { Appointment } from '@/types/appointment'
import { QuickRegistrationModal } from '../components/QuickRegistrationModal'

const STATUS_STYLES = {
  BOOKED: 'bg-blue-50 text-blue-700 border-blue-200',
  RESCHEDULED: 'bg-amber-50 text-amber-700 border-amber-200',
  CHECKED_IN: 'bg-green-50 text-green-700 border-green-200',
  CANCELLED: 'bg-gray-100 text-gray-500 border-gray-200',
} as const

const TAB_CONFIG = {
  ALL: { active: 'bg-slate-800 border-slate-800 text-white', hover: 'hover:border-slate-400 hover:bg-slate-50', text: 'text-slate-600' },
  BOOKED: { active: 'bg-indigo-600 border-indigo-600 text-white', hover: 'hover:border-indigo-300 hover:bg-indigo-50', text: 'text-indigo-700' },
  CHECKED_IN: { active: 'bg-teal-600 border-teal-600 text-white', hover: 'hover:border-teal-300 hover:bg-teal-50', text: 'text-teal-700' },
  CANCELLED: { active: 'bg-rose-500 border-rose-500 text-white', hover: 'hover:border-rose-300 hover:bg-rose-50', text: 'text-rose-600' },
  RESCHEDULED: { active: 'bg-amber-500 border-amber-500 text-white', hover: 'hover:border-amber-300 hover:bg-amber-50', text: 'text-amber-700' },
} as const

const formatTime = (timeStr?: string | null) => {
  if (!timeStr) return '—'
  try {
    const [hours, minutes] = timeStr.split(':')
    const date = new Date()
    date.setHours(parseInt(hours), parseInt(minutes), 0)
    return format(date, 'hh:mm a')
  } catch { return timeStr }
}

export default function AppointmentPage() {
  const navigate = useNavigate()
  const [date, setDate] = useState<Date>(new Date())
  const [selectedProviderId, setSelectedProviderId] = useState<string>('')
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const [searchQuery, setSearchQuery] = useState('')
  const [isRegistering, setIsRegistering] = useState(false)
  const [selectedApptForReg, setSelectedApptForReg] = useState<Appointment | null>(null)

  const dateStr = format(date, 'yyyy-MM-dd')
  const { data: consultants } = useConsultants()
  const { data: appointments, isLoading } = useProviderAppointments(selectedProviderId || undefined, dateStr)
  const mutations = useAppointmentMutations()

  const counts = {
    ALL: appointments?.filter(a => a.status !== 'CANCELLED').length ?? 0,
    BOOKED: appointments?.filter(a => a.status === 'BOOKED').length ?? 0,
    CHECKED_IN: appointments?.filter(a => a.status === 'CHECKED_IN').length ?? 0,
    CANCELLED: appointments?.filter(a => a.status === 'CANCELLED').length ?? 0,
    RESCHEDULED: appointments?.filter(a => a.status === 'RESCHEDULED').length ?? 0,
  }

  const filteredAppointments = appointments?.filter(a => {
    if (statusFilter === 'ALL' && a.status === 'CANCELLED') return false
    if (statusFilter !== 'ALL' && a.status !== statusFilter) {
      if (statusFilter === 'CHECKED_IN' && a.status !== 'CHECKED_IN') return false
      if (statusFilter !== 'CHECKED_IN') return false
    }
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase()
      const matchesName = a.patientName?.toLowerCase().includes(q) || a.tempPatientName?.toLowerCase().includes(q)
      const matchesNum = a.patientNumber?.toLowerCase().includes(q)
      const matchesPhone = a.patientPhone?.includes(q) || a.tempPatientPhone?.includes(q)
      if (!matchesName && !matchesNum && !matchesPhone) return false
    }
    return true
  })

  const handleCheckIn = (appt: Appointment) => {
    if (!appt.patientId) {
      setSelectedApptForReg(appt)
      setIsRegistering(true)
    } else {
      mutations.checkIn.mutate(appt.id)
    }
  }

  const handleRegSuccess = async (patientId: string) => {
    if (!selectedApptForReg) return
    await mutations.linkPatient.mutateAsync({ id: selectedApptForReg.id, patientId })
    mutations.checkIn.mutate(selectedApptForReg.id)
    setIsRegistering(false)
    setSelectedApptForReg(null)
  }

  return (
    <div className="space-y-6 max-w-6xl mx-auto px-4 py-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-white p-6 rounded-2xl border border-gray-100 shadow-sm">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 tracking-tight">Appointment Schedule</h2>
          <p className="text-sm text-gray-500 font-medium">{format(date, 'EEEE, dd MMM yyyy')}</p>
        </div>
        <div className="flex items-center gap-3">
          <div className="flex items-center bg-gray-50 rounded-xl p-1 border border-gray-200">
            <button onClick={() => setDate(d => subDays(d, 1))} className="p-2 hover:bg-white hover:shadow-sm rounded-lg text-gray-600 transition-all">←</button>
            <button onClick={() => setDate(new Date())} className="w-28 py-2 text-xs font-bold text-gray-700 hover:text-blue-600 transition-colors text-center">
              {format(date, 'yyyy-MM-dd') === format(new Date(), 'yyyy-MM-dd') ? 'Today' : format(date, 'dd MMM yyyy')}
            </button>
            <button onClick={() => setDate(d => addDays(d, 1))} className="p-2 hover:bg-white hover:shadow-sm rounded-lg text-gray-600 transition-all">→</button>
          </div>
          {/* CHANGED: Navigate to BookAppointmentPage instead of opening a modal */}
          <button
            onClick={() => navigate('/appointments/book')}
            className="flex items-center gap-2 px-6 py-2.5 bg-blue-600 text-white font-bold rounded-xl hover:bg-blue-700 shadow-lg shadow-blue-200 transition-all active:scale-[0.98]"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6v6m0 0v6m0-6h6m-6 0H6" /></svg>
            Book Appointment
          </button>
        </div>
      </div>

      <div className="bg-white p-4 rounded-2xl border border-gray-100 shadow-sm space-y-4">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6">
          <div className="flex flex-col md:flex-row items-start md:items-center gap-10 flex-1">
            <div className="relative w-full md:w-64">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <svg className="h-4 w-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" /></svg>
              </div>
              <input type="text" placeholder="Search patient" value={searchQuery} onChange={e => setSearchQuery(e.target.value)}
                className="w-full pl-9 pr-4 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:ring-2 focus:ring-blue-500 outline-none transition-all" />
            </div>
            <div className="h-6 w-px bg-gray-200 hidden md:block" />
            <div className="flex items-center gap-3 w-full md:w-auto">
              <select value={selectedProviderId} onChange={e => setSelectedProviderId(e.target.value)}
                className="flex-1 md:flex-none px-4 py-2 bg-white border border-gray-200 rounded-xl text-sm focus:ring-2 focus:ring-blue-500 outline-none min-w-[180px] shadow-sm">
                <option value="">All Consultants</option>
                {consultants?.filter((c: any) => c.status !== 'INACTIVE' && c.status !== 0).map(c => <option key={c.id} value={c.id}>{c.firstName} {c.lastName}</option>)}
              </select>
            </div>
          </div>
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 md:pb-0 no-scrollbar">
            {[
              { id: 'ALL', label: 'All' }, { id: 'BOOKED', label: 'Booked' },
              { id: 'CHECKED_IN', label: 'Checkedin' }, { id: 'CANCELLED', label: 'Cancelled' },
              { id: 'RESCHEDULED', label: 'Rescheduled' },
            ].map(f => {
              const config = (TAB_CONFIG as any)[f.id]
              const isActive = statusFilter === f.id
              return (
                <button key={f.id} onClick={() => setStatusFilter(f.id)}
                  className={cn("px-3 py-1.5 rounded-lg text-[11px] font-bold border transition-all flex items-center gap-2 whitespace-nowrap",
                    isActive ? `${config.active} shadow-md` : `bg-white text-gray-600 border-gray-200 ${config.hover}`)}>
                  {f.label}
                  <span className={cn("px-1.5 py-0.5 rounded-md text-[9px]", isActive ? "bg-white/20 text-white" : `bg-gray-100 ${config.text}`)}>
                    {(counts as any)[f.id]}
                  </span>
                </button>
              )
            })}
          </div>
        </div>
      </div>

      <div className="flex flex-col gap-6">
        {isLoading && <p className="text-sm text-gray-500" aria-live="polite">Loading…</p>}
        {!isLoading && (
          <div className="bg-white rounded-xl border border-gray-200 overflow-hidden shadow-sm">
            <table className="w-full text-sm" role="table" aria-label="Today's appointments">
              <thead>
                <tr className="bg-gray-50 border-b border-gray-100 text-left text-xs">
                  <th className="px-4 py-3 font-semibold text-gray-600 w-12">S.No</th>
                  <th className="px-4 py-3 font-semibold text-gray-600">Patient</th>
                  <th className="px-4 py-3 font-semibold text-gray-600">Contact</th>
                  <th className="px-4 py-3 font-semibold text-gray-600">Consultant</th>
                  <th className="px-4 py-3 font-semibold text-gray-600">Date</th>
                  <th className="px-4 py-3 font-semibold text-gray-600">Slot</th>
                  <th className="px-4 py-3 font-semibold text-gray-600">Status</th>
                  <th className="px-4 py-3 text-center font-semibold text-gray-600">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {filteredAppointments?.map((a, index) => (
                  <tr key={a.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 text-gray-500 font-medium">{index + 1}</td>
                    <td className="px-4 py-3">
                      <div className="flex flex-col">
                        <span className="text-gray-900 font-medium">{a.patientName || a.tempPatientName || 'Walk-in'}</span>
                        <div className="flex flex-col gap-0.5 mt-0.5">
                          {a.patientNumber && a.patientNumber !== 'N/A' && a.patientNumber !== '—' && (
                            <span className="text-[10px] font-mono text-gray-400">{a.patientNumber}</span>
                          )}
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-gray-600 font-medium">{a.patientPhone || a.tempPatientPhone || '—'}</td>
                    <td className="px-4 py-3 text-gray-600 font-medium">{a.providerName ?? '—'}</td>
                    <td className="px-4 py-3 text-gray-600 font-medium whitespace-nowrap">
                      {a.appointmentDate ? format(new Date(a.appointmentDate), 'dd/MM/yyyy') : '—'}
                    </td>
                    <td className="px-4 py-3 font-mono text-sm text-gray-700 whitespace-nowrap">
                      {formatTime(a.appointmentTime)} - {formatTime(a.appointmentEndTime)}
                    </td>
                    <td className="px-4 py-3">
                      <span className={cn('inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border', STATUS_STYLES[a.status])}>
                        {a.status.replace('_', ' ')}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex gap-5 justify-center items-center">
                        {(a.status === 'BOOKED' || a.status === 'RESCHEDULED') && (
                          <>
                            {/* CHANGED: Navigate to RescheduleAppointmentPage */}
                            <button onClick={() => navigate('/appointments/reschedule', { state: { appointment: a } })}
                              className="text-xs text-blue-600 hover:text-blue-800 font-medium">
                              Reschedule
                            </button>
                            <button onClick={() => handleCheckIn(a)}
                              disabled={mutations.checkIn.isPending || mutations.linkPatient.isPending}
                              className="text-xs text-green-600 hover:text-green-800 font-medium disabled:opacity-40">
                              Check In
                            </button>
                            <button onClick={() => mutations.cancel.mutate(a.id)}
                              disabled={mutations.cancel.isPending}
                              className="text-xs text-red-500 hover:text-red-700 disabled:opacity-40">
                              Cancel
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
                {(!filteredAppointments || filteredAppointments.length === 0) && (
                  <tr>
                    <td colSpan={8} className="px-4 py-10 text-center text-gray-400 text-sm">
                      No appointments for {format(date, 'dd MMM yyyy')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* QuickRegistrationModal kept: it's a contextual check-in inline action, not a standalone create workflow */}
      {isRegistering && selectedApptForReg && (
        <QuickRegistrationModal
          appointment={selectedApptForReg}
          onSuccess={handleRegSuccess}
          onCancel={() => { setIsRegistering(false); setSelectedApptForReg(null) }}
        />
      )}
    </div>
  )
}
