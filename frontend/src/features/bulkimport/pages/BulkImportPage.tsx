import { useState, useRef } from 'react'
import { useMutation } from '@tanstack/react-query'
import api from '../../../lib/axios'
import type { ApiResponse } from '../../../types/api'
import { toast } from '../../../hooks/useToast'
import { cn } from '../../../lib/utils'
import {
  Bed, Tag, User, Package, Handshake, UsersRound, UserCog, Users, Building2,
  LayoutList, Microscope, Coins, TestTube, FileText, Download
} from 'lucide-react'

interface ImportResult {
  entityType: string; totalRows: number; createdCount: number
  skippedCount: number; errorCount: number; errors: string[]
}

const ENTITY_TYPES = [
  { value: 'bed',                 label: 'Beds',                 icon: Bed },
  { value: 'bed_type',            label: 'Bed Types / Room Categories', icon: Tag },
  { value: 'patient',             label: 'Patients',             icon: User },
  { value: 'item',                label: 'Items',      icon: Package },
  // { value: 'referral',            label: 'Referrals',            icon: Link },
  { value: 'payor',               label: 'Payors / TPA',         icon: Handshake },
  { value: 'user',                label: 'Users',                icon: UsersRound },
  { value: 'consultant',          label: 'Consultants',          icon: UserCog },
  { value: 'staff',               label: 'Staff',                icon: Users },
  { value: 'department',          label: 'Departments',          icon: Building2 },
  // { value: 'molecule',            label: 'Molecules',            icon: Dna },
  { value: 'category',            label: 'Categories',           icon: LayoutList },
  { value: 'diagnostic_template', label: 'Diagnostic Templates', icon: Microscope },
  // { value: 'stock',               label: 'Opening Stock',        icon: Download },
  { value: 'charge',              label: 'Service Charges',      icon: Coins },
  { value: 'lab_template_detail', label: 'Lab Template Details', icon: TestTube },
]

