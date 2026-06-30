import { useState, useRef, useCallback, useEffect } from 'react'
import { useFormContext } from 'react-hook-form'
import { X, Pencil, Trash2, ZoomIn, Undo2, Redo2, Type, Eraser, Save, XCircle, Palette } from 'lucide-react'
import { cn } from '../../../lib/utils'

interface ImageEntry {
  id: string
  original: string   // base64 data URL of original upload
  annotated: string  // base64 data URL with annotations (or same as original)
  fileName: string
}

interface Props {
  fieldKey: string
  readOnly?: boolean
  hideUpload?: boolean
  hideDelete?: boolean
  /** Pre-configured images from the template builder (field.validation.images) */
  templateImages?: string[]
}

const uid = () => Math.random().toString(36).substring(2, 10)

type Tool = 'pen' | 'eraser' | 'text'

const COLORS = [
  '#ef4444', // Red
  '#f97316', // Orange
  '#f59e0b', // Amber
  '#eab308', // Yellow
  '#84cc16', // Lime
  '#22c55e', // Green
  '#10b981', // Emerald
  '#06b6d4', // Cyan
  '#3b82f6', // Blue
  '#6366f1', // Indigo
  '#8b5cf6', // Purple
  '#d946ef', // Fuchsia
  '#ec4899', // Pink
  '#78350f', // Brown
  '#64748b', // Slate/Gray
  '#ffffff', // White
  '#000000'  // Black
]

