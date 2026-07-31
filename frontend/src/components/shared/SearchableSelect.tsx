import { useState, useRef, useEffect, useMemo } from 'react'
import { cn } from '../../lib/utils'

export interface SelectOption {
  value: string
  label: string
}

interface Props {
  options: SelectOption[]
  value: string
  onChange: (value: string) => void
  placeholder?: string
  className?: string
  size?: 'sm' | 'md'
  disabled?: boolean
  noOptionsMessage?: string
}

export function SearchableSelect({
  options,
  value,
  onChange,
  placeholder = 'Select option',
  className,
  size = 'md',
  disabled = false,
  noOptionsMessage = 'No options found'
}: Props) {
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  const selectedOption = useMemo(() => options.find(o => o.value === value), [options, value])

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

  const filteredOptions = useMemo(() => {
    if (query) {
      const q = query.toLowerCase()
      return options.filter(o => o.label.toLowerCase().includes(q))
    }
    return options
  }, [options, query])

  const handleSelect = (option: SelectOption) => {
    onChange(option.value)
    setOpen(false)
  }

  const displayValue = selectedOption ? selectedOption.label : ''

  return (
    <div ref={ref} className={cn('relative', className)}>
      <div className="relative group">
        <input
          type="text"
          disabled={disabled}
          value={open ? query : displayValue}
          title={displayValue}
          placeholder={open ? "Search..." : placeholder}
          className={cn(
            "w-full outline-none transition-all text-sm border focus:border-neutral-500 focus:ring-1 focus:ring-neutral-500 transition-colors",
            open && "border-neutral-500 ring-1 ring-neutral-500",
            size === 'sm'
              ? "px-3 py-1.5 bg-white border-gray-300 rounded-lg"
              : "px-4 py-2 bg-gray-50 border-gray-200 rounded-xl",
            disabled && "bg-gray-100 text-gray-500 cursor-not-allowed border-gray-200"
          )}
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
        <div className="absolute right-3 top-1/2 -translate-y-1/2 flex items-center gap-1">
          {value && !disabled && (
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
        <div className="absolute z-50 w-full mt-1 bg-white border border-gray-300 rounded-lg shadow-md flex flex-col">
          {filteredOptions.length > 0 ? (
            <ul className="max-h-60 overflow-y-auto">
              {filteredOptions.map(o => (
                <li
                  key={o.value}
                  title={o.label}
                  className={cn(
                    "px-4 py-2 hover:bg-[#C25727] hover:text-white cursor-pointer flex flex-col transition-colors text-gray-900",
                    value === o.value ? "bg-[#C25727] text-white" : ""
                  )}
                  onMouseDown={(e) => { e.preventDefault(); handleSelect(o); }}
                >
                  <span className="font-medium text-xs">
                    {o.label}
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <div className="px-4 py-3 text-xs text-gray-500 text-center">{noOptionsMessage}</div>
          )}
        </div>
      )}
    </div>
  )
}
