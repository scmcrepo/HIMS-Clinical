import React, { useEffect, useState } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import DatePicker from '../../../components/shared/DatePicker'

import { useConsultants } from '../../../hooks/consultant/useConsultant'
import { ConsultantSearchInput } from '../../../components/shared/ConsultantSearchInput'
import { cn } from '../../../lib/utils'

const schema = z.object({
  salutation: z.string().optional(),
  firstName: z.string().min(1, 'First name is required'),
  lastName: z.string().min(1, 'Last name is required'),
  gender: z.enum(['MALE', 'FEMALE', 'OTHER']),
  estimatedDateOfBirth: z.string()
    .min(1, 'Date of birth is required')
    .refine(val => {
      const date = new Date(val);
      return !isNaN(date.getTime()) && date <= new Date() && date.getFullYear() > 1900;
    }, { message: 'Invalid date of birth' }),
  contactNumber: z.string().min(1, 'Contact number is required').regex(/^\d{10}$/, 'Must be exactly 10 digits'),
  email: z.string().email('Invalid email format').optional().or(z.literal('')),
  bloodGroup: z.string().optional(),
  address: z.string().optional(),
  isClinicalTrial: z.boolean().optional(),
  primaryProviderId: z.string().optional().or(z.literal('')),
  createEncounter: z.boolean().optional(),
}).refine(data => {
  if (data.createEncounter && !data.primaryProviderId) {
    return false;
  }
  return true;
}, {
  message: "Primary Consultant is required when creating an encounter",
  path: ["primaryProviderId"]
});

export type PatientFormValues = z.infer<typeof schema>

interface Props {
  initialValues?: Partial<PatientFormValues>
  onSubmit: (data: PatientFormValues) => Promise<void>
  onCancel?: () => void
  isModal?: boolean
  isPending?: boolean
  error?: any
  submitLabel?: string
  isEdit?: boolean
  hideEncounterFields?: boolean
}

function Field({ label, id, error, children }: { label: string; id: string; error?: any; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <label htmlFor={id} className="text-[11px] font-bold text-gray-500 uppercase tracking-wider">{label}</label>
      {children}
      {error && typeof error === 'string' && (
        <p role="alert" className="text-[10px] text-red-600 mt-0.5 font-medium">{error}</p>
      )}
    </div>
  )
}

