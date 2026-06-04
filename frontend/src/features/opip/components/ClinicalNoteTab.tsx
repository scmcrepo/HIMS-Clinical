/**
 * ClinicalNoteTab.tsx
 * Reusable tab component for Progress Notes and Nurse Notes (IP only).
 * Renders list + "+ ADD" modal with Date, Time, Notes, Requested By.
 */
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from '../../../hooks/useToast'
import { progressNotesApi, nurseNotesApi, type ClinicalNoteResponse } from '../../../services/opip/opipApi'
import { consultantApi } from '../../../services/consultant/consultantApi'
import { formatDateTime } from '../../../lib/dateUtils'
import DatePicker from '../../../components/shared/DatePicker'
import { ConsultantSearchInput } from '../../../components/shared/ConsultantSearchInput'

interface Props {
  encounterId: string
  noteType:    'PROGRESS' | 'NURSE'
  readOnly?:   boolean
}

export function ClinicalNoteTab({ encounterId, noteType, readOnly }: Props) {
  const qc = useQueryClient()
  const [showModal, setShowModal] = useState(false)

  const api = noteType === 'PROGRESS' ? progressNotesApi : nurseNotesApi
  const label = noteType === 'PROGRESS' ? 'Progress Notes' : 'Nurse Notes'
  const emptyMsg = noteType === 'PROGRESS'
    ? 'No Progress Notes! There is no history of Progress Notes'
    : 'No Nurse Notes! There is no history of Nurse Notes'

  const { data: notes = [], isLoading } = useQuery({
    queryKey: [`${noteType.toLowerCase()}-notes`, encounterId],
    queryFn:  () => api.list(encounterId),
  })

  const invalidate = () =>
    qc.invalidateQueries({ queryKey: [`${noteType.toLowerCase()}-notes`, encounterId] })

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-bold text-gray-800">{label}</h3>
        {!readOnly && (
          <button
            onClick={() => setShowModal(true)}
            className="px-3 py-1.5 text-xs font-semibold bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors">
            + ADD {label.toUpperCase()}
          </button>
        )}
      </div>

      {isLoading ? (
        <p className="text-sm text-gray-400 text-center py-6">Loading…</p>
      ) : notes.length === 0 ? (
        <div className="bg-yellow-50 border border-yellow-200 rounded-lg px-4 py-3 text-xs text-yellow-800">
          {emptyMsg}
        </div>
      ) : (
        <div className="space-y-3">
          {[...notes].reverse().map((note, i) => (
            <NoteCard key={note.id ?? i} note={note} />
          ))}
        </div>
      )}

      {showModal && (
        <AddNoteModal
          encounterId={encounterId}
          noteType={noteType}
          label={label}
          onClose={() => setShowModal(false)}
          onSaved={() => { invalidate(); setShowModal(false) }}
        />
      )}
    </div>
  )
}

// ─── Note Card ────────────────────────────────────────────────────────────────

function NoteCard({ note }: { note: ClinicalNoteResponse }) {
  return (
    <div className="border border-gray-200 rounded-xl bg-white p-4">
      <div className="flex items-start justify-between gap-3 mb-2">
        <div className="text-xs text-gray-500">{formatDateTime(note.noteAt ?? note.createdAt)}</div>
        {note.requestedByName && (
          <span className="text-xs text-blue-600 font-medium shrink-0">
            {note.requestedByName}
          </span>
        )}
      </div>
      <p className="text-sm text-gray-700 whitespace-pre-wrap leading-relaxed">{note.notes}</p>
    </div>
  )
}

// ─── Add Note Modal ───────────────────────────────────────────────────────────

function AddNoteModal({ encounterId, noteType, label, onClose, onSaved }:
  { encounterId: string; noteType: 'PROGRESS' | 'NURSE'; label: string;
    onClose: () => void; onSaved: () => void }) {

  const api = noteType === 'PROGRESS' ? progressNotesApi : nurseNotesApi
  const now = new Date()
  const todayStr = now.toISOString().split('T')[0]
  const timeStr  = now.toTimeString().slice(0, 5)

  const [notes,          setNotes]          = useState('')
  const [date,           setDate]           = useState(todayStr)
  const [time,           setTime]           = useState(timeStr)
  const [requestedById,  setRequestedById]  = useState('')

  const { data: consultants = [] } = useQuery({
    queryKey: ['consultants'], queryFn: consultantApi.getAll,
  })

  const saveMut = useMutation({
    mutationFn: () => {
      if (!notes.trim()) throw new Error('Notes are required')
      const noteAt = date && time ? new Date(`${date}T${time}:00`).toISOString() : undefined
      return api.add(encounterId, {
        notes: notes.trim(),
        noteAt,
        requestedById: requestedById || undefined,
      })
    },
    onSuccess: () => {
      toast({ title: `${label} added`, variant: 'success' })
      onSaved()
    },
    onError: (e: Error) => toast({ title: 'Failed', description: e.message, variant: 'destructive' }),
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
          <h3 className="text-base font-bold text-gray-900">Add {label}</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">✕</button>
        </div>

        <div className="p-6 space-y-4">
          {/* Date & Time */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Date *</label>
              <DatePicker value={date} onChange={val => setDate(val || new Date().toISOString().split('T')[0])} size="sm" />
            </div>
             <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Time *</label>
              <input type="time" value={time} onChange={e => setTime(e.target.value)}
                className="w-full px-2.5 py-1.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
          </div>

          {/* Notes */}
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Notes *</label>
            <textarea rows={5} value={notes} onChange={e => setNotes(e.target.value)}
              placeholder={`Enter ${label.toLowerCase()}…`}
              className="w-full px-2.5 py-1.5 border border-gray-300 rounded-lg text-sm resize-none focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>

          {/* Requested By */}
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Requested By *</label>
            <ConsultantSearchInput
              consultants={consultants}
              value={requestedById}
              onChange={setRequestedById}
              className="w-full"
              size="sm"
            />
          </div>
        </div>

        <div className="flex justify-end gap-2 px-6 py-4 border-t border-gray-200">
          <button onClick={onClose}
            className="px-4 py-1.5 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors">
            CANCEL
          </button>
          <button
            onClick={() => saveMut.mutate()}
            disabled={saveMut.isPending || !notes.trim()}
            className="px-5 py-1.5 text-sm font-semibold bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50 transition-colors">
            {saveMut.isPending ? 'Saving…' : `ADD ${label.toUpperCase()}`}
          </button>
        </div>
      </div>
    </div>
  )
}
