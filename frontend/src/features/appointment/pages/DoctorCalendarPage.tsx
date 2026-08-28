import { useState, useMemo, useEffect, useRef } from 'react'
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
  isBefore,
  startOfDay,
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
import type { Appointment } from '../../../types/appointment'
import { 
  CalendarDays, 
  AlertTriangle, 
  Trash2, 
  CalendarRange, 
  Clock, 
  User, 
  Ban, 
  Search, 
  ChevronDown, 
  X,
  Phone,
  FileText,
  Stethoscope,
  Activity,
  CheckCircle2,
  Calendar as CalendarIcon,
  Tag
} from 'lucide-react'

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
  const autocompleteRef = useRef<HTMLDivElement>(null)

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

  // Close dropdown on click outside
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (autocompleteRef.current && !autocompleteRef.current.contains(e.target as Node)) {
        setShowDropdown(false)
        // Reset search to selected consultant name if user didn't pick
        if (defaultConsultantName) setSearchQuery(defaultConsultantName)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [defaultConsultantName])

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
  const todayStr = useMemo(() => format(new Date(), 'yyyy-MM-dd'), [])
  const [leaveStartDate, setLeaveStartDate] = useState<string>(todayStr)
  const [leaveEndDate, setLeaveEndDate] = useState<string>(todayStr)
  const [leaveReason, setLeaveReason] = useState<string>('')

  // Automatically sync leave date fields with selected calendar day (prevent past dates)
  useEffect(() => {
    const isPast = isBefore(startOfDay(selectedDate), startOfDay(new Date()))
    if (isPast) {
      setLeaveStartDate(todayStr)
      setLeaveEndDate(todayStr)
    } else {
      const str = format(selectedDate, 'yyyy-MM-dd')
      setLeaveStartDate(str)
      setLeaveEndDate(str)
    }
  }, [selectedDate, todayStr])

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

  // Group all appointments by date for calendar tile rendering
  const appointmentsByDate = useMemo(() => {
    const map: Record<string, typeof calendarData.appointments> = {}
    calendarData?.appointments?.forEach(appt => {
      if (!map[appt.appointmentDate]) map[appt.appointmentDate] = []
      map[appt.appointmentDate].push(appt)
    })
    return map
  }, [calendarData])

  // Get appointments booked for the selected date
  const selectedDateAppointments = useMemo(() => {
    const targetStr = format(selectedDate, 'yyyy-MM-dd')
    return calendarData?.appointments?.filter(appt => appt.appointmentDate === targetStr) ?? []
  }, [selectedDate, calendarData])

  // Modal state for viewing all appointments on a date
  const [popupDate, setPopupDate] = useState<string | null>(null)
  const popupAppointments = popupDate ? (appointmentsByDate[popupDate] ?? []) : []

  // Modal state for viewing single appointment details popup
  const [selectedAppointment, setSelectedAppointment] = useState<Appointment | null>(null)

  const handleCreateLeave = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!leaveStartDate || !leaveEndDate) return

    // Prevent marking leave for past dates
    if (leaveStartDate < todayStr) {
      alert('Cannot mark leave/unavailability for past dates. Please select today or a future date.')
      return
    }

    if (leaveEndDate < leaveStartDate) {
      alert('End date cannot be earlier than start date.')
      return
    }

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
    <div className="space-y-5 max-w-7xl mx-auto px-4 py-5">
      {/* Page Heading */}
      <div className="bg-white px-5 py-4 rounded-xl border border-neutral-200">
        <h2 className="text-xl font-bold text-neutral-900 tracking-tight flex items-center gap-2">
          <CalendarDays className="h-5 w-5 text-neutral-600" />
          {user?.consultantId ? 'My Calendar & Availability' : 'Consultant Calendar & Availability'}
        </h2>
        <p className="text-xs text-neutral-500 font-medium mt-1 ml-7">
          {user?.consultantId 
            ? 'Manage leave unavailabilities and track your appointments' 
            : 'Select a consultant to view or manage their unavailabilities and appointments'}
        </p>
      </div>

      {/* Toolbar Row: Consultant Selector + Action */}
      {(!user?.consultantId || effectiveConsultantId) && (
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-white px-5 py-3.5 rounded-xl border border-neutral-200">
          {!user?.consultantId && (
            <div className="relative flex-1 max-w-md" ref={autocompleteRef}>
              <label className="text-xs font-medium text-neutral-600 mb-1 block">
                Select Consultant
              </label>
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-neutral-400 pointer-events-none" />
                <input
                  type="text"
                  value={searchQuery}
                  onChange={e => {
                    setSearchQuery(e.target.value)
                    setShowDropdown(true)
                  }}
                  onFocus={() => {
                    setShowDropdown(true)
                    setSearchQuery('')
                  }}
                  placeholder="Search by name or specialisation..."
                  className="w-full pl-9 pr-8 py-1.5 bg-white border border-neutral-200 rounded-lg text-xs font-medium text-neutral-800 focus:border-neutral-900 focus:outline-none transition-colors placeholder:text-neutral-400"
                />
                <ChevronDown className={cn(
                  "absolute right-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-neutral-400 transition-transform pointer-events-none",
                  showDropdown && "rotate-180"
                )} />
              </div>
              
              {/* Autocomplete Dropdown */}
              {showDropdown && (
                <div className="absolute z-50 top-full left-0 right-0 mt-1 bg-white border border-neutral-200 rounded-lg shadow-md max-h-60 overflow-y-auto">
                  {filteredConsultants.length === 0 ? (
                    <div className="px-3.5 py-2.5 text-xs text-neutral-400 text-center">No consultants found</div>
                  ) : (
                    filteredConsultants.map(c => {
                      const fullName = `${c.salutation ? c.salutation + ' ' : ''}${c.firstName} ${c.lastName}`
                      const isSelected = c.id === selectedConsultantId
                      return (
                        <button
                          key={c.id}
                          type="button"
                          onClick={() => {
                            setSelectedConsultantId(c.id)
                            setSearchQuery(fullName)
                            setShowDropdown(false)
                          }}
                          className={cn(
                            "w-full text-left px-3.5 py-2 flex items-center justify-between gap-2.5 hover:bg-neutral-50 transition-colors text-xs cursor-pointer",
                            isSelected && "bg-neutral-50 font-semibold"
                          )}
                        >
                          <div className="flex items-center gap-2.5 min-w-0">
                            <div className="h-7 w-7 rounded-full bg-neutral-100 border border-neutral-200 flex items-center justify-center shrink-0">
                              <User className="h-3.5 w-3.5 text-neutral-600" />
                            </div>
                            <div className="min-w-0">
                              <p className={cn("text-neutral-800 truncate", isSelected && "text-neutral-900 font-semibold")}>
                                {fullName}
                              </p>
                              {c.specialisation && (
                                <p className="text-[11px] text-neutral-400 truncate">{c.specialisation}</p>
                              )}
                            </div>
                          </div>
                          {isSelected && (
                            <span className="text-[10px] font-semibold text-neutral-700 bg-neutral-100 px-2 py-0.5 rounded shrink-0">
                              Selected
                            </span>
                          )}
                        </button>
                      )
                    })
                  )}
                </div>
              )}
            </div>
          )}

          {effectiveConsultantId && (
            <Link
              to={`/settings/consultants/${effectiveConsultantId}/slots?name=${encodeURIComponent(defaultConsultantName)}`}
              className="rounded-lg bg-neutral-900 px-3.5 py-2 text-xs font-semibold text-white hover:bg-neutral-800 transition-colors flex items-center gap-1.5 whitespace-nowrap self-end sm:self-auto"
            >
              <Clock className="h-3.5 w-3.5" />
              Manage Availability Times
            </Link>
          )}
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {/* Left Column: Monthly Calendar View */}
        <div className="lg:col-span-2 space-y-5">
          <div className="bg-white p-5 rounded-xl border border-neutral-200 space-y-5">
            {/* Month Navigation */}
            <div className="flex items-center justify-between border-b border-neutral-100 pb-3.5">
              <h3 className="text-base font-bold text-neutral-900">
                {format(currentMonth, 'MMMM yyyy')}
              </h3>
              <div className="flex items-center bg-neutral-50 rounded-lg p-0.5 border border-neutral-200">
                <button onClick={prevMonth} className="p-1.5 hover:bg-white rounded text-neutral-600 transition-colors text-xs font-medium">←</button>
                <button onClick={() => setCurrentMonth(new Date())} className="px-3 py-1 text-xs font-semibold text-neutral-700 hover:text-neutral-900 transition-colors">
                  Current
                </button>
                <button onClick={nextMonth} className="p-1.5 hover:bg-white rounded text-neutral-600 transition-colors text-xs font-medium">→</button>
              </div>
            </div>

            {/* Calendar Grid Header (Days of week) */}
            <div className="grid grid-cols-7 gap-1.5 text-center text-xs font-semibold text-neutral-400 uppercase tracking-wider">
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
              <div className="h-80 flex items-center justify-center">
                <div className="w-6 h-6 border-2 border-neutral-200 border-t-neutral-800 rounded-full animate-spin" />
              </div>
            ) : (
              <div className="grid grid-cols-7 gap-1.5">
                {/* Pad empty starting days of the month grid */}
                {Array.from({ length: monthStart.getDay() }).map((_, idx) => (
                  <div key={`empty-${idx}`} className="h-24 bg-neutral-50/50 rounded-lg border border-dashed border-neutral-200" />
                ))}

                {days.map(day => {
                  const dateStr = format(day, 'yyyy-MM-dd')
                  const info = dateStatusesMap[dateStr]
                  const status = info?.status || 'AVAILABLE'
                  const isDaySelected = isSameDay(day, selectedDate)
                  const isCurrent = isToday(day)
                  const dayAppts = appointmentsByDate[dateStr] ?? []
                  const visibleAppts = dayAppts.slice(0, 2)
                  const overflowCount = dayAppts.length - visibleAppts.length

                  // Determine colors and styles based on status
                  let tileStyle = 'bg-white border-neutral-200 text-neutral-800 hover:border-neutral-400'

                  if (status === 'LEAVE') {
                    tileStyle = 'bg-red-50/60 border-red-200 text-red-900 hover:bg-red-50'
                  } else if (status === 'FULLY_BOOKED') {
                    tileStyle = 'bg-amber-50/60 border-amber-200 text-amber-900 hover:bg-amber-50'
                  } else if (status === 'HAS_APPOINTMENTS') {
                    tileStyle = 'bg-blue-50/40 border-blue-200 text-blue-900 hover:bg-blue-50/80'
                  } else if (status === 'UNAVAILABLE') {
                    tileStyle = 'bg-neutral-100 border-neutral-200 text-neutral-400 cursor-not-allowed'
                  }

                  return (
                    <button
                      key={dateStr}
                      onClick={() => setSelectedDate(day)}
                      disabled={status === 'UNAVAILABLE'}
                      className={cn(
                        'min-h-[5.75rem] p-1.5 rounded-lg border flex flex-col items-start transition-colors relative overflow-hidden focus:outline-none',
                        tileStyle,
                        isDaySelected && 'ring-2 ring-neutral-900 border-transparent shadow-xs',
                        isCurrent && 'border-neutral-900 font-bold'
                      )}
                    >
                      {/* Day number + status label row */}
                      <div className="flex items-center justify-between w-full mb-0.5">
                        <span className={cn(
                          'text-xs font-semibold flex items-center justify-center w-5 h-5 rounded-full',
                          isCurrent && 'bg-neutral-900 text-white font-bold'
                        )}>
                          {format(day, 'd')}
                        </span>
                        {status === 'LEAVE' && (
                          <span className="text-[8px] font-bold text-red-600 uppercase tracking-wide bg-red-100/80 px-1 rounded">Leave</span>
                        )}
                        {status === 'FULLY_BOOKED' && (
                          <span className="text-[8px] font-bold text-amber-600 uppercase tracking-wide bg-amber-100/80 px-1 rounded">Full</span>
                        )}
                        {status === 'UNAVAILABLE' && (
                          <span className="text-[8px] font-medium text-neutral-500 uppercase bg-neutral-200/80 px-1 rounded">Closed</span>
                        )}
                      </div>

                      {/* Mini appointment event bars */}
                      <div className="flex flex-col gap-[2px] w-full flex-1 min-h-0">
                        {visibleAppts.map(appt => (
                          <div
                            key={appt.id}
                            role="button"
                            tabIndex={0}
                            onClick={(e) => {
                              e.stopPropagation()
                              setSelectedDate(day)
                              setSelectedAppointment(appt)
                            }}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter' || e.key === ' ') {
                                e.stopPropagation()
                                setSelectedDate(day)
                                setSelectedAppointment(appt)
                              }
                            }}
                            title={`${appt.patientName || appt.tempPatientName || 'Patient'} (${formatTime(appt.appointmentTime)}) - Click for details`}
                            className={cn(
                              'w-full text-[8px] leading-tight font-semibold px-1.5 py-[2px] rounded truncate text-left cursor-pointer transition-colors duration-150 flex items-center gap-1 border',
                              appt.status === 'BOOKED' && 'bg-blue-50 text-blue-700 border-blue-200 hover:bg-blue-100',
                              appt.status === 'CHECKED_IN' && 'bg-emerald-50 text-emerald-700 border-emerald-200 hover:bg-emerald-100',
                              appt.status === 'RESCHEDULED' && 'bg-amber-50 text-amber-700 border-amber-200 hover:bg-amber-100',
                              appt.status === 'CANCELLED' && 'bg-neutral-100 text-neutral-500 border-neutral-200 line-through hover:bg-neutral-200',
                              !['BOOKED','CHECKED_IN','RESCHEDULED','CANCELLED'].includes(appt.status) && 'bg-blue-50 text-blue-700 border-blue-200 hover:bg-blue-100'
                            )}
                          >
                            <span className="font-mono text-[7px] shrink-0 opacity-75">{formatTime(appt.appointmentTime)}</span>
                            <span className="truncate">{appt.patientName || appt.tempPatientName || 'Patient'}</span>
                          </div>
                        ))}
                        {overflowCount > 0 && (
                          <span
                            role="button"
                            tabIndex={0}
                            onClick={(e) => {
                              e.stopPropagation()
                              setPopupDate(dateStr)
                              setSelectedDate(day)
                            }}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter' || e.key === ' ') {
                                e.stopPropagation()
                                setPopupDate(dateStr)
                                setSelectedDate(day)
                              }
                            }}
                            className="text-[8px] font-semibold text-blue-600 hover:text-blue-800 cursor-pointer text-left px-1 hover:underline select-none"
                          >
                            +{overflowCount} more
                          </span>
                        )}
                      </div>
                    </button>
                  )
                })}
              </div>
            )}

            {/* Legend / Color Guides */}
            <div className="flex flex-wrap items-center justify-center gap-5 pt-3 border-t border-neutral-100 text-xs font-medium text-neutral-600">
              <div className="flex items-center gap-1.5">
                <span className="w-3 h-3 rounded bg-white border border-neutral-300 inline-block" />
                <span>Available</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="w-3 h-3 rounded bg-blue-50 border border-blue-200 inline-block" />
                <span>Appointments Booked</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="w-3 h-3 rounded bg-amber-50 border border-amber-200 inline-block" />
                <span>Fully Booked</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="w-3 h-3 rounded bg-red-50 border border-red-200 inline-block" />
                <span>Leave / Unavailable</span>
              </div>
            </div>
          </div>
        </div>

        {/* Right Column: Day Appointments details & Leave Scheduler */}
        <div className="space-y-5">
          {/* Section 1: Day Appointments */}
          <div className="bg-white p-5 rounded-xl border border-neutral-200 space-y-3.5">
            <div className="border-b border-neutral-100 pb-2.5">
              <h3 className="text-sm font-bold text-neutral-900 flex items-center gap-2">
                <CalendarRange className="h-4 w-4 text-neutral-500" />
                Schedule for {format(selectedDate, 'dd MMM yyyy')}
              </h3>
            </div>

            <div className="space-y-2 max-h-[260px] overflow-y-auto pr-1">
              {selectedDateAppointments.length === 0 ? (
                <div className="text-center py-6 text-neutral-400 text-xs flex flex-col items-center gap-1.5">
                  <Ban className="h-6 w-6 text-neutral-300" />
                  <span>No appointments booked on this date</span>
                </div>
              ) : (
                selectedDateAppointments.map(appt => (
                  <div 
                    key={appt.id} 
                    role="button"
                    tabIndex={0}
                    onClick={() => setSelectedAppointment(appt)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        setSelectedAppointment(appt)
                      }
                    }}
                    title="Click to view full appointment details"
                    className="p-2.5 bg-neutral-50 rounded-lg border border-neutral-200 space-y-1.5 hover:bg-neutral-100/80 hover:border-neutral-300 transition-colors cursor-pointer group"
                  >
                    <div className="flex items-center justify-between">
                      <span className="text-[11px] font-mono font-semibold text-neutral-600 bg-neutral-200/60 px-1.5 py-0.5 rounded">
                        {formatTime(appt.appointmentTime)} - {formatTime(appt.appointmentEndTime)}
                      </span>
                      <span className={cn(
                        'text-[9px] font-semibold uppercase px-1.5 py-0.5 rounded border',
                        appt.status === 'BOOKED' && 'bg-blue-50 text-blue-700 border-blue-200',
                        appt.status === 'CHECKED_IN' && 'bg-emerald-50 text-emerald-700 border-emerald-200',
                        appt.status === 'RESCHEDULED' && 'bg-amber-50 text-amber-700 border-amber-200',
                        appt.status === 'CANCELLED' && 'bg-neutral-100 text-neutral-500 border-neutral-200'
                      )}>
                        {appt.status}
                      </span>
                    </div>
                    <div className="flex items-center gap-2 text-xs text-neutral-900 font-semibold">
                      <User className="h-3.5 w-3.5 text-neutral-400" />
                      <span>{appt.patientName || appt.tempPatientName || 'Walk-in Patient'}</span>
                    </div>
                    {appt.patientPhone && (
                      <p className="text-[11px] text-neutral-500 ml-5 flex items-center gap-1">
                        <Phone className="h-3 w-3 text-neutral-400" />
                        {appt.patientPhone}
                      </p>
                    )}
                  </div>
                ))
              )}
            </div>

            {/* Availability Slots */}
            <div className="border-t border-neutral-100 pt-3 mt-1">
              <h4 className="text-[11px] font-semibold text-neutral-500 uppercase tracking-wider mb-0.5 flex items-center gap-1.5">
                <Clock className="h-3 w-3" />
                Working Hours / Slots
              </h4>
              <p className="text-[10px] text-neutral-400 mb-2">
                {format(selectedDate, 'dd MMM yyyy')} • {format(selectedDate, 'EEEE')}
              </p>
              {slotsLoading ? (
                <p className="text-xs text-neutral-400">Loading slots...</p>
              ) : activeSlotsForDay.length === 0 ? (
                <p className="text-xs text-red-600 font-medium italic flex items-center gap-1">
                  <Ban className="h-3 w-3" /> No working hours configured for this day
                </p>
              ) : (
                <div className="flex flex-wrap gap-1.5">
                  {activeSlotsForDay.map(slot => (
                    <span key={slot.id} className="text-xs font-medium text-neutral-800 bg-neutral-100 border border-neutral-200 px-2 py-1 rounded-md">
                      {formatTime(slot.fromTime)} - {formatTime(slot.toTime)} ({slot.maxPatients} max)
                    </span>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Section 2: Take Leave Scheduler */}
          <div className="bg-white p-5 rounded-xl border border-neutral-200 space-y-3.5">
            <div className="border-b border-neutral-100 pb-2.5">
              <h3 className="text-sm font-bold text-neutral-900 flex items-center gap-2">
                <AlertTriangle className="h-4 w-4 text-neutral-500" />
                Mark Leave / Unavailability
              </h3>
            </div>

            <form onSubmit={handleCreateLeave} className="space-y-3">
              <div className="grid grid-cols-2 gap-2.5">
                <div>
                  <label htmlFor="start-date" className="block text-xs font-medium text-neutral-600 mb-1">Start Date</label>
                  <input
                    id="start-date"
                    type="date"
                    required
                    value={leaveStartDate}
                    min={todayStr}
                    onChange={e => {
                      const val = e.target.value
                      setLeaveStartDate(val)
                      if (leaveEndDate && leaveEndDate < val) {
                        setLeaveEndDate(val)
                      }
                    }}
                    className="w-full rounded-lg border border-neutral-200 px-2.5 py-1.5 text-xs bg-white focus:border-neutral-900 focus:outline-none transition-colors"
                  />
                </div>
                <div>
                  <label htmlFor="end-date" className="block text-xs font-medium text-neutral-600 mb-1">End Date</label>
                  <input
                    id="end-date"
                    type="date"
                    required
                    value={leaveEndDate}
                    min={leaveStartDate && leaveStartDate >= todayStr ? leaveStartDate : todayStr}
                    onChange={e => setLeaveEndDate(e.target.value)}
                    className="w-full rounded-lg border border-neutral-200 px-2.5 py-1.5 text-xs bg-white focus:border-neutral-900 focus:outline-none transition-colors"
                  />
                </div>
              </div>

              <div>
                <label htmlFor="reason" className="block text-xs font-medium text-neutral-600 mb-1">Reason (Optional)</label>
                <input
                  id="reason"
                  type="text"
                  placeholder="e.g. Vacation, Medical Leave"
                  value={leaveReason}
                  onChange={e => setLeaveReason(e.target.value)}
                  className="w-full rounded-lg border border-neutral-200 px-2.5 py-1.5 text-xs bg-white focus:border-neutral-900 focus:outline-none transition-colors"
                />
              </div>

              <button
                type="submit"
                disabled={createLeave.isPending || !leaveStartDate || leaveStartDate < todayStr || !leaveEndDate || leaveEndDate < leaveStartDate}
                className="w-full rounded-lg bg-red-600 hover:bg-red-700 px-3.5 py-2 text-xs font-semibold text-white transition-colors disabled:opacity-50 flex items-center justify-center gap-1.5 cursor-pointer"
              >
                {createLeave.isPending ? 'Marking...' : 'Mark as Unavailable'}
              </button>
            </form>
          </div>

          {/* Section 3: Leave Ranges List */}
          <div className="bg-white p-5 rounded-xl border border-neutral-200 space-y-3.5">
            <div className="border-b border-neutral-100 pb-2.5">
              <h3 className="text-sm font-bold text-neutral-900 flex items-center gap-2">
                <Clock className="h-4 w-4 text-neutral-500" />
                Active Leaves List
              </h3>
            </div>

            <div className="space-y-2 max-h-[200px] overflow-y-auto pr-1">
              {leavesLoading ? (
                <p className="text-center text-xs text-neutral-400 py-3">Loading leaves...</p>
              ) : !leavesData || leavesData.length === 0 ? (
                <p className="text-center text-xs text-neutral-400 py-3">No scheduled leaves</p>
              ) : (
                leavesData.map(leave => (
                  <div key={leave.id} className="p-2.5 bg-red-50/50 rounded-lg border border-red-100 flex items-center justify-between">
                    <div className="space-y-0.5">
                      <p className="text-xs font-semibold text-red-900">
                        {format(parseISO(leave.startDate), 'dd MMM')} - {format(parseISO(leave.endDate), 'dd MMM yyyy')}
                      </p>
                      {leave.reason && (
                        <p className="text-[11px] text-red-700 italic">{leave.reason}</p>
                      )}
                    </div>
                    <button
                      onClick={() => handleDeleteLeave(leave.id)}
                      disabled={deleteLeave.isPending}
                      className="p-1.5 hover:bg-red-100 rounded text-red-600 transition-colors cursor-pointer"
                      title="Remove leave"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Multiple Appointments for Date Popup Modal */}
      {popupDate && (
        <div
          className="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 backdrop-blur-sm p-4 animate-in fade-in duration-150"
          onClick={() => setPopupDate(null)}
        >
          <div
            className="bg-white rounded-xl shadow-xl border border-neutral-200 w-full max-w-lg max-h-[80vh] flex flex-col overflow-hidden"
            onClick={e => e.stopPropagation()}
          >
            {/* Modal Header */}
            <div className="flex items-center justify-between px-5 py-3.5 border-b border-neutral-200 bg-neutral-50">
              <div>
                <h3 className="text-sm font-bold text-neutral-900 flex items-center gap-2">
                  <CalendarRange className="h-4 w-4 text-neutral-600" />
                  Appointments
                </h3>
                <p className="text-[11px] text-neutral-500 font-medium mt-0.5">
                  {format(parseISO(popupDate), 'EEEE, dd MMM yyyy')} • {popupAppointments.length} appointment{popupAppointments.length !== 1 ? 's' : ''}
                </p>
              </div>
              <button
                onClick={() => setPopupDate(null)}
                className="p-1.5 hover:bg-neutral-200/70 rounded-md text-neutral-400 hover:text-neutral-600 transition-colors cursor-pointer"
                title="Close"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            {/* Modal Body */}
            <div className="p-5 space-y-2.5 overflow-y-auto flex-1">
              {popupAppointments.length === 0 ? (
                <div className="text-center py-6 text-neutral-400 text-xs flex flex-col items-center gap-1.5">
                  <Ban className="h-6 w-6 text-neutral-300" />
                  <span>No appointments on this date</span>
                </div>
              ) : (
                popupAppointments.map(appt => (
                  <div 
                    key={appt.id} 
                    role="button"
                    tabIndex={0}
                    onClick={() => {
                      setSelectedAppointment(appt)
                    }}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        setSelectedAppointment(appt)
                      }
                    }}
                    title="Click to view details"
                    className="p-3 bg-neutral-50 rounded-lg border border-neutral-200 space-y-1.5 hover:bg-neutral-100 hover:border-neutral-300 transition-colors cursor-pointer group"
                  >
                    <div className="flex items-center justify-between">
                      <span className="text-[11px] font-mono font-semibold text-neutral-600 bg-neutral-200/60 px-1.5 py-0.5 rounded">
                        {formatTime(appt.appointmentTime)} – {formatTime(appt.appointmentEndTime)}
                      </span>
                      <span className={cn(
                        'text-[9px] font-semibold uppercase px-1.5 py-0.5 rounded border',
                        appt.status === 'BOOKED' && 'bg-blue-50 text-blue-700 border-blue-200',
                        appt.status === 'CHECKED_IN' && 'bg-emerald-50 text-emerald-700 border-emerald-200',
                        appt.status === 'RESCHEDULED' && 'bg-amber-50 text-amber-700 border-amber-200',
                        appt.status === 'CANCELLED' && 'bg-neutral-100 text-neutral-500 border-neutral-200'
                      )}>
                        {appt.status}
                      </span>
                    </div>
                    <div className="flex items-center gap-2 text-xs text-neutral-900 font-semibold">
                      <User className="h-3.5 w-3.5 text-neutral-400" />
                      <span>{appt.patientName || appt.tempPatientName || 'Walk-in Patient'}</span>
                    </div>
                    {appt.patientPhone && (
                      <p className="text-[11px] text-neutral-500 ml-5 flex items-center gap-1">
                        <Phone className="h-3 w-3 text-neutral-400" />
                        {appt.patientPhone}
                      </p>
                    )}
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      )}

      {/* Single Appointment Information Details Modal Popup */}
      {selectedAppointment && (
        <div
          className="fixed inset-0 z-[110] flex items-center justify-center bg-black/50 backdrop-blur-sm p-4 animate-in fade-in duration-150"
          onClick={() => setSelectedAppointment(null)}
        >
          <div
            className="bg-white rounded-xl shadow-2xl border border-neutral-200 w-full max-w-lg max-h-[85vh] flex flex-col overflow-hidden animate-in zoom-in-95 duration-150"
            onClick={e => e.stopPropagation()}
          >
            {/* Modal Header */}
            <div className="flex items-center justify-between px-5 py-3.5 border-b border-neutral-200 bg-neutral-900 text-white">
              <div className="flex items-center gap-2.5">
                <div className="p-1.5 rounded-lg bg-white/10 text-white">
                  <CalendarRange className="h-4 w-4" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-white leading-tight">
                    Appointment Details
                  </h3>
                  <p className="text-[11px] text-neutral-300 font-mono mt-0.5">
                    {format(parseISO(selectedAppointment.appointmentDate), 'dd MMMM yyyy')}
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-2.5">
                <span className={cn(
                  'text-[10px] font-semibold uppercase px-2 py-0.5 rounded-full tracking-wider border flex items-center gap-1.5',
                  selectedAppointment.status === 'BOOKED' && 'bg-blue-500/20 text-blue-200 border-blue-400/30',
                  selectedAppointment.status === 'CHECKED_IN' && 'bg-emerald-500/20 text-emerald-200 border-emerald-400/30',
                  selectedAppointment.status === 'RESCHEDULED' && 'bg-amber-500/20 text-amber-200 border-amber-400/30',
                  selectedAppointment.status === 'CANCELLED' && 'bg-red-500/20 text-red-200 border-red-400/30'
                )}>
                  <span className={cn(
                    'w-1.5 h-1.5 rounded-full',
                    selectedAppointment.status === 'BOOKED' && 'bg-blue-400',
                    selectedAppointment.status === 'CHECKED_IN' && 'bg-emerald-400',
                    selectedAppointment.status === 'RESCHEDULED' && 'bg-amber-400',
                    selectedAppointment.status === 'CANCELLED' && 'bg-red-400'
                  )} />
                  {selectedAppointment.status}
                </span>
                <button
                  onClick={() => setSelectedAppointment(null)}
                  className="p-1 hover:bg-white/10 rounded text-neutral-300 hover:text-white transition-colors cursor-pointer"
                  title="Close"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
            </div>

            {/* Modal Body */}
            <div className="p-5 space-y-4 overflow-y-auto flex-1">
              {/* Patient Card */}
              <div className="bg-neutral-50 border border-neutral-200 rounded-xl p-3.5 space-y-2.5">
                <div className="flex items-start justify-between gap-3">
                  <div className="flex items-center gap-2.5">
                    <div className="w-9 h-9 rounded-lg bg-neutral-900 text-white font-bold text-sm flex items-center justify-center">
                      {(selectedAppointment.patientName || selectedAppointment.tempPatientName || 'P').charAt(0).toUpperCase()}
                    </div>
                    <div>
                      <h4 className="text-sm font-bold text-neutral-900 leading-tight">
                        {selectedAppointment.patientName || 
                          `${selectedAppointment.tempPatientSalutation ? selectedAppointment.tempPatientSalutation + ' ' : ''}${selectedAppointment.tempPatientName || 'Walk-in Patient'}`
                        }
                      </h4>
                      {selectedAppointment.patientNumber && (
                        <p className="text-xs font-mono font-medium text-neutral-600 mt-0.5">
                          ID: {selectedAppointment.patientNumber}
                        </p>
                      )}
                    </div>
                  </div>
                  <span className="text-[10px] font-semibold text-neutral-600 bg-white border border-neutral-200 px-2 py-0.5 rounded uppercase">
                    {selectedAppointment.visitMode || 'OPD'}
                  </span>
                </div>

                {/* Demographics / Phone row */}
                <div className="grid grid-cols-2 gap-2 pt-2 border-t border-neutral-200 text-xs">
                  <div>
                    <span className="text-[10px] uppercase font-semibold text-neutral-400 block">Phone</span>
                    <span className="font-medium text-neutral-800 flex items-center gap-1 mt-0.5">
                      <Phone className="h-3 w-3 text-neutral-400" />
                      {selectedAppointment.patientPhone || selectedAppointment.tempPatientPhone || '—'}
                    </span>
                  </div>
                  <div>
                    <span className="text-[10px] uppercase font-semibold text-neutral-400 block">Age / Gender</span>
                    <span className="font-medium text-neutral-800 mt-0.5 block">
                      {selectedAppointment.tempPatientAge ? `${selectedAppointment.tempPatientAge} yrs` : '—'}
                      {selectedAppointment.tempPatientGender ? ` • ${selectedAppointment.tempPatientGender}` : ''}
                    </span>
                  </div>
                </div>
              </div>

              {/* Appointment Schedule Details Grid */}
              <div className="space-y-2">
                <h5 className="text-[10px] font-semibold text-neutral-400 uppercase tracking-wider">
                  Schedule Details
                </h5>
                <div className="grid grid-cols-2 gap-2.5">
                  <div className="p-2.5 bg-white border border-neutral-200 rounded-lg space-y-0.5">
                    <div className="flex items-center gap-1 text-xs text-neutral-500">
                      <CalendarIcon className="h-3 w-3 text-neutral-600" />
                      <span className="font-semibold text-[10px] uppercase">Date</span>
                    </div>
                    <p className="text-xs font-semibold text-neutral-900">
                      {format(parseISO(selectedAppointment.appointmentDate), 'dd MMM yyyy')}
                    </p>
                    <p className="text-[10px] text-neutral-500">
                      {format(parseISO(selectedAppointment.appointmentDate), 'EEEE')}
                    </p>
                  </div>

                  <div className="p-2.5 bg-white border border-neutral-200 rounded-lg space-y-0.5">
                    <div className="flex items-center gap-1 text-xs text-neutral-500">
                      <Clock className="h-3 w-3 text-neutral-600" />
                      <span className="font-semibold text-[10px] uppercase">Time Slot</span>
                    </div>
                    <p className="text-xs font-semibold text-neutral-900 font-mono">
                      {formatTime(selectedAppointment.appointmentTime)} – {formatTime(selectedAppointment.appointmentEndTime)}
                    </p>
                    <p className="text-[10px] text-emerald-600 font-medium">
                      Confirmed Slot
                    </p>
                  </div>

                  <div className="p-2.5 bg-white border border-neutral-200 rounded-lg space-y-0.5 col-span-2">
                    <div className="flex items-center gap-1 text-xs text-neutral-500">
                      <Stethoscope className="h-3 w-3 text-neutral-600" />
                      <span className="font-semibold text-[10px] uppercase">Consultant Doctor</span>
                    </div>
                    <p className="text-xs font-semibold text-neutral-900">
                      {selectedAppointment.providerName || defaultConsultantName || 'Assigned Consultant'}
                    </p>
                  </div>
                </div>
              </div>

              {/* Notes / Reason for Visit */}
              {selectedAppointment.notes ? (
                <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg space-y-1">
                  <div className="flex items-center gap-1 text-amber-800 text-xs font-semibold">
                    <FileText className="h-3 w-3" />
                    <span>Reason / Clinical Notes</span>
                  </div>
                  <p className="text-xs text-amber-950 leading-relaxed">
                    {selectedAppointment.notes}
                  </p>
                </div>
              ) : (
                <div className="p-2.5 bg-neutral-50 border border-neutral-200 rounded-lg flex items-center gap-1.5 text-xs text-neutral-400 italic">
                  <FileText className="h-3 w-3" />
                  <span>No additional clinical notes recorded</span>
                </div>
              )}
            </div>

            {/* Modal Footer */}
            <div className="px-5 py-3 border-t border-neutral-200 bg-neutral-50 flex items-center justify-end">
              <button
                type="button"
                onClick={() => setSelectedAppointment(null)}
                className="rounded-lg bg-neutral-900 px-3.5 py-1.5 text-xs font-semibold text-white hover:bg-neutral-800 transition-colors cursor-pointer"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
