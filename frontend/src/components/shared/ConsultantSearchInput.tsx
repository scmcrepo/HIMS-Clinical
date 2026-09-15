import { useState, useRef, useEffect, useMemo } from 'react'
import type { Consultant } from '../../services/consultant/consultantApi'
import { cn } from '../../lib/utils'
import { useComboboxNavigation } from '../../hooks/useComboboxNavigation'

interface Props {
  consultants: Consultant[]
  value: string
  onChange: (id: string) => void
  placeholder?: string
  className?: string
  size?: 'sm' | 'md'
  disabled?: boolean
}

export function ConsultantSearchInput({ consultants, value, onChange, placeholder = 'Choose Doctor', className, size = 'md', disabled = false }: Props) {
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

  const selectedIndex = useMemo(
    () => filteredConsultants.findIndex(c => c.id === value),
    [filteredConsultants, value]
  )

  const handleSelect = (c: Consultant) => {
    onChange(c.id)
    setOpen(false)
  }

  const { highlightedIndex, setHighlightedIndex, onKeyDown, listRef } = useComboboxNavigation({
    items: filteredConsultants,
    isOpen: open,
    setIsOpen: setOpen,
    onSelect: handleSelect,
    selectedIndex,
  })

  const displayValue = selectedConsultant 
    ? `${selectedConsultant.salutation || ''} ${selectedConsultant.firstName} ${selectedConsultant.lastName} ${selectedConsultant.specialisation || selectedConsultant.qualification ? `(${selectedConsultant.specialisation || selectedConsultant.qualification})` : ''}`.trim() 
    : ''

  return (
    <div ref={ref} className={cn('relative', className)}>
      <div className="relative group">
        <input
          type="text"
          role="combobox"
          aria-expanded={open}
          aria-autocomplete="list"
          aria-haspopup="listbox"
          disabled={disabled}
          value={open ? query : displayValue}
          title={displayValue}
          placeholder={open ? "Search..." : placeholder}
          className={cn(
            "w-full outline-none text-sm border focus:border-neutral-500 focus:ring-1 focus:ring-neutral-500 transition-colors",
            open ? "border-neutral-500 ring-1 ring-neutral-500" : "border-gray-200",
            size === 'sm'
              ? "pl-3 pr-9 py-1.5 bg-white rounded-lg"
              : "pl-3 pr-9 py-1.5 bg-white rounded-lg",
            disabled && "bg-gray-100 text-gray-500 cursor-not-allowed border-gray-200"
          )}
          onKeyDown={onKeyDown}
          onChange={e => {
            if (disabled) return
            const val = e.target.value
            setQuery(val)
            if (!val && value) {
              onChange('')
            }
            if (!open) setOpen(true)
          }}
          onFocus={() => !disabled && setOpen(true)}
          onClick={() => !disabled && setOpen(true)}
        />
        <div className="absolute right-2.5 top-1/2 -translate-y-1/2 flex items-center gap-1">
          {value && !disabled && (
            <button
              type="button"
              tabIndex={-1}
              onMouseDown={(e) => {
                e.preventDefault()
                onChange('')
                setQuery('')
                setOpen(true)
              }}
              className="p-1 text-gray-400 hover:text-gray-600 rounded-full hover:bg-gray-100 transition-colors"
              aria-label="Clear consultant selection"
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
            <ul ref={listRef as React.RefObject<HTMLUListElement>} role="listbox" className="max-h-60 overflow-y-auto">
              {filteredConsultants.map((c, idx) => {
                const fullName = `${c.salutation || ''} ${c.firstName} ${c.lastName} ${c.specialisation || c.qualification ? `(${c.specialisation || c.qualification})` : ''}`.trim()
                const isSelected = value === c.id
                const isHighlighted = idx === highlightedIndex
                return (
                  <li
                    key={c.id}
                    role="option"
                    aria-selected={isSelected}
                    title={fullName}
                    className={cn(
                      "px-4 py-2 cursor-pointer flex items-center justify-between transition-colors text-xs",
                      isSelected && isHighlighted
                        ? "bg-neutral-700 text-white font-medium"
                        : isSelected
                        ? "bg-neutral-600 text-white font-medium"
                        : isHighlighted
                        ? "bg-neutral-100 text-neutral-900 font-medium"
                        : "text-gray-900 hover:bg-neutral-100"
                    )}
                    onMouseMove={() => {
                      if (highlightedIndex !== idx) setHighlightedIndex(idx)
                    }}
                    onMouseDown={(e) => { e.preventDefault(); handleSelect(c); }}
                  >
                    <span className="truncate">
                      {fullName}
                    </span>
                    {isSelected && (
                      <svg className="w-3.5 h-3.5 text-white flex-shrink-0 ml-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M5 13l4 4L19 7" />
                      </svg>
                    )}
                  </li>
                )
              })}
            </ul>
          ) : (
            <div className="px-4 py-3 text-xs text-gray-500 text-center">No doctors found</div>
          )}
        </div>
      )}
    </div>
  )
}

