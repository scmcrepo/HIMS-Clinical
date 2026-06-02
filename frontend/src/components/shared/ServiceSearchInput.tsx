import { useState, useRef, useEffect } from 'react'
import { useCatalogSearch } from '../../hooks/catalog/useCatalog'
import { cn } from '../../lib/utils'

interface PricingTier {
  billType: string
  unitRate: number
}

interface ServiceItem {
  id: string
  name: string
  pricingTiers: PricingTier[]
}

interface Props { 
  onSelect: (item: ServiceItem) => void; 
  placeholder?: string; 
  className?: string 
}

export function ServiceSearchInput({ onSelect, placeholder = 'Search diagnostic tests...', className }: Props) {
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const { data } = useCatalogSearch(query)

  useEffect(() => {
    const handler = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false) }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  return (
    <div ref={ref} className={cn('relative', className)}>
      <div className="relative group">
        <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 group-focus-within:text-blue-500 transition-colors">
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
        </span>
        <input
          type="text" value={query} placeholder={placeholder}
          className="w-full pl-9 pr-4 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 transition-all"
          onChange={e => { setQuery(e.target.value); setOpen(true) }}
          onFocus={() => query.length >= 1 && setOpen(true)}
          aria-label="Search diagnostic services"
        />
      </div>
      {open && data?.content && data.content.length > 0 && (
        <ul className="absolute z-50 w-full mt-1 bg-white border border-gray-200 rounded-lg shadow-lg max-h-60 overflow-auto">
          {data.content.map(item => (
            <li key={item.id}
              className="px-4 py-2.5 hover:bg-blue-50 cursor-pointer flex flex-col border-b border-gray-50 last:border-0"
              onMouseDown={() => { onSelect(item); setQuery(''); setOpen(false) }}>
              <span className="font-medium text-sm text-gray-900">{item.name}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
