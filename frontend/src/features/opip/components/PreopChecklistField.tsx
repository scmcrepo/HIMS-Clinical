import { Controller, useFormContext } from 'react-hook-form'
import { cn } from '../../../lib/utils'

interface Props {
  fieldKey: string
  readOnly?: boolean
}

const CHECKLIST_ITEMS = [
  { key: 'consent',         label: 'Informed consent obtained and signed' },
  { key: 'site_marked',     label: 'Operation site marked by surgeon' },
  { key: 'blood_group',     label: 'Blood group and cross-match sent' },
  { key: 'blood_available', label: 'Blood available / arranged' },
  { key: 'pre_op_bloods',   label: 'Pre-op bloods reviewed (Hb, renal, coag)' },
  { key: 'anaes_review',    label: 'Anaesthetic review completed' },
  { key: 'fitness_letter',  label: 'Fitness for surgery confirmed' },
  { key: 'npo_status',      label: 'Patient NBM (nil by mouth) confirmed' },
  { key: 'anticoag_stopped',label: 'Anticoagulants / antiplatelets stopped (if applicable)' },
  { key: 'abx_given',       label: 'Prophylactic antibiotics prescribed / given' },
  { key: 'vte_prophylaxis', label: 'VTE prophylaxis plan documented' },
  { key: 'implant_checked', label: 'Implant / prosthesis confirmed available in theatre' },
  { key: 'imaging_available',label: 'Pre-op imaging in theatre / available' },
  { key: 'allergies_noted', label: 'Allergies checked and documented on anaesthetic chart' },
  { key: 'escort_arranged', label: 'Post-op escort / transport arranged' },
  { key: 'icu_bed',         label: 'HDU / ICU bed booked if required' },
]

/**
 * PREOP_CHECKLIST field renderer.
 * A safety checklist with checkbox per item.
 * Stored as: { [fieldKey]: { [key]: boolean } }
 */
export function PreopChecklistField({ fieldKey, readOnly }: Props) {
  const { control } = useFormContext()

  return (
    <Controller name={fieldKey} control={control} defaultValue={{}}
      render={({ field }) => {
        const data: Record<string, boolean> = (field.value as Record<string, boolean>) ?? {}
        const checkedCount = CHECKLIST_ITEMS.filter(i => data[i.key]).length
        const allChecked = checkedCount === CHECKLIST_ITEMS.length

        const toggle = (key: string) => {
          if (readOnly) return
          field.onChange({ ...data, [key]: !data[key] })
        }

        return (
          <div className="space-y-1">
            {/* Progress bar */}
            <div className="flex items-center gap-3 mb-3">
              <div className="flex-1 h-2 bg-gray-200 rounded-full overflow-hidden">
                <div
                  className={cn(
                    'h-full rounded-full transition-all duration-300',
                    allChecked ? 'bg-green-500' : checkedCount > 0 ? 'bg-amber-400' : 'bg-gray-300'
                  )}
                  style={{ width: `${(checkedCount / CHECKLIST_ITEMS.length) * 100}%` }}
                />
              </div>
              <span className={cn(
                'text-xs font-bold',
                allChecked ? 'text-green-600' : 'text-amber-600'
              )}>
                {checkedCount}/{CHECKLIST_ITEMS.length}
                {allChecked ? ' ✓ Complete' : ' pending'}
              </span>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-1">
              {CHECKLIST_ITEMS.map(item => {
                const checked = !!data[item.key]
                return (
                  <label key={item.key}
                    className={cn(
                      'flex items-start gap-2.5 px-3 py-2 rounded-lg border text-xs cursor-pointer transition-colors',
                      checked
                        ? 'bg-green-50 border-green-300 text-green-800'
                        : 'bg-white border-gray-200 text-gray-700 hover:border-gray-300',
                      readOnly && 'cursor-not-allowed'
                    )}>
                    <div className={cn(
                      'mt-0.5 w-4 h-4 rounded border-2 flex items-center justify-center shrink-0 transition-colors',
                      checked ? 'bg-green-500 border-green-500' : 'border-gray-300 bg-white'
                    )}>
                      {checked && (
                        <svg className="w-3 h-3 text-white" fill="none" viewBox="0 0 12 12">
                          <path d="M2 6l3 3 5-5" stroke="currentColor" strokeWidth="2"
                            strokeLinecap="round" strokeLinejoin="round"/>
                        </svg>
                      )}
                    </div>
                    <input type="checkbox" className="hidden" checked={checked}
                      disabled={readOnly} onChange={() => toggle(item.key)} />
                    <span className="leading-snug">{item.label}</span>
                  </label>
                )
              })}
            </div>

            {!allChecked && !readOnly && (
              <p className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 mt-2">
                ⚠ Complete all checklist items before sending patient to theatre.
              </p>
            )}
          </div>
        )
      }}
    />
  )
}