export function ImageEditorField({ fieldKey, readOnly, hideUpload, hideDelete, templateImages = [] }: Props) {
  const { setValue, watch } = useFormContext()
  const images: ImageEntry[] = watch(fieldKey) ?? []

  const fileInputRef = useRef<HTMLInputElement>(null)
  const [editingImage, setEditingImage] = useState<ImageEntry | null>(null)
  const [previewImage, setPreviewImage] = useState<ImageEntry | null>(null)
  const prevTemplateRef = useRef<string>('')

  // ── Sync template images whenever they change ─────────────────────────────
  useEffect(() => {
    // Build a fingerprint so we detect real changes (not just re-renders)
    const fingerprint = templateImages.map(s => s.substring(0, 60)).join('|')
    if (fingerprint === prevTemplateRef.current) return
    prevTemplateRef.current = fingerprint

    if (templateImages.length === 0) return

    const entries: ImageEntry[] = templateImages.map((src, i) => ({
      id: uid(),
      original: src,
      annotated: src,
      fileName: `Template Image ${i + 1}`,
    }))
    setValue(fieldKey, entries, { shouldDirty: false })
  }, [templateImages, fieldKey, setValue])

  // ── Upload handler ────────────────────────────────────────────────────────
  const handleFiles = useCallback((fileList: FileList) => {
    const maxSize = 5 * 1024 * 1024
    const acceptedFiles = Array.from(fileList).filter(f => {
      if (!f.type.startsWith('image/')) return false
      if (f.size > maxSize) return false
      return true
    })

    const promises = acceptedFiles.map(file =>
      new Promise<ImageEntry>((resolve) => {
        const reader = new FileReader()
        reader.onload = () => {
          const img = new Image()
          img.onload = () => {
            const MAX_W = 1200
            let w = img.width, h = img.height
            if (w > MAX_W) { h = Math.round((h * MAX_W) / w); w = MAX_W }
            const canvas = document.createElement('canvas')
            canvas.width = w; canvas.height = h
            canvas.getContext('2d')!.drawImage(img, 0, 0, w, h)
            const resized = canvas.toDataURL('image/jpeg', 0.85)
            resolve({ id: uid(), original: resized, annotated: resized, fileName: file.name })
          }
          img.src = reader.result as string
        }
        reader.readAsDataURL(file)
      })
    )

    Promise.all(promises).then(newImages => {
      setValue(fieldKey, [...images, ...newImages], { shouldDirty: true })
    })
  }, [images, fieldKey, setValue])

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault(); e.stopPropagation()
    if (readOnly || hideUpload) return
    handleFiles(e.dataTransfer.files)
  }, [handleFiles, readOnly, hideUpload])

  const removeImage = (id: string) => {
    setValue(fieldKey, images.filter(img => img.id !== id), { shouldDirty: true })
  }

  const handleEditorSave = (annotated: string) => {
    if (!editingImage) return
    setValue(fieldKey, images.map(img =>
      img.id === editingImage.id ? { ...img, annotated } : img
    ), { shouldDirty: true })
    setEditingImage(null)
  }

  return (
    <div className="space-y-4">
      {/* Upload zone + Gallery wrapper */}
      <div className="flex flex-wrap gap-4 items-start">
        {/* Render existing images */}
        {images.map((img, index) => (
          <div
            key={img.id}
            className="relative group rounded-xl overflow-hidden border border-slate-200 shadow-sm bg-white hover:border-slate-300 hover:shadow-md transition-all duration-300 w-40 h-32 shrink-0"
          >
            <img src={img.annotated} alt={`Image #${index + 1}`} className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105" />
            
            {/* Hover overlay with actions */}
            <div className="absolute inset-0 bg-black/40 backdrop-blur-[1px] opacity-0 group-hover:opacity-100 transition-opacity duration-200 flex items-center justify-center gap-2">
              {!readOnly && (
                <button
                  type="button"
                  onClick={() => setEditingImage(img)}
                  className="p-2 bg-white hover:bg-indigo-50 text-indigo-600 rounded-lg shadow-sm transition-all transform hover:scale-110 active:scale-95"
                  title="Edit & Annotate"
                >
                  <Pencil className="w-4 h-4" />
                </button>
              )}
              <button
                type="button"
                onClick={() => setPreviewImage(img)}
                className="p-2 bg-white hover:bg-slate-50 text-slate-700 rounded-lg shadow-sm transition-all transform hover:scale-110 active:scale-95"
                title="Preview Full Size"
              >
                <ZoomIn className="w-4 h-4" />
              </button>
              {!readOnly && !hideDelete && (
                <button
                  type="button"
                  onClick={() => removeImage(img.id)}
                  className="p-2 bg-white hover:bg-red-50 text-red-600 rounded-lg shadow-sm transition-all transform hover:scale-110 active:scale-95"
                  title="Delete Image"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              )}
            </div>

            {/* File name / label */}
            <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/80 via-black/45 to-transparent px-2.5 py-1.5">
              <p className="text-[10px] text-white font-medium truncate" title={img.fileName}>
                #{index + 1}
              </p>
            </div>

          </div>
        ))}

        {/* Upload card (only shown if not readOnly and not hideUpload) */}
        {!readOnly && !hideUpload && (
          <>
            <div
              onClick={() => fileInputRef.current?.click()}
              onDrop={handleDrop}
              onDragOver={e => { e.preventDefault(); e.stopPropagation() }}
              className={cn(
                "border-2 border-dashed border-slate-300 hover:border-indigo-400 rounded-xl cursor-pointer w-40 h-32 shrink-0",
                "bg-slate-50/30 hover:bg-indigo-50/20 transition-all duration-300 flex flex-col items-center justify-center gap-1.5 text-center group shadow-sm"
              )}
              title="Add more images"
            >
              <div className="p-2 bg-white rounded-full border border-slate-100 shadow-sm group-hover:scale-110 transition-transform duration-300">
                <svg className="w-5 h-5 text-slate-500 group-hover:text-indigo-600 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 4v16m8-8H4" />
                </svg>
              </div>
              <span className="text-xs font-semibold text-slate-600 group-hover:text-indigo-600 transition-colors">
                Add Image
              </span>
            </div>

            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              multiple
              className="hidden"
              onChange={e => { if (e.target.files) handleFiles(e.target.files); e.target.value = '' }}
            />
          </>
        )}
      </div>

      {/* Preview modal */}
      {previewImage && (
        <div className="fixed inset-0 z-[9999] bg-black/80 flex items-center justify-center p-4" style={{ marginTop: 0 }} onClick={() => setPreviewImage(null)}>
          <div className="relative max-w-4xl max-h-[90vh]" onClick={e => e.stopPropagation()}>
            <button type="button" onClick={() => setPreviewImage(null)}
              className="absolute -top-3 -right-3 p-1 bg-white rounded-full shadow-lg hover:bg-gray-100 z-10">
              <X className="w-5 h-5 text-gray-700" />
            </button>
            <img src={previewImage.annotated} alt={previewImage.fileName}
              className="max-w-full max-h-[85vh] rounded-lg shadow-2xl object-contain" />
          </div>
        </div>
      )}

      {/* Annotation editor */}
      {editingImage && (
        <AnnotationEditor image={editingImage} onSave={handleEditorSave} onCancel={() => setEditingImage(null)} />
      )}
    </div>
  )
}