export default function BulkImportPage() {
  const [entityType, setEntityType]   = useState('patient')
  const [file, setFile]               = useState<File | null>(null)
  const [result, setResult]           = useState<ImportResult | null>(null)
  const fileInputRef                  = useRef<HTMLInputElement>(null)

  const importMutation = useMutation({
    mutationFn: async () => {
      if (!file) throw new Error('Please select a CSV file')
      const form = new FormData()
      form.append('file', file)
      const res = await api.post<ApiResponse<ImportResult>>(`/bulk-upload/${entityType}`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      return res.data.data!
    },
    onSuccess: (data) => {
      setResult(data)
      setFile(null)
      if (fileInputRef.current) fileInputRef.current.value = ''
      const msg = `${data.createdCount} created, ${data.skippedCount} skipped, ${data.errorCount} errors`
      if (data.errorCount === 0) {
        toast({ title: 'Import complete', description: msg, variant: 'success' })
      } else {
        toast({ title: 'Import completed with errors', description: msg, variant: 'destructive' })
      }
    },
    onError: (e: Error) => toast({ title: 'Import failed', description: e.message, variant: 'destructive' }),
  })

  const downloadTemplate = async () => {
    try {
      const res = await api.get<ApiResponse<string[]>>(`/bulk-upload/${entityType}/template`)
      const headers = res.data.data ?? []
      const csv = headers.join(',') + '\n'
      const blob = new Blob([csv], { type: 'text/csv' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${entityType}_template.csv`
      a.click()
      URL.revokeObjectURL(url)
    } catch {
      toast({ title: 'Could not download template', variant: 'destructive' })
    }
  }

  const selectedEntity = ENTITY_TYPES.find(e => e.value === entityType)

  return (
    <div className="space-y-5 max-w-3xl">
      <div>
        <h2 className="text-xl font-bold text-gray-900">Bulk Import</h2>
        <p className="text-sm text-gray-500 mt-0.5">
          Upload a CSV file to import multiple records at once. Maximum 50,000 rows per import.
        </p>
      </div>

      <div className="bg-white border border-gray-200 rounded-xl p-6 space-y-5">
        {/* Entity type selector */}
        <div>
          <label className="block text-xs font-medium text-gray-700 mb-2">Entity Type</label>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
            {ENTITY_TYPES.map(et => {
              const Icon = et.icon
              return (
                <button key={et.value}
                  onClick={() => { setEntityType(et.value); setResult(null) }}
                  className={cn(
                    'flex items-center gap-2 px-3 py-2 rounded-lg border text-xs font-medium transition-colors',
                    entityType === et.value
                      ? 'bg-neutral-100 border-neutral-400 text-neutral-800'
                      : 'border-gray-200 text-gray-600 hover:bg-gray-50'
                  )}
                  aria-pressed={entityType === et.value}>
                  <Icon size={14} className="shrink-0 text-neutral-500" aria-hidden="true" />
                  <span className="truncate">{et.label}</span>
                </button>
              )
            })}
          </div>
        </div>

        {/* Download template + file upload */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <p className="text-sm font-medium text-gray-700">
              Upload CSV for: <span className="text-neutral-700">{selectedEntity?.label}</span>
            </p>
            <button onClick={downloadTemplate}
              className="text-xs text-neutral-600 hover:text-neutral-800 font-medium flex items-center gap-1">
              <Download size={14} className="shrink-0 text-neutral-500" /> Download template
            </button>
          </div>

          <label
            htmlFor="csv-upload"
            className={cn(
              'block border-2 border-dashed rounded-xl p-8 text-center cursor-pointer transition-colors',
              file ? 'border-neutral-300 bg-neutral-50' : 'border-gray-200 hover:border-neutral-300 hover:bg-gray-50'
            )}>
            <input
              id="csv-upload" type="file" accept=".csv,.CSV"
              ref={fileInputRef}
              className="sr-only"
              onChange={e => {
                setFile(e.target.files?.[0] ?? null)
                setResult(null)
              }}
              aria-label="Select CSV file to import"
            />
            {file ? (
              <div className="flex flex-col items-center justify-center">
                <p className="text-sm font-medium text-neutral-700 flex items-center gap-1.5">
                  <FileText size={16} className="text-neutral-500" />
                  {file.name}
                </p>
                <p className="text-xs text-neutral-500 mt-1">{(file.size / 1024).toFixed(1)} KB — ready to import</p>
              </div>
            ) : (
              <div>
                <p className="text-sm text-gray-500">Click to select a CSV file, or drag and drop</p>
                <p className="text-xs text-gray-400 mt-1">CSV only · Max 50,000 rows</p>
              </div>
            )}
          </label>
        </div>

        <button
          onClick={() => importMutation.mutate()}
          disabled={!file || importMutation.isPending}
          className="w-full py-2.5 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 disabled:opacity-50 transition-colors"
        >
          {importMutation.isPending ? 'Importing…' : 'Import CSV'}
        </button>
      </div>

      {/* CSV Template Guide */}
      <div className="bg-white border border-gray-200 rounded-xl p-5 space-y-3">
        <h3 className="text-sm font-bold text-gray-900">CSV Header Guide</h3>
        {entityType === 'bed' ? (
          <div className="space-y-2 text-xs">
            <p className="text-gray-500">The CSV file for importing Beds must contain these columns:</p>
            <ul className="list-disc pl-5 space-y-1 text-gray-600">
              <li><strong className="text-gray-800">Bed No</strong>: Unique identifier or name of the physical bed (e.g., <code className="bg-gray-100 px-1 rounded text-red-600">B-101</code>).</li>
              <li><strong className="text-gray-800">Bed Type</strong>: Name of the room category (e.g., <code className="bg-gray-100 px-1 rounded text-red-600">ICU</code> or <code className="bg-gray-100 px-1 rounded text-red-600">General Ward</code>). If multiple categories are specified separated by semicolons (e.g. <code className="bg-gray-100 px-1 rounded text-red-600">General Ward; ICU</code>), the first category is automatically linked.</li>
            </ul>
          </div>
        ) : entityType === 'bed_type' ? (
          <div className="space-y-2 text-xs">
            <p className="text-gray-500">The CSV file for importing Bed Types must contain these columns:</p>
            <ul className="list-disc pl-5 space-y-1 text-gray-600">
              <li><strong className="text-gray-800">Bed Type</strong>: Name of the room category to create/update (e.g., <code className="bg-gray-100 px-1 rounded text-red-600">Deluxe Room</code>).</li>
              <li><strong className="text-gray-800">Charge Name</strong>: Optional. The exact name of the service charge to link for automatic billing (e.g., <code className="bg-gray-100 px-1 rounded text-red-600">Deluxe Bed Charge</code>). If the category already exists, its linked charge will be updated.</li>
            </ul>
          </div>
        ) : entityType === 'item' ? (
          <div className="space-y-2 text-xs">
            <p className="text-gray-500">The CSV file for importing Inventory Items must contain these columns:</p>
            <ul className="list-disc pl-5 space-y-1 text-gray-600">
              <li><strong className="text-gray-800">Item Name</strong>: Name of the inventory item/medicine (e.g., <code className="bg-gray-100 px-1 rounded text-red-600">Paracetamol 500mg</code>).</li>
              <li><strong className="text-gray-800">CIMS Id</strong>: Optional. CIMS identifier.</li>
              <li><strong className="text-gray-800">Batch Required</strong>: Whether a batch number is required (<code className="bg-gray-100 px-1 rounded text-red-600">true</code> or <code className="bg-gray-100 px-1 rounded text-red-600">false</code>).</li>
              <li><strong className="text-gray-800">Base Unit</strong>: Standard unit of measure (e.g., <code className="bg-gray-100 px-1 rounded text-red-600">Tablet</code>, <code className="bg-gray-100 px-1 rounded text-red-600">Bottle</code>).</li>
              <li><strong className="text-gray-800">Category</strong>: Category name to classify the item (e.g., <code className="bg-gray-100 px-1 rounded text-red-600">Analgesics</code>).</li>
            </ul>
          </div>
        ) : entityType === 'payor' ? (
          <div className="space-y-2 text-xs">
            <p className="text-gray-500">The CSV file for importing Payors / TPA must contain these columns:</p>
            <ul className="list-disc pl-5 space-y-1 text-gray-600">
              <li><strong className="text-gray-800">Payer Name</strong>: Name of the payer / TPA / insurance company (e.g., <code className="bg-gray-100 px-1 rounded text-red-600">Star Health Insurance</code>).</li>
              <li><strong className="text-gray-800">Payer Type</strong>: Type of payer (e.g., <code className="bg-gray-100 px-1 rounded text-red-600">COMPANY</code>, <code className="bg-gray-100 px-1 rounded text-red-600">INSURANCE</code>, <code className="bg-gray-100 px-1 rounded text-red-600">TPA</code>, <code className="bg-gray-100 px-1 rounded text-red-600">GOVERNMENT</code>).</li>
            </ul>
          </div>
        ) : (
          <p className="text-xs text-gray-500 italic">
            Download the template using the button above to view all required and optional columns for the selected entity type.
          </p>
        )}
      </div>

      {/* Results */}
      {result && (
        <div className={cn(
          'bg-white border rounded-xl p-5 space-y-3',
          result.errorCount === 0 ? 'border-green-200' : 'border-amber-200'
        )} role="region" aria-label="Import results">
          <h3 className="text-sm font-semibold text-gray-900">Import Results</h3>

          <div className="grid grid-cols-4 gap-3">
            {[
              { label: 'Total Rows',  value: result.totalRows,    color: 'text-gray-700' },
              { label: 'Created',     value: result.createdCount, color: 'text-green-700' },
              { label: 'Skipped',     value: result.skippedCount, color: 'text-amber-700' },
              { label: 'Errors',      value: result.errorCount,   color: 'text-red-700'   },
            ].map(({ label, value, color }) => (
              <div key={label} className="text-center bg-gray-50 rounded-lg py-3">
                <p className={cn('text-2xl font-bold', color)}>{value}</p>
                <p className="text-xs text-gray-500 mt-0.5">{label}</p>
              </div>
            ))}
          </div>

          {result.errors.length > 0 && (
            <div className="bg-red-50 border border-red-200 rounded-lg p-3 space-y-1 max-h-48 overflow-y-auto">
              <p className="text-xs font-semibold text-red-700 mb-1">
                Row Errors (showing up to 50):
              </p>
              {result.errors.map((e, i) => (
                <p key={i} className="text-xs text-red-600 font-mono">{e}</p>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
