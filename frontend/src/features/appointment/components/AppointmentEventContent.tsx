import type { EventContentArg } from '@fullcalendar/core'
import { cn } from '../../../lib/utils'
import type { Appointment, AppointmentStatus } from '../../../types/appointment'

/**
 * Colour per status, shared by the event chip and the status dot.
 *
 * <p>Deliberately the same palette the list view's status pills use, so a
 * cancelled appointment reads as cancelled whichever view the user is in.
 */
export const EVENT_STATUS_STYLES: Record<AppointmentStatus, { chip: string; dot: string; label: string }> = {
  BOOKED: { chip: 'bg-blue-50 text-blue-800 border-blue-200', dot: 'bg-blue-500', label: 'Booked' },
  CHECKED_IN: { chip: 'bg-green-50 text-green-800 border-green-200', dot: 'bg-green-500', label: 'Checked in' },
  RESCHEDULED: { chip: 'bg-amber-50 text-amber-800 border-amber-200', dot: 'bg-amber-500', label: 'Rescheduled' },
  CANCELLED: { chip: 'bg-gray-100 text-gray-500 border-gray-200 line-through', dot: 'bg-gray-400', label: 'Cancelled' },
}

/** What {@link AppointmentCalendar} hangs off each FullCalendar event. */
export interface AppointmentEventProps {
  appointment: Appointment
  patientLabel: string
  consultantLabel: string
  timeLabel: string
}

const isCompact = (viewType: string) => viewType === 'dayGridMonth'

/**
 * Renders one appointment inside a FullCalendar event box.
 *
 * <p>Month cells are a few pixels tall and hold a whole day's list, so there
 * the event collapses to a dot, a time and a truncated name. Week and day
 * views have a block sized to the slot and can afford the consultant line.
 */
export function AppointmentEventContent(arg: EventContentArg) {
  const props = arg.event.extendedProps as Partial<AppointmentEventProps>
  const appointment = props.appointment

  const isLeave = (props as any).isLeave
  if (isLeave) {
    const isPartial = (props as any).leave?.blockType === 'TIME_RANGE'
    const compact = isCompact(arg.view.type)

    if (compact) {
      return (
        <div
          className={cn(
            'flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-bold border overflow-hidden truncate cursor-pointer shadow-2xs',
            isPartial
              ? 'bg-amber-100/90 text-amber-900 border-amber-300 hover:bg-amber-200'
              : 'bg-red-100/90 text-red-900 border-red-300 hover:bg-red-200'
          )}
          title={(props as any).title || arg.event.title}
        >
          <span className={cn('h-1.5 w-1.5 rounded-full shrink-0', isPartial ? 'bg-amber-600' : 'bg-red-600')} />
          <span className="truncate">{arg.event.title}</span>
        </div>
      )
    }

    return (
      <div
        className={cn(
          'h-full w-full rounded-md border px-2 py-1 overflow-hidden cursor-pointer flex flex-col justify-center shadow-2xs',
          isPartial
            ? 'bg-amber-50 text-amber-900 border-amber-200 hover:bg-amber-100'
            : 'bg-red-50 text-red-900 border-red-200 hover:bg-red-100'
        )}
        title={(props as any).title || arg.event.title}
      >
        <div className="flex items-center gap-1 text-[10px] font-bold">
          <span className={cn('h-1.5 w-1.5 rounded-full shrink-0', isPartial ? 'bg-amber-600' : 'bg-red-600')} />
          <span className="truncate">{arg.event.title}</span>
        </div>
        {(props as any).leave?.reason && (
          <div className="text-[9px] text-neutral-600 truncate mt-0.5 italic">{(props as any).leave.reason}</div>
        )}
      </div>
    )
  }

  if (!appointment) {
    return (
      <div className="px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-red-700/80">
        {arg.event.title}
      </div>
    )
  }

  const style = EVENT_STATUS_STYLES[appointment.status]
  const compact = isCompact(arg.view.type)

  if (compact) {
    return (
      <div
        className="flex items-center gap-1 px-1 py-px w-full overflow-hidden"
        title={`${props.timeLabel ?? ''} · ${props.patientLabel ?? ''} · ${props.consultantLabel ?? ''} · ${style.label}`}
      >
        <span className={cn('h-1.5 w-1.5 rounded-full shrink-0', style.dot)} />
        <span className="text-[10px] font-semibold text-gray-500 shrink-0">{props.timeLabel}</span>
        <span
          className={cn(
            'text-[10px] font-medium text-gray-800 truncate',
            appointment.status === 'CANCELLED' && 'line-through text-gray-400',
          )}
        >
          {props.patientLabel}
        </span>
      </div>
    )
  }

  return (
    <div
      className={cn('h-full w-full rounded-md border px-1.5 py-1 overflow-hidden', style.chip)}
      title={`${props.timeLabel ?? ''} · ${props.patientLabel ?? ''} · ${props.consultantLabel ?? ''} · ${style.label}`}
    >
      <div className="flex items-center gap-1">
        <span className={cn('h-1.5 w-1.5 rounded-full shrink-0', style.dot)} />
        <span className="text-[10px] font-bold truncate">{props.patientLabel}</span>
      </div>
      <div className="text-[9px] font-medium opacity-80 truncate">{props.timeLabel}</div>
      <div className="text-[9px] opacity-70 truncate">{props.consultantLabel}</div>
    </div>
  )
}

export default AppointmentEventContent