// ═══════════════════════════════════════════════════════════════════════════════
// Canvas Annotation Editor
// ═══════════════════════════════════════════════════════════════════════════════
interface EditorProps {
  image: ImageEntry
  onSave: (annotated: string) => void
  onCancel: () => void
}

interface TextAnnotation {
  id: string
  text: string
  x: number
  y: number
  color: string
  fontSize: number
}

interface HistoryState {
  imageData: ImageData
  texts: TextAnnotation[]
}

function AnnotationEditor({ image, onSave, onCancel }: EditorProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const originalImageRef = useRef<HTMLImageElement | null>(null)
  const [tool, setTool] = useState<Tool>('pen')
  const [color, setColor] = useState('#ef4444')
  const [lineWidth, setLineWidth] = useState(3)
  const [isDrawing, setIsDrawing] = useState(false)
  const [history, setHistory] = useState<HistoryState[]>([])
  const [historyIdx, setHistoryIdx] = useState(-1)
  const [showColorPicker, setShowColorPicker] = useState(false)
  const [textInput, setTextInput] = useState('')
  const [textPos, setTextPos] = useState<{ x: number; y: number } | null>(null)
  const [fontSize, setFontSize] = useState(20)

  // Structured text annotations state
  const [texts, setTextsState] = useState<TextAnnotation[]>([])
  const textsRef = useRef<TextAnnotation[]>([])
  const [selectedTextId, setSelectedTextId] = useState<string | null>(null)
  const [draggedTextId, setDraggedTextId] = useState<string | null>(null)
  const [dragOffset, setDragOffset] = useState({ x: 0, y: 0 })

  const setTexts = (newTexts: TextAnnotation[] | ((prev: TextAnnotation[]) => TextAnnotation[])) => {
    if (typeof newTexts === 'function') {
      setTextsState(prev => {
        const next = newTexts(prev)
        textsRef.current = next
        return next
      })
    } else {
      textsRef.current = newTexts
      setTextsState(newTexts)
    }
  }

  // Calculate text size based on current canvas width and lineWidth
  const getFontSizeForWidth = (width: number) => {
    const baseSize = Math.max(14, Math.round((canvasRef.current?.width ?? 800) / 40))
    return Math.round(baseSize * (width / 3))
  }

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')!
    
    const loadAnnotated = () => {
      const img = new Image()
      img.onload = () => {
        const maxW = Math.min(img.width, window.innerWidth - 120)
        const maxH = Math.min(img.height, window.innerHeight - 200)
        const scale = Math.min(maxW / img.width, maxH / img.height, 1)
        canvas.width = Math.round(img.width * scale)
        canvas.height = Math.round(img.height * scale)
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
        const initial = ctx.getImageData(0, 0, canvas.width, canvas.height)
        setHistory([{ imageData: initial, texts: [] }])
        setHistoryIdx(0)
        setTexts([])
      }
      img.src = image.annotated
    }

    const origImg = new Image()
    origImg.onload = () => {
      originalImageRef.current = origImg
      loadAnnotated()
    }
    origImg.onerror = () => {
      loadAnnotated()
    }
    origImg.src = image.original
  }, [image.original, image.annotated])

  const pushHistory = useCallback((customTexts?: TextAnnotation[]) => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')!
    const snap = ctx.getImageData(0, 0, canvas.width, canvas.height)
    const nextTexts = customTexts !== undefined ? customTexts : textsRef.current
    setHistory(prev => [
      ...prev.slice(0, historyIdx + 1),
      { imageData: snap, texts: nextTexts }
    ])
    setHistoryIdx(prev => prev + 1)
  }, [historyIdx])

  const undo = () => {
    if (historyIdx <= 0) return
    const ctx = canvasRef.current?.getContext('2d')
    if (!ctx) return
    const prev = history[historyIdx - 1]
    ctx.putImageData(prev.imageData, 0, 0)
    setTexts(prev.texts)
    setHistoryIdx(i => i - 1)
    setSelectedTextId(null)
  }

  const redo = () => {
    if (historyIdx >= history.length - 1) return
    const ctx = canvasRef.current?.getContext('2d')
    if (!ctx) return
    const next = history[historyIdx + 1]
    ctx.putImageData(next.imageData, 0, 0)
    setTexts(next.texts)
    setHistoryIdx(i => i + 1)
    setSelectedTextId(null)
  }

  const getPos = (e: React.MouseEvent<HTMLCanvasElement> | MouseEvent) => {
    const canvas = canvasRef.current!
    const rect = canvas.getBoundingClientRect()
    return {
      x: (e.clientX - rect.left) * (canvas.width / rect.width),
      y: (e.clientY - rect.top) * (canvas.height / rect.height),
    }
  }

  const startDraw = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (tool === 'text') {
      setSelectedTextId(null)
      setTextPos(getPos(e))
      return
    }
    setIsDrawing(true)
    const ctx = canvasRef.current!.getContext('2d')!
    const pos = getPos(e)
    ctx.beginPath()
    ctx.moveTo(pos.x, pos.y)
    ctx.lineWidth = tool === 'eraser' ? lineWidth * 4 : lineWidth
    ctx.lineCap = 'round'; ctx.lineJoin = 'round'
    ctx.strokeStyle = tool === 'eraser' ? '#ffffff' : color
    ctx.globalCompositeOperation = tool === 'eraser' ? 'destination-out' : 'source-over'
  }

  const draw = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (!isDrawing) return
    const ctx = canvasRef.current!.getContext('2d')!
    const pos = getPos(e)
    ctx.lineTo(pos.x, pos.y)
    ctx.stroke()
  }

  const endDraw = () => {
    if (!isDrawing) return
    setIsDrawing(false)
    canvasRef.current!.getContext('2d')!.globalCompositeOperation = 'source-over'
    pushHistory()
  }

  const placeText = () => {
    if (!textPos || !textInput.trim()) { setTextPos(null); return }
    const nextTexts = [
      ...texts,
      {
        id: uid(),
        text: textInput,
        x: textPos.x,
        y: textPos.y,
        color: color,
        fontSize: fontSize
      }
    ]
    setTexts(nextTexts)
    pushHistory(nextTexts)
    setTextInput('')
    setTextPos(null)
  }

  // Handle window drag event listeners
  useEffect(() => {
    if (draggedTextId === null) return

    const handleMouseMove = (e: MouseEvent) => {
      const canvas = canvasRef.current
      if (!canvas) return
      const rect = canvas.getBoundingClientRect()
      
      const x = (e.clientX - rect.left) * (canvas.width / rect.width)
      const y = (e.clientY - rect.top) * (canvas.height / rect.height)
      
      const boundedX = Math.max(0, Math.min(canvas.width, x - dragOffset.x))
      const boundedY = Math.max(0, Math.min(canvas.height, y - dragOffset.y))
      
      setTexts(prev => prev.map(t => t.id === draggedTextId ? { ...t, x: boundedX, y: boundedY } : t))
    }

    const handleMouseUp = () => {
      setDraggedTextId(null)
      pushHistory()
    }

    window.addEventListener('mousemove', handleMouseMove)
    window.addEventListener('mouseup', handleMouseUp)
    return () => {
      window.removeEventListener('mousemove', handleMouseMove)
      window.removeEventListener('mouseup', handleMouseUp)
    }
  }, [draggedTextId, dragOffset, pushHistory])

  const handleTextMouseDown = (e: React.MouseEvent, t: TextAnnotation) => {
    setSelectedTextId(t.id)
    setDraggedTextId(t.id)
    setFontSize(t.fontSize)
    
    const canvas = canvasRef.current
    if (!canvas) return
    const rect = canvas.getBoundingClientRect()
    const x = (e.clientX - rect.left) * (canvas.width / rect.width)
    const y = (e.clientY - rect.top) * (canvas.height / rect.height)
    
    setDragOffset({ x: x - t.x, y: y - t.y })
  }

  const handleEditTextChange = (newVal: string) => {
    if (!selectedTextId) return
    setTexts(prev => prev.map(t => t.id === selectedTextId ? { ...t, text: newVal } : t))
  }

  const handleColorChange = (newColor: string) => {
    setColor(newColor)
    if (selectedTextId) {
      const nextTexts = texts.map(t => t.id === selectedTextId ? { ...t, color: newColor } : t)
      setTexts(nextTexts)
      pushHistory(nextTexts)
    }
  }

  const handleSizeChange = (newWidth: number) => {
    setLineWidth(newWidth)
    if (selectedTextId) {
      const nextTexts = texts.map(t => t.id === selectedTextId ? { ...t, fontSize: getFontSizeForWidth(newWidth) } : t)
      setTexts(nextTexts)
      pushHistory(nextTexts)
    }
  }

  const handleFontSizeChange = (newSize: number) => {
    setFontSize(newSize)
    if (selectedTextId) {
      const nextTexts = texts.map(t => t.id === selectedTextId ? { ...t, fontSize: newSize } : t)
      setTexts(nextTexts)
      pushHistory(nextTexts)
    }
  }

  const handleSave = () => {
    if (!canvasRef.current) return
    const canvas = canvasRef.current
    
    const drawTextsToContext = (ctx: CanvasRenderingContext2D) => {
      texts.forEach(t => {
        ctx.font = `bold ${t.fontSize}px Inter, sans-serif`
        ctx.fillStyle = t.color
        ctx.textAlign = 'center'
        ctx.textBaseline = 'middle'
        
        ctx.strokeStyle = '#ffffff'
        ctx.lineWidth = Math.max(2, Math.round(t.fontSize / 10))
        ctx.lineJoin = 'round'
        ctx.miterLimit = 2
        ctx.strokeText(t.text, t.x, t.y)
        
        ctx.fillText(t.text, t.x, t.y)
      })
    }

    if (originalImageRef.current) {
      const tempCanvas = document.createElement('canvas')
      tempCanvas.width = canvas.width
      tempCanvas.height = canvas.height
      const tempCtx = tempCanvas.getContext('2d')!
      tempCtx.drawImage(originalImageRef.current, 0, 0, tempCanvas.width, tempCanvas.height)
      tempCtx.drawImage(canvas, 0, 0)
      drawTextsToContext(tempCtx)
      onSave(tempCanvas.toDataURL('image/jpeg', 0.9))
    } else {
      const tempCanvas = document.createElement('canvas')
      tempCanvas.width = canvas.width
      tempCanvas.height = canvas.height
      const tempCtx = tempCanvas.getContext('2d')!
      tempCtx.drawImage(canvas, 0, 0)
      drawTextsToContext(tempCtx)
      onSave(tempCanvas.toDataURL('image/jpeg', 0.9))
    }
  }

  const toolBtn = (t: Tool, Icon: typeof Pencil, label: string) => (
    <button type="button" onClick={() => { setTool(t); setSelectedTextId(null); }}
      className={cn('flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all',
        tool === t ? 'bg-neutral-700 text-white shadow-md hover:bg-neutral-800' : 'bg-white text-gray-700 hover:bg-gray-100 border border-gray-200')}>
      <Icon className="w-3.5 h-3.5" /> {label}
    </button>
  )

  return (
    <div className="fixed inset-0 z-[9999] bg-black/80 flex flex-col items-center justify-center p-4" style={{ marginTop: 0 }}>
      {/* Toolbar - Added relative and z-50 to ensure overlays are on top of canvas */}
      <div className="relative z-50 flex items-center gap-2 mb-3 bg-white/95 backdrop-blur-sm rounded-xl px-4 py-2.5 shadow-xl border border-gray-200 flex-wrap justify-center">
        {toolBtn('pen', Pencil, 'Draw')}
        {toolBtn('eraser', Eraser, 'Eraser')}
        {toolBtn('text', Type, 'Text')}
        <div className="w-px h-6 bg-gray-300 mx-1" />
        <div className="relative">
          <button type="button" onClick={() => setShowColorPicker(!showColorPicker)}
            className="flex items-center gap-1.5 px-2 py-1.5 rounded-lg text-xs font-semibold bg-white border border-gray-200 hover:bg-gray-100">
            <div className="w-4 h-4 rounded-full border border-gray-300" style={{ backgroundColor: color }} />
            <Palette className="w-3.5 h-3.5 text-gray-500" />
          </button>
          {showColorPicker && (
            <div className="absolute top-full mt-1 left-0 bg-white rounded-lg shadow-xl border border-gray-200 p-2 flex flex-col gap-2 z-50 min-w-[180px]">
              <div className="grid grid-cols-6 gap-1.5">
                {COLORS.map(c => (
                  <button key={c} type="button" onClick={() => { handleColorChange(c); setShowColorPicker(false) }}
                    className={cn('w-6 h-6 rounded-full border-2 transition-transform hover:scale-110',
                      color === c ? 'border-blue-500 scale-110 shadow-sm' : 'border-gray-300')}
                    style={{ backgroundColor: c }}
                    title={c} />
                ))}
                
                {/* Custom Color Picker */}
                <label 
                  className={cn(
                    'w-6 h-6 rounded-full border-2 cursor-pointer flex items-center justify-center hover:scale-110 transition-transform bg-gradient-to-tr from-red-500 via-green-500 to-blue-500 relative',
                    !COLORS.includes(color) ? 'border-blue-500 scale-110 shadow-sm' : 'border-gray-300'
                  )}
                  title="Custom Color"
                >
                  <input 
                    type="color" 
                    value={COLORS.includes(color) ? '#000000' : color} 
                    onChange={e => handleColorChange(e.target.value)} 
                    className="absolute inset-0 opacity-0 cursor-pointer w-full h-full" 
                  />
                  <span className="text-[10px] text-white font-black drop-shadow-md select-none">+</span>
                </label>
              </div>
            </div>
          )}
        </div>
        <div className="flex items-center gap-1.5 ml-1">
          <span className="text-[10px] text-gray-400 font-medium">Size</span>
          <input type="range" min={1} max={12} value={lineWidth}
            onChange={e => handleSizeChange(Number(e.target.value))} className="w-16 h-1 accent-neutral-700" />
        </div>
        <div className="w-px h-6 bg-gray-300 mx-1" />
        <button type="button" onClick={undo} disabled={historyIdx <= 0}
          className="p-1.5 rounded-lg hover:bg-gray-100 disabled:opacity-30 transition-colors" title="Undo">
          <Undo2 className="w-4 h-4 text-gray-600" />
        </button>
        <button type="button" onClick={redo} disabled={historyIdx >= history.length - 1}
          className="p-1.5 rounded-lg hover:bg-gray-100 disabled:opacity-30 transition-colors" title="Redo">
          <Redo2 className="w-4 h-4 text-gray-600" />
        </button>
        <div className="w-px h-6 bg-gray-300 mx-1" />
        <button type="button" onClick={handleSave}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-neutral-700 text-white hover:bg-neutral-800 shadow-md transition-colors">
          <Save className="w-3.5 h-3.5" /> Save
        </button>
        <button type="button" onClick={onCancel}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-gray-200 text-gray-700 hover:bg-gray-300 transition-colors">
          <XCircle className="w-3.5 h-3.5" /> Cancel
        </button>
      </div>

      {/* Canvas */}
      <div className="relative bg-gray-900 rounded-xl shadow-2xl overflow-hidden">
        <img 
          src={image.original} 
          alt="background" 
          className="absolute inset-0 w-full h-full pointer-events-none rounded-xl"
          style={{ zIndex: 0 }}
        />
        <canvas ref={canvasRef}
          onMouseDown={startDraw} onMouseMove={draw} onMouseUp={endDraw} onMouseLeave={endDraw}
          className={cn('block max-w-full max-h-[70vh] rounded-xl relative',
            tool === 'pen' && 'cursor-crosshair', tool === 'eraser' && 'cursor-cell', tool === 'text' && 'cursor-crosshair')}
          style={{ zIndex: 10, background: 'transparent' }} />

        {/* SVG Text Overlay */}
        <svg 
          className="absolute inset-0 pointer-events-none" 
          viewBox={`0 0 ${canvasRef.current?.width ?? 800} ${canvasRef.current?.height ?? 600}`}
          style={{ zIndex: 20, width: '100%', height: '100%' }}
        >
          {texts.map((t) => (
            <text
              key={t.id}
              x={t.x}
              y={t.y}
              textAnchor="middle"
              dominantBaseline="central"
              fill={t.color}
              fontSize={t.fontSize}
              fontWeight="bold"
              fontFamily="Inter, sans-serif"
              onMouseDown={(e) => {
                if (tool !== 'text') return
                e.stopPropagation()
                handleTextMouseDown(e, t)
              }}
              style={{
                pointerEvents: tool === 'text' ? 'auto' : 'none',
                cursor: tool === 'text' ? 'move' : 'default',
                userSelect: 'none',
                paintOrder: 'stroke fill',
                stroke: selectedTextId === t.id && tool === 'text' ? '#3b82f6' : '#ffffff',
                strokeWidth: selectedTextId === t.id && tool === 'text' ? '4px' : '2px',
                strokeLinejoin: 'round',
              }}
            >
              {t.text}
            </text>
          ))}
        </svg>
        
        {/* Visual Target Dot Indicator */}
        {tool === 'text' && textPos && (
          <div className="absolute w-4 h-4 bg-red-500 rounded-full border-2 border-white shadow-lg -translate-x-1/2 -translate-y-1/2 pointer-events-none animate-pulse"
            style={{ 
              left: `${(textPos.x / (canvasRef.current?.width ?? 1)) * 100}%`, 
              top: `${(textPos.y / (canvasRef.current?.height ?? 1)) * 100}%` 
            }} />
        )}
      </div>

      {/* Text Input Panel - Positioned below the image */}
      {tool === 'text' && (
        <div className="mt-3 w-full max-w-md bg-white/95 backdrop-blur-sm rounded-xl p-3 shadow-xl border border-gray-200">
          {selectedTextId ? (
            <div className="flex flex-col gap-2">
              <div className="flex items-center justify-between border-b border-gray-100 pb-1.5 mb-1.5">
                <span className="text-[10px] font-semibold text-gray-500 uppercase">Edit Text Annotation</span>
                <button
                  type="button"
                  onClick={() => {
                    const nextTexts = texts.filter(t => t.id !== selectedTextId)
                    setTexts(nextTexts)
                    pushHistory(nextTexts)
                    setSelectedTextId(null)
                  }}
                  className="text-xs text-red-500 hover:text-red-700 flex items-center gap-1 font-semibold"
                >
                  <Trash2 className="w-3.5 h-3.5" /> Delete
                </button>
              </div>
              <div className="flex items-center gap-2">
                <input 
                  autoFocus 
                  value={texts.find(t => t.id === selectedTextId)?.text ?? ''} 
                  onChange={e => handleEditTextChange(e.target.value)}
                  onBlur={() => pushHistory()}
                  onKeyDown={e => { 
                    if (e.key === 'Enter') { 
                      pushHistory()
                      setSelectedTextId(null) 
                    } 
                    if (e.key === 'Escape') {
                      setSelectedTextId(null)
                    }
                  }}
                  placeholder="Edit text..." 
                  className="flex-1 px-3 py-1.5 text-sm border border-gray-300 rounded-lg outline-none focus:border-blue-500 text-black bg-white" 
                />
                <button 
                  type="button" 
                  onClick={() => {
                    pushHistory()
                    setSelectedTextId(null)
                  }}
                  className="px-3.5 py-1.5 bg-neutral-700 text-white text-xs rounded-lg font-semibold hover:bg-neutral-800 active:scale-95 transition-transform"
                >
                  Done
                </button>
              </div>
              <div className="flex items-center gap-3 mt-2 border-t border-gray-100 pt-2">
                <span className="text-[10px] font-semibold text-gray-500 uppercase shrink-0">Font Size:</span>
                <button 
                  type="button" 
                  onClick={() => handleFontSizeChange(Math.max(10, fontSize - 2))}
                  className="px-2 py-0.5 bg-gray-105 hover:bg-gray-200 border border-gray-200 rounded text-xs font-bold text-gray-700 transition-colors"
                  title="Decrease Font Size"
                >
                  A-
                </button>
                <input 
                  type="range" 
                  min={10} 
                  max={80} 
                  value={fontSize} 
                  onChange={e => handleFontSizeChange(Number(e.target.value))} 
                  className="flex-1 h-1 accent-neutral-750" 
                />
                <button 
                  type="button" 
                  onClick={() => handleFontSizeChange(Math.min(100, fontSize + 2))}
                  className="px-2 py-0.5 bg-gray-105 hover:bg-gray-200 border border-gray-200 rounded text-xs font-bold text-gray-700 transition-colors"
                  title="Increase Font Size"
                >
                  A+
                </button>
                <span className="text-xs font-semibold text-gray-750 min-w-[28px] text-right">
                  {fontSize}px
                </span>
              </div>
            </div>
          ) : !textPos ? (
            <p className="text-xs text-gray-500 text-center font-medium py-1">
              Click on the image to set the text position, or click on existing text to move/edit it
            </p>
          ) : (
            <div className="flex flex-col gap-2">
              <div className="flex items-center gap-2">
                <input 
                  autoFocus 
                  value={textInput} 
                  onChange={e => setTextInput(e.target.value)}
                  onKeyDown={e => { if (e.key === 'Enter') placeText(); if (e.key === 'Escape') setTextPos(null) }}
                  placeholder="Type text to place..." 
                  className="flex-1 px-3 py-1.5 text-sm border border-gray-300 rounded-lg outline-none focus:border-blue-500 text-black bg-white" 
                />
                <button 
                  type="button" 
                  onClick={placeText}
                  className="px-3.5 py-1.5 bg-neutral-700 text-white text-xs rounded-lg font-semibold hover:bg-neutral-800 active:scale-95 transition-transform shadow-sm"
                >
                  Place
                </button>
                <button 
                  type="button" 
                  onClick={() => setTextPos(null)}
                  className="p-1.5 text-gray-400 hover:text-red-500 rounded-lg hover:bg-gray-100 transition-colors"
                  title="Cancel Position"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
              <div className="flex items-center gap-3 border-t border-gray-100 pt-2">
                <span className="text-[10px] font-semibold text-gray-500 uppercase shrink-0">Font Size:</span>
                <button 
                  type="button" 
                  onClick={() => handleFontSizeChange(Math.max(10, fontSize - 2))}
                  className="px-2 py-0.5 bg-gray-105 hover:bg-gray-200 border border-gray-200 rounded text-xs font-bold text-gray-700 transition-colors"
                  title="Decrease Font Size"
                >
                  A-
                </button>
                <input 
                  type="range" 
                  min={10} 
                  max={80} 
                  value={fontSize} 
                  onChange={e => handleFontSizeChange(Number(e.target.value))} 
                  className="flex-1 h-1 accent-neutral-750" 
                />
                <button 
                  type="button" 
                  onClick={() => handleFontSizeChange(Math.min(100, fontSize + 2))}
                  className="px-2 py-0.5 bg-gray-105 hover:bg-gray-200 border border-gray-200 rounded text-xs font-bold text-gray-700 transition-colors"
                  title="Increase Font Size"
                >
                  A+
                </button>
                <span className="text-xs font-semibold text-gray-750 min-w-[28px] text-right">
                  {fontSize}px
                </span>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
