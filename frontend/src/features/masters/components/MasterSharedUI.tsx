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

export function Section({ title, description, action, children }: { title: string; description: string; action?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="space-y-0">
      <div className="flex items-center justify-between mb-5 border-b border-gray-150 pb-4">
        <div>
          <h3 className="text-lg font-bold text-gray-900">{title}</h3>
          <p className="text-sm text-gray-500 mt-0.5">{description}</p>
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
