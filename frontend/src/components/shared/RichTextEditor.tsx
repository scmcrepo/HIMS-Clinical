import React, { useRef, useEffect, useState } from 'react'
import { Bold, Italic, Underline, Strikethrough, List, ListOrdered, Eraser } from 'lucide-react'
import { cn } from '../../lib/utils'

interface RichTextEditorProps {
  value: string
  onChange: (value: string) => void
  placeholder?: string
  className?: string
  minHeight?: string
}

export function RichTextEditor({
  value,
  onChange,
  placeholder = 'Type here...',
  className,
  minHeight = '120px',
}: RichTextEditorProps) {
  const contentRef = useRef<HTMLDivElement>(null)
  
  const [activeStates, setActiveStates] = useState({
    bold: false,
    italic: false,
    underline: false,
    strikeThrough: false,
    insertUnorderedList: false,
    insertOrderedList: false,
  })

  // Sync state from outer value to editor div (only if not focused to avoid cursor jumping)
  useEffect(() => {
    if (contentRef.current) {
      if (contentRef.current.innerHTML !== value && document.activeElement !== contentRef.current) {
        contentRef.current.innerHTML = value || ''
      }
    }
  }, [value])

  const updateActiveStates = () => {
    if (typeof document !== 'undefined') {
      setActiveStates({
        bold: document.queryCommandState('bold'),
        italic: document.queryCommandState('italic'),
        underline: document.queryCommandState('underline'),
        strikeThrough: document.queryCommandState('strikeThrough'),
        insertUnorderedList: document.queryCommandState('insertUnorderedList'),
        insertOrderedList: document.queryCommandState('insertOrderedList'),
      })
    }
  }

  const handleInput = (e: React.FormEvent<HTMLDivElement>) => {
    const html = e.currentTarget.innerHTML
    onChange(html)
    updateActiveStates()
  }

  const handleBlur = () => {
    if (contentRef.current) {
      onChange(contentRef.current.innerHTML)
    }
  }

  const execCommand = (command: string, val: string = '') => {
    document.execCommand(command, false, val)
    contentRef.current?.focus()
    if (contentRef.current) {
      onChange(contentRef.current.innerHTML)
    }
    updateActiveStates()
  }

  return (
    <div className={cn(
      "border border-gray-200 rounded-xl overflow-hidden focus-within:ring-2 focus-within:ring-neutral-400 focus-within:border-transparent bg-slate-50/30 transition-all flex flex-col w-full",
      className
    )}>
      {/* Dynamic placeholder CSS scope */}
      <style>{`
        .rich-editor:empty::before {
          content: attr(data-placeholder);
          color: #94a3b8;
          pointer-events: none;
          display: block;
        }
      `}</style>

      {/* Toolbar */}
      <div className="bg-slate-50/80 border-b border-gray-200 px-3 py-1.5 flex flex-wrap gap-1 items-center select-none">
        <button
          type="button"
          onClick={() => execCommand('bold')}
          className={cn(
            "p-1.5 rounded transition-all duration-150 text-slate-600 hover:bg-slate-200 hover:text-slate-900",
            activeStates.bold && "bg-neutral-100 text-neutral-700 hover:bg-neutral-200 hover:text-neutral-800"
          )}
          title="Bold"
        >
          <Bold className="w-4 h-4" />
        </button>
        <button
          type="button"
          onClick={() => execCommand('italic')}
          className={cn(
            "p-1.5 rounded transition-all duration-150 text-slate-600 hover:bg-slate-200 hover:text-slate-900",
            activeStates.italic && "bg-neutral-100 text-neutral-700 hover:bg-neutral-200 hover:text-neutral-800"
          )}
          title="Italic"
        >
          <Italic className="w-4 h-4" />
        </button>
        <button
          type="button"
          onClick={() => execCommand('underline')}
          className={cn(
            "p-1.5 rounded transition-all duration-150 text-slate-600 hover:bg-slate-200 hover:text-slate-900",
            activeStates.underline && "bg-neutral-100 text-neutral-700 hover:bg-neutral-200 hover:text-neutral-800"
          )}
          title="Underline"
        >
          <Underline className="w-4 h-4" />
        </button>
        <button
          type="button"
          onClick={() => execCommand('strikeThrough')}
          className={cn(
            "p-1.5 rounded transition-all duration-150 text-slate-600 hover:bg-slate-200 hover:text-slate-900",
            activeStates.strikeThrough && "bg-neutral-100 text-neutral-700 hover:bg-neutral-200 hover:text-neutral-800"
          )}
          title="Strikethrough"
        >
          <Strikethrough className="w-4 h-4" />
        </button>

        <div className="w-px h-5 bg-gray-200 mx-1" />

        <button
          type="button"
          onClick={() => execCommand('insertUnorderedList')}
          className={cn(
            "p-1.5 rounded transition-all duration-150 text-slate-600 hover:bg-slate-200 hover:text-slate-900",
            activeStates.insertUnorderedList && "bg-neutral-100 text-neutral-700 hover:bg-neutral-200 hover:text-neutral-800"
          )}
          title="Bullet List"
        >
          <List className="w-4 h-4" />
        </button>
        <button
          type="button"
          onClick={() => execCommand('insertOrderedList')}
          className={cn(
            "p-1.5 rounded transition-all duration-150 text-slate-600 hover:bg-slate-200 hover:text-slate-900",
            activeStates.insertOrderedList && "bg-neutral-100 text-neutral-700 hover:bg-neutral-200 hover:text-neutral-800"
          )}
          title="Numbered List"
        >
          <ListOrdered className="w-4 h-4" />
        </button>

        <div className="w-px h-5 bg-gray-200 mx-1" />

        <button
          type="button"
          onClick={() => execCommand('removeFormat')}
          className="p-1.5 rounded transition-all duration-150 text-slate-600 hover:bg-slate-200 hover:text-slate-900"
          title="Clear Formatting"
        >
          <Eraser className="w-4 h-4" />
        </button>
      </div>

      {/* Editor editable area */}
      <div
        ref={contentRef}
        contentEditable
        onInput={handleInput}
        onBlur={handleBlur}
        onKeyUp={updateActiveStates}
        onMouseUp={updateActiveStates}
        onFocus={updateActiveStates}
        className="rich-editor w-full px-4 py-3 text-sm focus:outline-none overflow-y-auto bg-white"
        style={{ minHeight }}
        data-placeholder={placeholder}
      />
    </div>
  )
}
