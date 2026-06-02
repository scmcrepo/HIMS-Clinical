import { useState, useEffect, useRef } from 'react'
import { itemApi } from '../../services/item/itemApi'
import type { InventoryItem } from '../../types/inventory'
import { cn } from '../../lib/utils'

interface Props {
  value?: string
  onSelect: (item: InventoryItem) => void
  placeholder?: string
  className?: string
  initialValue?: string
  clearOnSelect?: boolean
}

export function MedicineSearchInput({ value, onSelect, placeholder, className, initialValue, clearOnSelect }: Props) {
  const [query, setQuery] = useState(value || initialValue || '')
  const [results, setResults] = useState<InventoryItem[]>([])
  const [isOpen, setIsOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (value !== undefined) {
      setQuery(value)
    }
  }, [value])

  useEffect(() => {
    if (initialValue !== undefined && value === undefined) {
      setQuery(initialValue)
    }
  }, [initialValue, value])

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  useEffect(() => {
    if (value !== undefined && query === value) {
      return
    }
    if (initialValue !== undefined && query === initialValue && value === undefined) {
      return
    }

    const timer = setTimeout(async () => {
      if (query.length < 2) {
        setResults([])
        return
      }
      setLoading(true)
      try {
        const items = await itemApi.search(query)
        setResults(items.filter((item: any) => item.status !== 'INACTIVE' && item.status !== 0))
        setIsOpen(true)
      } catch (err) {
        console.error('Failed to search items', err)
      } finally {
        setLoading(false)
      }
    }, 300)
    return () => clearTimeout(timer)
  }, [query, value, initialValue])

  return (
    <div ref={containerRef} className={cn("relative", className)}>
      <div className="relative group">
        <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 group-focus-within:text-blue-500 transition-colors">
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
        </span>
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onFocus={() => query.length >= 2 && setIsOpen(true)}
          placeholder={placeholder || "Search medicine..."}
          className={cn(
            "w-full pl-9 pr-10 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 transition-all",
            className
          )}
        />
        {loading && (
          <div className="absolute right-3 top-1/2 -translate-y-1/2">
            <div className="w-4 h-4 border-2 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
          </div>
        )}
      </div>

      {isOpen && results.length > 0 && (
        <div className="absolute z-50 w-full mt-1 bg-white border border-gray-200 rounded-lg shadow-xl max-h-64 overflow-y-auto overflow-x-hidden">
          {results.map((item) => (
            <button
              key={item.id}
              onClick={() => {
                onSelect(item)
                if (clearOnSelect) {
                  setQuery('')
                } else {
                  setQuery(item.name)
                }
                setIsOpen(false)
              }}
              className="w-full px-4 py-3 text-left hover:bg-blue-50 border-b border-gray-50 last:border-0 transition-colors group"
            >
              <div className="flex justify-between items-start">
                <div>
                  <p className="text-sm font-semibold text-gray-900 group-hover:text-blue-700">{item.name}</p>
                  <p className="text-[10px] text-gray-400 font-mono mt-0.5">{item.hsnCode || 'NO HSN'}</p>
                </div>
                {item.taxRate > 0 && (
                  <span className="text-[10px] bg-gray-100 text-gray-600 px-1.5 py-0.5 rounded">
                    Tax {item.taxRate}%
                  </span>
                )}
              </div>
            </button>
          ))}
        </div>
      )}

      {isOpen && query.length >= 2 && results.length === 0 && !loading && (
        <div className="absolute z-50 w-full mt-1 bg-white border border-gray-200 rounded-lg shadow-xl p-4 text-center">
          <p className="text-sm text-gray-500">No medicines found matching "{query}"</p>
        </div>
      )}
    </div>
  )
}
