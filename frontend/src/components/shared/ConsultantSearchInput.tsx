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
            "w-full outline-none transition-all text-sm border focus:border-neutral-500 focus:ring-1 focus:ring-neutral-500 transition-colors",
            open && "border-neutral-500 ring-1 ring-neutral-500",
            size === 'sm'
              ? "px-3 py-1.5 bg-white border-gray-300 rounded-lg"
              : "px-4 py-2 bg-gray-50 border-gray-300 rounded-lg"
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
        <div className="absolute z-50 w-full mt-1 bg-white border border-gray-300 rounded-lg shadow-md flex flex-col">
          {filteredConsultants.length > 0 ? (
            <ul className="max-h-60 overflow-y-auto">
              {filteredConsultants.map(c => (
                <li
                  key={c.id}
                  className={cn(
                    "px-4 py-2 hover:bg-[#C25727] hover:text-white cursor-pointer flex flex-col transition-colors text-gray-900",
                    value === c.id ? "bg-[#C25727] text-white" : ""
                  )}
                  onMouseDown={(e) => { e.preventDefault(); handleSelect(c); }}
                >
                  <span className="font-medium text-xs">
                    {c.salutation || ''} {c.firstName} {c.lastName} {c.specialisation || c.qualification ? `(${c.specialisation || c.qualification})` : ''}
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <div className="px-4 py-3 text-xs text-gray-500 text-center">No doctors found</div>
          )}
        </div>
      )}
    </div>
  )
}
