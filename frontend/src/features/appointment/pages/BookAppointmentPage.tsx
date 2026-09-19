import { useState, useEffect, useMemo } from 'react'
import type { ReactNode } from 'react'
import { format, addDays, parseISO } from 'date-fns'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { CalendarOff, Clock, Search, UserPlus, X } from 'lucide-react'

import { useAvailabilityCheck, useAppointmentMutations, useConsultantLeavesById } from '../../../hooks/appointment/useAppointment'
import { useConsultants, useConsultantSlots } from '../../../hooks/consultant/useConsultant'
import { patientApi } from '../../../services/patient/patientApi'
import { ConsultantSearchInput } from '../../../components/shared/ConsultantSearchInput'
import type { Patient } from '../../../types/patient'
import type { AppointmentSlot as SlotType } from '../../../services/slot/slotApi'
import { cn } from '../../../lib/utils'
import DatePicker from '../../../components/shared/DatePicker'
import BackButton from '../../../components/shared/BackButton'

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

const FIELD = 'w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:ring-2 focus:ring-neutral-500 outline-none transition-all'
const LABEL = 'text-xs font-bold text-gray-500 uppercase tracking-widest'

/**
 * Booking a slot for one patient.
 *
 * There used to be a New Patient / Existing Patient tab at the top of this form, which
 * asked the user a question the software can answer for itself: search for the person,
 * and whether they are already registered falls out of whether anything comes back. The
 * tab also forced two near-identical copies of every field, so a fix to one silently
 * missed the other.
 */
