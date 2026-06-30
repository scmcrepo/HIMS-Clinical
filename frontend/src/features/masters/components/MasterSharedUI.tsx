import React, { useEffect } from 'react'
import { cn } from '../../../../src/lib/utils'

export const inputCls = 'w-full px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-neutral-500 focus:bg-white transition-all'
export const labelCls = 'block text-xs font-semibold text-gray-600 mb-1'

export function Field({ label, children }: { label: React.ReactNode; children: React.ReactNode }) {
  return (
    <div>
      <label className={labelCls}>{label}</label>
      {children}
    </div>
  )
}

export function FormShell({ title, onCancel, onSave, saving, canSave, children }: {
  title: string; onCancel: () => void; onSave: () => void
  saving: boolean; canSave: boolean; children: React.ReactNode
}) {
  useEffect(() => {
    const el = document.getElementById('active-form-shell')
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  }, [])

  return (
    <div id="active-form-shell" className="bg-neutral-50/60 border border-neutral-100 rounded-xl p-5 space-y-4 mb-4 animate-in slide-in-from-top-2 duration-150 scroll-mt-20">
      <h4 className="text-sm font-bold text-neutral-900">{title}</h4>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">{children}</div>
      <div className="flex gap-3 pt-1">
        <button onClick={onSave} disabled={!canSave || saving}
          className="px-5 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 disabled:opacity-50 transition-colors">
          {saving ? 'Saving…' : 'Save'}
        </button>
        <button onClick={onCancel}
          className="px-4 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-white transition-colors">
          Cancel
        </button>
      </div>
    </div>
  )
}

export function StatusBadge({ active }: { active: boolean }) {
  return (
    <span className={cn('inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold border',
      active ? 'bg-green-50 text-green-700 border-green-200' : 'bg-gray-100 text-gray-500 border-gray-200')}>
      {active ? 'Active' : 'Inactive'}
    </span>
  )
}

export function EmptyState({ label }: { label: string }) {
  return <tr><td colSpan={99} className="px-4 py-10 text-center text-gray-400 text-sm">No {label} found</td></tr>
}

export function AddButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button onClick={onClick}
      className="px-4 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 transition-colors flex items-center gap-1.5">
      <span className="text-base leading-none">+</span> {label}
    </button>
  )
}

export function Th({ children }: { children: React.ReactNode }) {
  return (
    <th className="px-4 py-3 font-semibold text-gray-600 text-xs text-left">
      {children}
    </th>
  );
}

export function Section({ title, description: _description, action, children }: { title: string; description?: string; action?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="space-y-0">
      <div className="flex items-center justify-between mb-5 border-b border-gray-150 pb-4">
        <div>
          <h3 className="text-lg font-bold text-gray-900">{title}</h3>
          {/* <p className="text-sm text-gray-500 mt-0.5">{description}</p> */}
        </div>
        {action && <div className="flex-shrink-0 ml-4">{action}</div>}
      </div>
      {children}
    </div>
  )
}

export function Table({ headers, children, className }: { headers: string[]; children: React.ReactNode; className?: string }) {
  return (
    <div className={cn("bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm", className)}>
      <table className="w-full text-sm text-left [&_td]:text-left [&_th]:text-left">
        <thead>
          <tr className="bg-gray-50 border-b border-gray-100">
            {headers.map(h => <Th key={h}>{h}</Th>)}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">{children}</tbody>
      </table>
    </div>
  )
}

export function EditBtn({ onClick }: { onClick: () => void }) {
  return (
    <button onClick={onClick} className="text-xs font-semibold text-neutral-600 hover:text-neutral-800 px-2 py-1 rounded hover:bg-neutral-50 transition-colors">
      Edit
    </button>
  )
}

export function LoadingRow() {
  return <tr><td colSpan={99} className="px-4 py-10 text-center text-gray-400 text-sm">Loading…</td></tr>
}

export function LoadingSection() {
  return <div className="text-sm text-gray-400 py-10 text-center">Loading…</div>
}

import { useState, useRef } from 'react';
import { chargeApi } from '../../../services/masters/masterApi';

