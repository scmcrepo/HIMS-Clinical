import { useState, useMemo, useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { useAuthStore } from '../../../store/authStore'
import {
  format,
  startOfWeek,
  endOfWeek,
  startOfMonth,
  endOfMonth,
  addDays,
  addWeeks,
  addMonths,
  subDays,
  subWeeks,
  subMonths,
  isToday as isTodayFn,
} from 'date-fns'
import {
  useConsultantCalendar,
  useConsultantLeavesById,
  useConsultantLeaveMutations,
} from '../../../hooks/appointment/useAppointment'
import { useConsultants } from '../../../hooks/consultant/useConsultant'
import { cn } from '../../../lib/utils'
import AppointmentCalendar, { type CalendarViewMode } from '../components/AppointmentCalendar'
import { TimeBlockModal } from '../components/TimeBlockModal'
import { FullDayLeaveModal } from '../components/FullDayLeaveModal'
import { AppointmentDetailModal } from '../components/AppointmentDetailModal'
import { DateScheduleModal } from '../components/DateScheduleModal'
import { ConfirmModal } from '../../../components/ui/ConfirmModal'
import type { Appointment, ConsultantLeave, TimeRangeInput } from '../../../types/appointment'
import {
  CalendarDays,
  CalendarRange,
  Clock,
  User,
  Search,
  ChevronDown,
} from 'lucide-react'

export default function DoctorCalendarPage() {
  const { user } = useAuthStore()
  const [selectedDate, setSelectedDate] = useState<Date>(new Date())
  const [viewMode, setViewMode] = useState<CalendarViewMode>('dayGridMonth')

  // Search and selector states for admin
  const { data: consultants } = useConsultants()
  const [selectedConsultantId, setSelectedConsultantId] = useState<string>('')
  const [searchQuery, setSearchQuery] = useState('')
  const [showDropdown, setShowDropdown] = useState(false)
  const autocompleteRef = useRef<HTMLDivElement>(null)

  // If logged-in user is a doctor, default to their consultant ID.
  const effectiveConsultantId = user?.consultantId || selectedConsultantId

  // Set default selected consultant to the first one in list for admins/staff
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
        if (defaultConsultantName) setSearchQuery(defaultConsultantName)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [defaultConsultantName])

  useEffect(() => {
    if (defaultConsultantName) {
      setSearchQuery(defaultConsultantName)
    }
  }, [defaultConsultantName])

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

  const todayStr = useMemo(() => format(new Date(), 'yyyy-MM-dd'), [])

  // Modals state
  const [modalTargetDate, setModalTargetDate] = useState<string>(todayStr)
  const [modalTargetEndDate, setModalTargetEndDate] = useState<string>(todayStr)
  const [showFullDayModal, setShowFullDayModal] = useState(false)
  const [showTimeBlockModal, setShowTimeBlockModal] = useState(false)
  const [selectedTimeRange, setSelectedTimeRange] = useState<TimeRangeInput | undefined>(undefined)
  const [selectedAppointment, setSelectedAppointment] = useState<Appointment | null>(null)
  const [clickedDateForSchedule, setClickedDateForSchedule] = useState<string | null>(null)
  const [leaveToDelete, setLeaveToDelete] = useState<string | null>(null)

  // Compute visible date range to fetch based on active view mode
  const calendarRange = useMemo(() => {
    const anchor = selectedDate
    if (viewMode === 'dayGridMonth') {
      return {
        start: format(startOfWeek(startOfMonth(anchor), { weekStartsOn: 1 }), 'yyyy-MM-dd'),
        end: format(endOfWeek(endOfMonth(anchor), { weekStartsOn: 1 }), 'yyyy-MM-dd'),
      }
    }
    if (viewMode === 'timeGridWeek') {
      return {
        start: format(startOfWeek(anchor, { weekStartsOn: 1 }), 'yyyy-MM-dd'),
        end: format(endOfWeek(anchor, { weekStartsOn: 1 }), 'yyyy-MM-dd'),
      }
    }
    return {
      start: format(anchor, 'yyyy-MM-dd'),
      end: format(anchor, 'yyyy-MM-dd'),
    }
  }, [viewMode, selectedDate])

  // Fetch calendar data & leaves for this consultant
  const { data: calendarData, isLoading: calendarLoading } = useConsultantCalendar(
    calendarRange.start,
    calendarRange.end,
    effectiveConsultantId || undefined
  )
  const { data: leavesData } = useConsultantLeavesById(effectiveConsultantId || undefined)
  const { createLeave, deleteLeave, createTimeBlocks } = useConsultantLeaveMutations()

  // Stepping dates: ← / → adapts to Month / Week / Day
  const stepDate = (direction: 1 | -1) => {
    if (viewMode === 'dayGridMonth') {
      setSelectedDate(prev => (direction === 1 ? addMonths(prev, 1) : subMonths(prev, 1)))
    } else if (viewMode === 'timeGridWeek') {
      setSelectedDate(prev => (direction === 1 ? addWeeks(prev, 1) : subWeeks(prev, 1)))
    } else {
      setSelectedDate(prev => (direction === 1 ? addDays(prev, 1) : subDays(prev, 1)))
    }
  }

  // Header period title
  const periodTitle = useMemo(() => {
    if (viewMode === 'dayGridMonth') {
      return format(selectedDate, 'MMMM yyyy')
    }
    if (viewMode === 'timeGridWeek') {
      const wStart = startOfWeek(selectedDate, { weekStartsOn: 1 })
      const wEnd = endOfWeek(selectedDate, { weekStartsOn: 1 })
      return `${format(wStart, 'dd MMM')} – ${format(wEnd, 'dd MMM yyyy')}`
    }
    return `${isTodayFn(selectedDate) ? 'Today · ' : ''}${format(selectedDate, 'EEEE, dd MMM yyyy')}`
  }, [viewMode, selectedDate])

  const handleDeleteLeave = (id: string) => {
    setLeaveToDelete(id)
  }

  // Handle clicking a date on the calendar: do NOT switch view; open the date schedule modal!
  const handleDateClick = (dateStr: string) => {
    setModalTargetDate(dateStr)
    setModalTargetEndDate(dateStr)
    setClickedDateForSchedule(dateStr)
  }

  // Handle dragging across multiple dates on the calendar: directly open full day leave modal with range
  const handleDateRangeSelect = (startDate: string, endDate: string) => {
    if (startDate < todayStr) return
    setModalTargetDate(startDate)
    setModalTargetEndDate(endDate)
    setShowFullDayModal(true)
  }

  // Handle clicking a leave block on the calendar: open the date schedule modal for that date
  const handleLeaveClick = (leave: ConsultantLeave) => {
    setModalTargetDate(leave.startDate)
    setModalTargetEndDate(leave.endDate || leave.startDate)
    setClickedDateForSchedule(leave.startDate)
  }

  // Appointments on clickedDateForSchedule
  const appointmentsOnClickedDate = useMemo(() => {
    if (!clickedDateForSchedule) return []
    return calendarData?.appointments?.filter(appt => appt.appointmentDate === clickedDateForSchedule) ?? []
  }, [clickedDateForSchedule, calendarData])

  const leavesList = leavesData ?? calendarData?.leaves ?? []

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
            ? 'View your schedule across Day, Week, and Month views, and click any date or event to manage appointments and block availability.'
            : 'Select a consultant to view their schedule across Day, Week, and Month views, and manage their availability.'}
        </p>
      </div>

      {/* Toolbar Row: Consultant Selector + Action Buttons */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 bg-white px-5 py-3.5 rounded-xl border border-neutral-200 shadow-xs">
        {/* Consultant Selector for Admins */}
        {!user?.consultantId ? (
          <div className="relative flex-1 max-w-md" ref={autocompleteRef}>
            <label className="text-[11px] font-bold text-neutral-500 uppercase tracking-wider mb-1 block">
              Consultant:
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
                className="w-full pl-9 pr-8 py-1.5 bg-neutral-50 border border-neutral-200 rounded-lg text-xs font-medium text-neutral-800 focus:border-neutral-900 focus:bg-white focus:outline-none transition-colors"
              />
              <ChevronDown
                className={cn(
                  'absolute right-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-neutral-400 transition-transform pointer-events-none',
                  showDropdown && 'rotate-180'
                )}
              />
            </div>

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
                          'w-full text-left px-3.5 py-2 flex items-center justify-between gap-2.5 hover:bg-neutral-50 transition-colors text-xs cursor-pointer',
                          isSelected && 'bg-neutral-50 font-semibold'
                        )}
                      >
                        <div className="flex items-center gap-2.5 min-w-0">
                          <div className="h-7 w-7 rounded-full bg-neutral-100 border border-neutral-200 flex items-center justify-center shrink-0">
                            <User className="h-3.5 w-3.5 text-neutral-600" />
                          </div>
                          <div className="min-w-0">
                            <p className={cn('text-neutral-800 truncate', isSelected && 'text-neutral-900 font-semibold')}>
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
        ) : (
          <div className="flex items-center gap-2.5">
            <div className="h-9 w-9 rounded-full bg-neutral-900 text-white font-bold flex items-center justify-center text-xs shadow-xs">
              {(defaultConsultantName || user.username)?.[0] || 'D'}
            </div>
            <div>
              <p className="text-xs font-bold text-neutral-900">{defaultConsultantName || user.username}</p>
              <p className="text-[11px] text-neutral-500 font-medium">Doctor Calendar</p>
            </div>
          </div>
        )}

        {/* Action Buttons: Block Full Day(s) + Block Hours */}
        <div className="flex items-center gap-2 flex-wrap">
          <button
            type="button"
            onClick={() => {
              const d = format(selectedDate, 'yyyy-MM-dd')
              const target = d < todayStr ? todayStr : d
              setModalTargetDate(target)
              setModalTargetEndDate(target)
              setShowFullDayModal(true)
            }}
            disabled={!effectiveConsultantId}
            className="flex items-center gap-1.5 px-3.5 py-2 bg-red-600 hover:bg-red-700 text-white text-xs font-bold rounded-xl transition-all shadow-xs disabled:opacity-50 cursor-pointer"
          >
            <CalendarRange className="h-3.5 w-3.5" />
            Block Full Day(s)
          </button>

          <button
            type="button"
            onClick={() => {
              const d = format(selectedDate, 'yyyy-MM-dd')
              const target = d < todayStr ? todayStr : d
              setModalTargetDate(target)
              setSelectedTimeRange(undefined)
              setShowTimeBlockModal(true)
            }}
            disabled={!effectiveConsultantId}
            className="flex items-center gap-1.5 px-3.5 py-2 bg-neutral-800 hover:bg-neutral-900 text-white text-xs font-bold rounded-xl transition-all shadow-xs disabled:opacity-50 cursor-pointer"
          >
            <Clock className="h-3.5 w-3.5" />
            Block Specific Hours
          </button>

          {effectiveConsultantId && (
            <Link
              to={`/settings/consultants/${effectiveConsultantId}/slots?name=${encodeURIComponent(defaultConsultantName)}`}
              className="px-3 py-2 border border-neutral-200 hover:bg-neutral-50 text-neutral-700 text-xs font-semibold rounded-xl transition-colors whitespace-nowrap"
            >
              Working Slots
            </Link>
          )}
        </div>
      </div>

      {/* Calendar Navigation & View Segmented Toggle */}
      <div className="bg-white p-4 rounded-xl border border-neutral-200 shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        {/* Date Navigator */}
        <div className="flex items-center gap-2">
          <div className="flex items-center bg-neutral-100 rounded-lg p-0.5 border border-neutral-200">
            <button
              onClick={() => stepDate(-1)}
              className="p-1.5 hover:bg-white rounded text-neutral-700 font-bold transition-all text-xs cursor-pointer"
              title="Previous"
            >
              ←
            </button>
            <button
              onClick={() => setSelectedDate(new Date())}
              className="px-3 py-1 text-xs font-bold text-neutral-800 hover:bg-white rounded transition-all cursor-pointer"
            >
              Today
            </button>
            <button
              onClick={() => stepDate(1)}
              className="p-1.5 hover:bg-white rounded text-neutral-700 font-bold transition-all text-xs cursor-pointer"
              title="Next"
            >
              →
            </button>
          </div>
          <span className="text-sm font-bold text-neutral-900 ml-2">{periodTitle}</span>
        </div>

        {/* Segmented View Mode Toggle: [ Month ] [ Week ] [ Day ] */}
        <div className="flex items-center bg-neutral-100 rounded-xl p-1 border border-neutral-200" role="tablist">
          {[
            { id: 'dayGridMonth' as CalendarViewMode, label: 'Month', title: 'Monthly Grid' },
            { id: 'timeGridWeek' as CalendarViewMode, label: 'Week', title: '7-Day Time Grid' },
            { id: 'timeGridDay' as CalendarViewMode, label: 'Day', title: 'Single-Day Time Grid' },
          ].map(tab => (
            <button
              key={tab.id}
              role="tab"
              aria-selected={viewMode === tab.id}
              title={tab.title}
              onClick={() => setViewMode(tab.id)}
              className={cn(
                'px-3.5 py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer',
                viewMode === tab.id
                  ? 'bg-white text-neutral-900 shadow-sm'
                  : 'text-neutral-500 hover:text-neutral-800'
              )}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* Main Full-Width FullCalendar Display */}
      <div className="space-y-4">
        <AppointmentCalendar
          view={viewMode}
          date={format(selectedDate, 'yyyy-MM-dd')}
          appointments={calendarData?.appointments ?? []}
          leaves={leavesList}
          onDateClick={handleDateClick}
          onDateRangeSelect={handleDateRangeSelect}
          onLeaveClick={handleLeaveClick}
          onTimeRangeSelect={(dateStr, startTime, endTime) => {
            setModalTargetDate(dateStr)
            setModalTargetEndDate(dateStr)
            setSelectedTimeRange({ startTime, endTime })
            setShowTimeBlockModal(true)
          }}
          onAppointmentClick={appt => setSelectedAppointment(appt)}
          isLoading={calendarLoading}
        />

        {/* Calendar Legend / Helpful Hint */}
        <div className="p-3.5 bg-white border border-neutral-200 rounded-xl text-xs text-neutral-600 flex flex-wrap items-center justify-between gap-3 shadow-xs">
          <div className="flex items-center gap-4 flex-wrap">
            <div className="flex items-center gap-1.5">
              <span className="h-2.5 w-2.5 rounded-full bg-blue-500" />
              <span className="font-semibold text-neutral-700">Booked Appointment</span>
            </div>
            <div className="flex items-center gap-1.5">
              <span className="h-2.5 w-2.5 rounded-full bg-green-500" />
              <span className="font-semibold text-neutral-700">Checked In</span>
            </div>
            <div className="flex items-center gap-1.5">
              <span className="h-2.5 w-2.5 rounded-full bg-amber-500" />
              <span className="font-semibold text-neutral-700">Blocked Hours (Time Window)</span>
            </div>
            <div className="flex items-center gap-1.5">
              <span className="h-2.5 w-2.5 rounded-full bg-red-500" />
              <span className="font-semibold text-neutral-700">Full Day Leave</span>
            </div>
          </div>
          <p className="text-[11px] text-neutral-500 font-medium">
            💡 Drag across dates to block multiple days at once. Click any date or event to view appointments & schedule details.
          </p>
        </div>
      </div>

      {/* Modal 1: Date Schedule & Availability (Opened on clicking any date or leave chip) */}
      {clickedDateForSchedule && (
        <DateScheduleModal
          dateStr={clickedDateForSchedule}
          todayStr={todayStr}
          appointments={appointmentsOnClickedDate}
          leaves={leavesList}
          onBlockFullDay={() => {
            if (clickedDateForSchedule < todayStr) return
            setModalTargetDate(clickedDateForSchedule)
            setModalTargetEndDate(clickedDateForSchedule)
            setShowFullDayModal(true)
          }}
          onBlockHours={() => {
            if (clickedDateForSchedule < todayStr) return
            setModalTargetDate(clickedDateForSchedule)
            setSelectedTimeRange(undefined)
            setShowTimeBlockModal(true)
          }}
          onDeleteLeave={handleDeleteLeave}
          onViewAppointment={appt => setSelectedAppointment(appt)}
          onClose={() => setClickedDateForSchedule(null)}
        />
      )}

      {/* Modal 2: Block Full Day(s) */}
      {showFullDayModal && effectiveConsultantId && (
        <FullDayLeaveModal
          defaultDate={modalTargetDate}
          defaultEndDate={modalTargetEndDate}
          todayStr={todayStr}
          appointments={calendarData?.appointments ?? []}
          isSubmitting={createLeave.isPending}
          onSubmit={payload => {
            createLeave.mutate(
              { ...payload, consultantId: effectiveConsultantId },
              {
                onSuccess: () => {
                  setShowFullDayModal(false)
                },
              }
            )
          }}
          onClose={() => setShowFullDayModal(false)}
        />
      )}

      {/* Modal 3: Block Specific Hours */}
      {showTimeBlockModal && effectiveConsultantId && (
        <TimeBlockModal
          defaultDate={modalTargetDate}
          todayStr={todayStr}
          appointments={calendarData?.appointments ?? []}
          initialTimeRange={selectedTimeRange}
          isSubmitting={createTimeBlocks.isPending}
          onSubmit={payload => {
            createTimeBlocks.mutate(
              { ...payload, consultantId: effectiveConsultantId },
              {
                onSuccess: () => {
                  setShowTimeBlockModal(false)
                },
              }
            )
          }}
          onClose={() => {
            setShowTimeBlockModal(false)
            setSelectedTimeRange(undefined)
          }}
        />
      )}

      {/* Modal 4: Appointment Details */}
      {selectedAppointment && (
        <AppointmentDetailModal
          appointment={selectedAppointment}
          onClose={() => setSelectedAppointment(null)}
        />
      )}

      {/* Modal 5: Confirm Delete Leave / Blocked Hours */}
      <ConfirmModal
        isOpen={Boolean(leaveToDelete)}
        onClose={() => setLeaveToDelete(null)}
        onConfirm={() => {
          if (leaveToDelete) {
            deleteLeave.mutate(leaveToDelete, {
              onSettled: () => {
                setLeaveToDelete(null)
              },
            })
          }
        }}
        title="Cancel Leave / Blocked Hours"
        message="Are you sure you want to cancel this leave / blocked hours? The time will become available again."
        confirmText="Yes, Unblock"
        cancelText="Keep Block"
        variant="danger"
        isLoading={deleteLeave.isPending}
      />
    </div>
  )
}
