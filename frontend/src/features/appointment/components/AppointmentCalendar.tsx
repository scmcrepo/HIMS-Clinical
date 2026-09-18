import { useEffect, useMemo, useRef } from 'react'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import type { DateSelectArg, EventClickArg, EventInput } from '@fullcalendar/core'
import { eachDayOfInterval, format, parseISO, subDays } from 'date-fns'
import type { Appointment, ConsultantLeave } from '../../../types/appointment'
import { AppointmentEventContent, type AppointmentEventProps } from './AppointmentEventContent'

export type CalendarViewMode = 'dayGridMonth' | 'timeGridWeek' | 'timeGridDay'

interface AppointmentCalendarProps {
  view: CalendarViewMode
  /** Anchor date, yyyy-MM-dd. The parent owns navigation; the calendar follows. */
  date: string
  appointments: Appointment[]
  leaves: ConsultantLeave[]
  /** Month-view day click, and click on empty time in week/day view. */
  onDateClick: (date: string) => void
  /** Month-view drag selection across multiple dates to block entire range. */
  onDateRangeSelect?: (startDate: string, endDate: string) => void
  onAppointmentClick: (appointment: Appointment) => void
  onLeaveClick?: (leave: ConsultantLeave) => void
  consultantLabel?: (providerId: string, fallback: string | null) => string
  isLoading?: boolean
  onTimeRangeSelect?: (date: string, startTime: string, endTime: string) => void
}

/** "10:00:00" and "10:00" both have to become a valid ISO local datetime. */
const toIso = (date: string, time: string | null | undefined): string | undefined => {
  if (!time) return undefined
  const hhmmss = time.length === 5 ? `${time}:00` : time
  return `${date}T${hhmmss}`
}

const patientLabelOf = (a: Appointment) =>
  a.patientName || a.tempPatientName || 'Walk-in'

/**
 * FullCalendar month / week / day views over the appointment queue and leaves.
 */
