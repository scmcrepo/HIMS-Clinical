import { useMemo, useState } from 'react'
import { CalendarOff, ChevronDown, ChevronRight } from 'lucide-react'

import { useDayBoard } from '../../../hooks/appointment/useAppointment'
import type { DayBoardDoctor, DayBoardSession } from '../../../types/appointment'
import { cn } from '../../../lib/utils'
import { formatTime } from '../quickbook/sessions'
import { dateToken } from '../quickbook/vocab'

interface Props {
  /** The day in view, as yyyy-MM-dd. */
  date: string
  /** Hands a ready-made line to the quick-book box. */
  onPick: (text: string) => void
}

/**
 * The clinic's day at a glance: who is sitting, in which sessions, and how full each is.
 *
 * This is the answer to the question the booking form could not answer — "who can see
 * this patient today?" — which previously required choosing a doctor before any
 * availability appeared at all, then backing out and trying the next one.
 *
 * Clicking a session does not book anything. It writes the doctor, the day and the
 * session into the quick-book box as ordinary text, which is then parsed exactly as if it
 * had been typed. Routing the click through the same parser rather than through a
 * private id means the board and the box cannot end up disagreeing about what is
 * selected, and the receptionist can still edit any part of it before committing.
 */
export function DayBoard({ date, onPick }: Props) {
  const { data, isLoading } = useDayBoard(date)
  const [collapsed, setCollapsed] = useState(false)

  const target = useMemo(() => new Date(`${date}T00:00:00`), [date])
  const doctors = data?.doctors ?? []
  const heading = dateToken(target, new Date()) === 'today'
    ? 'Clinic today'
    : `Clinic on ${target.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })}`

  const totals = useMemo(() => doctors.reduce(
    (acc, doctor) => {
      if (doctor.onLeave) return { ...acc, away: acc.away + 1 }
      doctor.sessions.forEach(session => {
        acc.booked += session.bookedCount
        acc.capacity += session.maxPatients
      })
      return acc
    },
    { booked: 0, capacity: 0, away: 0 },
  ), [doctors])

  if (isLoading || doctors.length === 0) return null

  return (
    <div className="bg-white border border-gray-200 rounded-2xl shadow-sm">
      <button
        type="button"
        onClick={() => setCollapsed(c => !c)}
        aria-expanded={!collapsed}
        className="w-full flex items-center justify-between gap-4 px-5 py-3 text-left"
      >
        <span className="flex items-center gap-2">
          {collapsed ? <ChevronRight className="h-4 w-4 text-gray-400" /> : <ChevronDown className="h-4 w-4 text-gray-400" />}
          <span className="text-sm font-bold text-gray-800">{heading}</span>
          <span className="text-xs text-gray-500">
            {doctors.length - totals.away} sitting
            {totals.away > 0 && ` · ${totals.away} away`}
            {totals.capacity > 0 && ` · ${totals.booked} of ${totals.capacity} booked`}
          </span>
        </span>
      </button>

      {!collapsed && (
        <div className="divide-y divide-gray-100 border-t border-gray-100">
          {doctors.map(doctor => (
            <DoctorRow
              key={doctor.consultantId}
              doctor={doctor}
              onPick={session => onPick(pickText(doctor, session, target))}
            />
          ))}
        </div>
      )}
    </div>
  )
}

/**
 * The line a click writes into the box: surname, day, session start.
 *
 * The start time is used rather than "morning" because slots cannot overlap, which makes
 * a start time unique for a doctor on a day — where "morning" would be ambiguous for
 * someone who sits twice before noon.
 */
function pickText(doctor: DayBoardDoctor, session: DayBoardSession, date: Date): string {
  const surname = doctor.name.trim().split(/\s+/).slice(-1)[0] ?? doctor.name
  return `${surname} ${dateToken(date, new Date())} ${formatTime(session.fromTime)}`
}

function DoctorRow({ doctor, onPick }: { doctor: DayBoardDoctor; onPick: (s: DayBoardSession) => void }) {
  return (
    <div className="flex flex-col sm:flex-row sm:items-center gap-3 px-5 py-3">
      <div className="sm:w-56 shrink-0">
        <p className={cn('text-sm font-semibold', doctor.onLeave ? 'text-gray-400' : 'text-gray-900')}>
          {doctor.name}
        </p>
        {doctor.speciality && <p className="text-xs text-gray-500">{doctor.speciality}</p>}
      </div>

      {doctor.onLeave ? (
        <p className="flex items-center gap-2 text-xs font-medium text-amber-700">
          <CalendarOff className="h-3.5 w-3.5" />
          On leave{doctor.leaveReason ? ` — ${doctor.leaveReason}` : ''}
        </p>
      ) : (
        <div className="flex flex-wrap gap-2">
          {doctor.sessions.map(session => (
            <SessionCell key={session.slotId} session={session} onPick={() => onPick(session)} />
          ))}
        </div>
      )}
    </div>
  )
}

function SessionCell({ session, onPick }: { session: DayBoardSession; onPick: () => void }) {
  const full = session.availableCount <= 0
  // Nearly full is worth flagging before someone commits: at 10 of 12 the receptionist
  // may well offer the other session instead.
  const tight = !full && session.maxPatients > 0 && session.bookedCount / session.maxPatients >= 0.8
  const fill = session.maxPatients > 0
    ? Math.min(100, Math.round((session.bookedCount / session.maxPatients) * 100))
    : 0

  return (
    <button
      type="button"
      onClick={onPick}
      disabled={full}
      title={full ? 'This session is full' : 'Start a booking in this session'}
      className={cn(
        'group min-w-[9.5rem] rounded-xl border px-3 py-2 text-left transition-colors',
        full && 'border-gray-200 bg-gray-50 opacity-60 cursor-not-allowed',
        tight && 'border-amber-200 bg-amber-50 hover:border-amber-300',
        !full && !tight && 'border-gray-200 bg-white hover:border-neutral-400 hover:bg-neutral-50',
      )}
    >
      <span className="block text-xs font-semibold text-gray-700">
        {formatTime(session.fromTime)}–{formatTime(session.toTime)}
      </span>
      <span className={cn('block text-[11px] font-medium', full ? 'text-gray-500' : tight ? 'text-amber-700' : 'text-gray-500')}>
        {full ? 'Full' : `${session.bookedCount} of ${session.maxPatients} booked`}
      </span>
      <span aria-hidden="true" className="mt-1.5 block h-1 w-full rounded-full bg-gray-100 overflow-hidden">
        <span
          className={cn('block h-full rounded-full', full ? 'bg-gray-400' : tight ? 'bg-amber-400' : 'bg-neutral-500')}
          style={{ width: `${fill}%` }}
        />
      </span>
    </button>
  )
}
