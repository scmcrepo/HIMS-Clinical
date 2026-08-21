import type { ReactNode } from 'react'
import { cn } from '../../../lib/utils'

/**
 * Form primitives shared by the seven stage forms (WO-020 / ID-007).
 *
 * Extracted because seven forms sharing seven copies of the same input class
 * string drift apart within a release. Styling matches the rest of the app —
 * neutral palette, rounded-lg controls — so the desk does not look like a
 * different product from the billing screen next to it.
 */

export const inputCls =
  'w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500 disabled:bg-gray-50 disabled:text-gray-400'

export const labelCls = 'block text-xs font-medium text-gray-700 mb-1'

export function Field({
  label,
  required,
  hint,
  error,
  children,
}: {
  label: string
  required?: boolean
  hint?: string
  error?: string | null
  children: ReactNode
}) {
  return (
    <div>
      <label className={labelCls}>
        {label}
        {required && <span className="text-red-500"> *</span>}
      </label>
      {children}
      {/* Errors replace hints rather than stacking — two lines of guidance
          under one control is one line too many at a busy desk. */}
      {error ? (
        <p className="text-xs text-red-600 mt-1">{error}</p>
      ) : hint ? (
        <p className="text-xs text-gray-400 mt-1">{hint}</p>
      ) : null}
    </div>
  )
}

/** Rupee input. Displays rupees, reports paise, so no component does its own maths. */
export function AmountInput({
  valuePaise,
  onChangePaise,
  placeholder = '0.00',
  disabled,
  ariaLabel,
}: {
  valuePaise: number | null
  onChangePaise: (paise: number | null) => void
  placeholder?: string
  disabled?: boolean
  ariaLabel?: string
}) {
  return (
    <div className="relative">
      <span className="absolute left-3 top-1/2 -translate-y-1/2 text-sm text-gray-400">₹</span>
      <input
        type="number"
        step="0.01"
        min={0}
        disabled={disabled}
        aria-label={ariaLabel}
        value={valuePaise == null || valuePaise === 0 ? '' : valuePaise / 100}
        onChange={e => {
          const v = e.target.value
          if (v === '' || v === null) {
            onChangePaise(null)
          } else {
            const num = Number(v)
            onChangePaise(isNaN(num) ? null : Math.round(num * 100))
          }
        }}
        placeholder={placeholder}
        className={cn(inputCls, 'pl-7')}
      />
    </div>
  )
}

export function StageHeader({
  title,
  description,
  savedAt,
  action,
}: {
  title: string
  description: string
  savedAt?: string | null
  action?: ReactNode
}) {
  return (
    <div className="flex items-start justify-between gap-4 pb-4 border-b border-gray-100">
      <div>
        <h3 className="text-sm font-semibold text-gray-900">{title}</h3>
        <p className="text-xs text-gray-500 mt-0.5">{description}</p>
        {savedAt && (
          <p className="text-xs text-gray-400 mt-1">
            Last saved {new Date(savedAt).toLocaleString('en-IN')}
          </p>
        )}
      </div>
      {action}
    </div>
  )
}

export function SaveBar({
  onSave,
  saving,
  disabled,
  error,
  label = 'Save',
}: {
  onSave: () => void
  saving?: boolean
  disabled?: boolean
  error?: string | null
  label?: string
}) {
  return (
    <div className="flex items-center gap-3 pt-4 border-t border-gray-100">
      <button
        onClick={onSave}
        disabled={saving || disabled}
        className="px-5 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 disabled:opacity-50 transition-colors"
      >
        {saving ? 'Saving…' : label}
      </button>
      {/* aria-live so a validation failure is announced, not just coloured. */}
      {error && (
        <p className="text-xs text-red-600" aria-live="polite">
          {error}
        </p>
      )}
    </div>
  )
}

/**
 * Warning banner. Amber for "you should know this", red for "this will block
 * you". Deliberately not a toast: a lapsed card is a standing fact about the
 * claim, and a toast disappears before the clerk finishes reading the form.
 */
export function Banner({
  tone = 'warning',
  children,
}: {
  tone?: 'warning' | 'danger' | 'info'
  children: ReactNode
}) {
  const tones = {
    warning: 'bg-amber-50 border-amber-200 text-amber-800',
    danger: 'bg-red-50 border-red-200 text-red-800',
    info: 'bg-blue-50 border-blue-200 text-blue-800',
  }
  return (
    <div className={cn('rounded-lg border px-3 py-2 text-xs', tones[tone])} role="status">
      {children}
    </div>
  )
}
