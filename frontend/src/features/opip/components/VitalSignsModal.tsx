/**
 * VitalSignsModal.tsx
 * Shared modal for recording vital signs in both OP (once) and IP (multiple).
 * OP: rendered as a modal from the queue list row.
 * IP: rendered as a form in the Vital Signs tab.
 */
import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { opVitalsApi, ipVitalsApi, type VitalsPayload } from '../../../services/opip/opipApi'
import { toast } from '../../../hooks/useToast'

interface Props {
  encounterId: string
  mode:        'OP' | 'IP'
  readOnly?:   boolean
  onClose?:    () => void
  onSaved?:    () => void
}

const FIELDS: { key: keyof VitalsPayload; label: string; unit: string; step?: string }[] = [
  { key: 'weight',            label: 'Weight',           unit: 'kg',      step: '0.1' },
  { key: 'height',            label: 'Height',           unit: 'cm'                   },
  { key: 'bpSystolic',        label: 'BP Systolic',      unit: 'mmHg'                 },
  { key: 'bpDiastolic',       label: 'BP Diastolic',     unit: 'mmHg'                 },
  { key: 'pulseRate',         label: 'Pulse Rate',       unit: 'bpm'                  },
  { key: 'respiratoryRate',   label: 'Respiratory Rate', unit: 'rpm'                  },
  { key: 'temperature',       label: 'Temperature',      unit: '°F',      step: '0.1' },
  { key: 'spo2',              label: 'SpO₂',             unit: '%'                    },
]

export function VitalSignsModal({ encounterId, mode, readOnly, onClose, onSaved }: Props) {
  const qc = useQueryClient()
  const [form, setForm] = useState<Partial<VitalsPayload>>({})

  const { data: encounterData, isLoading: isQueryLoading } = useQuery({
    queryKey: ['op-vitals', encounterId],
    queryFn: () => opVitalsApi.get(encounterId),
    enabled: mode === 'OP' && !!encounterId,
  })

  useEffect(() => {
    const enc = encounterData as any
    if (mode === 'OP' && enc?.vitalData) {
      const payload: Partial<VitalsPayload> = {}
      FIELDS.forEach(f => {
        if (enc.vitalData[f.key] !== undefined && enc.vitalData[f.key] !== null) {
          payload[f.key] = enc.vitalData[f.key]
        }
      })
      if (enc.vitalData.remark !== undefined && enc.vitalData.remark !== null) {
        payload.remark = enc.vitalData.remark
      }
      setForm(payload)
    }
  }, [encounterData, mode])

  const set = (k: keyof VitalsPayload, v: string) =>
    setForm(f => ({ ...f, [k]: v === '' ? undefined : v }))

  const mutation = useMutation({
    mutationFn: (vitals: VitalsPayload) =>
      mode === 'OP'
        ? opVitalsApi.record(encounterId, vitals)
        : ipVitalsApi.add(encounterId, vitals),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['encounter', encounterId] })
      qc.invalidateQueries({ queryKey: ['op-queue'] })
      qc.invalidateQueries({ queryKey: ['op-vitals', encounterId] })
      qc.invalidateQueries({ queryKey: ['ip-vitals', encounterId] })
      toast({ title: 'Vitals recorded', variant: 'success' })
      onSaved?.()
      onClose?.()
    },
    onError: (e: Error) => toast({ title: 'Failed to save', description: e.message, variant: 'destructive' }),
  })

  const handleSave = () => {
    const filled = Object.entries(form).filter(([, v]) => v !== undefined && v !== '')
    if (filled.length === 0) {
      toast({ title: 'Enter at least one value', variant: 'destructive' })
      return
    }
    mutation.mutate(form as VitalsPayload)
  }

  const content = (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-3">
        {FIELDS.map(f => (
          <div key={f.key}>
            <label className="block text-xs font-medium text-gray-600 mb-1">
              {f.label} <span className="text-gray-400">({f.unit})</span>
            </label>
            <input
              type="number"
              step={f.step ?? '1'}
              min="0"
              value={(form[f.key] as string) ?? ''}
              onChange={e => set(f.key, e.target.value)}
              disabled={readOnly}
              placeholder={`—`}
              className="w-full px-2.5 py-1.5 border border-gray-300 rounded-lg text-sm
                         focus:outline-none focus:ring-2 focus:ring-neutral-500
                         disabled:bg-gray-50 disabled:text-gray-400"
            />
          </div>
        ))}
      </div>

      {/* Remark */}
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Remark</label>
        <textarea
          rows={2}
          value={(form.remark as string) ?? ''}
          onChange={e => set('remark', e.target.value)}
          disabled={readOnly}
          placeholder="Any additional observations…"
          className="w-full px-2.5 py-1.5 border border-gray-300 rounded-lg text-sm resize-none
                     focus:outline-none focus:ring-2 focus:ring-neutral-500
                     disabled:bg-gray-50 disabled:text-gray-400"
        />
      </div>

      {!readOnly && (
        <div className="flex justify-end gap-2 pt-1">
          {onClose && (
            <button
              onClick={onClose}
              className="px-4 py-1.5 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
            >
              CANCEL
            </button>
          )}
          <button
            onClick={handleSave}
            disabled={mutation.isPending}
            className="px-5 py-1.5 text-sm font-semibold bg-neutral-600 text-white rounded-lg hover:bg-neutral-700 disabled:opacity-50 transition-colors"
          >
            {mutation.isPending ? 'Saving…' : 'SAVE'}
          </button>
        </div>
      )}
    </div>
  )

  // OP: wrapped as a modal overlay; IP: just the form
  if (mode === 'OP' && onClose) {
    return (
      <div 
          className="fixed inset-0 z-50 flex items-center justify-center p-2 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200"
          style={{ marginTop: 0 }}
        >
        <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg p-6 space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-base font-bold text-gray-900">
              {readOnly ? 'Recorded Vital Signs' : 'Record Vital Signs'}
            </h3>
            <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl leading-none">✕</button>
          </div>
          {isQueryLoading ? (
            <div className="text-center py-8 text-sm text-gray-500">Loading vitals...</div>
          ) : (
            content
          )}
        </div>
      </div>
    )
  }

  return content
}
