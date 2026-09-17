import { useState, useEffect, useMemo } from 'react'
import { format, addDays, subDays, startOfWeek, endOfWeek, startOfMonth, endOfMonth, parseISO, isToday as isTodayFn } from 'date-fns'
import { useNavigate } from 'react-router-dom'
import { useProviderAppointments, useAppointmentMutations } from '../../../hooks/appointment/useAppointment'
import { useConsultants } from '../../../hooks/consultant/useConsultant'
import { cn } from '../../../lib/utils'
import { Appointment } from '../../../types/appointment'
import { ConsultantSearchInput } from '../../../components/shared/ConsultantSearchInput'
import DatePicker from '../../../components/shared/DatePicker'
import { QuickRegistrationModal } from '../components/QuickRegistrationModal'

const STATUS_STYLES = {
  BOOKED: 'bg-blue-50 text-blue-700 border-blue-200',
  RESCHEDULED: 'bg-amber-50 text-amber-700 border-amber-200',
  CHECKED_IN: 'bg-green-50 text-green-700 border-green-200',
  CANCELLED: 'bg-gray-100 text-gray-500 border-gray-200',
} as const

const TAB_CONFIG = {
  ALL: { active: 'bg-slate-800 border-slate-800 text-white', hover: 'hover:border-slate-400 hover:bg-slate-50', text: 'text-slate-600' },
  BOOKED: { active: 'bg-neutral-600 border-neutral-600 text-white', hover: 'hover:border-neutral-300 hover:bg-neutral-50', text: 'text-neutral-700' },
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
  const todayStr = useMemo(() => format(new Date(), 'yyyy-MM-dd'), [])

  // From Date & To Date Range states (Defaulting both to current date)
  const [fromDate, setFromDate] = useState<string>(todayStr)
  const [toDate, setToDate] = useState<string>(todayStr)

  const [selectedProviderId, setSelectedProviderId] = useState<string>('')
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const [searchQuery, setSearchQuery] = useState('')
  const [isRegistering, setIsRegistering] = useState(false)
  const [selectedApptForReg, setSelectedApptForReg] = useState<Appointment | null>(null)
  const [page, setPage] = useState(0)
  const pageSize = 10

  useEffect(() => {
    setPage(0)
  }, [selectedProviderId, fromDate, toDate, statusFilter, searchQuery])

  const isSingleDay = fromDate === toDate

  // Date navigation handlers
  const handlePrevDay = () => {
    try {
      const f = parseISO(fromDate)
      const t = parseISO(toDate)
      setFromDate(format(subDays(f, 1), 'yyyy-MM-dd'))
      setToDate(format(subDays(t, 1), 'yyyy-MM-dd'))
    } catch {
      setFromDate(todayStr)
      setToDate(todayStr)
    }
  }

  const handleNextDay = () => {
    try {
      const f = parseISO(fromDate)
      const t = parseISO(toDate)
      setFromDate(format(addDays(f, 1), 'yyyy-MM-dd'))
      setToDate(format(addDays(t, 1), 'yyyy-MM-dd'))
    } catch {
      setFromDate(todayStr)
      setToDate(todayStr)
    }
  }

  const handleSetToday = () => {
    setFromDate(todayStr)
    setToDate(todayStr)
  }

  const handleSetTomorrow = () => {
    const tom = format(addDays(new Date(), 1), 'yyyy-MM-dd')
    setFromDate(tom)
    setToDate(tom)
  }

  const handleSetThisWeek = () => {
    const now = new Date()
    setFromDate(format(startOfWeek(now, { weekStartsOn: 1 }), 'yyyy-MM-dd'))
    setToDate(format(endOfWeek(now, { weekStartsOn: 1 }), 'yyyy-MM-dd'))
  }

  const handleSetThisMonth = () => {
    const now = new Date()
    setFromDate(format(startOfMonth(now), 'yyyy-MM-dd'))
    setToDate(format(endOfMonth(now), 'yyyy-MM-dd'))
  }

  const handleFromDateChange = (val: string) => {
    if (!val) return
    setFromDate(val)
    if (val > toDate) {
      setToDate(val)
    }
  }

  const handleToDateChange = (val: string) => {
    if (!val) return
    setToDate(val)
    if (val < fromDate) {
      setFromDate(val)
    }
  }

  const getHeaderSubtitle = () => {
    if (isSingleDay) {
      try {
        const d = parseISO(fromDate)
        return `${isTodayFn(d) ? 'Today · ' : ''}${format(d, 'EEEE, dd MMM yyyy')}`
      } catch { return fromDate }
    }
    try {
      const f = parseISO(fromDate)
      const t = parseISO(toDate)
      return `${format(f, 'dd MMM yyyy')} to ${format(t, 'dd MMM yyyy')}`
    } catch { return `${fromDate} to ${toDate}` }
  }

  const { data: consultants } = useConsultants()
  const getConsultantFullNameWithDegree = (providerId: string, fallbackName: string | null) => {
    const match = consultants?.find(c => c.id === providerId)
    if (match) {
      const degree = match.specialisation || match.qualification
      return `${match.salutation || ''} ${match.firstName} ${match.lastName}${degree ? `, ${degree}` : ''}`.replace(/\s+/g, ' ').trim()
    }
    return fallbackName ?? '—'
  }

  const { data: appointments, isLoading } = useProviderAppointments(selectedProviderId || undefined, fromDate, toDate)
  const mutations = useAppointmentMutations()

  const counts = {
    ALL: appointments?.length ?? 0,
    BOOKED: appointments?.filter(a => a.status === 'BOOKED').length ?? 0,
    CHECKED_IN: appointments?.filter(a => a.status === 'CHECKED_IN').length ?? 0,
    CANCELLED: appointments?.filter(a => a.status === 'CANCELLED').length ?? 0,
    RESCHEDULED: appointments?.filter(a => a.status === 'RESCHEDULED').length ?? 0,
  }

  const filteredAppointments = appointments?.filter(a => {
    if (statusFilter !== 'ALL' && a.status !== statusFilter) return false
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase()
      const matchesName = a.patientName?.toLowerCase().includes(q) || a.tempPatientName?.toLowerCase().includes(q)
      const matchesNum = a.patientNumber?.toLowerCase().includes(q)
      const matchesPhone = a.patientPhone?.includes(q) || a.tempPatientPhone?.includes(q)
      if (!matchesName && !matchesNum && !matchesPhone) return false
    }
    return true
  })

  const totalPages = Math.ceil((filteredAppointments?.length || 0) / pageSize)
  const paginatedAppointments = filteredAppointments?.slice(page * pageSize, (page + 1) * pageSize)

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
      {/* Page Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-white p-6 rounded-2xl border border-gray-100 shadow-sm">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 tracking-tight">Appointment Schedule</h2>
          <p className="text-sm text-gray-500 font-medium mt-0.5">{getHeaderSubtitle()}</p>
        </div>
        <div className="flex items-center gap-3">
          <div className="flex items-center bg-gray-50 rounded-xl p-1 border border-gray-200">
            <button
              onClick={handlePrevDay}
              className="p-2 hover:bg-white hover:shadow-sm rounded-lg text-gray-600 transition-all text-xs font-bold"
              title="Previous Day"
            >
              ←
            </button>
            <DatePicker
              value={fromDate}
              onChange={(val) => {
                if (val) {
                  setFromDate(val)
                  setToDate(val)
                }
              }}
              clearable={false}
              customTrigger={({ onClick }) => (
                <button
                  type="button"
                  onClick={onClick}
                  className="w-32 py-2 text-xs font-bold text-gray-700 hover:text-neutral-900 transition-colors text-center hover:bg-white hover:shadow-sm rounded-lg flex items-center justify-center gap-1.5 cursor-pointer"
                >
                  <span>{fromDate === todayStr ? 'Today' : format(parseISO(fromDate), 'dd MMM yyyy')}</span>
                  <svg className="w-3.5 h-3.5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 002-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                  </svg>
                </button>
              )}
            />
            <button
              onClick={handleNextDay}
              className="p-2 hover:bg-white hover:shadow-sm rounded-lg text-gray-600 transition-all text-xs font-bold"
              title="Next Day"
            >
              →
            </button>
          </div>

          <button
            onClick={() => navigate('/appointments/book')}
            className="flex items-center gap-2 px-6 py-2.5 bg-neutral-600 text-white font-bold rounded-xl hover:bg-neutral-700 shadow-lg shadow-neutral-200 transition-all active:scale-[0.98]"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6v6m0 0v6m0-6h6m-6 0H6" /></svg>
            Book Appointment
          </button>
        </div>
      </div>

      {/* Filter & Search Bar */}
      <div className="bg-white p-5 rounded-2xl border border-gray-100 shadow-sm space-y-4">
        {/* Date Range Filter Controls */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-4 border-b border-gray-100">
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-2">
              <span className="text-xs font-bold text-gray-500 uppercase tracking-wider">From:</span>
              <div className="w-36">
                <DatePicker value={fromDate} onChange={handleFromDateChange} size="sm" clearable={false} />
              </div>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-xs font-bold text-gray-500 uppercase tracking-wider">To:</span>
              <div className="w-36">
                <DatePicker value={toDate} onChange={handleToDateChange} size="sm" clearable={false} />
              </div>
            </div>
          </div>

          <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar">
            <span className="text-xs font-semibold text-gray-400 mr-1">Presets:</span>
            <button
              onClick={handleSetToday}
              className={cn("px-2.5 py-1 rounded-lg text-xs font-semibold transition-all",
                fromDate === todayStr && toDate === todayStr
                  ? "bg-neutral-800 text-white shadow-sm"
                  : "bg-gray-100 text-gray-600 hover:bg-gray-200")}
            >
              Today
            </button>
            <button
              onClick={handleSetTomorrow}
              className={cn("px-2.5 py-1 rounded-lg text-xs font-semibold transition-all",
                fromDate === format(addDays(new Date(), 1), 'yyyy-MM-dd') && toDate === format(addDays(new Date(), 1), 'yyyy-MM-dd')
                  ? "bg-neutral-800 text-white shadow-sm"
                  : "bg-gray-100 text-gray-600 hover:bg-gray-200")}
            >
              Tomorrow
            </button>
            <button
              onClick={handleSetThisWeek}
              className="px-2.5 py-1 rounded-lg text-xs font-semibold bg-gray-100 text-gray-600 hover:bg-gray-200 transition-all"
            >
              This Week
            </button>
            <button
              onClick={handleSetThisMonth}
              className="px-2.5 py-1 rounded-lg text-xs font-semibold bg-gray-100 text-gray-600 hover:bg-gray-200 transition-all"
            >
              This Month
            </button>
          </div>
        </div>

        {/* Patient Search, Consultant Filter, and Status Tabs */}
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6">
          <div className="flex flex-col md:flex-row items-start md:items-center gap-4 flex-1">
            <div className="relative w-full md:w-64">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <svg className="h-4 w-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" /></svg>
              </div>
              <input type="text" placeholder="Search patient" value={searchQuery} onChange={e => setSearchQuery(e.target.value)}
                className="w-full pl-9 pr-4 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:ring-2 focus:ring-neutral-500 outline-none transition-all" />
            </div>
            <div className="h-6 w-px bg-gray-200 hidden md:block" />
            <div className="flex items-center gap-3 w-full md:w-auto min-w-[200px]">
              <ConsultantSearchInput
                consultants={(consultants ?? []).filter((c: any) => c.status !== 'INACTIVE' && c.status !== 0)}
                value={selectedProviderId}
                onChange={setSelectedProviderId}
                placeholder="All Consultants"
              />
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

      {/* Appointments List Table */}
      <div className="flex flex-col gap-6">
        {isLoading && <p className="text-sm text-gray-500" aria-live="polite">Loading appointments…</p>}
        {!isLoading && (
          <div className="bg-white rounded-xl border border-gray-200 overflow-hidden shadow-sm">
            <table className="w-full text-sm" role="table" aria-label="Appointments schedule">
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
                {paginatedAppointments?.map((a, index) => (
                  <tr key={a.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 text-gray-500 font-medium">{(page * pageSize) + index + 1}</td>
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
                    <td className="px-4 py-3 text-gray-600 font-medium" title={getConsultantFullNameWithDegree(a.providerId, a.providerName)}>{a.providerName ?? '—'}</td>
                    <td className="px-4 py-3 text-gray-600 font-medium whitespace-nowrap">
                      {a.appointmentDate ? format(parseISO(a.appointmentDate), 'dd/MM/yyyy') : '—'}
                    </td>
                    <td className="px-4 py-3 font-mono text-xs text-gray-600 whitespace-nowrap">
                      {formatTime(a.appointmentTime)} - {formatTime(a.appointmentEndTime)}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span className={cn('inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-bold uppercase tracking-wider border', STATUS_STYLES[a.status])}>
                        {a.status.replace('_', ' ')}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex gap-5 justify-center items-center">
                        {a.status === 'BOOKED' && (
                          <>
                            <button onClick={() => navigate('/appointments/reschedule', { state: { appointment: a } })}
                              className="text-xs text-neutral-600 hover:text-neutral-800 font-medium">
                              Reschedule
                            </button>
                            {a.appointmentDate === todayStr && (
                              <button onClick={() => handleCheckIn(a)}
                                disabled={mutations.checkIn.isPending || mutations.linkPatient.isPending}
                                className="text-xs text-green-600 hover:text-green-800 font-medium disabled:opacity-40">
                                Check In
                              </button>
                            )}
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
                      No appointments found for selected filter ({getHeaderSubtitle()})
                    </td>
                  </tr>
                )}
              </tbody>
            </table>

            {/* Pagination Footer */}
            {filteredAppointments && filteredAppointments.length > 0 && (
              <div className="px-6 py-4 bg-gray-50 border-t border-gray-200 flex items-center justify-between">
                <div className="text-xs text-gray-500">
                  Page <span className="font-medium text-gray-900">{String(page + 1)}</span> of <span className="font-medium text-gray-900">{String(totalPages || 1)}</span>
                  <span className="ml-2">· {String(filteredAppointments.length)} total appointments</span>
                </div>
                <div className="flex items-center gap-1">
                  <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}
                    className="p-1.5 text-gray-500 hover:text-neutral-600 hover:bg-neutral-50 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed">
                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7" /></svg>
                  </button>
                  {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
                    let pageNum = i
                    if (totalPages > 5 && page > 2) pageNum = Math.min(page - 2 + i, totalPages - 5 + i)
                    return (
                      <button key={pageNum} onClick={() => setPage(pageNum)}
                        className={cn("min-w-[32px] h-8 flex items-center justify-center rounded text-xs font-semibold transition-all",
                          page === pageNum ? "bg-neutral-600 text-white shadow-sm" : "text-gray-600 hover:bg-gray-100")}>
                        {String(pageNum + 1)}
                      </button>
                    )
                  })}
                  <button onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}
                    className="p-1.5 text-gray-500 hover:text-neutral-600 hover:bg-neutral-50 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed">
                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7" /></svg>
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

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