export default function AppointmentCalendar({
  view,
  date,
  appointments,
  leaves,
  onDateClick,
  onDateRangeSelect,
  onAppointmentClick,
  onLeaveClick,
  consultantLabel,
  isLoading = false,
  onTimeRangeSelect,
}: AppointmentCalendarProps) {
  const calendarRef = useRef<FullCalendar | null>(null)

  // The parent's ←/→/Today buttons and the date picker move `date`; push that
  // into the calendar imperatively rather than remounting it, so the scroll
  // position of a time grid survives a day step.
  useEffect(() => {
    const api = calendarRef.current?.getApi()
    if (!api) return
    api.changeView(view)
    api.gotoDate(date)
  }, [view, date])

  const byId = useMemo(() => {
    const map = new Map<string, Appointment>()
    appointments.forEach(a => map.set(a.id, a))
    return map
  }, [appointments])

  const events = useMemo<EventInput[]>(() => {
    const appointmentEvents: EventInput[] = appointments.map(a => {
      const start = toIso(a.appointmentDate, a.appointmentTime)
      const end = toIso(a.appointmentDate, a.appointmentEndTime)
      const extendedProps: AppointmentEventProps = {
        appointment: a,
        patientLabel: patientLabelOf(a),
        consultantLabel: consultantLabel ? consultantLabel(a.providerId, a.providerName) : (a.providerName || ''),
        timeLabel: a.appointmentTime ? a.appointmentTime.slice(0, 5) : '',
      }
      return {
        id: a.id,
        title: patientLabelOf(a),
        start: start ?? a.appointmentDate,
        ...(end ? { end } : {}),
        allDay: !start,
        extendedProps,
        classNames: ['hms-appointment-event', `hms-status-${a.status.toLowerCase()}`],
      }
    })

    const blockEvents: EventInput[] = []
    for (const leave of leaves) {
      const reason = leave.reason?.trim()
      const days = eachDayOfInterval({ start: parseISO(leave.startDate), end: parseISO(leave.endDate) })

      if (leave.blockType === 'TIME_RANGE' && leave.startTime && leave.endTime) {
        const timeLabel = `${leave.startTime.slice(0, 5)} - ${leave.endTime.slice(0, 5)}`
        days.forEach(day => {
          const dayStr = format(day, 'yyyy-MM-dd')
          blockEvents.push({
            id: `leave-${leave.id}-${dayStr}`,
            title: `⏱️ ${timeLabel} Blocked`,
            start: view === 'dayGridMonth' ? dayStr : (toIso(dayStr, leave.startTime) ?? dayStr),
            end: view === 'dayGridMonth' ? undefined : (toIso(dayStr, leave.endTime) ?? dayStr),
            allDay: view === 'dayGridMonth',
            extendedProps: {
              isLeave: true,
              leave,
              title: `Blocked: ${timeLabel}${reason ? ` · ${reason}` : ''}`,
            },
            classNames: ['hms-block-event', 'hms-block-chip'],
          })
        })
      } else {
        days.forEach(day => {
          const dayStr = format(day, 'yyyy-MM-dd')
          blockEvents.push({
            id: `leave-${leave.id}-${dayStr}`,
            title: reason ? `⛔ Leave: ${reason}` : '⛔ On Leave',
            start: dayStr,
            allDay: true,
            extendedProps: {
              isLeave: true,
              leave,
              title: reason ? `On Leave · ${reason}` : 'On Leave (Full Day)',
            },
            classNames: ['hms-leave-event', 'hms-leave-chip'],
          })
        })
      }
    }

    return [...blockEvents, ...appointmentEvents]
  }, [appointments, leaves, view, consultantLabel])

  const handleEventClick = (arg: EventClickArg) => {
    if (arg.event.extendedProps?.isLeave) {
      if (onLeaveClick) onLeaveClick(arg.event.extendedProps.leave)
      return
    }
    const appointment = byId.get(arg.event.id)
    if (appointment) onAppointmentClick(appointment)
  }

  const handleSelect = (arg: DateSelectArg) => {
    calendarRef.current?.getApi().unselect()

    if (onTimeRangeSelect && arg.start && arg.end && !arg.allDay) {
      const dateStr = format(arg.start, 'yyyy-MM-dd')
      const startTime = format(arg.start, 'HH:mm')
      const endTime = format(arg.end, 'HH:mm')
      onTimeRangeSelect(dateStr, startTime, endTime)
      return
    }

    if (arg.allDay) {
      const startStr = format(arg.start, 'yyyy-MM-dd')
      // FullCalendar all-day selection arg.end is exclusive (day after last selected date)
      const inclusiveEnd = subDays(arg.end, 1)
      const endStr = format(inclusiveEnd, 'yyyy-MM-dd')

      if (startStr !== endStr && onDateRangeSelect) {
        onDateRangeSelect(startStr, endStr)
      } else {
        onDateClick(startStr)
      }
    }
  }

  return (
    <div className="hms-calendar bg-white rounded-xl border border-gray-200 shadow-sm p-4 relative">
      {isLoading && (
        <div className="absolute inset-0 z-10 bg-white/60 flex items-center justify-center rounded-xl" aria-live="polite">
          <span className="text-sm text-gray-500">Loading appointments…</span>
        </div>
      )}
      <FullCalendar
        ref={calendarRef}
        plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
        initialView={view}
        initialDate={date}
        headerToolbar={false}
        firstDay={1}
        height="auto"
        expandRows
        nowIndicator
        allDaySlot={false}
        slotDuration="00:30:00"
        slotLabelInterval="01:00"
        slotMinTime="07:00:00"
        slotMaxTime="21:00:00"
        slotLabelFormat={{ hour: 'numeric', minute: '2-digit', meridiem: 'short' }}
        eventTimeFormat={{ hour: '2-digit', minute: '2-digit', hour12: false }}
        dayMaxEvents={4}
        selectable={true}
        selectMirror={true}
        unselectAuto={true}
        select={handleSelect}
        events={events}
        eventContent={AppointmentEventContent}
        eventClick={handleEventClick}
      />
    </div>
  )
}
