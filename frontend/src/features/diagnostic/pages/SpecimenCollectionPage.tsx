import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { diagnosticApi } from '../../../services/diagnostic/diagnosticApi'
import { useSpecimenCollections, useDiagnosticMutations } from '../../../hooks/diagnostic/useDiagnostic'
import { formatDateTime } from '../../../lib/dateUtils'
import { Syringe, Clipboard } from 'lucide-react'
import BackButton from '../../../components/shared/BackButton'

export default function SpecimenCollectionPage() {
  const navigate = useNavigate()
  const { orderId } = useParams<{ orderId: string }>()
  const { recordSpecimenCollection } = useDiagnosticMutations()

  const { data: order, isLoading: orderLoading } = useQuery({
    queryKey: ['diagnosticOrder', orderId],
    queryFn: () => diagnosticApi.getById(orderId!),
    enabled: !!orderId,
  })

  const { data: collections = [], isLoading: collectionsLoading } = useSpecimenCollections(orderId!)
  const [localCollected, setLocalCollected] = useState<Record<string, boolean>>({})
  const getCollectionForLine = (lineId: string) => {
    if (!order) return undefined
    const line = order.lines.find(l => l.id === lineId)
    if (!line) return undefined
    const exactMatch = collections.find(c => c.orderLineId === lineId)
    if (exactMatch) return exactMatch
    return collections.find(c => c.specimenId === line.specimenId && !c.orderLineId)
  }

  const handleRecord = async (lineId: string) => {
    if (!order) return
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

  const isLoading = orderLoading || collectionsLoading

  if (isLoading) return <div className="flex items-center justify-center h-64 text-gray-500">Loading…</div>
  if (!order) return <div className="text-center py-12 text-gray-400">Order not found</div>

  return (
    <div className="max-w-4xl mx-auto px-4 py-6 space-y-6">
      <div className="flex items-center justify-between border-b border-gray-100 pb-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-teal-100 rounded-lg flex items-center justify-center">
            <Syringe className="w-5 h-5 text-teal-700" />
          </div>
          <div>
            <h2 className="text-2xl font-bold text-gray-900 tracking-tight">Specimen Collection</h2>
            <p className="text-sm text-gray-500">Order: {order.sequenceNumber || order.id.slice(0, 8)} • Patient: {order.patientName}</p>
          </div>
        </div>
        <BackButton />
      </div>

      <div className="bg-white rounded-2xl border border-gray-100 shadow-sm">
        {order.lines.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-gray-400">
            <div className="w-16 h-16 bg-gray-50 rounded-full flex items-center justify-center mb-4">
              <Clipboard className="w-8 h-8 text-gray-300" />
            </div>
            <p>No tests found in this order</p>
          </div>
        ) : (
          <div className="border border-gray-200 rounded-xl overflow-hidden shadow-sm m-6">
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
                            {recordSpecimenCollection.isPending ? '...' : 'Collect'}
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

        <div className="flex justify-end px-6 py-4 border-t border-gray-100 bg-gray-50/50 rounded-b-2xl">
          <button onClick={() => navigate(-1)}
            className="px-6 py-2 text-sm font-bold text-gray-600 bg-white border border-gray-200 rounded-xl hover:bg-gray-50 transition-colors shadow-sm">
            Done
          </button>
        </div>
      </div>
    </div>
  )
}
