import { useState } from 'react'
import type { DiagnosticOrder } from '../../../types/diagnostic'
import { useSpecimenCollections, useDiagnosticMutations } from '../../../hooks/diagnostic/useDiagnostic'
import { formatDateTime } from '../../../lib/dateUtils'
import { Syringe, Clipboard, X } from 'lucide-react'

interface Props { order: DiagnosticOrder; onClose: () => void }

export function SpecimenCollectionModal({ order, onClose }: Props) {
  const { data: collections = [], isLoading } = useSpecimenCollections(order.id)
  const { recordSpecimenCollection } = useDiagnosticMutations()
  const [localCollected, setLocalCollected] = useState<Record<string, boolean>>({})

  const getCollectionForLine = (lineId: string) => {
    const line = order.lines.find(l => l.id === lineId)
    if (!line) return undefined
    
    // 1. Exact match by orderLineId
    const exactMatch = collections.find(c => c.orderLineId === lineId)
    if (exactMatch) return exactMatch

    // 2. Fallback to specimen match if no orderLineId is present (legacy or group collection)
    return collections.find(c => c.specimenId === line.specimenId && !c.orderLineId)
  }

  const handleRecord = async (lineId: string) => {
    const line = order.lines.find(l => l.id === lineId)
    if (!line) return

    try {
      await recordSpecimenCollection.mutateAsync({
        diagnosticId: order.id,
        specimenId: line.specimenId || undefined,
        orderLineId: lineId,
        notes: `Collected for ${line.itemName}`
      })
      setLocalCollected(prev => ({ ...prev, [lineId]: true }))
    } catch (error) {
      console.error('Failed to record collection', error)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm" onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-2xl w-[70vw] max-w-3xl max-h-[85vh] flex flex-col" onClick={e => e.stopPropagation()}>
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 bg-gradient-to-r from-teal-50 to-white rounded-t-2xl">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-teal-100 rounded-lg flex items-center justify-center">
              <Syringe className="w-5 h-5 text-teal-700" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-gray-900">Specimen Collection</h2>
              <p className="text-xs text-gray-500 mt-0.5">Order: {order.sequenceNumber || order.id.slice(0, 8)} • Patient: {order.patientName}</p>
            </div>
          </div>
          <button onClick={onClose} className="w-8 h-8 rounded-lg bg-gray-100 hover:bg-gray-200 flex items-center justify-center text-gray-500 transition-colors">
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-4">
          {isLoading ? (
            <div className="flex items-center justify-center py-20">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-teal-500"></div>
            </div>
          ) : order.lines.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-20 text-gray-400">
              <div className="w-16 h-16 bg-gray-50 rounded-full flex items-center justify-center mb-4">
                <Clipboard className="w-8 h-8 text-gray-300" />
              </div>
              <p>No tests found in this order</p>
            </div>
          ) : (
            <div className="border border-gray-200 rounded-xl overflow-hidden shadow-sm">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-gray-50 text-xs font-bold text-gray-500 uppercase tracking-tight">
                    <th className="px-4 py-3 text-left">Test Name</th>
                    <th className="px-4 py-3 text-left">Specimen Type</th>
                    <th className="px-4 py-3 text-center">Sample ID</th>
                    <th className="px-4 py-3 text-center">Status</th>
                    <th className="px-4 py-3 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {order.lines.map(line => {
                    const collection = getCollectionForLine(line.id)
                    const collected = !!collection || localCollected[line.id]

                    return (
                      <tr key={line.id} className="hover:bg-teal-50/20 transition-colors">
                        <td className="px-4 py-4 font-semibold text-gray-800">{line.itemName}</td>
                        <td className="px-4 py-4">
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-blue-50 text-blue-700 border border-blue-100">
                            {line.specimenName || 'Not Specified'}
                          </span>
                        </td>
                        <td className="px-4 py-4 text-center">
                          {collection ? (
                            <code className="text-xs font-bold text-teal-700 bg-teal-50 px-2 py-1 rounded border border-teal-100">
                              {collection.sampleNumber}
                            </code>
                          ) : (
                            <span className="text-gray-300">—</span>
                          )}
                        </td>
                        <td className="px-4 py-4 text-center">
                          {collected ? (
                            <span className="inline-flex items-center gap-1.5 text-xs font-bold text-emerald-600">
                              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
                              Collected
                            </span>
                          ) : (
                            <span className="text-xs font-medium text-amber-500">Pending</span>
                          )}
                        </td>
                        <td className="px-4 py-4 text-right">
                          {!collected ? (
                            <button
                              onClick={() => handleRecord(line.id)}
                              disabled={recordSpecimenCollection.isPending}
                              className="px-4 py-1.5 text-xs font-bold text-white bg-teal-600 rounded-lg hover:bg-teal-700 shadow-sm transition-all active:scale-95 disabled:opacity-50"
                            >
                              {recordSpecimenCollection.isPending ? '...' : 'Record'}
                            </button>
                          ) : (
                            <span className="text-[10px] text-gray-400 font-medium">
                              {collection ? formatDateTime(collection.collectedAt) : 'Just now'}
                            </span>
                          )}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div className="flex justify-end px-6 py-4 border-t border-gray-100 bg-gray-50/50 rounded-b-2xl">
          <button onClick={onClose}
            className="px-6 py-2 text-sm font-bold text-gray-600 bg-white border border-gray-200 rounded-xl hover:bg-gray-50 transition-colors shadow-sm">
            Done
          </button>
        </div>
      </div>
    </div>
  )
}

