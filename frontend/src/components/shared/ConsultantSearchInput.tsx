import { useState, useRef, useEffect, useMemo } from 'react'
import type { Consultant } from '../../services/consultant/consultantApi'
import { cn } from '../../lib/utils'

interface Props {
  consultants: Consultant[]
  value: string
  onChange: (id: string) => void
  placeholder?: string
  className?: string
  size?: 'sm' | 'md'
}

export function ConsultantSearchInput({ consultants, value, onChange, placeholder = 'Choose Doctor', className, size = 'md' }: Props) {
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  const selectedConsultant = useMemo(() => consultants.find(c => c.id === value), [consultants, value])

  useEffect(() => {
    if (!open) {
      setQuery('')
    }
  }, [open])

  useEffect(() => {
    const handler = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false) }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const filteredConsultants = useMemo(() => {
    let list = consultants.filter((c: any) => c.status !== 'INACTIVE' && c.status !== 0)
    if (query) {
      const q = query.toLowerCase()
      list = list.filter(c => {
        const fullName = `${c.salutation || ''} ${c.firstName} ${c.lastName}`.toLowerCase()
        const spec = (c.specialisation || c.qualification || '').toLowerCase()
        return fullName.includes(q) || spec.includes(q)
      })
    }
    return list
  }, [consultants, query])

  const handleSelect = (c: Consultant) => {
    onChange(c.id)
    setOpen(false)
  }

  const displayValue = selectedConsultant 
    ? `${selectedConsultant.salutation || ''} ${selectedConsultant.firstName} ${selectedConsultant.lastName}`.trim() 
    : ''

  return (
    <div ref={ref} className={cn('relative', className)}>
      <div className="relative group">
        <input
          type="text"
          value={open ? query : displayValue}
          placeholder={open ? "Search..." : placeholder}
          className={cn(
            "w-full outline-none transition-all text-sm border focus:ring-2 focus:ring-blue-500",
            size === 'sm'
              ? "px-3 py-2 bg-white border-gray-200 rounded"
              : "px-4 py-3 bg-gray-50 border-gray-200 rounded-2xl"
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
        />
        <div className="absolute right-3 top-1/2 -translate-y-1/2 flex items-center gap-1">
          {value && (
            <button
              type="button"
              onMouseDown={(e) => {
                e.preventDefault()
                onChange('')
                setQuery('')
                setOpen(false)
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
        <div className="absolute z-50 w-full mt-1 bg-white border border-gray-200 rounded-xl shadow-lg flex flex-col overflow-hidden">
          {filteredConsultants.length > 0 ? (
            <ul className="max-h-60 overflow-y-auto">
              {filteredConsultants.map(c => (
                <li
                  key={c.id}
                  className={cn(
                    "px-4 py-3 hover:bg-blue-50 cursor-pointer flex flex-col border-b border-gray-50 last:border-0 transition-colors",
                    value === c.id ? "bg-blue-50/50" : ""
                  )}
                  onMouseDown={(e) => { e.preventDefault(); handleSelect(c); }}
                >
                  <span className="font-medium text-sm text-gray-900">
                    {c.salutation || ''} {c.firstName} {c.lastName} {c.specialisation || c.qualification ? `(${c.specialisation || c.qualification})` : ''}
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <div className="px-4 py-3 text-sm text-gray-500 text-center">No doctors found</div>
          )}
        </div>
      )}
    </div>
  )
}
