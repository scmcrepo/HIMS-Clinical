import { useState, useEffect } from 'react'
import { format } from 'date-fns'
import { useNavigate } from 'react-router-dom'
import { useSlotAvailability, useAppointmentMutations } from '../../../hooks/appointment/useAppointment'
import { useConsultants } from '../../../hooks/consultant/useConsultant'
import { PatientSearchInput } from '../../../components/shared/PatientSearchInput'
import { ConsultantSearchInput } from '../../../components/shared/ConsultantSearchInput'
import type { Patient } from '../../../types/patient'
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

export default function BookAppointmentPage() {
  const navigate = useNavigate()
  const [date, setDate] = useState<Date>(new Date())
  const [selectedSlotId, setSelectedSlotId] = useState<string>('')
  const [bookingProviderId, setBookingProviderId] = useState<string>('')
  const [selectedPatient, setSelectedPatient] = useState<Patient | null>(null)
  const [notes, setNotes] = useState('')
  const [activePatientTab, setActivePatientTab] = useState<'EXISTING' | 'NEW'>('NEW')
  const [newPatient, setNewPatient] = useState({
    salutation: '',
    name: '',
    gender: '',
    age: '',
    phone: ''
  })

  const dateStr = format(date, 'yyyy-MM-dd')
  const { data: consultants } = useConsultants()
  const { data: slots } = useSlotAvailability(bookingProviderId || undefined, dateStr)
  const mutations = useAppointmentMutations()

  // Sync gender with salutation for new patients
  useEffect(() => {
    const s = newPatient.salutation
    if (s === 'Mr' || s === 'Master') {
      setNewPatient(prev => ({ ...prev, gender: 'MALE' }))
    } else if (s === 'Ms' || s === 'Mrs') {
      setNewPatient(prev => ({ ...prev, gender: 'FEMALE' }))
    }
  }, [newPatient.salutation])

  const resetForm = () => {
    setSelectedPatient(null)
    setNewPatient({ salutation: '', name: '', gender: '', age: '', phone: '' })
    setSelectedSlotId('')
    setBookingProviderId('')
    setNotes('')
  }

  const handleBook = () => {
    if (!selectedSlotId || !bookingProviderId) return
    if (activePatientTab === 'EXISTING' && !selectedPatient) return
    if (activePatientTab === 'NEW' && !newPatient.name) return

    mutations.book.mutate({
      patientId: activePatientTab === 'EXISTING' ? selectedPatient?.id : undefined,
      tempPatientName: activePatientTab === 'NEW' ? newPatient.name : undefined,
      tempPatientSalutation: activePatientTab === 'NEW' ? newPatient.salutation : undefined,
      tempPatientGender: activePatientTab === 'NEW' ? newPatient.gender : undefined,
      tempPatientPhone: activePatientTab === 'NEW' ? newPatient.phone : undefined,
      tempPatientAge: activePatientTab === 'NEW' && newPatient.age ? parseInt(newPatient.age) : undefined,
      providerId: bookingProviderId,
      slotId: selectedSlotId,
      appointmentDate: dateStr,
      notes: notes,
    }, {
      onSuccess: () => {
        navigate('/appointments')
      }
    })
  }

  return (
    <div className="max-w-3xl mx-auto px-4 py-6 space-y-6">
      <div className="flex items-center justify-between mb-8 px-4">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 tracking-tight">New Appointment</h2>
          <p className="text-sm text-gray-500">Book an appointment for a patient</p>
        </div>
        <BackButton />
      </div>

      <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 space-y-6">
        {/* Patient Selection Tabs */}
        <div className="flex bg-gray-100 p-1 rounded-xl max-w-sm">
          <button
            onClick={() => { setActivePatientTab('NEW'); resetForm() }}
            className={cn(
              "flex-1 py-2 text-xs font-bold rounded-lg transition-all",
              activePatientTab === 'NEW' ? "bg-white text-blue-600 shadow-sm" : "text-gray-500 hover:text-gray-700"
            )}
          >
            New Patient
          </button>
          <button
            onClick={() => { setActivePatientTab('EXISTING'); resetForm() }}
            className={cn(
              "flex-1 py-2 text-xs font-bold rounded-lg transition-all",
              activePatientTab === 'EXISTING' ? "bg-white text-blue-600 shadow-sm" : "text-gray-500 hover:text-gray-700"
            )}
          >
            Existing Patient
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-x-12 gap-y-6">
          {activePatientTab === 'NEW' ? (
            <>
              <div className="col-span-1 md:col-span-2 grid grid-cols-1 md:grid-cols-5 gap-4">
                <div className="space-y-2 col-span-1">
                  <label className="text-xs font-bold text-gray-500 uppercase tracking-widest">1. Salutation</label>
                  <select
                    value={newPatient.salutation}
                    onChange={e => setNewPatient({ ...newPatient, salutation: e.target.value })}
                    className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:ring-2 focus:ring-blue-500 outline-none transition-all"
                  >
                    <option value="">—</option>
                    <option value="Mr">Mr</option>
                    <option value="Mrs">Mrs</option>
                    <option value="Ms">Ms</option>
                    <option value="Dr">Dr</option>
                    <option value="Baby">Baby</option>
                    <option value="Master">Master</option>
                  </select>
                </div>
                <div className="space-y-2 col-span-1 md:col-span-2">
                  <label className="text-xs font-bold text-gray-500 uppercase tracking-widest">2. Patient Name *</label>
                  <input
                    type="text"
                    value={newPatient.name}
                    onChange={e => setNewPatient({ ...newPatient, name: e.target.value })}
                    placeholder="Full Name"
                    className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:ring-2 focus:ring-blue-500 outline-none transition-all"
                  />
                </div>
                <div className="space-y-2 col-span-1">
                  <label className="text-xs font-bold text-gray-500 uppercase tracking-widest">3. Age</label>
                  <input
                    type="text"
                    value={newPatient.age}
                    onChange={e => setNewPatient({ ...newPatient, age: e.target.value.replace(/\D/g, '') })}
                    placeholder="Age"
                    className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:ring-2 focus:ring-blue-500 outline-none transition-all"
                  />
                </div>
                <div className="space-y-2 col-span-1">
                  <label className="text-xs font-bold text-gray-500 uppercase tracking-widest">4. Gender</label>
                  <select
                    value={newPatient.gender}
                    onChange={e => setNewPatient({ ...newPatient, gender: e.target.value })}
                    className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:ring-2 focus:ring-blue-500 outline-none transition-all"
                  >
                    <option value="">Select gender</option>
                    <option value="MALE">Male</option>
                    <option value="FEMALE">Female</option>
                    <option value="OTHER">Other</option>
                  </select>
                </div>
              </div>

              <div className="space-y-2">
                <label className="text-xs font-bold text-gray-500 uppercase tracking-widest">5. Contact No</label>
                <input
                  type="tel"
                  value={newPatient.phone}
                  onChange={e => {
                    const val = e.target.value.replace(/\D/g, '').slice(0, 10)
                    setNewPatient({ ...newPatient, phone: val })
                  }}
                  placeholder="10-digit mobile"
                  maxLength={10}
                  className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:ring-2 focus:ring-blue-500 outline-none transition-all"
                />
              </div>
              <div className="space-y-2">
                <label className="text-xs font-bold text-gray-500 uppercase tracking-widest">6. Consultant</label>
                <ConsultantSearchInput
                  consultants={consultants ?? []}
                  value={bookingProviderId}
                  onChange={setBookingProviderId}
                />
              </div>

              <div className="space-y-2">
                <label className="text-xs font-bold text-gray-500 uppercase tracking-widest">7. Appointment Date</label>
                <DatePicker
                  value={dateStr}
                  minDate={format(new Date(), 'yyyy-MM-dd')}
                  onChange={val => setDate(val ? new Date(val + 'T00:00:00') : new Date())}
                  size="sm"
                />
              </div>
              <div className="space-y-2">
                <label className="text-xs font-bold text-gray-500 uppercase tracking-widest">8. Available Slot</label>
                <select
                  value={selectedSlotId}
                  onChange={e => setSelectedSlotId(e.target.value)}
                  disabled={!bookingProviderId}
                  className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-2xl text-sm focus:ring-2 focus:ring-blue-500 outline-none disabled:opacity-50 transition-all"
                >
                  <option value="">Select Time Slot</option>
                  {slots?.filter(s => {
                    if (!s.isAvailable) return false
                    const isToday = dateStr === format(new Date(), 'yyyy-MM-dd')
                    if (isToday) {
                      const currentTime = format(new Date(), 'HH:mm:ss')
                      return s.toTime > currentTime
                    }
                    return true
                  }).map(s => (
                    <option key={s.slotId} value={s.slotId}>
                      {formatTime(s.fromTime)} – {formatTime(s.toTime)} ({s.availableCount} available)
                    </option>
                  ))}
                </select>
              </div>
            </>
          ) : (
            <>
              <div className="space-y-2">
                <label className="text-xs font-bold text-gray-500 uppercase tracking-widest">1. Select Patient</label>
                <PatientSearchInput
                  selectedPatient={selectedPatient}
                  onSelect={setSelectedPatient}
                  placeholder="Search by Name, ID or Mobile..."
                  className="w-full"
                />
                {selectedPatient && (
                  <div className="p-4 bg-blue-50 rounded-2xl border border-blue-100 flex items-center gap-4 animate-in slide-in-from-top-2">
                    <div className="w-10 h-10 rounded-full bg-blue-600 flex items-center justify-center text-white font-bold">
                      {selectedPatient.fullName[0]}
                    </div>
                    <div className="flex-1">
                      <p className="text-sm font-bold text-blue-900">{selectedPatient.fullName}</p>
                      <p className="text-xs text-blue-600">{selectedPatient.patientNumber} {selectedPatient.contactNumber ? `• ${selectedPatient.contactNumber}` : ''}</p>
                    </div>
                  </div>
                )}
              </div>
              <div className="space-y-2">
                <label className="text-xs font-bold text-gray-500 uppercase tracking-widest">2. Consultant</label>
                <ConsultantSearchInput
                  consultants={consultants ?? []}
                  value={bookingProviderId}
                  onChange={setBookingProviderId}
                />
              </div>

              <div className="space-y-2">
                <label className="text-xs font-bold text-gray-500 uppercase tracking-widest">3. Appointment Date</label>
                <DatePicker
                  value={dateStr}
                  minDate={format(new Date(), 'yyyy-MM-dd')}
                  onChange={val => setDate(val ? new Date(val + 'T00:00:00') : new Date())}
                  size="sm"
                />
              </div>
              <div className="space-y-2">
                <label className="text-xs font-bold text-gray-500 uppercase tracking-widest">4. Available Slot</label>
                <select
                  value={selectedSlotId}
                  onChange={e => setSelectedSlotId(e.target.value)}
                  disabled={!bookingProviderId}
                  className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-2xl text-sm focus:ring-2 focus:ring-blue-500 outline-none disabled:opacity-50 transition-all"
                >
                  <option value="">Select Time Slot</option>
                  {slots?.filter(s => {
                    if (!s.isAvailable) return false
                    const isToday = dateStr === format(new Date(), 'yyyy-MM-dd')
                    if (isToday) {
                      const currentTime = format(new Date(), 'HH:mm:ss')
                      return s.toTime > currentTime
                    }
                    return true
                  }).map(s => (
                    <option key={s.slotId} value={s.slotId}>
                      {formatTime(s.fromTime)} – {formatTime(s.toTime)} ({s.availableCount} available)
                    </option>
                  ))}
                </select>
              </div>
            </>
          )}
        </div>
      </div>

      <div className="flex justify-end gap-3">
        <button
          onClick={() => navigate('/appointments')}
          className="px-6 py-2.5 text-sm font-bold text-gray-500 hover:text-gray-700 transition-colors border border-gray-200 rounded-xl"
        >
          Cancel
        </button>
        <button
          onClick={handleBook}
          disabled={(!selectedPatient && activePatientTab === 'EXISTING') || (activePatientTab === 'NEW' && (!newPatient.name || newPatient.phone.length !== 10)) || !bookingProviderId || !selectedSlotId || mutations.book.isPending}
          className="px-8 py-2.5 bg-blue-600 text-white font-bold rounded-xl hover:bg-blue-700 shadow-lg shadow-blue-200 disabled:opacity-50 transition-all"
        >
          {mutations.book.isPending ? 'Booking...' : 'Book Appointment'}
        </button>
      </div>
    </div>
  )
}