export function PatientForm({ initialValues, onSubmit, onCancel, isModal, isPending, error, submitLabel, isEdit, hideEncounterFields }: Props) {
  const { register, handleSubmit, control, setValue, watch, formState: { errors } } = useForm<PatientFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      ...initialValues,
      estimatedDateOfBirth: initialValues?.estimatedDateOfBirth || ''
    }
  })

  const watchedDob = watch('estimatedDateOfBirth')
  const watchedSalutation = watch('salutation')
  const watchedCreateEncounter = watch('createEncounter')
  const { data: consultants } = useConsultants()
  const [ageInput, setAgeInput] = useState('')

  // Sync gender with salutation
  useEffect(() => {
    if (watchedSalutation === 'Mr' || watchedSalutation === 'Master') {
      setValue('gender', 'MALE', { shouldValidate: true })
    } else if (watchedSalutation === 'Ms' || watchedSalutation === 'Mrs') {
      setValue('gender', 'FEMALE', { shouldValidate: true })
    }
  }, [watchedSalutation, setValue])

  // Sync ageInput when DOB changes (e.g. from DatePicker or initial load)
  useEffect(() => {
    if (watchedDob && watchedDob !== 'INVALID') {
      const parts = watchedDob.split('-')
      if (parts.length === 3) {
        const yyyy = parts[0]
        const mm = parts[1]
        const dd = parts[2]

        if (yyyy.length > 4) return;

        // Calculate precise age (years)
        const today = new Date()
        const birthDate = new Date(parseInt(yyyy), parseInt(mm) - 1, parseInt(dd))
        let age = today.getFullYear() - birthDate.getFullYear()
        const monthDiff = today.getMonth() - birthDate.getMonth()
        if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birthDate.getDate())) {
          age--
        }

        const formattedDate = `${dd}/${mm}/${yyyy}`

        // On initial load (ageInput empty), default to numeric age
        if (!ageInput) {
          setAgeInput(String(age))
          return
        }

        // If it was already a numeric age, keep it synced as numeric age
        if (/^\d+$/.test(ageInput)) {
          if (ageInput !== String(age)) {
            setAgeInput(String(age))
          }
        }
        // If it's a date format, keep it synced as date format
        else if (ageInput.includes('/')) {
          if (ageInput !== formattedDate) {
            setAgeInput(formattedDate)
          }
        }
      }
    }
  }, [watchedDob])

  const handleAgeInputChange = (val: string) => {
    setAgeInput(val)

    // Clear underlying value if input is empty
    if (!val.trim()) {
      setValue('estimatedDateOfBirth', '', { shouldValidate: true })
      return
    }

    if (/^\d+$/.test(val)) {
      const age = parseInt(val)
      if (age >= 0 && age < 150) {
        const date = new Date()
        date.setFullYear(date.getFullYear() - age)
        const yyyy = date.getFullYear()
        const mm = String(date.getMonth() + 1).padStart(2, '0')
        const dd = String(date.getDate()).padStart(2, '0')
        setValue('estimatedDateOfBirth', `${yyyy}-${mm}-${dd}`, { shouldValidate: true })
      } else {
        setValue('estimatedDateOfBirth', '', { shouldValidate: true })
      }
    } else {
      const match = val.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/)
      if (match) {
        const d = parseInt(match[1]), m = parseInt(match[2]) - 1, y = parseInt(match[3])
        const date = new Date(y, m, d)

        // Strict check: No future dates, no year > 9999, no year < 1900
        if (!isNaN(date.getTime()) && date <= new Date() && y >= 1900 && y <= 2100) {
          const yyyy = String(date.getFullYear()).padStart(4, '0')
          const mm = String(date.getMonth() + 1).padStart(2, '0')
          const dd = String(date.getDate()).padStart(2, '0')
          setValue('estimatedDateOfBirth', `${yyyy}-${mm}-${dd}`, { shouldValidate: true })
        } else {
          setValue('estimatedDateOfBirth', 'INVALID', { shouldValidate: true }) // Force error
        }
      } else if (val.length >= 10) {
        setValue('estimatedDateOfBirth', 'INVALID', { shouldValidate: true }) // Force error
      }
    }
  }

  const inputCls = "w-full px-4 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:ring-2 focus:ring-blue-500 outline-none transition-all"

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className={cn("space-y-4", !isModal && "bg-white border border-gray-200 rounded-3xl p-8")}>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Field label="Salutation" id="salutation">
          <select id="salutation" {...register('salutation')} className={inputCls}>
            <option value="">—</option>
            <option value="Mr">Mr</option>
            <option value="Mrs">Mrs</option>
            <option value="Ms">Ms</option>
            <option value="Dr">Dr</option>
            <option value="Baby">Baby</option>
            <option value="Master">Master</option>
          </select>
        </Field>
        <Field label="First Name *" id="firstName" error={errors.firstName?.message}>
          <input id="firstName" {...register('firstName')} className={inputCls} />
        </Field>
        <Field label="Last Name *" id="lastName" error={errors.lastName?.message}>
          <input id="lastName" {...register('lastName')} className={inputCls} />
        </Field>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <Field label="Gender *" id="gender" error={errors.gender?.message}>
          <select id="gender" {...register('gender')} className={inputCls}>
            <option value="">Select gender</option>
            <option value="MALE">Male</option>
            <option value="FEMALE">Female</option>
            <option value="OTHER">Other</option>
          </select>
        </Field>
        <Field label="Age / DOB *" id="ageInput" error={errors.estimatedDateOfBirth?.message}>
          <div className="relative">
            <input
              id="ageInput"
              value={ageInput}
              onChange={(e) => handleAgeInputChange(e.target.value)}
              placeholder="Age (e.g. 25) or DOB (DD/MM/YYYY)"
              className={cn(inputCls, !!errors.estimatedDateOfBirth && "border-red-300 bg-red-50/30")}
            />
            <div className="absolute right-2 top-1/2 -translate-y-1/2 opacity-0 pointer-events-none">
              <Controller name="estimatedDateOfBirth" control={control} render={({ field }) => <DatePicker value={field.value} onChange={field.onChange} />} />
            </div>
          </div>
        </Field>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Field label="Contact Number *" id="contactNumber" error={errors.contactNumber?.message}>
          <input id="contactNumber" {...register('contactNumber', {
            onChange: (e) => {
              e.target.value = e.target.value.replace(/\D/g, '').slice(0, 10);
            }
          })} type="tel" maxLength={10} placeholder="10-digit mobile number" className={inputCls} />
        </Field>
        <Field label="Email" id="email" error={errors.email?.message}>
          <input id="email" {...register('email')} type="email" placeholder="email@example.com" className={inputCls} />
        </Field>
        <Field label="Blood Group" id="bloodGroup" error={errors.bloodGroup?.message}>
          <input id="bloodGroup" {...register('bloodGroup')} placeholder="e.g. A+" className={inputCls} />
        </Field>
      </div>

      <Field label="Address" id="address">
        <textarea id="address" {...register('address')} rows={2} className={cn(inputCls, "resize-none")} />
      </Field>

      {!hideEncounterFields && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <Field label={`Primary Consultant ${watchedCreateEncounter ? '*' : ''}`} id="primaryProviderId" error={errors.primaryProviderId?.message}>
            <Controller
              name="primaryProviderId"
              control={control}
              render={({ field }) => (
                <ConsultantSearchInput
                  consultants={(consultants ?? []).filter((c: any) => c.status !== 'INACTIVE' && c.status !== 0)}
                  value={field.value ?? ''}
                  onChange={field.onChange}
                />
              )}
            />
          </Field>

          {!isEdit && (
            <div className="flex items-center">
              <label className="flex items-center gap-3 p-3 bg-blue-50/50 border border-blue-100 rounded-xl cursor-pointer hover:bg-blue-100/50 transition-colors w-full mt-5">
                <input type="checkbox" {...register('createEncounter')} className="w-5 h-5 rounded-lg border-blue-300 text-blue-600 focus:ring-blue-500" />
                <div className="flex flex-col">
                  <span className="text-sm font-bold text-blue-900">Check-in</span>
                </div>
              </label>
            </div>
          )}
        </div>
      )}

      {/* <label className="flex items-center gap-3 p-3 bg-gray-50 rounded-xl cursor-pointer hover:bg-gray-100 transition-colors">
        <input type="checkbox" {...register('isClinicalTrial')} className="w-5 h-5 rounded-lg border-gray-300 text-blue-600 focus:ring-blue-500" />
        <span className="text-sm font-medium text-gray-700">Enroll in clinical trial</span>
      </label> */}

      {error && (
        <div className="p-4 bg-red-50 border border-red-100 rounded-2xl">
          <p className="text-xs text-red-600 font-medium">
            {typeof error === 'string' ? error : (String(error?.response?.data?.message || error?.message || 'Action failed'))}
          </p>
        </div>
      )}

      <div className="flex justify-end gap-3 pt-2">
        {onCancel && (
          <button type="button" onClick={onCancel}
            className="px-6 py-2.5 text-sm font-bold text-gray-500 hover:text-gray-700 transition-colors border border-gray-200 rounded-xl">
            Cancel
          </button>
        )}
        <button type="submit" disabled={isPending}
          className="px-8 py-2.5 bg-blue-600 text-white font-bold rounded-xl hover:bg-blue-700 shadow-lg shadow-blue-200 disabled:opacity-50 transition-all">
          {isPending ? 'Processing…' : (submitLabel || 'Submit')}
        </button>
      </div>
    </form>
  )
}

export const PatientRegistrationForm = PatientForm;
