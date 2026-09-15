import { useState, useRef, useEffect, useCallback } from 'react'

export interface UseComboboxNavigationOptions<T> {
  items: T[]
  isOpen: boolean
  setIsOpen: (open: boolean) => void
  onSelect: (item: T) => void
  selectedIndex?: number
}

export function useComboboxNavigation<T>({
  items,
  isOpen,
  setIsOpen,
  onSelect,
  selectedIndex = -1,
}: UseComboboxNavigationOptions<T>) {
  const [highlightedIndex, setHighlightedIndex] = useState<number>(selectedIndex)
  const listRef = useRef<HTMLUListElement | HTMLDivElement>(null)

  // When dropdown opens, only highlight if there is an active selection; otherwise -1
  useEffect(() => {
    if (isOpen) {
      if (selectedIndex >= 0 && selectedIndex < items.length) {
        setHighlightedIndex(selectedIndex)
      } else {
        setHighlightedIndex(-1)
      }
    } else {
      setHighlightedIndex(-1)
    }
  }, [isOpen, items.length, selectedIndex])

  // Scroll active item into view smoothly
  useEffect(() => {
    if (isOpen && highlightedIndex >= 0 && listRef.current) {
      const parent = listRef.current
      const element = parent.children[highlightedIndex] as HTMLElement | undefined
      if (element) {
        element.scrollIntoView({ block: 'nearest' })
      }
    }
  }, [isOpen, highlightedIndex])

  const onKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (!isOpen) {
        if (e.key === 'ArrowDown' || e.key === 'ArrowUp' || e.key === 'Enter') {
          e.preventDefault()
          setIsOpen(true)
          if (items.length > 0) {
            setHighlightedIndex(
              selectedIndex >= 0 ? selectedIndex : e.key === 'ArrowUp' ? items.length - 1 : 0
            )
          }
        }
        return
      }

      switch (e.key) {
        case 'ArrowDown': {
          e.preventDefault()
          if (items.length === 0) return
          setHighlightedIndex(prev => {
            if (prev === -1) return 0
            return prev < items.length - 1 ? prev + 1 : 0
          })
          break
        }
        case 'ArrowUp': {
          e.preventDefault()
          if (items.length === 0) return
          setHighlightedIndex(prev => {
            if (prev === -1) return items.length - 1
            return prev > 0 ? prev - 1 : items.length - 1
          })
          break
        }
        case 'Enter': {
          e.preventDefault()
          if (highlightedIndex >= 0 && highlightedIndex < items.length) {
            onSelect(items[highlightedIndex])
          }
          break
        }
        case 'Escape': {
          e.preventDefault()
          setIsOpen(false)
          break
        }
        case 'Tab': {
          setIsOpen(false)
          break
        }
        case 'Home': {
          if (items.length > 0) {
            e.preventDefault()
            setHighlightedIndex(0)
          }
          break
        }
        case 'End': {
          if (items.length > 0) {
            e.preventDefault()
            setHighlightedIndex(items.length - 1)
          }
          break
        }
      }
    },
    [isOpen, items, highlightedIndex, onSelect, selectedIndex, setIsOpen]
  )

  return {
    highlightedIndex,
    setHighlightedIndex,
    onKeyDown,
    listRef,
  }
}
