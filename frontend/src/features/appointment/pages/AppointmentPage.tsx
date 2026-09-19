import { useState, useEffect, useMemo, useCallback } from 'react'
import { format, addDays, subDays, startOfWeek, endOfWeek, startOfMonth, endOfMonth, parseISO, isValid, isToday as isTodayFn } from 'date-fns'
import { useNavigate } from 'react-router-dom'
import { useProviderAppointments, useAppointmentMutations } from '../../../hooks/appointment/useAppointment'
import { useConsultants } from '../../../hooks/consultant/useConsultant'
import { cn } from '../../../lib/utils'
import { formatDate } from '../../../lib/dateUtils'
import { Appointment } from '../../../types/appointment'
import { ConsultantSearchInput } from '../../../components/shared/ConsultantSearchInput'
import DatePicker from '../../../components/shared/DatePicker'
import { QuickRegistrationModal } from '../components/QuickRegistrationModal'
import { formatAgeGender } from '../components/AppointmentDetailModal'
import { ConfirmModal } from '../../../components/ui/ConfirmModal'

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

/** parseISO throws nothing but can return Invalid Date; every caller wants a fallback. */
const safeParse = (value: string, fallback: Date) => {
  const parsed = parseISO(value)
  return isValid(parsed) ? parsed : fallback
}

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
  const tomorrowStr = useMemo(() => format(addDays(new Date(), 1), 'yyyy-MM-dd'), [])
  const thisWeekStart = useMemo(() => format(startOfWeek(new Date(), { weekStartsOn: 1 }), 'yyyy-MM-dd'), [])
  const thisWeekEnd = useMemo(() => format(endOfWeek(new Date(), { weekStartsOn: 1 }), 'yyyy-MM-dd'), [])
  const thisMonthStart = useMemo(() => format(startOfMonth(new Date()), 'yyyy-MM-dd'), [])
  const thisMonthEnd = useMemo(() => format(endOfMonth(new Date()), 'yyyy-MM-dd'), [])

  // From Date & To Date Range states (Defaulting both to current date)
  const [fromDate, setFromDate] = useState<string>(todayStr)
  const [toDate, setToDate] = useState<string>(todayStr)

  const [selectedProviderId, setSelectedProviderId] = useState<string>('')
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const [searchQuery, setSearchQuery] = useState('')
  const [isRegistering, setIsRegistering] = useState(false)
  const [selectedApptForReg, setSelectedApptForReg] = useState<Appointment | null>(null)
  const [appointmentToCancel, setAppointmentToCancel] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const pageSize = 10

  useEffect(() => {
    setPage(0)
  }, [selectedProviderId, fromDate, toDate, statusFilter, searchQuery])

  const isSingleDay = fromDate === toDate

  const handlePrevDay = () => {
    const prev = format(subDays(safeParse(fromDate, new Date()), 1), 'yyyy-MM-dd')
    setFromDate(prev)
    setToDate(prev)
  }

  const handleNextDay = () => {
    const next = format(addDays(safeParse(fromDate, new Date()), 1), 'yyyy-MM-dd')
    setFromDate(next)
    setToDate(next)
  }

  const handleSetToday = () => {
    setFromDate(todayStr)
    setToDate(todayStr)
  }

  const handleSetTomorrow = () => {
    setFromDate(tomorrowStr)
    setToDate(tomorrowStr)
  }

  const handleSetThisWeek = () => {
    setFromDate(thisWeekStart)
    setToDate(thisWeekEnd)
  }

  const handleSetThisMonth = () => {
    setFromDate(thisMonthStart)
    setToDate(thisMonthEnd)
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
  const getConsultantInfo = useCallback((providerId: string, fallbackName: string | null) => {
    const match = consultants?.find(c => c.id === providerId)
    if (match) {
      const name = `${match.salutation ? match.salutation + ' ' : ''}${match.firstName} ${match.lastName}`.trim()
      const degree = match.specialisation || match.qualification
      return { name, degree }
    }
    return { name: fallbackName ?? '—', degree: null }
  }, [consultants])

  const getConsultantFullNameWithDegree = useCallback((providerId: string, fallbackName: string | null) => {
    const info = getConsultantInfo(providerId, fallbackName)
    return `${info.name}${info.degree ? `, ${info.degree}` : ''}`
  }, [getConsultantInfo])

  const { data: appointments, isLoading } = useProviderAppointments(
    selectedProviderId || undefined,
    fromDate,
    toDate
  )
  const mutations = useAppointmentMutations()

  const searchFilteredAppointments = useMemo(() => {
    if (!appointments) return []
    if (!searchQuery.trim()) return appointments
    const q = searchQuery.toLowerCase().trim()
    return appointments.filter(a => {
      const matchesName = Boolean(a.patientName?.toLowerCase().includes(q) || a.tempPatientName?.toLowerCase().includes(q))
      const matchesNum = Boolean(a.patientNumber?.toLowerCase().includes(q))
      const matchesPhone = Boolean(a.patientPhone?.includes(q) || a.tempPatientPhone?.includes(q))
      const consultantFull = getConsultantFullNameWithDegree(a.providerId, a.providerName).toLowerCase()
      const matchesConsultant = Boolean(a.providerName?.toLowerCase().includes(q) || consultantFull.includes(q))
      return matchesName || matchesNum || matchesPhone || matchesConsultant
    })
  }, [appointments, searchQuery, getConsultantFullNameWithDegree])

  const counts = useMemo(() => ({
    ALL: searchFilteredAppointments.length,
    BOOKED: searchFilteredAppointments.filter(a => a.status === 'BOOKED').length,
    CHECKED_IN: searchFilteredAppointments.filter(a => a.status === 'CHECKED_IN').length,
    CANCELLED: searchFilteredAppointments.filter(a => a.status === 'CANCELLED').length,
    RESCHEDULED: searchFilteredAppointments.filter(a => a.status === 'RESCHEDULED').length,
  }), [searchFilteredAppointments])

  const filteredAppointments = useMemo(() => {
    if (statusFilter === 'ALL') return searchFilteredAppointments
    return searchFilteredAppointments.filter(a => a.status === statusFilter)
  }, [searchFilteredAppointments, statusFilter])

  const totalPages = Math.ceil((filteredAppointments?.length || 0) / pageSize)
  const paginatedAppointments = filteredAppointments?.slice(page * pageSize, (page + 1) * pageSize)

  return (
    <div className="space-y-4">
      {/* Header Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-gray-900 tracking-tight">Appointments</h1>
          <p className="text-xs text-gray-500 font-medium mt-0.5">
            {getHeaderSubtitle()}
          </p>
        </div>

        <div className="flex items-center gap-2.5 flex-wrap">
          {/* ← Day Nav → */}
          <div className="flex items-center bg-gray-50 rounded-xl p-0.5 border border-gray-200">
            <button
              onClick={handlePrevDay}
              className="p-1.5 hover:bg-white hover:shadow-xs rounded-lg text-gray-600 transition-all text-xs font-bold cursor-pointer"
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
                  className="w-28 py-1.5 text-xs font-bold text-gray-700 hover:text-neutral-900 transition-colors text-center hover:bg-white hover:shadow-xs rounded-lg flex items-center justify-center gap-1 cursor-pointer"
                >
                  <span>{fromDate === todayStr ? 'Today' : format(safeParse(fromDate, new Date()), 'dd MMM yyyy')}</span>
                  <svg className="w-3.5 h-3.5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 002-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                  </svg>
                </button>
              )}
            />
            <button
              onClick={handleNextDay}
              className="p-1.5 hover:bg-white hover:shadow-xs rounded-lg text-gray-600 transition-all text-xs font-bold cursor-pointer"
              title="Next Day"
            >
              →
            </button>
          </div>

          <button
            onClick={() => navigate('/appointments/book')}
            className="flex items-center gap-1.5 px-4 py-2 bg-neutral-800 text-white text-xs font-bold rounded-xl hover:bg-neutral-900 shadow-sm transition-all active:scale-[0.98] cursor-pointer"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6v6m0 0v6m0-6h6m-6 0H6" /></svg>
            Book Appointment
          </button>
        </div>
      </div>

      {/* Filter & Search Bar */}
      <div className="bg-white p-3.5 rounded-xl border border-gray-100 shadow-xs space-y-3">
        {/* Date Range Filter Controls */}
        <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-gray-100">
          <div className="flex flex-wrap items-center gap-2.5">
            <div className="flex items-center gap-1.5">
              <span className="text-[11px] font-bold text-gray-500 uppercase tracking-wider">From:</span>
              <div className="w-32">
                <DatePicker value={fromDate} onChange={handleFromDateChange} size="sm" clearable={false} />
              </div>
            </div>
            <div className="flex items-center gap-1.5">
              <span className="text-[11px] font-bold text-gray-500 uppercase tracking-wider">To:</span>
              <div className="w-32">
                <DatePicker value={toDate} onChange={handleToDateChange} size="sm" clearable={false} />
              </div>
            </div>
          </div>

          <div className="flex items-center gap-1 overflow-x-auto no-scrollbar flex-wrap">
            <span className="text-[11px] font-semibold text-gray-400 mr-1">Presets:</span>
            <button
              onClick={handleSetToday}
              className={cn("px-2.5 py-1 rounded-lg text-[11px] font-semibold transition-all cursor-pointer",
                fromDate === todayStr && toDate === todayStr
                  ? "bg-neutral-800 text-white shadow-xs"
                  : "bg-gray-100 text-gray-600 hover:bg-gray-200")}
            >
              Today
            </button>
            <button
              onClick={handleSetTomorrow}
              className={cn("px-2.5 py-1 rounded-lg text-[11px] font-semibold transition-all cursor-pointer",
                fromDate === tomorrowStr && toDate === tomorrowStr
                  ? "bg-neutral-800 text-white shadow-xs"
                  : "bg-gray-100 text-gray-600 hover:bg-gray-200")}
            >
              Tomorrow
            </button>
            <button
              onClick={handleSetThisWeek}
              className={cn("px-2.5 py-1 rounded-lg text-[11px] font-semibold transition-all cursor-pointer",
                fromDate === thisWeekStart && toDate === thisWeekEnd
                  ? "bg-neutral-800 text-white shadow-xs"
                  : "bg-gray-100 text-gray-600 hover:bg-gray-200")}
            >
              This Week
            </button>
            <button
              onClick={handleSetThisMonth}
              className={cn("px-2.5 py-1 rounded-lg text-[11px] font-semibold transition-all cursor-pointer",
                fromDate === thisMonthStart && toDate === thisMonthEnd
                  ? "bg-neutral-800 text-white shadow-xs"
                  : "bg-gray-100 text-gray-600 hover:bg-gray-200")}
            >
              This Month
            </button>
          </div>
        </div>

        {/* Search, Consultant filter, and Status tabs */}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2.5 flex-1 min-w-[280px]">
            <div className="relative w-full sm:w-56">
              <div className="absolute inset-y-0 left-0 pl-2.5 flex items-center pointer-events-none text-gray-400">
                <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" /></svg>
              </div>
              <input
                type="text"
                placeholder="Search patient"
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                className="w-full pl-8 pr-7 py-1.5 bg-gray-50 border border-gray-200 rounded-lg text-xs focus:ring-2 focus:ring-neutral-500 outline-none transition-all"
              />
              {searchQuery && (
                <button
                  type="button"
                  onClick={() => setSearchQuery('')}
                  className="absolute inset-y-0 right-0 pr-2 flex items-center text-gray-400 hover:text-gray-600 transition-colors cursor-pointer"
                  title="Clear search"
                  aria-label="Clear search"
                >
                  <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              )}
            </div>
            <div className="flex items-center gap-2 w-full sm:w-auto min-w-[180px]">
              <ConsultantSearchInput
                consultants={(consultants ?? []).filter((c: any) => c.status !== 'INACTIVE' && c.status !== 0)}
                value={selectedProviderId}
                onChange={setSelectedProviderId}
                placeholder="All Consultants"
              />
            </div>
          </div>
          <div className="flex items-center gap-1 overflow-x-auto no-scrollbar flex-wrap">
            {[
              { id: 'ALL', label: 'All' }, { id: 'BOOKED', label: 'Booked' },
              { id: 'CHECKED_IN', label: 'Checkedin' }, { id: 'CANCELLED', label: 'Cancelled' },
              { id: 'RESCHEDULED', label: 'Rescheduled' },
            ].map(f => {
              const config = (TAB_CONFIG as any)[f.id]
              const isActive = statusFilter === f.id
              return (
                <button key={f.id} onClick={() => setStatusFilter(f.id)}
                  className={cn("px-2.5 py-1 rounded-lg text-[11px] font-bold border transition-all flex items-center gap-1.5 whitespace-nowrap cursor-pointer",
                    isActive ? `${config.active} shadow-xs` : `bg-white text-gray-600 border-gray-200 ${config.hover}`)}>
                  {f.label}
                  <span className={cn("px-1 py-0.2 rounded text-[9px]", isActive ? "bg-white/20 text-white" : `bg-gray-100 ${config.text}`)}>
                    {(counts as any)[f.id]}
                  </span>
                </button>
              )
            })}
          </div>
        </div>
      </div>

      {/* Appointments List Table */}
      <div className="flex flex-col gap-4">
        {isLoading && <p className="text-sm text-gray-500" aria-live="polite">Loading appointments…</p>}
        {!isLoading && (
          <div className="bg-white rounded-xl border border-gray-200 overflow-hidden shadow-xs">
            <table className="w-full text-xs" role="table" aria-label="Appointments schedule">
              <thead>
                <tr className="bg-gray-50 border-b border-gray-100 text-left text-xs">
                  <th className="px-2.5 py-2.5 font-semibold text-gray-600 w-10 text-center">S.No</th>
                  <th className="px-3 py-2.5 font-semibold text-gray-600">Patient</th>
                  <th className="px-2.5 py-2.5 font-semibold text-gray-600">Contact</th>
                  <th className="px-3 py-2.5 font-semibold text-gray-600">Consultant</th>
                  <th className="px-2.5 py-2.5 font-semibold text-gray-600">Date</th>
                  <th className="px-2.5 py-2.5 font-semibold text-gray-600">Slot</th>
                  <th className="px-2.5 py-2.5 font-semibold text-gray-600">Status</th>
                  <th className="px-3 py-2.5 text-right font-semibold text-gray-600">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {paginatedAppointments?.map((a, index) => (
                  <tr key={a.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-2.5 py-2 text-gray-500 font-medium text-center">{(page * pageSize) + index + 1}</td>
                    <td className="px-3 py-2">
                      <div className="flex flex-col min-w-0">
                        <span className="text-gray-900 font-semibold text-xs truncate" title={a.patientName || a.tempPatientName || 'Walk-in'}>
                          {a.patientName || a.tempPatientName || 'Walk-in'}
                        </span>
                        <div className="flex items-center gap-1.5 mt-0.5 flex-wrap">
                          {a.patientNumber && a.patientNumber !== 'N/A' && a.patientNumber !== '—' && (
                            <span className="text-[10px] font-mono text-gray-400">{a.patientNumber}</span>
                          )}
                          {(() => {
                            const age = a.patientAge || (a.tempPatientAge ? `${a.tempPatientAge} yrs` : null)
                            const gender = a.patientGender || a.tempPatientGender
                            const ageGender = formatAgeGender(age, gender)
                            return ageGender ? (
                              <span className="text-[10px] font-semibold text-neutral-600 bg-neutral-100 px-1 py-0.2 rounded">
                                {ageGender}
                              </span>
                            ) : null
                          })()}
                        </div>
                      </div>
                    </td>
                    <td className="px-2.5 py-2 text-gray-600 font-mono text-xs whitespace-nowrap">{a.patientPhone || a.tempPatientPhone || '—'}</td>
                    <td className="px-3 py-2">
                      {(() => {
                        const info = getConsultantInfo(a.providerId, a.providerName)
                        return (
                          <div className="flex flex-col min-w-0 max-w-[200px]">
                            <span className="font-semibold text-gray-900 text-xs truncate" title={info.name}>{info.name}</span>
                            {info.degree && (
                              <span className="text-[10px] text-gray-500 font-normal leading-tight line-clamp-1" title={info.degree}>
                                {info.degree}
                              </span>
                            )}
                          </div>
                        )
                      })()}
                    </td>
                    <td className="px-2.5 py-2 text-gray-700 whitespace-nowrap font-medium text-xs">
                      {a.appointmentDate ? formatDate(a.appointmentDate) : '—'}
                    </td>
                    <td className="px-2.5 py-2 text-gray-600 font-mono text-[11px] whitespace-nowrap">
                      {formatTime(a.appointmentTime)} - {formatTime(a.appointmentEndTime)}
                    </td>
                    <td className="px-2.5 py-2 whitespace-nowrap">
                      <span className={cn("px-2 py-0.5 rounded-full text-[10px] font-bold border inline-block", (STATUS_STYLES as any)[a.status] ?? 'bg-gray-50 text-gray-700 border-gray-200')}>
                        {a.status}
                      </span>
                    </td>
                    <td className="px-3 py-2 whitespace-nowrap text-right">
                      {a.status === 'BOOKED' ? (
                        <div className="flex items-center justify-end gap-1">
                          {!a.patientId && (
                            <button
                              type="button"
                              onClick={() => {
                                setSelectedApptForReg(a)
                                setIsRegistering(true)
                              }}
                              className="px-2 py-1 text-[11px] font-semibold rounded-md bg-teal-50 hover:bg-teal-100 text-teal-700 border border-teal-200 transition-colors cursor-pointer"
                            >
                              Register
                            </button>
                          )}
                          <button
                            type="button"
                            onClick={() => navigate(`/appointments/reschedule/${a.id}`, { state: { appointment: a } })}
                            className="px-2 py-1 text-[11px] font-semibold rounded-md bg-amber-50 hover:bg-amber-100 text-amber-700 border border-amber-200 transition-colors cursor-pointer"
                          >
                            Reschedule
                          </button>
                          {a.patientId && a.appointmentDate === todayStr && (
                            <button
                              type="button"
                              disabled={mutations.checkIn.isPending}
                              onClick={() => mutations.checkIn.mutate(a.id)}
                              className="px-2 py-1 text-[11px] font-semibold rounded-md bg-green-50 hover:bg-green-100 text-green-700 border border-green-200 transition-colors cursor-pointer"
                            >
                              Check-in
                            </button>
                          )}
                          <button
                            type="button"
                            disabled={mutations.cancel.isPending}
                            onClick={() => setAppointmentToCancel(a.id)}
                            className="px-2 py-1 text-[11px] font-semibold rounded-md bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 transition-colors cursor-pointer"
                          >
                            Cancel
                          </button>
                        </div>
                      ) : (
                        <span className="text-gray-400 font-medium pr-6 inline-block">—</span>
                      )}
                    </td>
                  </tr>
                ))}
                {(!paginatedAppointments || paginatedAppointments.length === 0) && (
                  <tr>
                    <td colSpan={8} className="px-4 py-8 text-center text-gray-400">
                      No appointments found.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>

            {/* Pagination Controls */}
            {totalPages > 1 && (
              <div className="flex items-center justify-between px-4 py-3 border-t border-gray-100 bg-gray-50 text-xs">
                <span className="text-gray-500">
                  Showing {(page * pageSize) + 1} to {Math.min((page + 1) * pageSize, filteredAppointments?.length || 0)} of {filteredAppointments?.length}
                </span>
                <div className="flex items-center gap-1">
                  <button
                    disabled={page === 0}
                    onClick={() => setPage(p => p - 1)}
                    className="px-2.5 py-1 rounded border border-gray-200 bg-white disabled:opacity-40 hover:bg-gray-50"
                  >
                    Previous
                  </button>
                  <span className="px-2 text-gray-600 font-medium">Page {page + 1} of {totalPages}</span>
                  <button
                    disabled={page >= totalPages - 1}
                    onClick={() => setPage(p => p + 1)}
                    className="px-2.5 py-1 rounded border border-gray-200 bg-white disabled:opacity-40 hover:bg-gray-50"
                  >
                    Next
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Quick Patient Registration Modal */}
      {isRegistering && selectedApptForReg && (
        <QuickRegistrationModal
          appointment={selectedApptForReg}
          onCancel={() => {
            setIsRegistering(false)
            setSelectedApptForReg(null)
          }}
          onSuccess={() => {
            setIsRegistering(false)
            setSelectedApptForReg(null)
          }}
        />
      )}

      {/* Cancel Appointment Confirmation Modal */}
      <ConfirmModal
        isOpen={Boolean(appointmentToCancel)}
        onClose={() => setAppointmentToCancel(null)}
        onConfirm={() => {
          if (appointmentToCancel) {
            mutations.cancel.mutate(appointmentToCancel, {
              onSettled: () => setAppointmentToCancel(null),
            })
          }
        }}
        title="Cancel Appointment"
        message="Are you sure you want to cancel this booked appointment? This action cannot be undone."
        confirmText="Cancel Appointment"
        cancelText="Keep Appointment"
        variant="danger"
        isLoading={mutations.cancel.isPending}
      />
    </div>
  )
}
