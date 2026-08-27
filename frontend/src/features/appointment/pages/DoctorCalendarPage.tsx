import { useState, useMemo, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useAuthStore } from '../../../store/authStore'
import {
  format,
  startOfMonth,
  endOfMonth,
  eachDayOfInterval,
  isSameDay,
  addMonths,
  subMonths,
  isToday,
  parseISO
} from 'date-fns'
import {
  useConsultantCalendar,
  useConsultantLeaves,
  useConsultantLeavesById,
  useConsultantLeaveMutations
} from '../../../hooks/appointment/useAppointment'
import { useConsultantSlots, useConsultants } from '../../../hooks/consultant/useConsultant'
import { cn } from '../../../lib/utils'
import { CalendarDays, AlertTriangle, Trash2, CalendarRange, Clock, User, Ban } from 'lucide-react'

const formatTime = (timeStr?: string | null) => {
  if (!timeStr) return '—'
  try {
    const [hours, minutes] = timeStr.split(':')
    const date = new Date()
    date.setHours(parseInt(hours), parseInt(minutes), 0)
    return format(date, 'hh:mm a')
  } catch {
    return timeStr
  }
}

export default function DoctorCalendarPage() {
  const { user } = useAuthStore()
  const [currentMonth, setCurrentMonth] = useState<Date>(new Date())
  const [selectedDate, setSelectedDate] = useState<Date>(new Date())

  // Search and selector states for admin
  const { data: consultants } = useConsultants()
  const [selectedConsultantId, setSelectedConsultantId] = useState<string>('')
  const [searchQuery, setSearchQuery] = useState('')
  const [showDropdown, setShowDropdown] = useState(false)
  const [isModalOpen, setIsModalOpen] = useState(false)

  // If the logged-in user is a doctor, default to their consultant ID.
  // Otherwise, use the selected consultant ID from the dropdown.
  const effectiveConsultantId = user?.consultantId || selectedConsultantId

  // Set default selected consultant to the first one in the list for admins/staff
  useEffect(() => {
    if (!user?.consultantId && consultants && consultants.length > 0 && !selectedConsultantId) {
      setSelectedConsultantId(consultants[0].id)
    }
  }, [consultants, user, selectedConsultantId])

  const defaultConsultantName = useMemo(() => {
    if (!effectiveConsultantId) return ''
    const match = consultants?.find(c => c.id === effectiveConsultantId)
    return match ? `${match.salutation ?? ''} ${match.firstName} ${match.lastName}`.trim() : ''
  }, [consultants, effectiveConsultantId])

  // Synchronize search input with selected doctor
  useEffect(() => {
    if (defaultConsultantName) {
      setSearchQuery(defaultConsultantName)
    }
  }, [defaultConsultantName])

  // Filter consultants by search query
  const filteredConsultants = useMemo(() => {
    if (!consultants) return []
    const q = searchQuery.toLowerCase().trim()
    if (!q) return consultants
    return consultants.filter(c => {
      const fullName = `${c.salutation ?? ''} ${c.firstName} ${c.lastName}`.toLowerCase()
      const spec = (c.specialisation || '').toLowerCase()
      return fullName.includes(q) || spec.includes(q)
    })
  }, [consultants, searchQuery])

  // Leave Form State
  const [leaveStartDate, setLeaveStartDate] = useState<string>('')
  const [leaveEndDate, setLeaveEndDate] = useState<string>('')
  const [leaveReason, setLeaveReason] = useState<string>('')

  // Automatically sync leave date fields with selected calendar day
  useEffect(() => {
    const str = format(selectedDate, 'yyyy-MM-dd')
    setLeaveStartDate(str)
    setLeaveEndDate(str)
  }, [selectedDate])

  // Calculate date range for the current month view
  const monthStart = startOfMonth(currentMonth)
  const monthEnd = endOfMonth(currentMonth)
  
  const queryStartDate = format(monthStart, 'yyyy-MM-dd')
  const queryEndDate = format(monthEnd, 'yyyy-MM-dd')

  // Fetch calendar statuses, leaves, and slots for the active consultant
  const { data: calendarData, isLoading: calendarLoading } = useConsultantCalendar(
    queryStartDate,
    queryEndDate,
    effectiveConsultantId || undefined
  )
  const { data: leavesData, isLoading: leavesLoading } = useConsultantLeavesById(effectiveConsultantId || undefined)
  const { createLeave, deleteLeave } = useConsultantLeaveMutations()
  const { data: slotsData, isLoading: slotsLoading } = useConsultantSlots(effectiveConsultantId || undefined)

  const dayOfWeekStr = format(selectedDate, 'EEEE').substring(0, 3).toUpperCase()
  const activeSlotsForDay = useMemo(() => {
    return slotsData?.filter(s => s.dayOfWeek === dayOfWeekStr && s.status === 1) ?? []
  }, [slotsData, dayOfWeekStr])

  // Generate all days in the current month grid
  const days = useMemo(() => {
    return eachDayOfInterval({ start: monthStart, end: monthEnd })
  }, [currentMonth])

  // Map backend status details for fast lookup
  const dateStatusesMap = useMemo(() => {
    const map: Record<string, { status: string; bookedCount: number; maxCapacity: number }> = {}
    calendarData?.dateStatuses?.forEach(ds => {
      map[ds.date] = {
        status: ds.status,
        bookedCount: ds.bookedCount,
        maxCapacity: ds.maxCapacity
      }
    })
    return map
  }, [calendarData])

  // Get appointments booked for the selected date
  const selectedDateAppointments = useMemo(() => {
    const targetStr = format(selectedDate, 'yyyy-MM-dd')
    return calendarData?.appointments?.filter(appt => appt.appointmentDate === targetStr) ?? []
  }, [selectedDate, calendarData])

  const handleCreateLeave = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!leaveStartDate || !leaveEndDate) return

    // Warn if there are booked appointments on these dates
    const start = parseISO(leaveStartDate)
    const end = parseISO(leaveEndDate)
    
    const bookedApptsCount = calendarData?.appointments?.filter(appt => {
      const apptDate = parseISO(appt.appointmentDate)
      return apptDate >= start && apptDate <= end && (appt.status === 'BOOKED' || appt.status === 'CHECKED_IN')
    }).length ?? 0

    if (bookedApptsCount > 0) {
      const confirmCancel = window.confirm(
        `Warning: There are ${bookedApptsCount} active appointments booked within this leave range. Marking these dates as unavailable will automatically CANCEL these appointments. Do you want to proceed?`
      )
      if (!confirmCancel) return
    }

    createLeave.mutate(
      { 
        startDate: leaveStartDate, 
        endDate: leaveEndDate, 
        reason: leaveReason,
        consultantId: effectiveConsultantId || undefined
      },
      {
        onSuccess: () => {
          setLeaveStartDate('')
          setLeaveEndDate('')
          setLeaveReason('')
        }
      }
    )
  }

  const handleDeleteLeave = (id: string) => {
    if (window.confirm('Are you sure you want to cancel this leave record? The dates will become available again.')) {
      deleteLeave.mutate(id)
    }
  }

  const prevMonth = () => setCurrentMonth(subMonths(currentMonth, 1))
  const nextMonth = () => setCurrentMonth(addMonths(currentMonth, 1))

  return (
    <div className="space-y-6 max-w-7xl mx-auto px-4 py-6">
      {/* Top Banner */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-white p-6 rounded-2xl border border-gray-100 shadow-sm">
        <div className="flex flex-col md:flex-row md:items-center gap-6">
          <div>
            <h2 className="text-2xl font-bold text-gray-900 tracking-tight flex items-center gap-2">
              <CalendarDays className="h-6 w-6 text-neutral-600" />
              {user?.consultantId ? 'My Calendar & Availability' : 'Consultant Calendar & Availability'}
            </h2>
            <p className="text-sm text-gray-500 font-medium">
              {user?.consultantId 
                ? 'Manage leave unavailabilities and track your appointments' 
                : 'Select a consultant to view or manage their unavailabilities and appointments'}
            </p>
          </div>

          {!user?.consultantId && (
            <div className="flex items-center gap-3 bg-gray-50 p-2 rounded-xl border border-gray-200">
              <span className="text-xs font-bold text-gray-500 uppercase pl-1">Consultant:</span>
              <select
                value={selectedConsultantId}
                onChange={e => setSelectedConsultantId(e.target.value)}
                className="px-3 py-1.5 border border-gray-300 rounded-lg text-sm font-semibold text-gray-800 bg-white focus:ring-2 focus:ring-neutral-500 outline-none min-w-[240px]"
              >
                <option value="">-- Select Doctor --</option>
                {consultants?.map(c => (
                  <option key={c.id} value={c.id}>
                    {c.salutation ? c.salutation + ' ' : ''}{c.firstName} {c.lastName} ({c.specialisation || 'General'})
                  </option>
                ))}
              </select>
            </div>
          )}
        </div>

        {effectiveConsultantId && (
          <Link
            to={`/settings/consultants/${effectiveConsultantId}/slots?name=${encodeURIComponent(defaultConsultantName)}`}
            className="px-4 py-2.5 bg-neutral-800 text-white hover:bg-neutral-700 text-sm font-bold rounded-xl transition-all shadow-md flex items-center gap-2"
          >
            <Clock className="h-4 w-4" />
            Manage Availability Times
          </Link>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column: Monthly Calendar View */}
        <div className="lg:col-span-2 space-y-6">
          <div className="bg-white p-6 rounded-2xl border border-gray-100 shadow-sm space-y-6">
            {/* Month Navigation */}
            <div className="flex items-center justify-between border-b border-gray-50 pb-4">
              <h3 className="text-lg font-bold text-gray-800">
                {format(currentMonth, 'MMMM yyyy')}
              </h3>
              <div className="flex items-center bg-gray-50 rounded-xl p-1 border border-gray-200">
                <button onClick={prevMonth} className="p-2 hover:bg-white hover:shadow-sm rounded-lg text-gray-600 transition-all">←</button>
                <button onClick={() => setCurrentMonth(new Date())} className="px-4 py-2 text-xs font-bold text-gray-700 hover:text-neutral-600 transition-colors text-center">
                  Current
                </button>
                <button onClick={nextMonth} className="p-2 hover:bg-white hover:shadow-sm rounded-lg text-gray-600 transition-all">→</button>
              </div>
            </div>

            {/* Calendar Grid Header (Days of week) */}
            <div className="grid grid-cols-7 gap-2 text-center text-xs font-bold text-gray-400 uppercase tracking-wider">
              <div>Sun</div>
              <div>Mon</div>
              <div>Tue</div>
              <div>Wed</div>
              <div>Thu</div>
              <div>Fri</div>
              <div>Sat</div>
            </div>

            {/* Calendar Grid Days */}
            {calendarLoading ? (
              <div className="h-96 flex items-center justify-center">
                <div className="w-8 h-8 border-2 border-neutral-200 border-t-neutral-600 rounded-full animate-spin" />
              </div>
            ) : (
              <div className="grid grid-cols-7 gap-2">
                {/* Pad empty starting days of the month grid */}
                {Array.from({ length: monthStart.getDay() }).map((_, idx) => (
                  <div key={`empty-${idx}`} className="h-20 bg-gray-50/40 rounded-xl border border-dashed border-gray-100" />
                ))}

                {days.map(day => {
                  const dateStr = format(day, 'yyyy-MM-dd')
                  const info = dateStatusesMap[dateStr]
                  const status = info?.status || 'AVAILABLE'
                  const isDaySelected = isSameDay(day, selectedDate)
                  const isCurrent = isToday(day)

                  // Determine colors and styles based on status
                  let tileStyle = 'bg-white border-gray-100 text-gray-700 hover:border-neutral-300'
                  let statusBadge = null

                  if (status === 'LEAVE') {
                    tileStyle = 'bg-rose-50 border-rose-100 text-rose-800 hover:bg-rose-100/50'
                    statusBadge = (
                      <span className="text-[9px] font-bold text-rose-600 uppercase tracking-wide">Leave</span>
                    )
                  } else if (status === 'FULLY_BOOKED') {
                    tileStyle = 'bg-amber-50 border-amber-100 text-amber-900 hover:bg-amber-100/50'
                    statusBadge = (
                      <span className="text-[9px] font-bold text-amber-600 uppercase tracking-wide">Full</span>
                    )
                  } else if (status === 'HAS_APPOINTMENTS') {
                    tileStyle = 'bg-blue-50/50 border-blue-100 text-blue-900 hover:bg-blue-100/30'
                    statusBadge = (
                      <span className="text-[9px] font-bold text-blue-600">
                        {info.bookedCount} Appts
                      </span>
                    )
                  } else if (status === 'UNAVAILABLE') {
                    tileStyle = 'bg-gray-100 border-gray-200 text-gray-400 cursor-not-allowed'
                    statusBadge = (
                      <span className="text-[9px] font-medium text-gray-500 uppercase">Closed</span>
                    )
                  }

                  return (
                    <button
                      key={dateStr}
                      onClick={() => setSelectedDate(day)}
                      disabled={status === 'UNAVAILABLE'}
                      className={cn(
                        'h-24 p-2 rounded-2xl border flex flex-col justify-between items-start transition-all relative overflow-hidden focus:outline-none focus:ring-2 focus:ring-neutral-500 focus:ring-offset-2',
                        tileStyle,
                        isDaySelected && 'ring-2 ring-neutral-700 border-transparent shadow-sm',
                        isCurrent && 'font-black border-neutral-700'
                      )}
                    >
                      <span className={cn(
                        'text-sm font-semibold flex items-center justify-center w-6 h-6 rounded-full',
                        isCurrent && 'bg-neutral-800 text-white font-bold'
                      )}>
                        {format(day, 'd')}
                      </span>
                      {statusBadge}
                    </button>
                  )
                })}
              </div>
            )}

            {/* Legend / Color Guides */}
            <div className="flex flex-wrap items-center justify-center gap-6 pt-4 border-t border-gray-50 text-xs font-semibold text-gray-600">
              <div className="flex items-center gap-2">
                <span className="w-3.5 h-3.5 rounded bg-white border border-gray-200 inline-block" />
                <span>Available</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="w-3.5 h-3.5 rounded bg-blue-50 border border-blue-100 inline-block" />
                <span>Appointments Booked</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="w-3.5 h-3.5 rounded bg-amber-50 border border-amber-100 inline-block" />
                <span>Fully Booked</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="w-3.5 h-3.5 rounded bg-rose-50 border border-rose-100 inline-block" />
                <span>Leave / Unavailable</span>
              </div>
            </div>
          </div>
        </div>

        {/* Right Column: Day Appointments details & Leave Scheduler */}
        <div className="space-y-6">
          {/* Section 1: Day Appointments */}
          <div className="bg-white p-6 rounded-2xl border border-gray-100 shadow-sm space-y-4">
            <div className="border-b border-gray-50 pb-3">
              <h3 className="text-md font-bold text-gray-900 flex items-center gap-2">
                <CalendarRange className="h-5 w-5 text-gray-500" />
                Schedule for {format(selectedDate, 'dd MMM yyyy')}
              </h3>
            </div>

            <div className="space-y-3 max-h-[300px] overflow-y-auto pr-1">
              {selectedDateAppointments.length === 0 ? (
                <div className="text-center py-8 text-gray-400 text-sm flex flex-col items-center gap-2">
                  <Ban className="h-8 w-8 text-gray-300" />
                  <span>No appointments booked on this date</span>
                </div>
              ) : (
                selectedDateAppointments.map(appt => (
                  <div key={appt.id} className="p-3 bg-gray-50 rounded-xl border border-gray-100 space-y-2 hover:bg-gray-100/50 transition-colors">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-mono font-bold text-gray-500 bg-gray-200/50 px-2 py-0.5 rounded">
                        {formatTime(appt.appointmentTime)} - {formatTime(appt.appointmentEndTime)}
                      </span>
                      <span className={cn(
                        'text-[9px] font-bold uppercase px-1.5 py-0.5 rounded tracking-wide border',
                        appt.status === 'BOOKED' && 'bg-blue-50 text-blue-700 border-blue-100',
                        appt.status === 'CHECKED_IN' && 'bg-green-50 text-green-700 border-green-100',
                        appt.status === 'RESCHEDULED' && 'bg-amber-50 text-amber-700 border-amber-100',
                        appt.status === 'CANCELLED' && 'bg-gray-100 text-gray-500 border-gray-200'
                      )}>
                        {appt.status}
                      </span>
                    </div>
                    <div className="flex items-center gap-2 text-sm text-gray-900 font-semibold">
                      <User className="h-4 w-4 text-gray-400" />
                      <span>{appt.patientName || appt.tempPatientName || 'Walk-in Patient'}</span>
                    </div>
                    {appt.patientPhone && (
                      <p className="text-xs text-gray-500 ml-6">Phone: {appt.patientPhone}</p>
                    )}
                  </div>
                ))
              )}
            </div>

            {/* Availability Slots */}
            <div className="border-t border-gray-100 pt-4 mt-2">
              <h4 className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-1 flex items-center gap-1.5">
                <Clock className="h-3.5 w-3.5" />
                Working Hours / Slots
              </h4>
              <p className="text-[10px] text-gray-400 mb-2 font-semibold">
                {format(selectedDate, 'dd MMM yyyy')} • {format(selectedDate, 'EEEE')}
              </p>
              {slotsLoading ? (
                <p className="text-xs text-gray-400">Loading slots...</p>
              ) : activeSlotsForDay.length === 0 ? (
                <p className="text-xs text-rose-500 font-semibold italic flex items-center gap-1">
                  <Ban className="h-3 w-3" /> No working hours configured for this day
                </p>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {activeSlotsForDay.map(slot => (
                    <span key={slot.id} className="text-xs font-semibold text-neutral-800 bg-neutral-100 border border-neutral-200 px-2.5 py-1.5 rounded-xl">
                      {formatTime(slot.fromTime)} - {formatTime(slot.toTime)} ({slot.maxPatients} max)
                    </span>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Section 2: Take Leave Scheduler */}
          <div className="bg-white p-6 rounded-2xl border border-gray-100 shadow-sm space-y-4">
            <div className="border-b border-gray-50 pb-3">
              <h3 className="text-md font-bold text-gray-900 flex items-center gap-2">
                <AlertTriangle className="h-5 w-5 text-gray-500" />
                Mark Leave / Unavailability
              </h3>
            </div>

            <form onSubmit={handleCreateLeave} className="space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <label htmlFor="start-date" className="text-xs font-bold text-gray-500 uppercase">Start Date</label>
                  <input
                    id="start-date"
                    type="date"
                    required
                    value={leaveStartDate}
                    min={format(new Date(), 'yyyy-MM-dd')}
                    onChange={e => setLeaveStartDate(e.target.value)}
                    className="w-full p-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:ring-2 focus:ring-neutral-500 outline-none transition-all"
                  />
                </div>
                <div className="space-y-1.5">
                  <label htmlFor="end-date" className="text-xs font-bold text-gray-500 uppercase">End Date</label>
                  <input
                    id="end-date"
                    type="date"
                    required
                    value={leaveEndDate}
                    min={leaveStartDate || format(new Date(), 'yyyy-MM-dd')}
                    onChange={e => setLeaveEndDate(e.target.value)}
                    className="w-full p-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:ring-2 focus:ring-neutral-500 outline-none transition-all"
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <label htmlFor="reason" className="text-xs font-bold text-gray-500 uppercase">Reason (Optional)</label>
                <input
                  id="reason"
                  type="text"
                  placeholder="e.g. Family Function, Medical Leave"
                  value={leaveReason}
                  onChange={e => setLeaveReason(e.target.value)}
                  className="w-full p-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:ring-2 focus:ring-neutral-500 outline-none transition-all"
                />
              </div>

              <button
                type="submit"
                disabled={createLeave.isPending}
                className="w-full py-3 bg-rose-600 hover:bg-rose-700 text-white font-bold rounded-xl transition-colors disabled:opacity-50 shadow-md shadow-rose-100 flex items-center justify-center gap-2"
              >
                Mark as Unavailable
              </button>
            </form>
          </div>

          {/* Section 3: Leave Ranges List */}
          <div className="bg-white p-6 rounded-2xl border border-gray-100 shadow-sm space-y-4">
            <div className="border-b border-gray-50 pb-3">
              <h3 className="text-md font-bold text-gray-900 flex items-center gap-2">
                <Clock className="h-5 w-5 text-gray-500" />
                Active Leaves List
              </h3>
            </div>

            <div className="space-y-3 max-h-[250px] overflow-y-auto pr-1">
              {leavesLoading ? (
                <p className="text-center text-xs text-gray-400 py-4">Loading leaves...</p>
              ) : !leavesData || leavesData.length === 0 ? (
                <p className="text-center text-xs text-gray-400 py-4">No scheduled leaves</p>
              ) : (
                leavesData.map(leave => (
                  <div key={leave.id} className="p-3 bg-rose-50/40 rounded-xl border border-rose-100/40 flex items-center justify-between">
                    <div className="space-y-0.5">
                      <p className="text-sm font-semibold text-rose-900">
                        {format(parseISO(leave.startDate), 'dd MMM')} - {format(parseISO(leave.endDate), 'dd MMM yyyy')}
                      </p>
                      {leave.reason && (
                        <p className="text-xs text-rose-600/80 italic font-medium">{leave.reason}</p>
                      )}
                    </div>
                    <button
                      onClick={() => handleDeleteLeave(leave.id)}
                      disabled={deleteLeave.isPending}
                      className="p-2 hover:bg-rose-100/60 rounded-lg text-rose-600 transition-colors"
                      title="Remove leave"
                    >
                      <Trash2 className="h-4.5 w-4.5" />
                    </button>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