export function ChargeAutocomplete({ onSelect, placeholder, cats }: { onSelect: (charge: any) => void; placeholder?: string; cats: any[] }) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<any[]>([])
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (query.trim().length === 0) {
      setResults([])
      return
    }
    const delayDebounce = setTimeout(() => {
      chargeApi.searchByName(query).then((res: any) => {
        setResults(res.filter((item: any) => item.status !== 'INACTIVE' && item.status !== 0))
      })
    }, 300)
    return () => clearTimeout(delayDebounce)
  }, [query])

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (!containerRef.current?.contains(e.target as Node)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  return (
    <div ref={containerRef} className="relative w-full">
      <input
        type="text"
        className={inputCls}
        placeholder={placeholder}
        value={query}
        onChange={e => { setQuery(e.target.value); setIsOpen(true) }}
        onFocus={() => setIsOpen(true)}
      />
      {isOpen && results.length > 0 && (
        <ul className="absolute z-[100] w-full mt-1 bg-white border border-gray-200 rounded-lg shadow-lg max-h-60 overflow-auto text-sm text-gray-700">
          {results.map(item => (
            <li
              key={item.id}
              onClick={() => {
                onSelect(item)
                setQuery('')
                setResults([])
                setIsOpen(false)
              }}
              className="px-4 py-2.5 hover:bg-neutral-50 cursor-pointer border-b border-gray-50 last:border-0 flex justify-between items-center"
            >
              <div>
                <div className="font-semibold text-gray-900">{item.name}</div>
                <div className="text-[10px] text-gray-400">
                  {cats.find(c => c.id === item.categoryId)?.name ?? 'No Category'}
                </div>
              </div>
              <span className="text-[10px] bg-blue-50 text-blue-700 px-1.5 py-0.5 rounded font-bold font-mono">
                {item.chargeType}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export function ScrollableSelect({
  value,
  onChange,
  options,
  disabled,
  placeholder = 'Select option'
}: {
  value: string;
  onChange: (val: string) => void;
  options: { value: string; label: string }[];
  disabled?: boolean;
  placeholder?: string;
}) {
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (!containerRef.current?.contains(e.target as Node)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const selectedOption = options.find(o => o.value === value)

  return (
    <div ref={containerRef} className="relative w-full">
      <button
        type="button"
        disabled={disabled}
        onClick={() => setIsOpen(!isOpen)}
        className={cn(
          inputCls,
          "flex justify-between items-center text-left",
          disabled && "opacity-50 cursor-not-allowed bg-gray-100"
        )}
      >
        <span className={selectedOption ? "text-gray-900" : "text-gray-400"}>
          {selectedOption ? selectedOption.label : placeholder}
        </span>
        <svg
          className={cn("w-4 h-4 text-gray-500 transition-transform duration-200", isOpen && "transform rotate-180")}
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {isOpen && (
        <ul className="absolute z-[100] w-full mt-1 bg-white border border-gray-200 rounded-lg shadow-lg max-h-60 overflow-y-auto text-sm text-gray-700 focus:outline-none">
          {options.map(option => (
            <li
              key={option.value}
              onClick={() => {
                onChange(option.value)
                setIsOpen(false)
              }}
              className={cn(
                "px-4 py-2.5 hover:bg-neutral-50 cursor-pointer border-b border-gray-50 last:border-0 flex justify-between items-center",
                option.value === value && "bg-neutral-50 font-semibold text-neutral-900"
              )}
            >
              {option.label}
              {option.value === value && (
                <svg className="w-4 h-4 text-neutral-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 13l4 4L19 7" />
                </svg>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export function SearchableSelect({
  value,
  onChange,
  options,
  disabled,
  placeholder = 'Select option'
}: {
  value: string;
  onChange: (val: string) => void;
  options: { value: string; label: string }[];
  disabled?: boolean;
  placeholder?: string;
}) {
  const [query, setQuery] = React.useState('')
  const [open, setOpen] = React.useState(false)
  const ref = React.useRef<HTMLDivElement>(null)

  const selectedOption = React.useMemo(() => options.find(o => o.value === value), [options, value])

  React.useEffect(() => {
    if (!open) {
      setQuery('')
    }
  }, [open])

  React.useEffect(() => {
    const handler = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false) }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const filteredOptions = React.useMemo(() => {
    if (!query) return options
    const q = query.toLowerCase()
    return options.filter(o => o.label.toLowerCase().includes(q))
  }, [options, query])

  const handleSelect = (val: string) => {
    onChange(val)
    setOpen(false)
  }

  const displayValue = selectedOption ? selectedOption.label : ''

  return (
    <div ref={ref} className="relative w-full">
      <div className="relative group">
        <input
          type="text"
          disabled={disabled}
          value={open ? query : displayValue}
          title={displayValue}
          placeholder={open ? "Search..." : placeholder}
          className={cn(
            inputCls,
            "pr-10 bg-white border border-gray-300 rounded-lg",
            open && "border-neutral-500 ring-1 ring-neutral-500",
            disabled && "opacity-50 cursor-not-allowed bg-gray-100"
          )}
          onChange={e => {
            const val = e.target.value
            setQuery(val)
            if (!val && value) {
              onChange('')
            }
            if (!open) setOpen(true)
          }}
          onFocus={() => setOpen(true)}
          onClick={() => setOpen(true)}
        />
        <div className="absolute right-3 top-1/2 -translate-y-1/2 flex items-center gap-1">
          {value && (
            <button
              type="button"
              onMouseDown={(e) => {
                e.preventDefault()
                onChange('')
                setQuery('')
                setOpen(true)
              }}
              className="p-1 text-gray-400 hover:text-gray-600 rounded-full hover:bg-gray-100 transition-colors"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          )}
          <div className="text-gray-400 pointer-events-none">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7" />
            </svg>
          </div>
        </div>
      </div>

      {open && (
        <div className="absolute z-[100] w-full mt-1 bg-white border border-gray-300 rounded-lg shadow-md flex flex-col">
          {filteredOptions.length > 0 ? (
            <ul className="max-h-60 overflow-y-auto text-sm text-gray-700">
              {filteredOptions.map(option => (
                <li
                  key={option.value}
                  title={option.label}
                  className={cn(
                    "px-4 py-2.5 hover:bg-[#C25727] hover:text-white cursor-pointer flex items-center justify-between border-b border-gray-50 last:border-0 text-gray-900 transition-colors",
                    value === option.value ? "bg-[#C25727] text-white" : ""
                  )}
                  onMouseDown={(e) => { e.preventDefault(); handleSelect(option.value); }}
                >
                  <span className="font-medium text-xs">
                    {option.label}
                  </span>
                  {value === option.value && (
                    <svg className={cn("w-4 h-4", value === option.value ? "text-white" : "text-neutral-600")} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 13l4 4L19 7" />
                    </svg>
                  )}
                </li>
              ))}
            </ul>
          ) : (
            <div className="px-4 py-3 text-xs text-gray-500 text-center">No options found</div>
          )}
        </div>
      )}
    </div>
  )
}