export default function BookAppointmentPage() {
  const navigate = useNavigate()
  const [date, setDate] = useState<Date>(new Date())
  const [selectedSlotId, setSelectedSlotId] = useState<string>('')
  const [bookingProviderId, setBookingProviderId] = useState<string>('')

  // ── the one patient field ────────────────────────────────────────────────

  const [query, setQuery] = useState('')
  const [selectedPatient, setSelectedPatient] = useState<Patient | null>(null)
  const [registering, setRegistering] = useState(false)
  const [searchFocused, setSearchFocused] = useState(false)
  const [newPatient, setNewPatient] = useState({ salutation: '', name: '', gender: '', age: '', phone: '' })

  const [settledQuery, setSettledQuery] = useState('')
  useEffect(() => {
    const handle = setTimeout(() => setSettledQuery(query), 350)
    return () => clearTimeout(handle)
  }, [query])

  const { data: searchPage, isFetching: searching } = useQuery({
    queryKey: ['patients', 'search', settledQuery],
    queryFn: () => patientApi.search(settledQuery, 0, 8),
    enabled: settledQuery.trim().length >= 3 && !selectedPatient,
  })

  /*
   * Results are only trusted once the request that finished was for the text now in the
   * box. Reading a stale empty page as "no such patient" would invite the user to
   * register someone who already exists, and duplicate patient records are considerably
   * harder to undo than a moment's wait.
   */
  const settled = settledQuery === query && !searching
  const results = settled ? (searchPage?.content ?? []) : []
  const noMatch = settled && query.trim().length >= 3 && results.length === 0 && !selectedPatient

  // An unmatched search is almost always a new patient, so the form offers itself rather
  // than making the user ask for it — prefilled with whichever half was typed.
  useEffect(() => {
    if (!noMatch) return
    setRegistering(true)
    const typed = query.trim()
    const digits = typed.replace(/\D/g, '')
    setNewPatient(prev => ({
      ...prev,
      phone: digits.length === 10 ? digits : prev.phone,
      name: /^[a-zA-Z\s]+$/.test(typed) ? typed : prev.name,
    }))
  }, [noMatch, query])

  const clearPatient = () => {
    setSelectedPatient(null)
    setRegistering(false)
    setQuery('')
    setNewPatient({ salutation: '', name: '', gender: '', age: '', phone: '' })
  }

  // ── scheduling ───────────────────────────────────────────────────────────

  const dateStr = format(date, 'yyyy-MM-dd')
  const { data: consultants } = useConsultants()
  const { data: availCheck } = useAvailabilityCheck(bookingProviderId || undefined, dateStr)
  const slots = availCheck?.slots
  const { data: doctorSlots } = useConsultantSlots(bookingProviderId || undefined)
  const { data: leavesData } = useConsultantLeavesById(bookingProviderId || undefined)
  const mutations = useAppointmentMutations()

  const leaveDatesSet = useMemo(() => {
    const set = new Set<string>()
    if (!leavesData) return set
    // Only full-day leave closes a date. A partial-day block leaves the rest
    // of the day bookable, and painting the whole date red would take those
    // hours away as surely as a leave would.
    leavesData.filter(leave => leave.blockType !== 'TIME_RANGE').forEach(leave => {
      let cursor = parseISO(leave.startDate as unknown as string)
      const end = parseISO(leave.endDate as unknown as string)
      while (cursor <= end) {
        set.add(format(cursor, 'yyyy-MM-dd'))
        cursor = addDays(cursor, 1)
      }
    })
    return set
  }, [leavesData])

  const isDoctorOnLeave = availCheck?.reason === 'ON_LEAVE' || leaveDatesSet.has(dateStr)

  const weeklySchedule = useMemo(() => {
    if (!doctorSlots || doctorSlots.length === 0) return []
    const groups: Record<string, { fromTime: string; toTime: string; days: string[]; maxPatients: number }> = {}
    doctorSlots.filter((s: SlotType) => s.status === 1).forEach((s: SlotType) => {
      const key = `${s.fromTime}-${s.toTime}`
      if (!groups[key]) groups[key] = { fromTime: s.fromTime, toTime: s.toTime, days: [], maxPatients: s.maxPatients }
      groups[key].days.push(s.dayOfWeek)
    })
    return Object.values(groups)
  }, [doctorSlots])

  // Salutation implies gender for every value that carries one, so asking twice is a
  // question with a knowable answer.
  useEffect(() => {
    const salutation = newPatient.salutation
    if (salutation === 'Mr' || salutation === 'Master') setNewPatient(prev => ({ ...prev, gender: 'MALE' }))
    else if (salutation === 'Ms' || salutation === 'Mrs') setNewPatient(prev => ({ ...prev, gender: 'FEMALE' }))
    else if (salutation === 'Mx') setNewPatient(prev => ({ ...prev, gender: 'OTHER' }))
  }, [newPatient.salutation])

  const selectableSlots = (slots ?? []).filter(slot => {
    if (!slot.isAvailable) return false
    if (dateStr !== format(new Date(), 'yyyy-MM-dd')) return true
    return slot.toTime > format(new Date(), 'HH:mm:ss')
  })

  const walkinAgeNum = newPatient.age ? parseInt(newPatient.age, 10) : NaN
  const isBabyAgeInvalid = registering && newPatient.salutation === 'Baby' && !isNaN(walkinAgeNum) && walkinAgeNum > 5
  const isMasterAgeInvalid = registering && newPatient.salutation === 'Master' && !isNaN(walkinAgeNum) && walkinAgeNum >= 18
  const isMrAgeInvalid = registering && newPatient.salutation === 'Mr' && !isNaN(walkinAgeNum) && walkinAgeNum < 18
  const isMrsOrMsAgeInvalid = registering && (newPatient.salutation === 'Mrs' || newPatient.salutation === 'Ms') && !isNaN(walkinAgeNum) && walkinAgeNum < 18
  const isDrAgeInvalid = registering && newPatient.salutation === 'Dr' && !isNaN(walkinAgeNum) && walkinAgeNum < 21
  const isMxAgeInvalid = registering && newPatient.salutation === 'Mx' && !isNaN(walkinAgeNum) && walkinAgeNum < 18
  const isWalkinValidationInvalid = isBabyAgeInvalid || isMasterAgeInvalid || isMrAgeInvalid || isMrsOrMsAgeInvalid || isDrAgeInvalid || isMxAgeInvalid

  const patientReady = selectedPatient !== null
    || (registering && newPatient.name.trim().length > 0 && newPatient.phone.length === 10 && !isWalkinValidationInvalid)

  const canBook = patientReady && !isWalkinValidationInvalid && !!bookingProviderId && !!selectedSlotId && !isDoctorOnLeave

  const handleBook = () => {
    if (!canBook) return
    mutations.book.mutate({
      patientId: selectedPatient?.id,
      tempPatientName: selectedPatient ? undefined : newPatient.name.trim(),
      tempPatientSalutation: selectedPatient ? undefined : newPatient.salutation || undefined,
      tempPatientGender: selectedPatient ? undefined : newPatient.gender || undefined,
      tempPatientPhone: selectedPatient ? undefined : newPatient.phone,
      tempPatientAge: selectedPatient || !newPatient.age ? undefined : parseInt(newPatient.age),
      providerId: bookingProviderId,
      slotId: selectedSlotId,
      appointmentDate: dateStr,
    }, {
      onSuccess: () => navigate('/appointments'),
    })
  }

  return (
    <div className="max-w-3xl mx-auto px-4 py-6 space-y-6">
      <div className="flex items-center justify-between mb-8 px-4">
        <h2 className="text-2xl font-bold text-gray-900 tracking-tight">New Appointment</h2>
        <BackButton />
      </div>

      {bookingProviderId && weeklySchedule.length > 0 && (
        <div className="bg-neutral-50 border border-neutral-200 rounded-xl p-3.5 space-y-2">
          <h4 className="text-[11px] font-semibold text-neutral-500 uppercase tracking-wider">Doctor's Weekly Schedule</h4>
          <div className="flex flex-wrap gap-1.5">
            {weeklySchedule.map((group, i) => (
              <span key={i} className="text-xs font-medium text-neutral-700 bg-white border border-neutral-200 px-2.5 py-1 rounded-lg">
                {group.days.join(', ')}: {formatTime(group.fromTime)} – {formatTime(group.toTime)} ({group.maxPatients} max)
              </span>
            ))}
          </div>
        </div>
      )}

      {bookingProviderId && isDoctorOnLeave && (
        <Notice
          icon={<CalendarOff className="h-4 w-4" />}
          title={`Doctor is unavailable on ${format(date, 'dd MMM yyyy')} (${availCheck?.dayOfWeek || format(date, 'EEEE')})`}
          detail="The doctor is marked on leave for this date. Please select another date to schedule an appointment."
        />
      )}
      {bookingProviderId && !isDoctorOnLeave && availCheck?.reason === 'NO_SLOTS' && (
        <Notice
          icon={<Clock className="h-4 w-4" />}
          title={`No slots configured for ${availCheck.dayOfWeek}`}
          detail="The doctor has no working hours scheduled on this day of the week."
        />
      )}
      {bookingProviderId && !isDoctorOnLeave && availCheck?.reason === 'TIME_BLOCKED' && (
        <Notice
          icon={<Clock className="h-4 w-4" />}
          title="All hours blocked for this date"
          detail="The doctor has blocked every configured slot on this date. Please select another date."
        />
      )}

      <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 space-y-6">
        {/* ── 1. Patient ─────────────────────────────────────────────────── */}
        <div className="space-y-3">
          <label className={LABEL}>1. Patient</label>

          {selectedPatient ? (
            <div className="p-4 bg-neutral-50 rounded-2xl border border-neutral-100 flex items-center gap-4">
              <div className="w-10 h-10 rounded-full bg-neutral-600 flex items-center justify-center text-white font-bold">
                {selectedPatient.fullName[0]}
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-bold text-neutral-900 truncate">{selectedPatient.fullName}</p>
                <p className="text-xs text-neutral-600">
                  {selectedPatient.patientNumber}
                  {selectedPatient.contactNumber ? ` • ${selectedPatient.contactNumber}` : ''}
                  {selectedPatient.age ? ` • ${selectedPatient.age}` : ''}
                </p>
              </div>
              <button type="button" onClick={clearPatient} aria-label="Choose a different patient"
                className="p-2 text-gray-400 hover:text-gray-700 rounded-lg hover:bg-white">
                <X className="h-4 w-4" />
              </button>
            </div>
          ) : (
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
              <input
                type="text"
                value={query}
                onChange={e => { setQuery(e.target.value); setRegistering(false) }}
                placeholder="Search by name, mobile number or patient ID"
                aria-label="Search for the patient"
                className={`${FIELD} pl-9`}
                autoFocus
                onFocus={() => setSearchFocused(true)}
                onBlur={() => window.setTimeout(() => setSearchFocused(false), 120)}
              />

              {searchFocused && results.length > 0 && (
                <ul role="listbox" className="absolute z-30 w-full mt-1 bg-white border border-gray-200 rounded-xl shadow-lg max-h-64 overflow-auto">
                  {results.map(patient => (
                    <li key={patient.id} role="option" aria-selected={false}
                      className="px-4 py-2.5 hover:bg-neutral-50 cursor-pointer"
                      onMouseDown={() => { setSelectedPatient(patient); setRegistering(false) }}>
                      <p className="text-sm font-medium text-gray-900">
                        {patient.fullName}
                        <span className="text-neutral-600 ml-2 text-[10px]">{patient.patientNumber}</span>
                      </p>
                      <p className="text-xs text-gray-500">{patient.contactNumber ?? 'No contact'} · {patient.age}</p>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}

          {!selectedPatient && !registering && query.trim().length > 0 && query.trim().length < 3 && (
            <p className="text-xs text-gray-400">Keep typing — at least three characters.</p>
          )}

          {!selectedPatient && !registering && searching && (
            <p className="text-xs text-gray-400">Searching…</p>
          )}

          {!selectedPatient && !registering && query.trim().length === 0 && (
            <button type="button" onClick={() => setRegistering(true)}
              className="inline-flex items-center gap-2 text-xs font-semibold text-neutral-700 hover:text-neutral-900">
              <UserPlus className="h-3.5 w-3.5" /> Register a new patient instead
            </button>
          )}
        </div>

        {/* ── new patient details, shown only when there is nobody to select ── */}
        {!selectedPatient && registering && (
          <div className="space-y-4 rounded-2xl border border-teal-100 bg-teal-50/40 p-4">
            <div className="flex items-center justify-between">
              <p className="text-xs font-bold text-teal-800 uppercase tracking-widest">New patient details</p>
              <button type="button" onClick={() => { setRegistering(false); setQuery('') }}
                className="text-xs font-semibold text-gray-500 hover:text-gray-700">
                Search again
              </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
              <div className="space-y-2">
                <label className={LABEL}>Salutation</label>
                <select value={newPatient.salutation} className={FIELD}
                  onChange={e => {
                    const sal = e.target.value
                    if (sal === 'Mr' || sal === 'Master') {
                      setNewPatient(prev => ({ ...prev, salutation: sal, gender: 'MALE' }))
                    } else if (sal === 'Mrs' || sal === 'Ms') {
                      setNewPatient(prev => ({ ...prev, salutation: sal, gender: 'FEMALE' }))
                    } else if (sal === 'Mx') {
                      setNewPatient(prev => ({ ...prev, salutation: sal, gender: 'OTHER' }))
                    } else {
                      setNewPatient(prev => ({ ...prev, salutation: sal }))
                    }
                  }}>
                  <option value="">—</option>
                  {['Mr', 'Mrs', 'Ms', 'Mx', 'Dr', 'Baby', 'Master'].map(s => <option key={s} value={s}>{s}</option>)}
                </select>
              </div>
              <div className="space-y-2 md:col-span-2">
                <label className={LABEL}>Name *</label>
                <input type="text" value={newPatient.name} placeholder="Full name" className={FIELD}
                  onChange={e => setNewPatient({ ...newPatient, name: e.target.value.replace(/[^a-zA-Z\s]/g, '') })} />
              </div>
              <div className="space-y-2">
                <label className={LABEL}>Age</label>
                <input type="text" value={newPatient.age} placeholder="Age" className={FIELD}
                  onChange={e => setNewPatient({ ...newPatient, age: e.target.value.replace(/\D/g, '') })} />
                {isBabyAgeInvalid && (
                  <p className="text-[10px] text-red-600 font-medium">Baby is only for age ≤ 5</p>
                )}
                {isMasterAgeInvalid && (
                  <p className="text-[10px] text-red-600 font-medium">Master is only for age &lt; 18</p>
                )}
                {isMrAgeInvalid && (
                  <p className="text-[10px] text-red-600 font-medium">Mr is only for age ≥ 18</p>
                )}
                {isMrsOrMsAgeInvalid && (
                  <p className="text-[10px] text-red-600 font-medium">{newPatient.salutation} is only for age ≥ 18</p>
                )}
                {isDrAgeInvalid && (
                  <p className="text-[10px] text-red-600 font-medium">Dr is only for age ≥ 21</p>
                )}
                {isMxAgeInvalid && (
                  <p className="text-[10px] text-red-600 font-medium">Mx is only for age ≥ 18</p>
                )}
              </div>
              <div className="space-y-2">
                <label className={LABEL}>Gender</label>
                <select value={newPatient.gender} className={FIELD}
                  onChange={e => {
                    const g = e.target.value
                    if (g === 'OTHER' && (newPatient.salutation === 'Mr' || newPatient.salutation === 'Master' || newPatient.salutation === 'Mrs' || newPatient.salutation === 'Ms')) {
                      setNewPatient(prev => ({ ...prev, gender: g, salutation: '' }))
                    } else {
                      setNewPatient(prev => ({ ...prev, gender: g }))
                    }
                  }}>
                  <option value="">Select</option>
                  <option value="MALE">Male</option>
                  <option value="FEMALE">Female</option>
                  <option value="OTHER">Other</option>
                </select>
              </div>
            </div>

            <div className="max-w-xs space-y-2">
              <label className={LABEL}>Contact No *</label>
              <input type="tel" maxLength={10} value={newPatient.phone} placeholder="10-digit mobile" className={FIELD}
                onChange={e => setNewPatient({ ...newPatient, phone: e.target.value.replace(/\D/g, '').slice(0, 10) })} />
              {newPatient.phone.length > 0 && newPatient.phone.length !== 10 && (
                <p className="text-xs text-amber-700">A mobile number must be exactly 10 digits.</p>
              )}
            </div>
          </div>
        )}

        {/* ── 2-4. Scheduling ────────────────────────────────────────────── */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-x-12 gap-y-6">
          <div className="space-y-2">
            <label className={LABEL}>2. Consultant</label>
            <ConsultantSearchInput
              consultants={consultants ?? []}
              value={bookingProviderId}
              onChange={setBookingProviderId}
              size="sm"
            />
          </div>

          <div className="space-y-2">
            <label className={LABEL}>3. Appointment Date</label>
            <DatePicker
              value={dateStr}
              minDate={format(new Date(), 'yyyy-MM-dd')}
              onChange={value => setDate(value ? new Date(value + 'T00:00:00') : new Date())}
              getDayProps={day => {
                const isLeave = leaveDatesSet.has(format(day, 'yyyy-MM-dd'))
                return {
                  disabled: isLeave,
                  className: isLeave ? 'bg-red-50 text-red-400 font-semibold line-through cursor-not-allowed' : undefined,
                }
              }}
              size="sm"
            />
          </div>

          <div className="space-y-2 md:col-span-2">
            <label className={LABEL}>4. Available Slot</label>
            <select
              value={selectedSlotId}
              onChange={e => setSelectedSlotId(e.target.value)}
              disabled={!bookingProviderId || isDoctorOnLeave}
              className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-2xl text-sm focus:ring-2 focus:ring-neutral-500 outline-none disabled:opacity-50 transition-all"
            >
              <option value="">{isDoctorOnLeave ? 'Doctor is on leave on this date' : 'Select Time Slot'}</option>
              {!isDoctorOnLeave && selectableSlots.map(slot => (
                <option key={slot.slotId} value={slot.slotId}>
                  {formatTime(slot.fromTime)} – {formatTime(slot.toTime)} ({slot.availableCount} available)
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      <div className="flex items-center justify-end gap-3">
        {!canBook && (
          <p className="text-xs text-gray-500 mr-auto">
            {!patientReady ? 'Choose a patient, or fill in the name and mobile number to register one.'
              : !bookingProviderId ? 'Choose a consultant.'
              : isDoctorOnLeave ? 'The doctor is on leave on this date.'
              : 'Choose a time slot.'}
          </p>
        )}
        <button
          onClick={() => navigate('/appointments')}
          className="px-6 py-2.5 text-sm font-bold text-gray-500 hover:text-gray-700 transition-colors border border-gray-200 rounded-xl"
        >
          Cancel
        </button>
        <button
          onClick={handleBook}
          disabled={!canBook || mutations.book.isPending}
          className={cn(
            'px-8 py-2.5 bg-neutral-600 text-white font-bold rounded-xl hover:bg-neutral-700',
            'shadow-lg shadow-neutral-200 disabled:opacity-50 transition-all',
          )}
        >
          {mutations.book.isPending ? 'Booking...' : selectedPatient ? 'Book Appointment' : 'Register & Book'}
        </button>
      </div>
    </div>
  )
}

function Notice({ icon, title, detail }: { icon: ReactNode; title: string; detail: string }) {
  return (
    <div className="flex items-center gap-3 rounded-xl border border-neutral-200 bg-neutral-50 px-4 py-3 text-xs">
      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-neutral-100 text-neutral-700">
        {icon}
      </div>
      <div className="min-w-0 flex-1">
        <p className="font-semibold text-neutral-900">{title}</p>
        <p className="text-neutral-500 text-[11px] mt-0.5">{detail}</p>
      </div>
    </div>
  )
}
